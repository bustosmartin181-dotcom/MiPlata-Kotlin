package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*
import kotlin.math.min

/* =====================================================================
   CAMBIOS SOBRE LOS DATOS — traducción fiel de migrarEstado, iniciarPeriodo,
   avanzarCuotas y la edición de montos (itemMonto) de Mi plata v9.9.36.
   Trabajan sobre el JSON completo para no perder ningún dato.
   ===================================================================== */

fun num(n: Double): JsonPrimitive = if (n % 1.0 == 0.0 && Math.abs(n) < 1e15) JsonPrimitive(n.toLong()) else JsonPrimitive(n)
fun redondear2(n: Double) = Math.round(n * 100).toDouble() / 100

typealias Mapa = MutableMap<String, JsonElement>
fun JsonObject.mutable(): Mapa = toMutableMap()
fun Mapa.obj(): JsonObject = JsonObject(this)
private fun JsonElement?.lista(): MutableList<JsonElement> = (this as? JsonArray)?.toMutableList() ?: mutableListOf()

object Periodo {

    private fun tieneCuotas(i: JsonObject): Boolean {
        val q = i["q"] as? JsonObject ?: return false
        return q["total"].num() > 1
    }

    /** Repara datos de versiones anteriores (igual que migrarEstado). */
    fun migrarEstado(o: JsonObject): JsonObject {
        val S = o.mutable()
        if (S["ingresos"] == null || S["ingresos"] is JsonNull)
            S["ingresos"] = JsonArray(listOf(JsonObject(mapOf("cat" to JsonPrimitive("Sueldo"), "a" to num(S["sueldo"].num())))))
        if (S["bienvenida"] == null)
            S["bienvenida"] = JsonPrimitive(S["sueldo"].num() > 0 || S["spent"].lista().isNotEmpty())
        val start = S["start"].texto()
        if (S["fondoUsos"].verdadero() && !start.isNullOrEmpty())
            S["fondoUsos"] = JsonArray(S["fondoUsos"].lista().filter { u ->
                val d = (u as? JsonObject)?.get("date").texto(); d.isNullOrEmpty() || d >= start })
        if (!S["ipcMig"].verdadero()) {
            S["ipcMig"] = JsonPrimitive(1)
            S["items"] = JsonArray(S["items"].lista().map { e ->
                val i = e as? JsonObject ?: return@map e
                if (Regex("alejandro", RegexOption.IGNORE_CASE).containsMatchIn(i["n"].texto() ?: "") && !i["ipc"].verdadero())
                    JsonObject(i + ("ipc" to JsonObject(mapOf("prox" to JsonPrimitive("2027-03"), "cada" to JsonPrimitive(6), "partes" to JsonPrimitive(2)))))
                else i
            })
        }
        return S.obj()
    }

    /** Cambia el monto de un gasto importante. Con el período empezado, lo nuevo se descuenta desde hoy. */
    fun cambiarMonto(o: JsonObject, k: Int, nuevo: Double, hoy: String): JsonObject {
        val S = o.mutable()
        val items = S["items"].lista()
        val it = (items[k] as JsonObject).mutable()
        val antes = it["a"].num()
        val start = S["start"].texto()!!; val end = S["end"].texto()!!
        if (Fechas.dia(hoy) >= Fechas.dia(start) && nuevo != antes) {
            val desde = Fechas.iso(min(Fechas.dia(hoy), Fechas.dia(end) - 1))
            if (antes == 0.0) { it["d"] = JsonPrimitive(desde); it.remove("x") }
            else if (nuevo > antes) {
                val x = it["x"].lista(); x += JsonObject(mapOf("amt" to num(redondear2(nuevo - antes)), "desde" to JsonPrimitive(desde)))
                it["x"] = JsonArray(x)
            } else {
                var baja = antes - nuevo
                val x = it["x"].lista()
                if (it["x"] != null) {
                    while (baja > 0 && x.isNotEmpty()) {
                        val u = (x.last() as JsonObject).mutable(); val amt = u["amt"].num()
                        if (amt > baja) { u["amt"] = num(redondear2(amt - baja)); x[x.size - 1] = u.obj(); baja = 0.0 }
                        else { baja -= amt; x.removeAt(x.size - 1) }
                    }
                    if (x.isEmpty()) it.remove("x") else it["x"] = JsonArray(x)
                }
            }
        }
        it["a"] = num(nuevo)
        items[k] = it.obj(); S["items"] = JsonArray(items)
        return S.obj()
    }

    data class Resultado(val estado: JsonObject, val terminadas: List<String>)

