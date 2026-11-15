package com.miplata.kotlin.nucleo

/* Lee la planilla de asistencia del taller a partir del texto que el teléfono reconoce en la foto
   (sin internet). Cada pedacito de texto viene con su posición (x, y) en la foto.

   Cómo se interpreta (pensado para fotos reales: torcidas, con letras mal leídas):
   1. Se juntan todas las horas escritas (07:00, 15:00, 04:00…).
   2. Se calcula cuánto está torcida la foto y se arman las FILAS: horas que están a la misma altura.
      Cada fila de la planilla tiene horas aunque sea franco, porque la columna TOTAL dice 00:00.
   3. La columna TOTAL (a la derecha) se separa sola: es el hueco más grande entre columnas de horas.
   4. En cada fila, la primera hora antes de TOTAL es la entrada. Sin entrada = franco.
   5. El número de día sale de las etiquetas "01-nov", "02-nov"… si se leyeron; si no, por el orden de las filas. */
object LeerPlanilla {
    data class Pieza(val texto: String, val x: Float, val y: Float)
    data class Resultado(val mes: Int?, val entradas: Map<Int, String>)

    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val NOMBRES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    /** Arregla confusiones típicas del lector en números: O→0, l/I→1. */
    private fun numeros(t: String) = t.replace('O', '0').replace('o', '0').replace('l', '1').replace('I', '1')

    /* Sin expresiones regulares: en algunos Android fallaban al arrancar. Se revisa letra por letra. */
    private fun esNum(c: Char) = c in '0'..'9' || c == 'O' || c == 'o' || c == 'l' || c == 'I'

    /** "01-nov", "[07-nov|", "10-novjMARTI" → (1, "nov") */
    private fun dia(t: String): Pair<String, String>? {
        for (i in t.indices) {
            if (t[i] != '-' && t[i] != '–' && t[i] != '.') continue
            var a = i - 1; while (a >= 0 && t[a] == ' ') a--
            var ini = a; while (ini >= 0 && esNum(t[ini]) && a - ini < 2) ini--
            val num = t.substring(ini + 1, a + 1)
            if (num.isEmpty() || num.length > 2 || (ini >= 0 && t[ini].isDigit())) continue
            var b = i + 1; while (b < t.length && t[b] == ' ') b++
            if (b + 3 > t.length) continue
            val letras = t.substring(b, b + 3)
            if (letras.all { it.isLetter() }) return num to letras
        }
        return null
    }

    /** "07:00", "08:00)", "O7:00" → "07:00". No toma "184:00:00" ni textos largos. */
    private fun hora(t0: String): Pair<Int, String>? {
        val t = numeros(t0.trim { !it.isLetterOrDigit() }).trim { !(it.isDigit() || it == ':' || it == '.' || it == ';' || it == ',') }
        val sep = t.indexOfFirst { it == ':' || it == '.' || it == ';' || it == ',' }
        if (sep < 1 || sep > 2) return null
        val h = t.substring(0, sep); val m = t.substring(sep + 1)
        if (m.length != 2 || !h.all { it.isDigit() } || !m.all { it.isDigit() }) return null
        val hh = h.toInt(); if (hh > 23 || m.toInt() > 59) return null
        return hh to m
    }

    private class H(val hora: String, val x: Float, val y: Float)

