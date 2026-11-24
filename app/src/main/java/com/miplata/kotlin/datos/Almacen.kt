package com.miplata.kotlin.datos

import android.content.Context
import com.miplata.kotlin.nucleo.Estado
import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.num
import com.miplata.kotlin.nucleo.texto
import com.miplata.kotlin.nucleo.verdadero
import kotlinx.serialization.json.*
import java.io.File

/** Con qué se pagó o se cobró un movimiento (campos "med" y "cobMed"). Sin marca = Mercado Pago. */
const val MEDIO_EFECTIVO = "ef"
const val MEDIO_MP = "mp"

/* Guarda los datos en un archivo JSON con el MISMO formato que las copias de Mi plata.
   Los cambios se hacen sobre el JSON completo, así nunca se pierde ningún dato que
   esta versión todavía no use (cuotas, IPC, alias de MP, copias, cierres…). */
class Almacen(private val carpeta: File, private val ctx: Context? = null) {
    constructor(context: Context) : this(context.filesDir, context.applicationContext)
    private val archivo = File(carpeta, "estado.json")
    private val respaldo = File(carpeta, "estado.anterior.json")

    var crudo: JsonObject? = leer()
        private set
    private var leidoEn = archivo.lastModified()

    /** Si otra parte de la app (el aviso, el widget) cambió los datos, los vuelve a leer. true = hubo cambios. */
    fun recargarSiCambio(): Boolean {
        val m = archivo.lastModified()
        if (m == leidoEn) return false
        crudo = leer(); leidoEn = m
        return true
    }

    val estado: Estado? get() = crudo?.let { runCatching { Estado.desdeJson(it) }.getOrNull() }

    private fun leer(): JsonObject? =
        if (archivo.exists()) runCatching { Json.parseToJsonElement(archivo.readText()).jsonObject }.getOrNull() else null

