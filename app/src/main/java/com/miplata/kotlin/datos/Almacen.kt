package com.miplata.kotlin.datos

import android.content.Context
import com.miplata.kotlin.nucleo.Estado
import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.num
import com.miplata.kotlin.nucleo.texto
import kotlinx.serialization.json.*
import java.io.File

/* Guarda los datos en un archivo JSON con el MISMO formato que las copias de Mi plata.
   Los cambios se hacen sobre el JSON completo, así nunca se pierde ningún dato que
   esta versión todavía no use (cuotas, IPC, alias de MP, copias, cierres…). */
class Almacen(carpeta: File) {
    constructor(context: Context) : this(context.filesDir)
    private val archivo = File(carpeta, "estado.json")
    private val respaldo = File(carpeta, "estado.anterior.json")

    var crudo: JsonObject? = leer()
        private set

    val estado: Estado? get() = crudo?.let { runCatching { Estado.desdeJson(it) }.getOrNull() }

    private fun leer(): JsonObject? =
        if (archivo.exists()) runCatching { Json.parseToJsonElement(archivo.readText()).jsonObject }.getOrNull() else null

    private fun guardar(nuevo: JsonObject) {
        if (archivo.exists()) archivo.copyTo(respaldo, overwrite = true)
        val tmp = File(archivo.parent, "estado.tmp")
        tmp.writeText(nuevo.toString())
        tmp.renameTo(archivo)
        crudo = nuevo
    }

    /** Importa una copia .json. Devuelve un mensaje de error o null si salió bien. */
    fun importar(texto: String): String? {
        val o = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull()
            ?: return "Ese archivo no es una copia válida de Mi plata."
        if (o["items"] !is JsonArray || o["spent"] !is JsonArray) return "Ese archivo no es una copia válida de Mi plata."
        val migrado = runCatching { com.miplata.kotlin.nucleo.Periodo.migrarEstado(o) }.getOrElse { return "No pude leer la copia: ${it.message}" }
        runCatching { Estado.desdeJson(migrado) }.onFailure { return "No pude leer la copia: ${it.message}" }
        guardar(migrado)
        return null
    }

    fun exportar(): String = crudo?.toString() ?: "{}"

    private fun cambiar(f: MutableMap<String, JsonElement>.() -> Unit) {
        val m = (crudo ?: return).toMutableMap()
        m.f()
        m["upd"] = JsonPrimitive(System.currentTimeMillis())
        guardar(JsonObject(m))
    }

    private fun MutableMap<String, JsonElement>.spent(): MutableList<JsonElement> =
        ((this["spent"] as? JsonArray)?.toMutableList() ?: mutableListOf())

