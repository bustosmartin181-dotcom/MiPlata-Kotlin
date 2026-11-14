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

    private val DIA = Regex("""(?<![0-9])([0-9OolI]{1,2})\s*[-–.]\s*([a-zA-Z]{3})""")
    private val HORA = Regex("""(?<![0-9:])([0-2]?\d)[:.;,]([0-5]\d)(?![0-9:])""")

    private class H(val hora: String, val x: Float, val y: Float)

    fun leer(piezas: List<Pieza>, diasDelMes: Int = 31): Resultado {
        var mes: Int? = null
        piezas.forEach { p -> NOMBRES.indexOfFirst { p.texto.lowercase().contains(it) }.takeIf { it >= 0 }?.let { mes = it + 1 } }

        /* Etiquetas de día (pueden faltar o estar mal leídas) */
        val etiquetas = piezas.mapNotNull { p ->
            val m = DIA.find(p.texto) ?: return@mapNotNull null
            val i = MESES.indexOf(m.groupValues[2].lowercase().replace("set", "sep")); if (i < 0) return@mapNotNull null
            val d = numeros(m.groupValues[1]).toIntOrNull()?.takeIf { it in 1..31 } ?: return@mapNotNull null
            if (mes == null) mes = i + 1
            Triple(d, p.x, p.y)
        }.distinctBy { it.first }

        val horas = piezas.mapNotNull { p ->
            if (p.texto.length > 9) return@mapNotNull null
            val m = HORA.find(numeros(p.texto)) ?: return@mapNotNull null
            val h = m.groupValues[1].toInt(); if (h > 23) return@mapNotNull null
            H("%02d:%s".format(h, m.groupValues[2]), p.x, p.y)
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
