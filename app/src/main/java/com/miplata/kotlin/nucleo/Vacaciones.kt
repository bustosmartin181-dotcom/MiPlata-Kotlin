package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*

/* "Vacaciones": un simulador de gastos que vive dentro de Mi plata.
   Se arma con calma (días, viaje, alojamiento, comida...) y no toca tus números
   hasta que tocás "Cargar". Ahí entra como UN solo gasto importante del período.
   Si lo pagás con plata aparte (aguinaldo, ahorro), entra también esa plata y tu día no cambia. */

/** Un rubro de las vacaciones. porDia = el monto se multiplica por la cantidad de días. */
data class Rubro(val n: String, val monto: Double, val porDia: Boolean)

data class Vacaciones(
    val dias: Int = 7,
    val rubros: List<Rubro> = DE_FABRICA,
    val imprevistos: Int = 10,         // % que se suma por las dudas
    val aparte: Boolean = true,        // true = se paga con plata aparte (aguinaldo/ahorro); false = sale del sueldo del mes
) {
    fun totalRubro(r: Rubro): Double = if (r.porDia) r.monto * dias.coerceAtLeast(1) else r.monto
    val subtotal: Double get() = rubros.sumOf { totalRubro(it) }
    val montoImprevistos: Double get() = Math.round(subtotal * imprevistos.coerceIn(0, 100) / 100.0).toDouble()
    val total: Double get() = Math.round(subtotal).toDouble() + montoImprevistos

    fun aJson(): JsonObject = JsonObject(mapOf(
        "dias" to JsonPrimitive(dias),
        "imp" to JsonPrimitive(imprevistos),
        "aparte" to JsonPrimitive(aparte),
        "rubros" to JsonArray(rubros.map { JsonObject(mapOf("n" to JsonPrimitive(it.n), "m" to num(it.monto), "d" to JsonPrimitive(it.porDia))) }),
    ))

    companion object {
        const val CLAVE = "vacaciones"
        const val NOMBRE_GASTO = "🏖️ Vacaciones"
        val DE_FABRICA = listOf(
            Rubro("Viaje (ida y vuelta)", 0.0, false),
            Rubro("Alojamiento (por noche)", 0.0, true),
            Rubro("Comida (por día)", 0.0, true),
            Rubro("Salidas y paseos (por día)", 0.0, true),
        )

        fun desde(o: JsonObject?): Vacaciones {
            val v = o?.get(CLAVE) as? JsonObject ?: return Vacaciones()
            val rubros = v.lista("rubros").map { Rubro(it["n"].texto() ?: "", it["m"].num(), it["d"].verdadero()) }
            return Vacaciones(
                dias = v["dias"].num().toInt().coerceIn(1, 60),
                rubros = rubros,
                imprevistos = v["imp"].num().toInt().coerceIn(0, 100),
                aparte = (v["aparte"] as? JsonPrimitive)?.booleanOrNull != false,
            )
        }
    }
}