    private fun guardar(nuevo: JsonObject) {
        if (archivo.exists()) archivo.copyTo(respaldo, overwrite = true)
        val tmp = File(archivo.parent, "estado.tmp")
        tmp.writeText(nuevo.toString())
        tmp.renameTo(archivo)
        crudo = nuevo
        leidoEn = archivo.lastModified()
        /* cada cambio guardado actualiza la copia automática (si está activada), sin depender de la pantalla */
        ctx?.let { c -> runCatching { CopiaAutomatica.programar(c) } }
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

    /** Id nuevo y único: la hora en milisegundos, o uno más que el mayor que ya exista (si dos se guardan en el mismo milisegundo). */
    private fun nuevoId(o: JsonObject?): Long {
        var max = 0L
        listOf("spent", "fondoUsos", "ajustes", "metas", "meDeben").forEach { k ->
            (o?.get(k) as? JsonArray)?.forEach { e -> (e as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull?.let { if (it > max) max = it } } }
        return maxOf(System.currentTimeMillis(), max + 1)
    }

    /** Cambia los datos. Si cambiaron los gastos/entradas, el saldo de "Mis cuentas" se mueve igual:
        un gasto lo baja, una entrada lo sube (moverCuenta = false para no tocarlo).
        Lo pagado o cobrado en efectivo mueve la cuenta "Efectivo"; lo demás, Mercado Pago. */
    private fun cambiar(moverCuenta: Boolean = true, f: MutableMap<String, JsonElement>.() -> Unit) {
        val antes = crudo ?: return
        val m = antes.toMutableMap()
        m.f()
        m["upd"] = JsonPrimitive(System.currentTimeMillis())
        var nuevo = JsonObject(m)
        if (moverCuenta) {
            val (mpAhora, efAhora) = efectoEnCuentas(nuevo)
            val (mpAntes, efAntes) = efectoEnCuentas(antes)
            val dMp = mpAhora - mpAntes
            val dEf = efAhora - efAntes
            if (Math.abs(dMp) >= 0.005) nuevo = sumarAMercadoPago(nuevo, dMp)
            if (Math.abs(dEf) >= 0.005) nuevo = sumarAEfectivo(nuevo, dEf)
        }
        guardar(nuevo)
    }

    /** Cuánto movieron las cuentas los movimientos: (Mercado Pago, Efectivo).
        Gasto = sale plata, entrada = entra. Un aporte a una meta no mueve nada.
        Un gasto que ya te devolvieron: salió por donde lo pagaste ("med") y volvió por donde te pagaron ("cobMed");
        si es por el mismo lado (o es un dato viejo, sin esas marcas), se anula, como antes. */
    private fun efectoEnCuentas(o: JsonObject): Pair<Double, Double> {
        var mp = 0.0; var ef = 0.0
        (o["spent"] as? JsonArray)?.forEach { e ->
            val x = e as? JsonObject ?: return@forEach
            if (x["meta"].verdadero()) return@forEach
            val amt = x["amt"].num()
            val pagoEf = x["med"].texto() == MEDIO_EFECTIVO
            if (pagoEf) ef -= amt else mp -= amt
            if (x["dev"].num().toInt() == 2) {
                val cobroEf = (x["cobMed"].texto() ?: x["med"].texto()) == MEDIO_EFECTIVO
                if (cobroEf) ef += amt else mp += amt
            }
        }
        return mp to ef
    }

    private fun MutableMap<String, JsonElement>.spent(): MutableList<JsonElement> =
        ((this["spent"] as? JsonArray)?.toMutableList() ?: mutableListOf())

    /** Anota un gasto (o una entrada si `entrada`). Igual que la app original. */
    fun anotar(monto: Double, nota: String, categoria: String?, entrada: Boolean, meLoDevuelven: Boolean,
               fecha: String = Fechas.hoy(), editarId: Long? = null, moverCuenta: Boolean = true, efectivo: Boolean = false) {
        cambiar(moverCuenta) {
            val lista = spent()
            val datos = buildMap<String, JsonElement> {
                put("date", JsonPrimitive(fecha))
                put("amt", JsonPrimitive(if (entrada) -Math.abs(monto) else Math.abs(monto)))
                put("note", JsonPrimitive(nota))
                if (!entrada && !categoria.isNullOrEmpty()) put("cat", JsonPrimitive(categoria))
                if (!entrada && meLoDevuelven) put("dev", JsonPrimitive(1))
                if (efectivo) put("med", JsonPrimitive(MEDIO_EFECTIVO))
            }
            val i = if (editarId != null) lista.indexOfFirst { (it as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull == editarId } else -1
            if (i >= 0) {
                val viejo = lista[i].jsonObject.toMutableMap()
                val yaCobrado = viejo["dev"]?.jsonPrimitive?.intOrNull == 2
                viejo.remove("cat"); viejo.remove("dev"); viejo.remove("med")
                viejo.putAll(datos)
                if (datos.containsKey("dev") && yaCobrado) viejo["dev"] = JsonPrimitive(2)
                lista[i] = JsonObject(viejo)
            } else {
                lista += JsonObject(mapOf("id" to JsonPrimitive(nuevoId(crudo))) + datos)
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

    /* ---------- Compras del taller ---------- */
    fun guardarTaller(t: com.miplata.kotlin.nucleo.Taller) = cambiar(moverCuenta = false) { this["taller"] = t.aJson() }

    /** Una compra para los muchachos: tu parte como gasto común y la de cada uno en "Me deben".
        En total baja de Mercado Pago lo que pagaste (miParte + la de todos). */
    fun anotarTaller(fecha: String, miParte: Double, detalle: String, partes: List<Pair<com.miplata.kotlin.nucleo.Muchacho, Double>>,
                     queLlevo: Map<String, String> = emptyMap(), suelto: Boolean = false, efectivo: Boolean = false) = cambiar {
        val lista = spent()
        var id = nuevoId(crudo)
        /* "suelto": algo que le compraste a un compañero fuera del desayuno (no cuenta como la compra del desayuno) */
        val marca = "taller" to JsonPrimitive(if (suelto) 2 else 1)
        /* pagado en efectivo: sale de la cuenta Efectivo y no de Mercado Pago */
        val medio = if (efectivo) mapOf("med" to JsonPrimitive(MEDIO_EFECTIVO)) else emptyMap()
        if (miParte > 0) lista += JsonObject(mapOf("id" to JsonPrimitive(id++), "date" to JsonPrimitive(fecha), "amt" to num(miParte),
            "note" to JsonPrimitive(("Taller · mi parte " + detalle).trim()), "cat" to JsonPrimitive("Comida"), marca) + medio)
        partes.filter { it.second > 0 }.forEach { (m, monto) ->
            lista += JsonObject(mapOf("id" to JsonPrimitive(id++), "date" to JsonPrimitive(fecha), "amt" to num(monto),
                "note" to JsonPrimitive("Taller · ${m.mostrar}" + (queLlevo[m.id]?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")), "cat" to JsonPrimitive("Comida"), "dev" to JsonPrimitive(1),
                "tp" to JsonPrimitive(m.id), marca) + medio)
        }
        /* si alguno tenía plata a favor (te la dio antes o le debías vuelto), se le descuenta sola */
        partes.map { it.first.id }.distinct().forEach { saldarConFavor(lista, it) }
        this["spent"] = JsonArray(lista)
    }

    /* ---------- Plata a favor de un compañero ----------
       Cuando un compañero te da plata (antes de comprar, o más de lo que debía y no tenías vuelto),
       se anota como un "Me deben" negativo con su nombre: la plata entra a tu cuenta (Efectivo o Mercado Pago)
       pero no es tuya, así que no te sube el límite del día. Con eso se van pagando sus deudas, de la más vieja a la más nueva. */

    /** Te dio `dado` (en efectivo o por transferencia). Paga sus deudas (primero `primero`, si se indica) y lo que sobra:
        si `dejarAFavor`, queda a su favor para la próxima; si no, es porque le diste el vuelto. */
    fun cobrarPersona(tp: String, nombre: String, dado: Double, efectivo: Boolean, dejarAFavor: Boolean, primero: Long? = null,
                      medioVuelto: String? = null) = cambiar {
        if (dado <= 0) return@cambiar
        val lista = spent()
        val id = nuevoId(crudo)
        lista += JsonObject(mapOf("id" to JsonPrimitive(id), "date" to JsonPrimitive(Fechas.hoy()),
            "amt" to num(-com.miplata.kotlin.nucleo.redondear2(dado)), "note" to JsonPrimitive("Taller · a favor de $nombre"),
            "dev" to JsonPrimitive(1), "tp" to JsonPrimitive(tp), "taller" to JsonPrimitive(2), "favor" to JsonPrimitive(true)) +
            (if (efectivo) mapOf("med" to JsonPrimitive(MEDIO_EFECTIVO)) else emptyMap()))
        saldarConFavor(lista, tp, primero)
        /* le diste el vuelto: lo que sobró de esta plata vuelve a él (se anula) */
        if (!dejarAFavor) {
            val i = lista.indexOfFirst { (it as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull == id }
            val x = lista.getOrNull(i) as? JsonObject
            if (x != null && x["dev"].num().toInt() == 1) lista[i] = JsonObject(x + mapOf("dev" to JsonPrimitive(2),
                "cob" to JsonPrimitive(Fechas.hoy()), "cobMed" to JsonPrimitive(medioVuelto ?: x["med"].texto() ?: MEDIO_MP), "vuelto" to JsonPrimitive(true)))
        }
        this["spent"] = JsonArray(lista)
    }

    /** El vuelto salió por otro lado del que la app lo descontó: la app sacó `monto` del vuelto de un medio
        y en realidad lo diste con el otro. Se anota un movimiento interno (no cuenta en el límite del día)
        que pasa esa plata de una cuenta a la otra. `vueltoEnEfectivo` = cómo diste el vuelto de verdad. */
    fun corregirVuelto(tp: String?, nombre: String, monto: Double, vueltoEnEfectivo: Boolean) = cambiar {
        val m = com.miplata.kotlin.nucleo.redondear2(monto)
        if (m < 0.005) return@cambiar
        val lista = spent()
        /* vuelto por MP: el efectivo que te dio queda entero (+) y sale de MP (−); vuelto en efectivo: al revés */
        val campos = mutableMapOf<String, JsonElement>("id" to JsonPrimitive(nuevoId(crudo)), "date" to JsonPrimitive(Fechas.hoy()),
            "amt" to num(-m), "note" to JsonPrimitive((if (tp != null) "Taller · " else "") + "vuelto" +
                (if (nombre.isNotBlank()) " a $nombre" else "") + (if (vueltoEnEfectivo) " en efectivo" else " por MP")),
            "dev" to JsonPrimitive(2), "cob" to JsonPrimitive(Fechas.hoy()),
            "cobMed" to JsonPrimitive(if (vueltoEnEfectivo) MEDIO_EFECTIVO else MEDIO_MP), "vuelto" to JsonPrimitive(true))
        if (!vueltoEnEfectivo) campos["med"] = JsonPrimitive(MEDIO_EFECTIVO)
        if (tp != null) { campos["tp"] = JsonPrimitive(tp); campos["taller"] = JsonPrimitive(2); campos["favor"] = JsonPrimitive(true) }
        lista += JsonObject(campos)
        this["spent"] = JsonArray(lista)
    }

    /** Corrige cómo te pagaron algo ya cobrado. `total` = lo que te entregó para esto; `enEfectivo` = cuánto de eso fue en efectivo
        (el resto, por transferencia; se cuenta primero la transferencia). Si te dio menos, lo que falta vuelve a "Me deben".
        Devuelve lo que sobró (transferencia, efectivo) para dejarlo a su favor si corresponde. Las cuentas se acomodan solas. */
    fun cambiarCobro(id: Long, total: Double, enEfectivo: Double): Pair<Double, Double> {
        var sobra = 0.0 to 0.0
        cambiar {
            val lista = spent()
            val i = lista.indexOfFirst { (it as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull == id }
            val x = lista.getOrNull(i) as? JsonObject ?: return@cambiar
            if (x["dev"].num().toInt() != 2) return@cambiar
            fun r2(v: Double) = com.miplata.kotlin.nucleo.redondear2(v)
            val amt = x["amt"].num()
            val ef = r2(enEfectivo.coerceIn(0.0, total)); val tr = r2(total - ef)
            val trUsa = Math.min(tr, amt); val efUsa = Math.min(ef, amt - trUsa); val resto = r2(amt - trUsa - efUsa)
            sobra = r2(tr - trUsa) to r2(ef - efUsa)
            val base = x - "cob" - "cobMed"
            val piezas = listOfNotNull(
                trUsa.takeIf { it >= 0.005 }?.let { JsonObject(base + mapOf("amt" to num(r2(it)), "dev" to JsonPrimitive(2), "cob" to (x["cob"] ?: JsonPrimitive(Fechas.hoy())), "cobMed" to JsonPrimitive(MEDIO_MP))) },
                efUsa.takeIf { it >= 0.005 }?.let { JsonObject(base + mapOf("amt" to num(r2(it)), "dev" to JsonPrimitive(2), "cob" to (x["cob"] ?: JsonPrimitive(Fechas.hoy())), "cobMed" to JsonPrimitive(MEDIO_EFECTIVO))) },
                resto.takeIf { it >= 0.005 }?.let { JsonObject(base + mapOf("amt" to num(it), "dev" to JsonPrimitive(1))) })
            if (piezas.isEmpty()) return@cambiar
            var nid = nuevoId(crudo)
            lista[i] = piezas[0]
            piezas.drop(1).forEachIndexed { k, p -> lista.add(i + 1 + k, JsonObject(p + ("id" to JsonPrimitive(nid++)))) }
            this["spent"] = JsonArray(lista)
        }
        return sobra
    }

    /** Usa la plata a favor de `tp` para pagar sus deudas. Lo que se usa queda cobrado; lo que sobra sigue a favor
        (o la deuda que no alcanza, sigue debiéndose). Trabaja sobre la lista de movimientos que recibe. */
    private fun saldarConFavor(lista: MutableList<JsonElement>, tp: String, primero: Long? = null) {
        fun obj(i: Int) = lista[i] as JsonObject
        fun idDe(i: Int) = obj(i)["id"].num().toLong()
        fun suyos(neg: Boolean) = lista.indices.filter { i ->
            val x = lista[i] as? JsonObject ?: return@filter false
            x["tp"].texto() == tp && x["dev"].num().toInt() == 1 && !x["meta"].verdadero() && (x["amt"].num() < 0) == neg && x["amt"].num() != 0.0
        }.sortedWith(compareBy({ if (idDe(it) == primero) 0 else 1 }, { obj(it)["date"].texto() ?: "" }, { idDe(it) })).toMutableList()
        val creditos = suyos(true)
        val deudas = suyos(false)
        val hoy = Fechas.hoy()
        var proximoId = nuevoId(JsonObject(mapOf("spent" to JsonArray(lista))))
        /* parte un movimiento: el original queda con `resto`; uno nuevo, igual, con `usado` y ya cobrado */
        fun cobrar(i: Int, usado: Double, resto: Double, medio: String) {
            val x = obj(i); val signo = if (x["amt"].num() < 0) -1 else 1
            val marcas = mapOf("dev" to JsonPrimitive(2), "cob" to JsonPrimitive(hoy), "cobMed" to JsonPrimitive(medio))
            if (resto < 0.005) lista[i] = JsonObject(x + marcas)
            else {
                lista[i] = JsonObject(x + ("amt" to num(signo * com.miplata.kotlin.nucleo.redondear2(resto))))
                lista += JsonObject(x + mapOf("id" to JsonPrimitive(proximoId++), "amt" to num(signo * com.miplata.kotlin.nucleo.redondear2(usado))) + marcas)
            }
        }
        while (creditos.isNotEmpty() && deudas.isNotEmpty()) {
            val c = creditos.first(); val d = deudas.first()
            val tengo = -obj(c)["amt"].num(); val debe = obj(d)["amt"].num()
            val medio = obj(c)["med"].texto() ?: MEDIO_MP
            val usa = Math.min(tengo, debe)
            /* la deuda se paga con la plata que te dio (por donde te la dio); esa plata a favor se gasta (se anula) */
            cobrar(d, usa, debe - usa, medio)
            cobrar(c, usa, tengo - usa, medio)
            if (debe - usa < 0.005) deudas.removeAt(0)
            if (tengo - usa < 0.005) creditos.removeAt(0)
        }
    }

    /** Marca o desmarca un gasto importante como pagado. */
    /** Gasto importante pagado / pendiente. Con `medio` ("mp" o "ef") baja `monto` de esa cuenta;
        al volverlo a pendiente, esa plata vuelve a la misma cuenta. Sin medio: solo la marca. */
    fun marcarPagado(indice: Int, pagado: Boolean, medio: String? = null, monto: Double = 0.0) = cambiar(moverCuenta = false) {
        val items = (this["items"] as? JsonArray)?.toMutableList() ?: return@cambiar
        val o = items.getOrNull(indice) as? JsonObject ?: return@cambiar
        val m = o.toMutableMap()
        val mover = marcaDePago(m, pagado, medio, monto)
        items[indice] = JsonObject(m)
        this["items"] = JsonArray(items)
        moverPago(this, mover)
    }

    /** Pone la marca de pagado/pendiente y devuelve (medio, monto) a mover en las cuentas (monto negativo = sale). */
    private fun marcaDePago(m: MutableMap<String, JsonElement>, pagado: Boolean, medio: String?, monto: Double): Pair<String, Double>? {
        if (pagado) {
            if (m["p"].verdadero()) return null                 // ya estaba pagado: no se descuenta dos veces
            m["p"] = JsonPrimitive(true)
            if (medio == null || monto < 0.005) return null
            val r = com.miplata.kotlin.nucleo.redondear2(monto)
            m["pMed"] = JsonPrimitive(medio); m["pMonto"] = num(r)
            return medio to -r
        }
        m["p"] = JsonPrimitive(false)
        val antes = m["pMed"].texto(); val cuanto = m["pMonto"].num()
        m.remove("pMed"); m.remove("pMonto")
        return if (antes != null && cuanto > 0.005) antes to cuanto else null
    }
    private fun moverPago(datos: MutableMap<String, JsonElement>, mover: Pair<String, Double>?) {
        val (medio, monto) = mover ?: return
        val o = if (medio == MEDIO_EFECTIVO) sumarAEfectivo(JsonObject(datos), monto) else sumarAMercadoPago(JsonObject(datos), monto)
        o["cuentas"]?.let { datos["cuentas"] = it }
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
    /* ---------- Mis planes ---------- */
    private fun cambiarPlanes(f: (MutableList<JsonObject>) -> Unit) = cambiar(moverCuenta = false) {
        val l = (this["planes"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.toMutableList() ?: mutableListOf()
        f(l); this["planes"] = JsonArray(l)
    }
    private fun cambiarPlan(id: Long, f: (MutableMap<String, JsonElement>) -> Unit) = cambiarPlanes { l ->
        val i = l.indexOfFirst { it["id"].num().toLong() == id }
        if (i >= 0) { val m = l[i].toMutableMap(); f(m); l[i] = JsonObject(m) }
    }
    fun guardarPlan(id: Long?, nombre: String, total: Double, meses: Int) = if (id == null) cambiarPlanes { l ->
        l += JsonObject(mapOf("id" to JsonPrimitive(System.currentTimeMillis()), "n" to JsonPrimitive(nombre), "total" to num(total),
            "meses" to JsonPrimitive(meses.coerceIn(1, 24)), "activo" to JsonPrimitive(false), "hechas" to JsonPrimitive(0), "p" to JsonPrimitive(false)))
    } else cambiarPlan(id) {
        it["n"] = JsonPrimitive(nombre); it["total"] = num(total); it["meses"] = JsonPrimitive(meses.coerceIn(1, 24))
        if (total <= 0) { it["activo"] = JsonPrimitive(false); it.remove("desde") }     // sin monto no se puede descontar
    }
    fun borrarPlan(id: Long) = cambiarPlanes { l -> l.removeAll { it["id"].num().toLong() == id } }
    /** Activar: se descuenta desde hoy (o desde el cobro si el período todavía no empezó). Desactivar: la plata vuelve. */
    fun activarPlan(id: Long, on: Boolean) {
        val o = crudo ?: return
        val hoy = Fechas.hoy(); val start = o["start"].texto() ?: hoy; val end = o["end"].texto() ?: hoy
        val desde = if (Fechas.dia(hoy) > Fechas.dia(start)) Fechas.iso(minOf(Fechas.dia(hoy), Fechas.dia(end) - 1)) else null
        cambiarPlan(id) { m ->
            m["activo"] = JsonPrimitive(on); m["p"] = JsonPrimitive(false); m.remove("pMed"); m.remove("pMonto")
            if (on && desde != null) m["desde"] = JsonPrimitive(desde) else m.remove("desde")
        }
    }
    /** Pagado / pendiente. Si decís con qué lo pagaste (`medio`), baja `monto` de esa cuenta;
        al volverlo a pendiente, esa plata vuelve a la misma cuenta. */
    fun marcarPlanPagado(id: Long, pagado: Boolean, medio: String? = null, monto: Double = 0.0) = cambiar(moverCuenta = false) {
        val l = (this["planes"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.toMutableList() ?: return@cambiar
        val i = l.indexOfFirst { it["id"].num().toLong() == id }
        if (i < 0) return@cambiar
        val m = l[i].toMutableMap()
        val mover = marcaDePago(m, pagado, medio, monto)
        l[i] = JsonObject(m)
        this["planes"] = JsonArray(l)
        moverPago(this, mover)
    }

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

    /** Sacar plata del fondo. para = "hoy" (suma al límite de hoy), "reparte" (se reparte entre los días que quedan) o "cubre" (cubre gastos ya hechos). */
    fun usarFondo(monto: Double, nota: String, para: String) = cambiarTodo { o ->
        val l = lista(o, "fondoUsos")
        /* la plata sale de la reserva y vuelve a Mercado Pago: también baja lo "ya pasado a la reserva" (se guarda cuánto, para poder deshacerlo) */
        val pasado = o["resPasado"].num()
        val baja = Math.min(monto, Math.max(0.0, pasado))
        l += JsonObject(mapOf("id" to JsonPrimitive(nuevoId(o)), "date" to JsonPrimitive(Fechas.hoy()),
            "amt" to com.miplata.kotlin.nucleo.num(monto), "note" to JsonPrimitive(nota), "para" to JsonPrimitive(if (para == "cubre" || para == "reparte") para else "hoy"),
            "resBaja" to com.miplata.kotlin.nucleo.num(baja)))
        moverEnCuentas(JsonObject(o + mapOf("fondoUsos" to JsonArray(l),
            "resPasado" to com.miplata.kotlin.nucleo.num(com.miplata.kotlin.nucleo.redondear2(pasado - baja)))), -monto)
    }
    fun quitarUsoFondo(i: Int) = cambiarTodo { o -> val l = lista(o, "fondoUsos")
        if (i !in l.indices) return@cambiarTodo o
        val uso = l.removeAt(i) as? JsonObject
        val monto = uso?.get("amt").num()
        val nuevo = JsonObject(o + mapOf("fondoUsos" to JsonArray(l),                   // se deshace: vuelve a la reserva
            "resPasado" to com.miplata.kotlin.nucleo.num(com.miplata.kotlin.nucleo.redondear2(o["resPasado"].num() + uso?.get("resBaja").num()))))
        /* Si la plata nunca salió de Mercado Pago ("Lo dejo en disponible"), tampoco hay que devolverla a la Reserva */
        if (uso?.get("sinMover").verdadero()) nuevo else moverEnCuentas(nuevo, monto)
    }

    /** "Lo dejo en disponible de Mercado Pago": el colchón que no se pasó a la reserva se usa y se reparte en los días que quedan.
        A diferencia de usarFondo, la plata nunca estuvo en la Reserva: no toca Mis cuentas ni "ya pasado". */
    fun dejarEnDisponible(monto: Double) = cambiarTodo { o ->
        if (monto <= 0) return@cambiarTodo o
        val l = lista(o, "fondoUsos")
        l += JsonObject(mapOf("id" to JsonPrimitive(nuevoId(o)), "date" to JsonPrimitive(Fechas.hoy()),
            "amt" to com.miplata.kotlin.nucleo.num(monto), "note" to JsonPrimitive("Dejado en disponible de Mercado Pago"),
            "para" to JsonPrimitive("reparte"), "resBaja" to com.miplata.kotlin.nucleo.num(0.0), "sinMover" to JsonPrimitive(true)))
        JsonObject(o + ("fondoUsos" to JsonArray(l)))
    }

    /** "Ya lo pasé a la reserva": además, en Mis cuentas, la plata pasa de Mercado Pago a la Reserva. */
    fun sumarPasado(monto: Double) = cambiarTodo { o ->
        moverEnCuentas(JsonObject(o + ("resPasado" to com.miplata.kotlin.nucleo.num(com.miplata.kotlin.nucleo.redondear2(o["resPasado"].num() + monto)))), monto)
    }

    /** Mueve plata entre las cuentas cargadas: positivo = de Mercado Pago a la Reserva; negativo = de la Reserva a Mercado Pago.
        La reserva es la cuenta cuyo nombre dice "reserva"; Mercado Pago, la que dice "mercado" (o la primera tildada que no sea la reserva).
        Si no hay las dos cuentas cargadas, no toca nada. */
    private fun moverEnCuentas(o: JsonObject, monto: Double): JsonObject {
        val l = cuentasDe(o) ?: return o
        val (iMp, iRes) = indicesCuentas(l)
        if (iRes < 0 || iMp < 0) return o
        sumarEn(l, iRes, monto); sumarEn(l, iMp, -monto)
        return JsonObject(o + ("cuentas" to JsonArray(l)))
    }

    /** Suma (o resta, si es negativo) al saldo de Mercado Pago en Mis cuentas. Si no hay cuenta cargada, no toca nada. */
    private fun sumarAMercadoPago(o: JsonObject, monto: Double): JsonObject {
        val l = cuentasDe(o) ?: return o
        val iMp = indicesCuentas(l).first
        if (iMp < 0) return o
        sumarEn(l, iMp, monto)
        return JsonObject(o + ("cuentas" to JsonArray(l)))
    }

    /** Suma (o resta) a la cuenta "Efectivo" de Mis cuentas. Si tenés cuentas cargadas pero ninguna de efectivo,
        la crea (así la plata en la billetera no se mezcla con Mercado Pago). Sin cuentas cargadas, no toca nada. */
    private fun sumarAEfectivo(o: JsonObject, monto: Double): JsonObject {
        val l = cuentasDe(o) ?: return o
        val i = indiceEfectivo(l)
        if (i >= 0) sumarEn(l, i, monto)
        else l += JsonObject(mapOf("n" to JsonPrimitive("Efectivo"), "m" to num(com.miplata.kotlin.nucleo.redondear2(monto))))
        return JsonObject(o + ("cuentas" to JsonArray(l)))
    }

    private fun indiceEfectivo(l: List<JsonObject>) = l.indexOfFirst { (it["n"].texto() ?: "").lowercase().contains("efectivo") }

    private fun cuentasDe(o: JsonObject) =
        (o["cuentas"] as? JsonArray)?.map { it as? JsonObject ?: JsonObject(emptyMap()) }?.toMutableList()?.takeIf { it.isNotEmpty() }

    /** (Mercado Pago, Reserva): la reserva es la que dice "reserva"; Mercado Pago, la que dice "mercado" (o la primera tildada que no sea la reserva). -1 si no está. */
    private fun indicesCuentas(l: List<JsonObject>): Pair<Int, Int> {
        fun nombre(c: JsonObject) = (c["n"].texto() ?: "").lowercase()
        val iRes = l.indexOfFirst { nombre(it).contains("reserva") }
        val iMp = l.indices.firstOrNull { it != iRes && nombre(l[it]).contains("mercado") }
            ?: l.indices.firstOrNull { it != iRes && !l[it]["x"].verdadero() && !(l[it]["n"].texto() ?: "").lowercase().contains("efectivo") } ?: -1
        return iMp to iRes
    }

    private fun sumarEn(l: MutableList<JsonObject>, i: Int, v: Double) {
        l[i] = JsonObject(l[i] + ("m" to num(com.miplata.kotlin.nucleo.redondear2(l[i]["m"].num() + v))))
    }

    /** Desde Ajustes: "Ya pasé a la reserva (en total)". Lo que cambió también pasa de Mercado Pago a la Reserva. */
    fun fijarPasado(total: Double) = cambiarTodo { o ->
        val nuevo = Math.max(0.0, com.miplata.kotlin.nucleo.redondear2(total))
        moverEnCuentas(JsonObject(o + ("resPasado" to num(nuevo))), nuevo - o["resPasado"].num())
    }

    data class CuentaEditable(val n: String, val m: Double, val cuenta: Boolean)
    fun guardarCuentas(l: List<CuentaEditable>) = cambiarTodo { o ->
        val cuentas = JsonArray(l.map { c ->
            JsonObject(buildMap { put("n", JsonPrimitive(c.n)); put("m", com.miplata.kotlin.nucleo.num(c.m)); if (!c.cuenta) put("x", JsonPrimitive(true)) })
        })
        /* Los saldos que cargás ya traen los rendimientos reales: desde acá se cuentan los nuevos */
        val r = rendimientosDe(o, Fechas.hoy())
        JsonObject(o + mapOf("cuentas" to cuentas, "rendCta" to marcaRend(r, o["start"].texto())))
    }

    /** Rendimientos que calcula la app hoy: (de la plata disponible → Mercado Pago, del fondo → Reserva). */
    private fun rendimientosDe(o: JsonObject, hoy: String): Pair<Double, Double> = runCatching {
        val c = com.miplata.kotlin.nucleo.Nucleo.calcular(Estado.desdeJson(o), hoy)
        c.rendDisp to (if (c.fondo.activo && c.fondo.rendEnMP) c.fondo.rend else 0.0)
    }.getOrDefault(0.0 to 0.0)

    /* Se guarda también el inicio del período ("s"), para saber de verdad cuándo empieza uno nuevo */
    private fun marcaRend(r: Pair<Double, Double>, inicio: String? = crudo?.get("start").texto()) =
        JsonObject(buildMap { put("d", num(r.first)); put("f", num(r.second)); if (!inicio.isNullOrEmpty()) put("s", JsonPrimitive(inicio)) })

    /** Mercado Pago te paga rendimientos todos los días: los que calcula la app se suman solos a Mis cuentas
        (lo de la plata disponible a Mercado Pago, lo del fondo a la Reserva). true = cambió algo. */
    fun aplicarRendimientos(hoy: String = Fechas.hoy()): Boolean {
        val o = crudo ?: return false
        val l = cuentasDe(o) ?: return false
        val r = rendimientosDe(o, hoy)
        val previo = o["rendCta"] as? JsonObject
        /* La primera vez se toma lo de ayer como punto de partida (los saldos son de ayer o de antes) */
        val base = if (previo != null) previo["d"].num() to previo["f"].num() else rendimientosDe(o, Fechas.iso(Fechas.dia(hoy) - 1))
        /* Período nuevo: los rendimientos de la app vuelven a empezar de cero.
           Antes se adivinaba "período nuevo" si el estimado bajaba, pero el estimado también baja cuando sacás del fondo
           o pagás algo: entonces se sumaba de nuevo TODO el rendimiento a Mis cuentas. Ahora se compara el inicio del período. */
        val inicio = o["start"].texto() ?: ""
        val inicioPrevio = previo?.get("s").texto()
        val periodoNuevo = if (inicioPrevio != null) inicioPrevio != inicio
            else (r.first + 0.01 < base.first && r.first < base.first * 0.5) || (r.second + 0.01 < base.second && r.second < base.second * 0.5)
        val dd = r.first - (if (periodoNuevo) 0.0 else base.first)
        val df = r.second - (if (periodoNuevo) 0.0 else base.second)
        if (Math.abs(dd) < 0.01 && Math.abs(df) < 0.01) {
            if (previo == null || previo["s"] == null) cambiarTodo { JsonObject(it + ("rendCta" to marcaRend(r, o["start"].texto()))) }
            return false
        }
        val (iMp, iRes) = indicesCuentas(l)
        if (iMp >= 0) sumarEn(l, iMp, dd)
        if (iRes >= 0) sumarEn(l, iRes, df) else if (iMp >= 0) sumarEn(l, iMp, df)
        cambiarTodo { JsonObject(it + mapOf("cuentas" to JsonArray(l), "rendCta" to marcaRend(r, o["start"].texto()))) }
        return true
    }

    /** Recomposición: la diferencia con las cuentas se reparte desde mañana (como "Ajustar con las cuentas"). */
    fun agregarRecomposicion(monto: Double) = cambiarTodo { o ->
        val l = lista(o, "ajustes"); val hoy = Fechas.hoy()
        l += JsonObject(mapOf("id" to JsonPrimitive(nuevoId(o)), "date" to JsonPrimitive(hoy), "desde" to JsonPrimitive(Fechas.manana(hoy)),
            "amt" to com.miplata.kotlin.nucleo.num(com.miplata.kotlin.nucleo.redondear2(monto)), "note" to JsonPrimitive("Recomposición")))
        JsonObject(o + ("ajustes" to JsonArray(l)))
    }
    fun quitarRecomposicion(i: Int) = cambiarTodo { o -> val l = lista(o, "ajustes")
        val quitado = if (i in l.indices) l.removeAt(i) as? JsonObject else null
        val sinEl = JsonObject(o + ("ajustes" to JsonArray(l)))
        /* si era plata que entró a Mercado Pago (repartida), también sale de Mis cuentas */
        if (quitado != null && quitado["mp"].verdadero()) sumarAMercadoPago(sinEl, -quitado["amt"].num()) else sinEl
    }

    /** Plata que te entró a Mercado Pago y se reparte en partes iguales entre hoy y los días que quedan del período
        (no sube solo el límite de hoy). Sube el saldo de Mercado Pago en Mis cuentas. */
    fun entradaRepartida(monto: Double, nota: String): Long {
        var id = 0L
        cambiarTodo { o ->
            val l = lista(o, "ajustes"); val hoy = Fechas.hoy(); id = nuevoId(o)
            l += JsonObject(mapOf("id" to JsonPrimitive(id), "date" to JsonPrimitive(hoy), "desde" to JsonPrimitive(hoy),
                "amt" to com.miplata.kotlin.nucleo.num(com.miplata.kotlin.nucleo.redondear2(monto)), "note" to JsonPrimitive(nota), "mp" to JsonPrimitive(true)))
            sumarAMercadoPago(JsonObject(o + ("ajustes" to JsonArray(l))), monto)
        }
        return id
    }

    /** Cambia la tasa desde HOY: los días que ya pasaron siguen con la tasa de antes (como Mercado Pago). */
    fun cambiarTasa(nueva: Double, auto: Boolean? = null) = cambiarTodo { o ->
        val vieja = o["rendTasa"].num(); val hoy = Fechas.hoy()
        val l = lista(o, "rendTasas").mapNotNull { it as? JsonObject }.toMutableList()
        val iHoy = l.indexOfFirst { it["d"].texto() == hoy }
        if (iHoy >= 0) l[iHoy] = JsonObject(l[iHoy] + ("t" to num(nueva)))
        else if (Math.abs(nueva - vieja) >= 0.001) l += JsonObject(mapOf("d" to JsonPrimitive(hoy), "t" to num(nueva), "a" to num(vieja)))
        JsonObject(o + mapOf("rendTasa" to num(nueva), "rendTasas" to JsonArray(l.takeLast(90))) +
            (if (auto != null) mapOf("rendTasaAuto" to JsonPrimitive(auto)) else emptyMap()))
    }

    /* ---------- Plata que entra por Mercado Pago (se anota sola desde la notificación) ---------- */

    /** Entrada común a Mercado Pago (suma a tu día). Devuelve el id, para poder deshacerla. */
    fun anotarEntradaMP(monto: Double, nota: String): Long {
        var id = 0L
        cambiar {
            val lista = spent(); id = nuevoId(crudo)
            lista += JsonObject(mapOf("id" to JsonPrimitive(id), "date" to JsonPrimitive(Fechas.hoy()),
                "amt" to JsonPrimitive(-Math.abs(monto)), "note" to JsonPrimitive(nota), "auto" to JsonPrimitive("mp")))
            this["spent"] = JsonArray(lista)
        }
        return id
    }

    /** Marca como cobradas por Mercado Pago esas deudas de "Me deben" (la plata entra sola a Mercado Pago). */
    fun cobrarPorMP(ids: List<Long>) = cambiar {
        this["spent"] = JsonArray(spent().map { e ->
            val o = e as? JsonObject ?: return@map e
            if (o["id"]?.jsonPrimitive?.longOrNull in ids) JsonObject(o + mapOf("dev" to JsonPrimitive(2), "cob" to JsonPrimitive(Fechas.hoy()),
                "cobMed" to JsonPrimitive("mp"))) else o
        })
    }

    /** Pago parcial por Mercado Pago de una deuda de "Me deben": la deuda se parte en dos, lo pagado queda cobrado
        (dev = 2) y lo que falta sigue debiendo. El gasto total no cambia; Mercado Pago sube lo que te pagaron.
        Devuelve el id de la parte cobrada (para poder deshacerlo). */
    fun cobrarParcialMP(id: Long, monto: Double): Long {
        var nuevo = 0L
        cambiar {
            val lista = spent().toMutableList()
            val i = lista.indexOfFirst { (it as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull == id }
            if (i < 0) return@cambiar
            val o = lista[i] as JsonObject
            val pago = com.miplata.kotlin.nucleo.redondear2(Math.min(monto, o["amt"].num()))
            nuevo = nuevoId(crudo)
            lista[i] = JsonObject(o + ("amt" to num(com.miplata.kotlin.nucleo.redondear2(o["amt"].num() - pago))))
            lista += JsonObject(o + mapOf("id" to JsonPrimitive(nuevo), "amt" to num(pago), "dev" to JsonPrimitive(2),
                "cob" to JsonPrimitive(Fechas.hoy()), "cobMed" to JsonPrimitive("mp"), "parteDe" to JsonPrimitive(id)))
            this["spent"] = JsonArray(lista)
        }
        return nuevo
    }

    /** Deshacer lo que se anotó solo: borra la entrada / el reparto, o vuelve las deudas a "Me deben". */
    fun deshacerEntradaMP(entrada: Long, repartida: Long, cobradas: List<Long>,
                          parcialNuevo: Long = 0L, parcialDe: Long = 0L, parcialMonto: Double = 0.0) {
        /* pago parcial: se junta otra vez la deuda entera */
        if (parcialNuevo != 0L) cambiar {
            this["spent"] = JsonArray(spent().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull e
                when (o["id"]?.jsonPrimitive?.longOrNull) {
                    parcialNuevo -> null
                    parcialDe -> JsonObject(o + ("amt" to num(com.miplata.kotlin.nucleo.redondear2(o["amt"].num() + parcialMonto))))
                    else -> o
                }
            })
        }
        if (entrada != 0L) borrarMovimiento(entrada)
        if (repartida != 0L) cambiarTodo { o -> val l = lista(o, "ajustes")
            val i = l.indexOfFirst { (it as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull == repartida }
            if (i < 0) o else {
                val q = l.removeAt(i) as JsonObject
                sumarAMercadoPago(JsonObject(o + ("ajustes" to JsonArray(l))), -q["amt"].num())
            }
        }
        if (cobradas.isNotEmpty()) cambiar {
            this["spent"] = JsonArray(spent().map { e ->
                val o = e as? JsonObject ?: return@map e
                if (o["id"]?.jsonPrimitive?.longOrNull in cobradas) JsonObject(o.filterKeys { it != "cob" && it != "cobMed" } + ("dev" to JsonPrimitive(1))) else o
            })
        }
    }

    /** Activar o apagar el fondo de ahorro (cuenta desde hoy: no toca lo que ya tenías). */
    fun activarFondo(on: Boolean) = cambiarTodo { o ->
        JsonObject(o + mapOf("modoFondo" to JsonPrimitive(on)) + (if (on) mapOf("fondoDesde" to JsonPrimitive(Fechas.hoy())) else emptyMap()))
    }
    /** Desde el día [dia] de cada mes, lo que sobra se reparte en los días que quedan en vez de ir al fondo (0 = nunca). */
    fun sobraSeReparte(dia: Int) = cambiarTodo { o -> JsonObject(o + ("sobraReparteDia" to JsonPrimitive(if (dia == Estado.REPARTE_TODO) dia else dia.coerceIn(0, 31)))) }
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
        val nueva = JsonObject(mapOf("id" to JsonPrimitive(id ?: nuevoId(o)), "n" to JsonPrimitive(n), "t" to num(t), "g0" to num(g0), "f" to JsonPrimitive(f)))
        val i = l.indexOfFirst { (it as? JsonObject)?.get("id").num().toLong() == id }
        if (id != null && i >= 0) l[i] = JsonObject((l[i] as JsonObject) + nueva) else l += nueva
        JsonObject(o + ("metas" to JsonArray(l)))
    }
    fun quitarMeta(id: Long) = cambiarTodo { o ->
        JsonObject(o + ("metas" to JsonArray(lista(o, "metas").filter { (it as? JsonObject)?.get("id").num().toLong() != id })))
    }
    fun aportar(id: Long, nombre: String, monto: Double) = cambiarTodo { o ->
        val l = lista(o, "spent")
        l += JsonObject(mapOf("id" to JsonPrimitive(nuevoId(o)), "date" to JsonPrimitive(Fechas.hoy()), "amt" to num(monto),
            "note" to JsonPrimitive("🎯 $nombre"), "meta" to JsonPrimitive(id)))
        JsonObject(o + ("spent" to JsonArray(l)))
    }

    /* ---- Me deben ---- */
    /** Cobrado. `previo` = viene del período anterior: la plata entra hoy como una entrada.
        `efectivo` = te pagó en mano: la plata va a la cuenta Efectivo, no a Mercado Pago.
        `monto` = lo que te dio, si es menos que la deuda (pagó una parte): lo demás sigue en "Me deben". null = todo. */
    fun cobrado(id: Long, previo: Boolean, efectivo: Boolean = false, monto: Double? = null) {
        val hoy = Fechas.hoy()
        val medio = if (efectivo) MEDIO_EFECTIVO else MEDIO_MP
        if (!previo) cambiar {
            val lista = spent()
            val i = lista.indexOfFirst { (it as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull == id }
            val x = lista.getOrNull(i) as? JsonObject ?: return@cambiar
            val deuda = x["amt"].num()
            val pago = monto?.takeIf { it > 0 }?.let { com.miplata.kotlin.nucleo.redondear2(it) } ?: deuda
            val marcas = mapOf("dev" to JsonPrimitive(2), "cob" to JsonPrimitive(hoy), "cobMed" to JsonPrimitive(medio))
            if (pago >= deuda - 0.005) lista[i] = JsonObject(x + marcas)
            else {
                /* pagó una parte: la deuda baja y lo pagado queda como un renglón aparte, ya cobrado */
                lista[i] = JsonObject(x + ("amt" to num(com.miplata.kotlin.nucleo.redondear2(deuda - pago))))
                lista.add(i + 1, JsonObject(x + mapOf("id" to JsonPrimitive(nuevoId(crudo)), "amt" to num(pago)) + marcas))
            }
            this["spent"] = JsonArray(lista)
        } else cambiar {
            val deben = lista(JsonObject(this), "meDeben")
            val j = deben.indexOfFirst { (it as? JsonObject)?.get("id").num().toLong() == id }
            val x = deben.getOrNull(j) as? JsonObject ?: return@cambiar
            val deuda = Math.abs(x["amt"].num())
            val pago = monto?.takeIf { it > 0 && it < deuda - 0.005 }?.let { com.miplata.kotlin.nucleo.redondear2(it) } ?: deuda
            val sp = spent()
            sp += JsonObject(mapOf("id" to JsonPrimitive(nuevoId(crudo)), "date" to JsonPrimitive(hoy),
                "amt" to num(-pago), "note" to JsonPrimitive("Me devolvieron: " + (x["note"].texto() ?: ""))) +
                (if (efectivo) mapOf("med" to JsonPrimitive(MEDIO_EFECTIVO)) else emptyMap()))
            if (pago >= deuda - 0.005) deben.removeAt(j)
            else deben[j] = JsonObject(x + ("amt" to num(com.miplata.kotlin.nucleo.redondear2(deuda - pago))))
            this["spent"] = JsonArray(sp)
            this["meDeben"] = JsonArray(deben)
        }
    }
    /** No me lo van a pagar: pasa a ser un gasto propio. */
    fun noPagaron(id: Long, previo: Boolean) = cambiarTodo { o ->
        if (previo) JsonObject(o + ("meDeben" to JsonArray(lista(o, "meDeben").filter { (it as? JsonObject)?.get("id").num().toLong() != id })))
        else JsonObject(o + ("spent" to JsonArray(lista(o, "spent").map { e ->
            val x = e as? JsonObject ?: return@map e
            if (x["id"].num().toLong() == id) JsonObject(x - "dev" - "cob" - "cobMed") else x
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

    /** Aplica un cambio solo si devuelve algo (no toca la fecha de actualización). */
    fun cambiarSiHace(f: (JsonObject) -> JsonObject?): Boolean {
        val nuevo = f(crudo ?: return false) ?: return false
        guardar(nuevo); return true
    }

    /* ---- Modo demo: los datos reales se guardan aparte y vuelven al salir ---- */
    private val real = File(carpeta, "estado.real.json")
    val enDemo: Boolean get() = real.exists()
    fun entrarDemo(demo: JsonObject) { if (!enDemo && archivo.exists()) archivo.copyTo(real, overwrite = true); guardar(demo) }
    fun salirDemo() { if (real.exists()) { val o = Json.parseToJsonElement(real.readText()).jsonObject; guardar(o); real.delete() } }
}