    fun leer(piezas: List<Pieza>, diasDelMes: Int = 31): Resultado {
        var mes: Int? = null
        piezas.forEach { p -> NOMBRES.indexOfFirst { p.texto.lowercase().contains(it) }.takeIf { it >= 0 }?.let { mes = it + 1 } }

        /* Etiquetas de día (pueden faltar o estar mal leídas) */
        val etiquetas = piezas.mapNotNull { p ->
            val (n, letras) = dia(p.texto) ?: return@mapNotNull null
            val i = MESES.indexOf(letras.lowercase().replace("set", "sep")); if (i < 0) return@mapNotNull null
            val d = numeros(n).toIntOrNull()?.takeIf { it in 1..31 } ?: return@mapNotNull null
            if (mes == null) mes = i + 1
            Triple(d, p.x, p.y)
        }.distinctBy { it.first }

        val horas = piezas.mapNotNull { p ->
            if (p.texto.length > 9) return@mapNotNull null
            val (h, m) = hora(p.texto) ?: return@mapNotNull null
            H((if (h < 10) "0" else "") + h + ":" + m, p.x, p.y)
        }
        if (horas.size < 8) return Resultado(mes, emptyMap())

        /* 2. Inclinación: la que más alinea las horas en filas */
        val alto = (horas.maxOf { it.y } - horas.minOf { it.y }).coerceAtLeast(1f)
        val tol = alto / (diasDelMes * 4f)                         // un cuarto de fila
        var pendiente = 0f; var mejor = -1
        var s = -0.15f
        while (s <= 0.1501f) {
            val ys = horas.map { it.y - s * it.x }.sorted()
            val juntas = ys.zipWithNext().count { (a, b) -> b - a < tol }
            if (juntas > mejor || (juntas == mejor && Math.abs(s) < Math.abs(pendiente))) { mejor = juntas; pendiente = s }
            s += 0.0025f
        }
        fun yr(x: Float, y: Float) = y - pendiente * x

        /* Filas: se corta donde hay un salto de más de media fila */
        val orden = horas.sortedBy { yr(it.x, it.y) }
        val filas = mutableListOf(mutableListOf(orden.first()))
        orden.zipWithNext().forEach { (a, b) -> if (yr(b.x, b.y) - yr(a.x, a.y) > tol * 2) filas += mutableListOf(b) else filas.last() += b }
        val centros = filas.map { f -> f.map { yr(it.x, it.y) }.average().toFloat() }
        val etiq = etiquetas.map { (d, x, y) -> d to yr(x, y) }.sortedBy { it.second }
        /* Alto de una fila: de las etiquetas de día si hay, si no de las filas de horas (los saltos grandes, que son filas de verdad) */
        val pasoEtiq = etiq.zipWithNext { a, b -> (b.second - a.second) / (b.first - a.first) }.filter { it > 0 }.sorted()
        val saltos = centros.zipWithNext { a, b -> b - a }.sorted()
        val grandes = saltos.filter { it > (saltos.lastOrNull() ?: 0f) * 0.6f }
        val paso = (pasoEtiq.getOrNull(pasoEtiq.size / 2) ?: grandes.getOrNull(grandes.size / 2) ?: saltos.getOrNull(saltos.size / 2))
            ?: return Resultado(mes, emptyMap())

        /* 3. Columna TOTAL: el hueco más grande entre las columnas de horas */
        val xs = horas.map { it.x }.sorted()
        val hueco = xs.zipWithNext().maxByOrNull { (a, b) -> b - a }
        val ancho = (xs.last() - xs.first()).coerceAtLeast(1f)
        val limite = if (hueco != null && hueco.second - hueco.first > ancho * 0.2f) (hueco.first + hueco.second) / 2 else Float.MAX_VALUE

        /* 5. Número de día de cada hora: por su altura, contando filas desde una etiqueta de día (o desde la primera fila) */
        fun diaDe(y: Float): Int {
            if (etiq.size >= 3) {
                val (d, ye) = etiq.minByOrNull { Math.abs(it.second - y) }!!
                return d + Math.round((y - ye) / paso)
            }
            return 1 + Math.round((y - centros.first()) / paso)
        }

        val porDia = horas.groupBy { diaDe(yr(it.x, it.y)) }.filterKeys { it in 1..diasDelMes }
        val entradas = porDia.mapValues { (_, l) -> l.filter { it.x < limite }.minByOrNull { it.x }?.hora ?: "" }
        return Resultado(mes, entradas)
    }
}
