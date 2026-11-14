package com.miplata.kotlin.nucleo

/* Lee la planilla de asistencia del taller a partir del texto que el teléfono reconoce en la foto
   (sin internet). Cada pedacito de texto viene con su posición (x, y) en la foto.

   Cómo se interpreta:
   - Las filas son los días: "01-nov", "02-nov"… a la izquierda.
   - En cada fila, la primera hora escrita (de MAÑANA o TARDE) es la entrada.
   - Las horas de la columna TOTAL (a la derecha) no se toman en cuenta.
   - Una fila sin horas de entrada es franco.
   La foto puede estar un poco torcida: se corrige con la inclinación de los títulos (MAÑANA … TOTAL). */
object LeerPlanilla {
    data class Pieza(val texto: String, val x: Float, val y: Float)
    data class Resultado(val mes: Int?, val entradas: Map<Int, String>)

    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val NOMBRES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    /** Arregla confusiones típicas del lector en números: O→0, l/I→1. */
    private fun numeros(t: String) = t.replace('O', '0').replace('o', '0').replace('l', '1').replace('I', '1')

    private val DIA = Regex("""(?<![0-9])([0-9OolI]{1,2})\s*[-–.]\s*([a-zA-Z]{3})""")
    private val HORA = Regex("""(?<![0-9])([0-2]?\d)[:.;,]([0-5]\d)(?![0-9])""")

    fun leer(piezas: List<Pieza>): Resultado {
        /* Mes: "MES NOVIEMBRE" o el "-nov" de los días */
        var mes: Int? = null
        piezas.forEach { p -> NOMBRES.indexOfFirst { p.texto.lowercase().contains(it) }.takeIf { it >= 0 }?.let { mes = it + 1 } }

        val dias = piezas.mapNotNull { p ->
            val m = DIA.find(p.texto) ?: return@mapNotNull null
            val abrev = m.groupValues[2].lowercase().replace("set", "sep")
            val i = MESES.indexOf(abrev); if (i < 0) return@mapNotNull null
            val d = numeros(m.groupValues[1]).toIntOrNull()?.takeIf { it in 1..31 } ?: return@mapNotNull null
            if (mes == null) mes = i + 1
            Triple(d, p.x, p.y)
        }.groupBy { it.first }.map { (_, l) -> l.first() }.sortedBy { it.third }
        if (dias.size < 3) return Resultado(mes, emptyMap())

        val horas = piezas.mapNotNull { p ->
            if (p.texto.length > 9) return@mapNotNull null            // un pedazo largo no es una hora suelta
            val m = HORA.find(numeros(p.texto)) ?: return@mapNotNull null
            val h = m.groupValues[1].toInt(); if (h > 23) return@mapNotNull null
            Triple("%02d:%s".format(h, m.groupValues[2]), p.x, p.y)
        }

        /* Inclinación: con los títulos MAÑANA y TOTAL (si se leyeron) */
        val manana = piezas.firstOrNull { it.texto.uppercase().replace("Ñ", "N").contains("MANANA") }
        val total = piezas.firstOrNull { it.texto.uppercase() == "TOTAL" }
        val pendiente = if (manana != null && total != null && total.x - manana.x > 50) (total.y - manana.y) / (total.x - manana.x) else 0f
        /* Hasta dónde llegan las columnas de entrada/salida: antes de TOTAL (o del 60 % del ancho) */
        val anchoMax = (piezas.maxOfOrNull { it.x } ?: 0f)
        val limite = total?.let { it.x - 20 } ?: (anchoMax * 0.6f)

        /* Alto de una fila: la distancia típica entre días seguidos */
        val saltos = dias.zipWithNext { a, b -> (b.third - a.third) / (b.first - a.first).coerceAtLeast(1) }.filter { it > 0 }.sorted()
        val alto = saltos.getOrNull(saltos.size / 2) ?: return Resultado(mes, emptyMap())

        val entradas = mutableMapOf<Int, String>()
        dias.forEach { (d, xd, yd) ->
            val enFila = horas.filter { (_, x, y) -> x < limite && x > xd && Math.abs(y - (yd + pendiente * (x - xd))) < alto * 0.5f }
            entradas[d] = enFila.minByOrNull { it.second }?.first ?: ""
        }
        return Resultado(mes, entradas)
    }
}
