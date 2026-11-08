package com.miplata.kotlin.nucleo

import java.time.LocalDate

/* Fechas como texto "AAAA-MM-DD" y como número de día (días desde 1970-01-01). */
object Fechas {
    fun dia(iso: String): Long = LocalDate.parse(iso.take(10)).toEpochDay()
    fun iso(n: Long): String = LocalDate.ofEpochDay(n).toString()
    fun hoy(): String = LocalDate.now().toString()
    fun manana(iso: String) = iso(dia(iso) + 1)
    /** Mismo día del mes siguiente (como Date.UTC de JavaScript: 31/01 -> 03/03). */
    fun mesSiguiente(iso: String): String {
        val (a, m, d) = iso.split("-").map { it.toInt() }
        return LocalDate.of(a, m, 1).plusMonths(1).plusDays((d - 1).toLong()).toString()
    }
    fun diaYMes(iso: String) = iso.substring(8) + "/" + iso.substring(5, 7)
    val NOMBRES_DIA = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
    fun nombreDia(n: Long) = NOMBRES_DIA[LocalDate.ofEpochDay(n).dayOfWeek.value - 1]

    /* Feriados de Argentina y Mendoza. sinBancos = true: ese día Mercado Pago no acredita rendimientos. */
    data class Feriado(val nombre: String, val sinBancos: Boolean)
    val FERIADOS = mapOf(
        "2026-11-06" to Feriado("Día del Bancario", true), "2026-11-09" to Feriado("Visita del Papa", true),
        "2026-11-10" to Feriado("Feriado bancario", true), "2026-11-23" to Feriado("Día de la Soberanía Nacional", true),
        "2026-12-07" to Feriado("Día no laborable (puente)", true), "2026-12-08" to Feriado("Inmaculada Concepción", true),
        "2026-12-25" to Feriado("Navidad", true),
        "2027-01-01" to Feriado("Año Nuevo", true), "2027-02-08" to Feriado("Carnaval", true), "2027-02-09" to Feriado("Carnaval", true),
        "2027-03-24" to Feriado("Día de la Memoria", true), "2027-03-25" to Feriado("Jueves Santo", true),
        "2027-03-26" to Feriado("Viernes Santo", true), "2027-04-02" to Feriado("Malvinas", true),
        "2027-05-01" to Feriado("Día del Trabajador", true), "2027-05-25" to Feriado("Revolución de Mayo", true),
        "2027-06-20" to Feriado("Día de la Bandera", true), "2027-06-21" to Feriado("Paso a la Inmortalidad de Güemes", true),
        "2027-07-09" to Feriado("Día de la Independencia", true), "2027-07-25" to Feriado("Santiago Apóstol (Mendoza)", false),
        "2027-11-06" to Feriado("Día del Bancario", true), "2027-12-08" to Feriado("Inmaculada Concepción", true),
        "2027-12-25" to Feriado("Navidad", true),
    )
    fun feriado(iso: String) = FERIADOS[iso]
    /** Día hábil bancario: lunes a viernes y no feriado nacional/bancario. */
    fun esHabil(n: Long): Boolean {
        val d = LocalDate.ofEpochDay(n).dayOfWeek.value
        val f = FERIADOS[iso(n)]
        return d in 1..5 && !(f != null && f.sinBancos)
    }
}