    /** Anota un gasto (o una entrada si `entrada`). Igual que la app original. */
    fun anotar(monto: Double, nota: String, categoria: String?, entrada: Boolean, meLoDevuelven: Boolean,
               fecha: String = Fechas.hoy(), editarId: Long? = null) {
        cambiar {
            val lista = spent()
            val datos = buildMap<String, JsonElement> {
                put("date", JsonPrimitive(fecha))
                put("amt", JsonPrimitive(if (entrada) -Math.abs(monto) else Math.abs(monto)))
                put("note", JsonPrimitive(nota))
                if (!entrada && !categoria.isNullOrEmpty()) put("cat", JsonPrimitive(categoria))
                if (!entrada && meLoDevuelven) put("dev", JsonPrimitive(1))
            }
            val i = if (editarId != null) lista.indexOfFirst { (it as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull == editarId } else -1
            if (i >= 0) {
                val viejo = lista[i].jsonObject.toMutableMap()
                val yaCobrado = viejo["dev"]?.jsonPrimitive?.intOrNull == 2
                viejo.remove("cat"); viejo.remove("dev")
                viejo.putAll(datos)
                if (datos.containsKey("dev") && yaCobrado) viejo["dev"] = JsonPrimitive(2)
                lista[i] = JsonObject(viejo)
            } else {
                lista += JsonObject(mapOf("id" to JsonPrimitive(System.currentTimeMillis())) + datos)
            }
            this["spent"] = JsonArray(lista)
        }
    }

    fun borrarMovimiento(id: Long) = cambiar {
        this["spent"] = JsonArray(spent().filter { (it as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull != id })
    }

    /** "Me lo devolvieron": el gasto deja de contar en el saldo (dev = 2). */
    fun marcarDevuelto(id: Long) = cambiar {
        this["spent"] = JsonArray(spent().map { e ->
            val o = e as? JsonObject ?: return@map e
            if (o["id"]?.jsonPrimitive?.longOrNull == id) JsonObject(o + mapOf("dev" to JsonPrimitive(2), "cob" to JsonPrimitive(Fechas.hoy()))) else o
        })
    }

    /** Marca o desmarca un gasto importante como pagado. */
    fun marcarPagado(indice: Int, pagado: Boolean) = cambiar {
        val items = (this["items"] as? JsonArray)?.toMutableList() ?: return@cambiar
        val o = items.getOrNull(indice) as? JsonObject ?: return@cambiar
        items[indice] = JsonObject(o + ("p" to JsonPrimitive(pagado)))
        this["items"] = JsonArray(items)
    }

    /** Cambio general sobre el JSON completo. */
    fun cambiarTodo(f: (JsonObject) -> JsonObject) {
        val actual = crudo ?: return
        val nuevo = f(actual).toMutableMap()
        nuevo["upd"] = JsonPrimitive(System.currentTimeMillis())
        guardar(JsonObject(nuevo))
    }

    /** Cambia campos de un gasto importante (null = borrar el campo). */
    fun cambiarItem(k: Int, campos: Map<String, JsonElement?>) = cambiarTodo { o ->
        val items = (o["items"] as? JsonArray)?.toMutableList() ?: return@cambiarTodo o
        val it = (items.getOrNull(k) as? JsonObject)?.toMutableMap() ?: return@cambiarTodo o
        campos.forEach { (c, v) -> if (v == null) it.remove(c) else it[c] = v }
        items[k] = JsonObject(it)
        JsonObject(o + ("items" to JsonArray(items)))
    }

    fun cambiarMontoItem(k: Int, monto: Double) = cambiarTodo { com.miplata.kotlin.nucleo.Periodo.cambiarMonto(it, k, monto, Fechas.hoy()) }

    /** Gasto importante nuevo: con el período empezado se descuenta desde hoy (como la app original). */
    fun agregarItem(): Int {
        var indice = 0
        cambiarTodo { o ->
            val items = (o["items"] as? JsonArray)?.toMutableList() ?: mutableListOf()
            val hoy = Fechas.hoy(); val start = o["start"]?.jsonPrimitive?.content ?: hoy; val end = o["end"]?.jsonPrimitive?.content ?: hoy
            val empezado = Fechas.dia(hoy) >= Fechas.dia(start)
            items += JsonObject(mapOf("n" to JsonPrimitive(""), "a" to JsonPrimitive(0), "p" to JsonPrimitive(false),
                "f" to JsonPrimitive(false), "v" to JsonPrimitive(false),
                "d" to JsonPrimitive(if (empezado) Fechas.iso(minOf(Fechas.dia(hoy), Fechas.dia(end) - 1)) else "")))
            indice = items.size - 1
            JsonObject(o + ("items" to JsonArray(items)))
        }
        return indice
    }

    fun quitarItem(k: Int) = cambiarTodo { o ->
        val items = (o["items"] as? JsonArray)?.toMutableList() ?: return@cambiarTodo o
        if (k in items.indices) items.removeAt(k)
        JsonObject(o + ("items" to JsonArray(items)))
    }

    /** Ingresos: cambia la lista y el sueldo pasa a ser la suma (como la app original). */
    fun guardarIngresos(lista: List<Pair<String, Double>>) = cambiarTodo { o ->
        val arr = JsonArray(lista.map { (c, a) -> JsonObject(mapOf("cat" to JsonPrimitive(c), "a" to com.miplata.kotlin.nucleo.num(a))) })
        JsonObject(o + mapOf("ingresos" to arr, "sueldo" to com.miplata.kotlin.nucleo.num(lista.sumOf { it.second })))
    }

    fun cambiarCampo(clave: String, valor: JsonElement) = cambiarTodo { JsonObject(it + (clave to valor)) }

    fun guardarProximos(lista: List<Pair<String, Double>>) = cambiarCampo("proximos",
        JsonArray(lista.map { (n, a) -> JsonObject(mapOf("n" to JsonPrimitive(n), "a" to com.miplata.kotlin.nucleo.num(a))) }))

    /** Empieza el período nuevo. Devuelve los gastos en cuotas que se terminaron de pagar. */
    fun periodoNuevo(previo: Double, inicio: String, fin: String): List<String> {
        var terminadas = emptyList<String>()
        cambiarTodo { o -> val r = com.miplata.kotlin.nucleo.Periodo.iniciar(o, Fechas.hoy(), previo, inicio, fin); terminadas = r.terminadas; r.estado }
        return terminadas
    }

    private fun lista(o: JsonObject, clave: String) = (o[clave] as? JsonArray)?.toMutableList() ?: mutableListOf()

    /** Sacar plata del fondo. para = "hoy" (suma al límite de hoy) o "cubre" (cubre gastos ya hechos). */
    fun usarFondo(monto: Double, nota: String, para: String) = cambiarTodo { o ->
        val l = lista(o, "fondoUsos")
        l += JsonObject(mapOf("id" to JsonPrimitive(System.currentTimeMillis()), "date" to JsonPrimitive(Fechas.hoy()),
            "amt" to com.miplata.kotlin.nucleo.num(monto), "note" to JsonPrimitive(nota), "para" to JsonPrimitive(if (para == "cubre") "cubre" else "hoy")))
        JsonObject(o + ("fondoUsos" to JsonArray(l)))
    }
    fun quitarUsoFondo(i: Int) = cambiarTodo { o -> val l = lista(o, "fondoUsos"); if (i in l.indices) l.removeAt(i); JsonObject(o + ("fondoUsos" to JsonArray(l))) }

    /** "Ya lo pasé a la reserva". */
    fun sumarPasado(monto: Double) = cambiarTodo { o ->
        JsonObject(o + ("resPasado" to com.miplata.kotlin.nucleo.num(com.miplata.kotlin.nucleo.redondear2(o["resPasado"].num() + monto))))
    }

    data class CuentaEditable(val n: String, val m: Double, val cuenta: Boolean)
    fun guardarCuentas(l: List<CuentaEditable>) = cambiarCampo("cuentas", JsonArray(l.map { c ->
        JsonObject(buildMap { put("n", JsonPrimitive(c.n)); put("m", com.miplata.kotlin.nucleo.num(c.m)); if (!c.cuenta) put("x", JsonPrimitive(true)) })
    }))

    /** Recomposición: la diferencia con las cuentas se reparte desde mañana (como "Ajustar con las cuentas"). */
    fun agregarRecomposicion(monto: Double) = cambiarTodo { o ->
        val l = lista(o, "ajustes"); val hoy = Fechas.hoy()
        l += JsonObject(mapOf("id" to JsonPrimitive(System.currentTimeMillis()), "date" to JsonPrimitive(hoy), "desde" to JsonPrimitive(Fechas.manana(hoy)),
            "amt" to com.miplata.kotlin.nucleo.num(com.miplata.kotlin.nucleo.redondear2(monto)), "note" to JsonPrimitive("Recomposición")))
        JsonObject(o + ("ajustes" to JsonArray(l)))
    }
    fun quitarRecomposicion(i: Int) = cambiarTodo { o -> val l = lista(o, "ajustes"); if (i in l.indices) l.removeAt(i); JsonObject(o + ("ajustes" to JsonArray(l))) }

    /** Activar o apagar el fondo de ahorro (cuenta desde hoy: no toca lo que ya tenías). */
    fun activarFondo(on: Boolean) = cambiarTodo { o ->
        JsonObject(o + mapOf("modoFondo" to JsonPrimitive(on)) + (if (on) mapOf("fondoDesde" to JsonPrimitive(Fechas.hoy())) else emptyMap()))
    }
    fun activarRendFondo(on: Boolean) = cambiarTodo { o ->
        var m = o + ("rendOn" to JsonPrimitive(on))
        if (on) { m = m + mapOf("rendDesde" to JsonPrimitive(Fechas.hoy()), "rendPrev" to JsonPrimitive(0)); if (o["rendTasa"].num() == 0.0) m = m + ("rendTasa" to JsonPrimitive(19)) }
        JsonObject(m)
    }
    fun activarRendDisp(on: Boolean) = cambiarTodo { o ->
        var m = o + ("rendDispOn" to JsonPrimitive(on))
        if (on) { m = m + ("rendDispDesde" to JsonPrimitive(Fechas.hoy())); if (o["rendTasa"].num() == 0.0) m = m + ("rendTasa" to JsonPrimitive(19)) }
        JsonObject(m)
    }

    /* ---- Objetivo por día y recalibrar (igual que aplicarObjetivo / recalibrar) ---- */
    fun aplicarObjetivo(valor: Double) = cambiarTodo { o ->
        val n = Math.max(0.0, Math.round(valor).toDouble()); val hoy = Fechas.hoy()
        val e = Estado.desdeJson(o); val c = com.miplata.kotlin.nucleo.Nucleo.calcular(e, hoy)
        if (n > 0 && n == e.objetivo && e.recal?.obj == true) return@cambiarTodo o
        val m = o.toMutableMap(); m["objetivo"] = num(n)
        if (n > 0) { m["objetivoDesde"] = JsonPrimitive(hoy)
            m["recal"] = JsonObject(mapOf("day" to JsonPrimitive(c.hoyIdx), "base" to num(n), "obj" to JsonPrimitive(true))) }
        else { m["objetivoDesde"] = JsonPrimitive(""); if (e.recal?.obj == true) m["recal"] = JsonNull }
        JsonObject(m)
    }
    fun recalibrar() = cambiarTodo { o ->
        val c = com.miplata.kotlin.nucleo.Nucleo.calcular(Estado.desdeJson(o), Fechas.hoy())
        JsonObject(o + mapOf("objetivo" to JsonPrimitive(0), "objetivoDesde" to JsonPrimitive(""),
            "recal" to JsonObject(mapOf("day" to JsonPrimitive(c.hoyIdx), "base" to num((c.disp - c.gastadoAntesDeHoy) / c.faltan)))))
    }
    fun quitarRecalibracion() = cambiarTodo { o -> JsonObject(o + mapOf("recal" to JsonNull, "objetivo" to JsonPrimitive(0), "objetivoDesde" to JsonPrimitive(""))) }

    /* ---- Metas ---- */
    fun guardarMeta(id: Long?, n: String, t: Double, g0: Double, f: String) = cambiarTodo { o ->
        val l = lista(o, "metas")
        val nueva = JsonObject(mapOf("id" to JsonPrimitive(id ?: System.currentTimeMillis()), "n" to JsonPrimitive(n), "t" to num(t), "g0" to num(g0), "f" to JsonPrimitive(f)))
        val i = l.indexOfFirst { (it as? JsonObject)?.get("id").num().toLong() == id }
        if (id != null && i >= 0) l[i] = JsonObject((l[i] as JsonObject) + nueva) else l += nueva
        JsonObject(o + ("metas" to JsonArray(l)))
    }
    fun quitarMeta(id: Long) = cambiarTodo { o ->
        JsonObject(o + ("metas" to JsonArray(lista(o, "metas").filter { (it as? JsonObject)?.get("id").num().toLong() != id })))
    }
    fun aportar(id: Long, nombre: String, monto: Double) = cambiarTodo { o ->
        val l = lista(o, "spent")
        l += JsonObject(mapOf("id" to JsonPrimitive(System.currentTimeMillis()), "date" to JsonPrimitive(Fechas.hoy()), "amt" to num(monto),
            "note" to JsonPrimitive("🎯 $nombre"), "meta" to JsonPrimitive(id)))
        JsonObject(o + ("spent" to JsonArray(l)))
    }

    /* ---- Me deben ---- */
    /** Cobrado. `previo` = viene del período anterior: la plata entra hoy como una entrada. */
    fun cobrado(id: Long, previo: Boolean) {
        if (!previo) { marcarDevuelto(id); return }
        cambiarTodo { o ->
            val x = lista(o, "meDeben").firstOrNull { (it as? JsonObject)?.get("id").num().toLong() == id } as? JsonObject ?: return@cambiarTodo o
            val sp = lista(o, "spent")
            sp += JsonObject(mapOf("id" to JsonPrimitive(System.currentTimeMillis()), "date" to JsonPrimitive(Fechas.hoy()),
                "amt" to num(-Math.abs(x["amt"].num())), "note" to JsonPrimitive("Me devolvieron: " + (x["note"].texto() ?: ""))))
            JsonObject(o + mapOf("spent" to JsonArray(sp),
                "meDeben" to JsonArray(lista(o, "meDeben").filter { (it as? JsonObject)?.get("id").num().toLong() != id })))
        }
    }
    /** No me lo van a pagar: pasa a ser un gasto propio. */
    fun noPagaron(id: Long, previo: Boolean) = cambiarTodo { o ->
        if (previo) JsonObject(o + ("meDeben" to JsonArray(lista(o, "meDeben").filter { (it as? JsonObject)?.get("id").num().toLong() != id })))
        else JsonObject(o + ("spent" to JsonArray(lista(o, "spent").map { e ->
            val x = e as? JsonObject ?: return@map e
            if (x["id"].num().toLong() == id) JsonObject(x - "dev" - "cob") else x
        })))
    }

    /* ---- PIN ---- */
    fun pinCorrecto(pin: String): Boolean { val e = estado ?: return false; return e.pin.isNotEmpty() && Seguridad.hashPin(pin, e.salt) == e.pin }
    fun ponerPin(pin: String) = cambiarTodo { o -> val sal = Seguridad.nuevaSal()
        JsonObject(o + mapOf("salt" to JsonPrimitive(sal), "pin" to JsonPrimitive(Seguridad.hashPin(pin, sal)))) }
    fun quitarPin() = cambiarTodo { o -> JsonObject(o - "pin" - "salt") }

    /** Borra todos los datos de la app (deja una copia de respaldo interna por si fue sin querer). */
    fun borrarTodo() {
        if (archivo.exists()) archivo.copyTo(respaldo, overwrite = true)
        archivo.delete(); crudo = null
    }

    /** Empezar de cero sin copia (como la introducción de Mi plata). */
    fun empezarDeCero(nombre: String, sueldo: Double, inicio: String, fin: String, gastos: List<Pair<String, Double>>) {
        val items = gastos.filter { it.first.isNotBlank() || it.second > 0 }.map { (n, a) ->
            JsonObject(mapOf("n" to JsonPrimitive(n), "a" to num(a), "p" to JsonPrimitive(false), "f" to JsonPrimitive(true), "v" to JsonPrimitive(false))) }
        guardar(JsonObject(mapOf(
            "sueldo" to num(sueldo), "previo" to JsonPrimitive(0), "bienvenida" to JsonPrimitive(true),
            "ingresos" to JsonArray(listOf(JsonObject(mapOf("cat" to JsonPrimitive("Sueldo"), "a" to num(sueldo))))),
            "start" to JsonPrimitive(inicio), "end" to JsonPrimitive(fin), "items" to JsonArray(items), "spent" to JsonArray(emptyList()),
            "recal" to JsonNull, "nombre" to JsonPrimitive(nombre), "ipcMig" to JsonPrimitive(1), "tema" to JsonPrimitive("auto"), "color" to JsonPrimitive("verde"),
        )))
    }
}
