package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*

/* "Vacaciones": un simulador de gastos que vive dentro de Mi plata.
   Se arma con calma (días, viaje, alojamiento, comida...) y no toca tus números
   hasta que tocás "Cargar". Ahí entra como UN solo gasto importante del período.
   Si lo pagás con plata aparte (aguinaldo, ahorro), entra también esa plata y tu día no cambia. */

/** Un rubro de las vacaciones. porDia = el monto se multiplica por la cantidad de días.
    viaje = este rubro es el viaje (auto o avión); si es en auto, el monto sale de la calculadora de nafta. */
data class Rubro(val n: String, val monto: Double, val porDia: Boolean, val viaje: Viaje? = null)

/** Cómo viajás. Avión: ponés el monto de los pasajes. Auto: km, cuánto rinde y el precio del litro. */
data class Viaje(
    val medio: String = AUTO,
    val kmIda: Double = 0.0,
    val kmPorLitro: Double = 0.0,
    val precioLitro: Double = 0.0,
    val idaYVuelta: Boolean = true,
    val peajes: Double = 0.0,
) {
    val km: Double get() = kmIda * if (idaYVuelta) 2 else 1
    val litros: Double get() = if (kmPorLitro > 0) km / kmPorLitro else 0.0
    /** Lo que sale el viaje en auto: nafta + peajes, redondeado al peso. */
    val costoAuto: Double get() = Math.round(litros * precioLitro + peajes).toDouble()
    val listoAuto: Boolean get() = kmIda > 0 && kmPorLitro > 0 && precioLitro > 0

    fun aJson(): JsonObject = JsonObject(mapOf("medio" to JsonPrimitive(medio), "km" to num(kmIda), "kml" to num(kmPorLitro),
        "pl" to num(precioLitro), "iv" to JsonPrimitive(idaYVuelta), "peajes" to num(peajes)))

    companion object {
        const val AUTO = "auto"; const val AVION = "avion"
        fun desde(o: JsonObject) = Viaje(if (o["medio"].texto() == AVION) AVION else AUTO, o["km"].num(), o["kml"].num(), o["pl"].num(),
            (o["iv"] as? JsonPrimitive)?.booleanOrNull != false, o["peajes"].num())
    }
}

data class Vacaciones(
    val dias: Int = 7,
    val rubros: List<Rubro> = DE_FABRICA,
    val imprevistos: Int = 10,         // % que se suma por las dudas
    val aparte: Boolean = true,        // true = se paga con plata aparte (aguinaldo/ahorro); false = sale del sueldo del mes
) {
    fun totalRubro(r: Rubro): Double {
        val enAuto = r.viaje?.takeIf { it.medio == Viaje.AUTO && it.listoAuto }
        return when {
        enAuto != null -> enAuto.costoAuto
        r.porDia -> r.monto * dias.coerceAtLeast(1)
        else -> r.monto
        }
    }
    val subtotal: Double get() = rubros.sumOf { totalRubro(it) }
    val montoImprevistos: Double get() = Math.round(subtotal * imprevistos.coerceIn(0, 100) / 100.0).toDouble()
    val total: Double get() = Math.round(subtotal).toDouble() + montoImprevistos

    fun aJson(): JsonObject = JsonObject(mapOf(
        "dias" to JsonPrimitive(dias),
        "imp" to JsonPrimitive(imprevistos),
        "aparte" to JsonPrimitive(aparte),
        "rubros" to JsonArray(rubros.map { r -> JsonObject(buildMap {
            put("n", JsonPrimitive(r.n)); put("m", num(r.monto)); put("d", JsonPrimitive(r.porDia))
            r.viaje?.let { put("viaje", it.aJson()) }
        }) }),
    ))

    companion object {
        const val CLAVE = "vacaciones"
        const val NOMBRE_GASTO = "🏖️ Vacaciones"
        val DE_FABRICA = listOf(
            Rubro("Viaje", 0.0, false, Viaje()),
            Rubro("Alojamiento (por noche)", 0.0, true),
            Rubro("Comida (por día)", 0.0, true),
            Rubro("Salidas y paseos (por día)", 0.0, true),
        )

        fun desde(o: JsonObject?): Vacaciones {
            val v = o?.get(CLAVE) as? JsonObject ?: return Vacaciones()
            val rubros = v.lista("rubros").map { Rubro(it["n"].texto() ?: "", it["m"].num(), it["d"].verdadero(), (it["viaje"] as? JsonObject)?.let { j -> Viaje.desde(j) }) }
            /* simulaciones guardadas antes de la calculadora: el rubro que dice "viaje" pasa a ser el viaje */
            val conViaje = if (rubros.any { it.viaje != null }) rubros else {
                val i = rubros.indexOfFirst { it.n == "Viaje (ida y vuelta)" }   // el nombre de fábrica de antes
                if (i < 0) rubros else rubros.mapIndexed { j, r -> if (j == i) r.copy(viaje = Viaje(Viaje.AVION)) else r }
            }
            return Vacaciones(
                dias = v["dias"].num().toInt().coerceIn(1, 60),
                rubros = conViaje,
                imprevistos = v["imp"].num().toInt().coerceIn(0, 100),
                aparte = (v["aparte"] as? JsonPrimitive)?.booleanOrNull != false,
            )
        }
    }
}
