package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*

/* 💰 Mi aguinaldo: una "app chiquita" dentro de Mi plata que aparece en temporada de aguinaldo
   (diciembre, enero, junio y julio) o mientras te quede plata del aguinaldo sin usar.
   La plata queda APARTE: no se suma a tu día. La repartís en partes (Ahorro, Vacaciones, Fiestas...)
   y cuando usás una parte, la pasás al mes como gasto importante o la repartís en los días que quedan. */

data class ParteAgui(val n: String, val m: Double)

/** Un uso de una parte. como = "gasto" (gasto importante del mes) o "dias" (se reparte en los días que quedan).
    vac = id del gasto importante al que va pegado (se borra el gasto → se borra el uso). */
data class UsoAgui(val id: Long, val parte: String, val n: String, val amt: Double, val date: String, val como: String, val vac: Long? = null)

data class Aguinaldo(
    val cobrado: Double = 0.0,
    val partes: List<ParteAgui> = DE_FABRICA,
    val usos: List<UsoAgui> = emptyList(),
) {
    val usado: Double get() = usos.sumOf { it.amt }
    val queda: Double get() = cobrado - usado
    val sinRepartir: Double get() = cobrado - partes.sumOf { it.m }
    fun usadoDe(parte: String): Double = usos.filter { it.parte == parte }.sumOf { it.amt }
    fun quedaDe(parte: String): Double = (partes.firstOrNull { it.n == parte }?.m ?: 0.0) - usadoDe(parte)
    /** La parte de las vacaciones (la primera cuyo nombre diga "vacacion"). */
    val parteVacaciones: String? get() = partes.firstOrNull { it.n.lowercase().contains("vacacion") }?.n

    fun aJson(): JsonObject = JsonObject(mapOf(
        "cobrado" to num(cobrado),
        "partes" to JsonArray(partes.map { JsonObject(mapOf("n" to JsonPrimitive(it.n), "m" to num(it.m))) }),
        "usos" to JsonArray(usos.map { u -> JsonObject(buildMap {
            put("id", JsonPrimitive(u.id)); put("parte", JsonPrimitive(u.parte)); put("n", JsonPrimitive(u.n))
            put("amt", num(u.amt)); put("date", JsonPrimitive(u.date)); put("como", JsonPrimitive(u.como))
            u.vac?.let { put("vac", JsonPrimitive(it)) }
        }) }),
    ))

    companion object {
        const val CLAVE = "aguinaldo"
        val DE_FABRICA = listOf(ParteAgui("Ahorro", 0.0), ParteAgui("Vacaciones", 0.0), ParteAgui("Fiestas", 0.0), ParteAgui("Planes pendientes", 0.0))

        fun desde(o: JsonObject?): Aguinaldo {
            val a = o?.get(CLAVE) as? JsonObject ?: return Aguinaldo()
            return Aguinaldo(
                cobrado = a["cobrado"].num(),
                partes = a.lista("partes").map { ParteAgui(it["n"].texto() ?: "", it["m"].num()) },
                usos = a.lista("usos").map {
                    UsoAgui(it["id"].num().toLong(), it["parte"].texto() ?: "", it["n"].texto() ?: "", it["amt"].num(),
                        it["date"].texto() ?: "", it["como"].texto() ?: "gasto", it["vac"]?.takeIf { v -> v !is JsonNull }?.num()?.toLong())
                },
            )
        }

        /** ¿Es temporada de aguinaldo? (se cobra en diciembre y en junio/julio; se usa hasta el mes siguiente) */
        fun esTemporada(hoy: String): Boolean = hoy.substring(5, 7).toIntOrNull() in setOf(12, 1, 6, 7)

        /** Se muestra en temporada o mientras quede plata del aguinaldo sin usar. */
        fun seMuestra(o: JsonObject?, hoy: String): Boolean = esTemporada(hoy) || desde(o).queda >= 1
    }
}
