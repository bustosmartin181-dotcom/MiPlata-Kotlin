package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*

/* El estado de la app. Mismo formato que las copias .json de Mi plata (versión web/Capacitor),
   así una copia vieja se importa tal cual y se puede volver a exportar. */

data class Aumento(val amt: Double, val desde: String?)

data class Cuotas(val actual: Int, val total: Int)

data class Item(
    val n: String = "",
    val a: Double = 0.0,
    val p: Boolean = false,          // pagado
    val d: String? = null,           // fecha desde la que se descuenta (gasto agregado a mitad de período)
    val x: List<Aumento> = emptyList(), // aumentos con fecha
    val f: Boolean? = null,          // false = no se repite el mes que viene
    val v: Boolean? = null,          // true = el monto cambia cada mes
    val q: Cuotas? = null,
    val extra: JsonObject = JsonObject(emptyMap()), // otros datos (alias de MP, código, IPC...) que se conservan
)

data class Movimiento(
    val id: Long,
    val date: String,
    val amt: Double,
    val note: String = "",
    val cat: String? = null,
    val meta: Long? = null,          // aporte a una meta
    val dev: Int = 0,                // 1 = me lo devuelven, 2 = ya me lo devolvieron
    val extra: JsonObject = JsonObject(emptyMap()),
) {
    val esMeta get() = meta != null && meta != 0L
    val cuentaEnDia get() = !esMeta && dev == 0
    val cuentaEnSaldo get() = !esMeta && dev != 2
}

data class Ajuste(val amt: Double, val desde: String?, val date: String? = null, val note: String = "", val id: Long = 0)

data class Recal(val day: Int?, val base: Double, val obj: Boolean = false)

data class UsoFondo(val id: Long, val date: String, val amt: Double, val note: String = "", val para: String = "hoy")

data class Cuenta(val n: String, val m: Double, val excluida: Boolean = false)

data class Ingreso(val cat: String, val a: Double)

data class Proximo(val n: String, val a: Double)

data class Debe(val id: Long, val date: String, val amt: Double, val note: String)

data class Meta(val id: Long, val n: String, val t: Double, val g0: Double, val f: String)