    /** Pasar al período siguiente (igual que iniciarPeriodo). */
    fun iniciar(o: JsonObject, hoy: String, previo: Double, inicio: String, fin: String, sueldo: Double? = null): Resultado {
        val est = Estado.desdeJson(o)
        val c = Nucleo.calcular(est, hoy)
        val S = o.mutable()
        val h = S["hist"].lista()
        h += JsonObject(buildMap {
            put("start", S["start"] ?: JsonNull); put("end", S["end"] ?: JsonNull)
            put("sueldo", S["sueldo"] ?: JsonNull); put("previo", S["previo"] ?: JsonNull)
            put("importantes", num(c.total)); put("gastado", num(est.spent.filter { it.cuentaEnSaldo }.sumOf { it.amt }))
            put("quedo", num(c.saldo))
            if (c.fondo.activo) put("ahorro", num(redondear2(c.fondo.ahorroPeriodo)))
        })
        S["hist"] = JsonArray(h)
        if (c.fondo.activo) {
            S["fondoPrev"] = num(redondear2(c.fondo.total))
            S["fondoRetirado"] = num(redondear2(S["fondoRetirado"].num() + c.fondo.usos))
            S["fondoUsos"] = JsonArray(emptyList())
            if (S["rendOn"].verdadero()) { S["rendPrev"] = num(redondear2(c.fondo.rend + c.fondo.rendPend)); S["rendDesde"] = JsonPrimitive(inicio) }
        }
        /* avanzarCuotas */
        val terminadas = mutableListOf<String>()
        var items = S["items"].lista().mapNotNull { e ->
            val i = e as JsonObject
            if (!tieneCuotas(i)) return@mapNotNull i
            val q = i["q"]!!.jsonObject
            if (q["actual"].num() >= q["total"].num()) { terminadas += (i["n"].texto()?.takeIf { it.isNotEmpty() } ?: "Gasto en cuotas"); return@mapNotNull null }
            JsonObject(i + ("q" to JsonObject(q + ("actual" to num(q["actual"].num() + 1)))))
        }
        items = items.filter { i -> tieneCuotas(i) || !((i["f"] as? JsonPrimitive)?.booleanOrNull == false) }
        val nuevos = items.map { i ->
            val m = i.mutable()
            m["p"] = JsonPrimitive(false); m.remove("d"); m.remove("x")
            val prox = m["prox"]
            if (prox != null && prox !is JsonNull && !(prox is JsonPrimitive && prox.isString && prox.content == "")) m["a"] = num(prox.num())
            else if (m["v"].verdadero() && !tieneCuotas(i)) m["a"] = num(0.0)
            m.remove("prox")
            val ipc = m["ipc"] as? JsonObject
            val pend = ipc?.get("pend") as? JsonObject
            if (pend != null && inicio.take(7) >= (pend["desde"].texto() ?: "")) {
                m["a"] = pend["monto"] ?: JsonNull
                m["ipc"] = JsonObject(ipc - "pend")
            }
            m.obj()
        }.toMutableList<JsonElement>()
        S["proximos"].lista().forEach { e ->
            val x = e as? JsonObject ?: return@forEach
            if (x["n"].verdadero() || x["a"].verdadero())
                nuevos += JsonObject(mapOf("n" to JsonPrimitive(x["n"].texto() ?: ""), "a" to num(x["a"].num()),
                    "p" to JsonPrimitive(false), "f" to JsonPrimitive(false), "v" to JsonPrimitive(false)))
        }
        S["items"] = JsonArray(nuevos)
        S["proximos"] = JsonArray(emptyList())
        S["fondoDesde"] = JsonPrimitive("")
        S["fondoUsos"] = JsonArray(emptyList())
        if (S["rendDispOn"].verdadero()) S["rendDispDesde"] = JsonPrimitive(inicio)
        val meDeben = S["meDeben"].lista()
        S["spent"].lista().forEach { e ->
            val x = e as? JsonObject ?: return@forEach
            if ((x["dev"] as? JsonPrimitive)?.doubleOrNull == 1.0)
                meDeben += JsonObject(mapOf("id" to (x["id"] ?: JsonNull), "date" to (x["date"] ?: JsonNull), "amt" to (x["amt"] ?: JsonNull),
                    "note" to JsonPrimitive(x["note"].texto()?.takeIf { it.isNotEmpty() } ?: "")))
        }
        S["meDeben"] = JsonArray(meDeben)
        S["cierres"] = JsonObject(emptyMap()); S["ajustes"] = JsonArray(emptyList()); S["spent"] = JsonArray(emptyList()); S["recal"] = JsonNull
        if (sueldo != null) { S["sueldo"] = num(sueldo); S["ingresos"] = JsonArray(listOf(JsonObject(mapOf("cat" to JsonPrimitive("Sueldo"), "a" to num(sueldo))))) }
        S["previo"] = num(previo); S["start"] = JsonPrimitive(inicio); S["end"] = JsonPrimitive(fin)
        return Resultado(S.obj(), terminadas)
    }

    /** Cierre nocturno (congelarDias): congela los días ya cerrados y guarda una copia (las últimas 14). null = no cambió nada. */
    fun congelarDias(o: JsonObject, hoy: String, incluirHoy: Boolean = false): JsonObject? {
        val S = Estado.desdeJson(o)
        val cierres = ((o["cierres"] as? JsonObject) ?: JsonObject(emptyMap())).toMutableMap()
        var ultimo: String? = null
        Nucleo.diasDelPeriodo(S, hoy).forEach { x ->
            if (cierres[x.iso] == null && (x.iso < hoy || (incluirHoy && x.iso == hoy))) {
                cierres[x.iso] = JsonObject(mapOf("b" to num(redondear2(x.presupuesto)), "g" to num(x.gasto), "l" to num(redondear2(x.sobra))))
                ultimo = x.iso
            }
        }
        val u = ultimo ?: return null
        val resto = JsonObject(o - "copias" - "cierres")
        val copias = ((o["copias"] as? JsonArray)?.toMutableList() ?: mutableListOf())
        copias += JsonObject(mapOf("iso" to JsonPrimitive(u), "json" to JsonPrimitive(resto.toString())))
        return JsonObject(o + mapOf("cierres" to JsonObject(cierres), "copias" to JsonArray(copias.takeLast(14))))
    }

    /** Volver al estado de una copia del cierre nocturno: el historial de copias y el PIN no se tocan. */
    fun restaurarCierre(o: JsonObject, indice: Int): JsonObject? {
        val copia = (o["copias"] as? JsonArray)?.getOrNull(indice) as? JsonObject ?: return null
        val viejo = Json.parseToJsonElement(copia["json"].texto() ?: return null).jsonObject
        val m = migrarEstado(viejo).toMutableMap()
        listOf("copias", "cierres", "pin", "salt", "pinApp").forEach { k -> o[k]?.let { m[k] = it } ?: m.remove(k) }
        return JsonObject(m)
    }
}
