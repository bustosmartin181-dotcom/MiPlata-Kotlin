package com.miplata.kotlin.ui

import com.miplata.kotlin.nucleo.texto
import kotlinx.serialization.json.*

/* Tasa de Mercado Pago automática.
   La plata de Mercado Pago rinde en "Mercado Fondo - Clase A" (un fondo común de inversión).
   ArgentinaDatos publica gratis los datos de la CNV de cada fondo, con su TNA estimada de los últimos días.
   La app la baja sola y la usa como tasa (igual que el IPC del INDEC para los alquileres). */
object TasaMP {
    private const val URL = "https://api.argentinadatos.com/v1/finanzas/fci/comparatasas"
    private const val PREFS = "tasa_mp"

    data class Dato(val tasa: Double, val fecha: String, val cuando: Long)

    fun guardada(ctx: android.content.Context): Dato? {
        val p = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val t = p.getFloat("tasa", -1f).toDouble().takeIf { it > 0 } ?: return null
        return Dato(t, p.getString("fecha", "") ?: "", p.getLong("cuando", 0))
    }

    /** Busca "Mercado Fondo - Clase A" y devuelve su TNA estimada redondeada a un decimal (como la muestra Mercado Pago). */
    fun leer(json: String): Pair<Double, String>? {
        val fondos = (Json.parseToJsonElement(json) as? JsonObject)?.get("fondos") as? JsonArray ?: return null
        val f = fondos.mapNotNull { it as? JsonObject }.firstOrNull {
            val n = (it["nombre"].texto() ?: "").lowercase()
            n.startsWith("mercado fondo") && n.contains("clase a")
        } ?: return null
        val tna = ((f["rendimientos"] as? JsonObject)?.get("tnaEstimada") as? JsonPrimitive)?.doubleOrNull ?: return null
        if (tna <= 0 || tna > 300) return null
        return Math.round(tna * 10) / 10.0 to (f["fecha"].texto() ?: "")
    }

    /** Baja la tasa de internet. Llamar fuera del hilo principal. null = sin conexión o no la encontró. */
    fun bajar(ctx: android.content.Context): Dato? = runCatching {
        val c = (java.net.URL(URL).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 20_000; setRequestProperty("Accept", "application/json") }
        val (t, fecha) = leer(c.inputStream.bufferedReader().use { it.readText() }) ?: return@runCatching null
        val d = Dato(t, fecha, System.currentTimeMillis())
        ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putFloat("tasa", t.toFloat()).putString("fecha", fecha).putLong("cuando", d.cuando).apply()
        d
    }.getOrNull()

    /** Se revisa como mucho cada 6 horas. */
    fun toca(ctx: android.content.Context) = System.currentTimeMillis() - (guardada(ctx)?.cuando ?: 0) > 6 * 3_600_000L
}