data class Estado(
    val sueldo: Double = 0.0,
    val previo: Double = 0.0,
    val start: String,
    val end: String,
    val items: List<Item> = emptyList(),
    val spent: List<Movimiento> = emptyList(),
    val recal: Recal? = null,
    val ajustes: List<Ajuste> = emptyList(),
    val cuentas: List<Cuenta> = emptyList(),
    val metas: List<Meta> = emptyList(),
    val ingresos: List<Ingreso> = emptyList(),
    val proximos: List<Proximo> = emptyList(),
    val objetivo: Double = 0.0,
    val objetivoDesde: String = "",
    val aviso: Double? = null,
    val modoFondo: Boolean = false,
    val fondoDesde: String = "",
    val fondoExceso: String = "",
    val fondoUsos: List<UsoFondo> = emptyList(),
    val fondoPrev: Double = 0.0,
    val fondoRetirado: Double = 0.0,
    val rendOn: Boolean = false,
    val rendDesde: String = "",
    val rendPrev: Double = 0.0,
    val rendTasa: Double = 0.0,
    val rendEnMP: Boolean = false,
    val rendDispOn: Boolean = false,
    val rendDispDesde: String = "",
    val resPasado: Double = 0.0,
    val resAviso: Boolean = true,
    val resHora: String = "22:00",
    val nombre: String = "",
    val meDeben: List<Debe> = emptyList(),
    val pin: String = "",
    val salt: String = "",
    val pinApp: Boolean = true,
    val tema: String = "auto",
    val color: String = "verde",
    val ocultar: Boolean = false,
    val recOn: Boolean = false,
    val recHora: String = "21:00",
    val demo: Boolean = false,
    /** Todo el JSON original: lo que esta versión todavía no usa se conserva al guardar. */
    val crudo: JsonObject = JsonObject(emptyMap()),
) {
    companion object {
        private val CLAVES_ITEM = setOf("n", "a", "p", "d", "x", "f", "v", "q")
        private val CLAVES_MOV = setOf("id", "date", "amt", "note", "cat", "meta", "dev")

        fun desdeJson(o: JsonObject): Estado = Estado(
            sueldo = o["sueldo"].num(),
            previo = o["previo"].num(),
            start = o["start"].texto() ?: "",
            end = o["end"].texto() ?: "",
            items = o.lista("items").map { i ->
                Item(
                    n = i["n"].texto() ?: "",
                    a = i["a"].num(),
                    p = i["p"].verdadero(),
                    d = i["d"].texto()?.takeIf { it.isNotEmpty() },
                    x = i.lista("x").map { Aumento(it["amt"].num(), it["desde"].texto()?.takeIf { s -> s.isNotEmpty() }) },
                    f = (i["f"] as? JsonPrimitive)?.booleanOrNull,
                    v = (i["v"] as? JsonPrimitive)?.booleanOrNull,
                    q = (i["q"] as? JsonObject)?.let { Cuotas(it["actual"].num().toInt(), it["total"].num().toInt()) },
                    extra = JsonObject(i.filterKeys { it !in CLAVES_ITEM }),
                )
            },
            spent = o.lista("spent").map { e ->
                Movimiento(
                    id = e["id"].num().toLong(),
                    date = e["date"].texto() ?: "",
                    amt = e["amt"].num(),
                    note = e["note"].texto() ?: "",
                    cat = e["cat"].texto(),
                    meta = if (e["meta"].verdadero()) e["meta"].num().toLong().let { if (it == 0L) 1L else it } else null,
                    dev = e["dev"].num().toInt(),
                    extra = JsonObject(e.filterKeys { it !in CLAVES_MOV }),
                )
            },
            recal = (o["recal"] as? JsonObject)?.let {
                Recal(day = (it["day"] as? JsonPrimitive)?.doubleOrNull?.toInt(), base = it["base"].num(), obj = it["obj"].verdadero())
            },
            ajustes = o.lista("ajustes").map {
                Ajuste(it["amt"].num(), it["desde"].texto()?.takeIf { s -> s.isNotEmpty() }, it["date"].texto(), it["note"].texto() ?: "", it["id"].num().toLong())
            },
            cuentas = o.lista("cuentas").map { Cuenta(it["n"].texto() ?: "", it["m"].num(), it["x"].verdadero()) },
            metas = o.lista("metas").map { Meta(it["id"].num().toLong(), it["n"].texto() ?: "", it["t"].num(), it["g0"].num(), it["f"].texto() ?: "") },
            ingresos = o.lista("ingresos").map { Ingreso(it["cat"].texto() ?: "Sueldo", it["a"].num()) },
            proximos = o.lista("proximos").map { Proximo(it["n"].texto() ?: "", it["a"].num()) },
            objetivo = o["objetivo"].num(),
            objetivoDesde = o["objetivoDesde"].texto() ?: "",
            aviso = (o["aviso"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { (it as JsonElement).num() },
            modoFondo = o["modoFondo"].verdadero(),
            fondoDesde = o["fondoDesde"].texto() ?: "",
            fondoExceso = o["fondoExceso"].texto() ?: "",
            fondoUsos = o.lista("fondoUsos").map {
                UsoFondo(it["id"].num().toLong(), it["date"].texto() ?: "", it["amt"].num(), it["note"].texto() ?: "", it["para"].texto() ?: "hoy")
            },
            fondoPrev = o["fondoPrev"].num(),
            fondoRetirado = o["fondoRetirado"].num(),
            rendOn = o["rendOn"].verdadero(),
            rendDesde = o["rendDesde"].texto() ?: "",
            rendPrev = o["rendPrev"].num(),
            rendTasa = o["rendTasa"].num(),
            rendEnMP = o["rendEnMP"].verdadero(),
            rendDispOn = o["rendDispOn"].verdadero(),
            rendDispDesde = o["rendDispDesde"].texto() ?: "",
            resPasado = o["resPasado"].num(),
            resAviso = (o["resAviso"] as? JsonPrimitive)?.booleanOrNull != false,
            resHora = o["resHora"].texto()?.takeIf { Regex("""\d{1,2}:\d{2}""").matches(it) } ?: "22:00",
            nombre = o["nombre"].texto() ?: "",
            meDeben = o.lista("meDeben").map { Debe(it["id"].num().toLong(), it["date"].texto() ?: "", it["amt"].num(), it["note"].texto() ?: "") },
            pin = o["pin"].texto() ?: "",
            salt = o["salt"].texto() ?: "",
            pinApp = (o["pinApp"] as? JsonPrimitive)?.booleanOrNull != false,
            tema = o["tema"].texto()?.takeIf { it in setOf("auto", "claro", "oscuro") } ?: "auto",
            color = o["color"].texto()?.takeIf { it.isNotEmpty() } ?: "verde",
            ocultar = (o["ocultar"] as? JsonPrimitive)?.booleanOrNull == true,
            recOn = (o["recOn"] as? JsonPrimitive)?.booleanOrNull == true,
            recHora = o["recHora"].texto()?.takeIf { Regex("""\d{1,2}:\d{2}""").matches(it) } ?: "21:00",
            demo = o["demo"].verdadero(),
            crudo = o,
        )
    }
}
