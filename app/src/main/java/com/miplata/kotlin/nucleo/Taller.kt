package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*

/* ---------- Compras del taller ----------
   Lo que antes iba en la app "Compras del taller", ahora dentro de Mi plata.
   Se guarda en la copia bajo la clave "taller", así no toca nada de las cuentas.

   Cómo se anota una compra: tu parte va como gasto común; la parte de cada muchacho va como
   "Me lo devuelven" (dev = 1) con su nombre. Es el mismo mecanismo que ya usa la app, así que
   las cuentas no cambian: cuando te paga, tocás "Me lo pagaron" y queda devuelto (dev = 2). */

data class Muchacho(val id: String, val nombre: String, val apodo: String = "", val activo: Boolean = true) {
    /** "Ariel (Chuña)" o solo "Ariel" */
    val mostrar: String get() = if (apodo.isBlank()) nombre else "$nombre ($apodo)"
}

/** Un día del horario: M = turno mañana, T = turno tarde, F = franco. */
data class Taller(
    val muchachos: List<Muchacho> = emptyList(),
    val precios: Map<String, Double> = emptyMap(),
    val horario: Map<String, String> = emptyMap(),
    val manana: Pair<String, String> = "09:20" to "10:10",
    val tarde: Pair<String, String> = "17:20" to "18:00",
    val aviso: Boolean = true,
    /** Hasta qué hora se estira el desayuno / la merienda: ahí te recuerda a quién cobrarle. */
    val cobroManana: String = "10:40",
    val cobroTarde: String = "19:00",
    /** Pedidos por día: fecha → (id del muchacho o "yo") → (cosa → cantidad). */
    val pedidos: Map<String, Map<String, Map<String, Int>>> = emptyMap(),
    /** Lo que ya tildaste en el negocio, por día. */
    val tildes: Map<String, List<String>> = emptyMap(),
    /** "Siempre lo mismo": quien → su pedido de siempre. Aparece solo cada día sin tener que anotarlo. */
    val fijos: Map<String, Map<String, Int>> = emptyMap(),
    /** Días en que alguien con pedido fijo no pide nada (le sacaste todo ese día). */
    val sinFijo: Map<String, List<String>> = emptyMap(),
) {
    val activos get() = muchachos.filter { it.activo }

    /** Turno de ese día según el horario cargado (null = no cargado o franco). */
    fun turno(fecha: String): String? = horario[fecha]?.takeIf { it == "M" || it == "T" }

    /** Ventana de compra de ese día (desde, hasta) o null si no trabaja. */
    fun ventana(fecha: String): Pair<String, String>? = when (turno(fecha)) { "M" -> manana; "T" -> tarde; else -> null }

    /** Pedidos de ese día: lo anotado + el pedido de siempre de los que lo tienen y ese día no cambiaron nada. */
    fun pedidosDe(fecha: String): Map<String, Map<String, Int>> {
        val anotado = pedidos[fecha] ?: emptyMap()
        val nada = sinFijo[fecha] ?: emptyList()
        val vigentes = activos.map { it.id }.toSet() + "yo"
        val deSiempre = fijos.filter { (q, p) -> q !in anotado && q !in nada && q in vigentes && p.isNotEmpty() }
        return anotado + deSiempre
    }

    fun esFijo(quien: String) = fijos[quien]?.isNotEmpty() == true

    /** Tilda / destilda "Siempre lo mismo": se guarda lo que pidió ese día como su pedido de siempre. */
    fun conFijo(fecha: String, quien: String, si: Boolean): Taller {
        val hoy = pedidosDe(fecha)[quien] ?: emptyMap()
        return if (si) { if (hoy.isEmpty()) this else copy(fijos = fijos + (quien to hoy)) }
        else {
            /* al destildar, lo de hoy queda anotado igual (solo deja de repetirse desde mañana) */
            val dia = (pedidos[fecha] ?: emptyMap()).toMutableMap()
            if (hoy.isNotEmpty()) dia[quien] = hoy
            copy(fijos = fijos - quien, pedidos = pedidos + (fecha to dia),
                sinFijo = sinFijo + (fecha to ((sinFijo[fecha] ?: emptyList()) - quien)))
        }
    }

    /** Cuánto sale lo que pidió alguien. */
    fun costo(pedido: Map<String, Int>): Double = pedido.entries.sumOf { (precios[it.key] ?: 0.0) * it.value }

    /** "1 Yogur, 2 Tortitas" */
    fun texto(pedido: Map<String, Int>): String = pedido.filter { it.value > 0 }.entries.joinToString(", ") { "${it.value} ${it.key}" }

    /** Lista para comprar: todo junto, sumado por cosa. */
    fun listaCompra(fecha: String): Map<String, Int> {
        val l = sortedMapOf<String, Int>()
        pedidosDe(fecha).values.forEach { p -> p.forEach { (cosa, n) -> if (n > 0) l[cosa] = (l[cosa] ?: 0) + n } }
        return l
    }

    /** Cambia la cantidad de una cosa en el pedido de alguien. Borra lo que queda en 0 y pedidos de hace más de 30 días. */
    fun conCantidad(fecha: String, quien: String, cosa: String, cant: Int): Taller {
        val dia = (pedidos[fecha] ?: emptyMap()).toMutableMap()
        val p = (pedidosDe(fecha)[quien] ?: emptyMap()).toMutableMap()   // parte de lo de siempre, si lo tiene
        if (cant <= 0) p.remove(cosa) else p[cosa] = cant
        if (p.isEmpty()) dia.remove(quien) else dia[quien] = p
        /* si alguien con pedido fijo queda sin nada ese día, se recuerda para no volver a ponérselo */
        val nada = (sinFijo[fecha] ?: emptyList()).let { if (p.isEmpty() && esFijo(quien)) (it + quien).distinct() else it - quien }
        val limite = runCatching { Fechas.iso(Fechas.dia(fecha) - 30) }.getOrDefault("")
        val todos = (pedidos + (fecha to dia)).filter { it.value.isNotEmpty() && it.key >= limite }
        return copy(pedidos = todos, tildes = tildes.filterKeys { it >= limite },
            sinFijo = (sinFijo + (fecha to nada)).filter { it.value.isNotEmpty() && it.key >= limite })
    }

    /** Hora del recordatorio para cobrar ese día. */
    fun horaCobro(fecha: String): String? = when (turno(fecha)) { "M" -> cobroManana; "T" -> cobroTarde; else -> null }

    /** ¿A esta hora Martín está haciendo las compras? "HH:mm" */
    fun enVentana(fecha: String, hora: String): Boolean {
        val (d, h) = ventana(fecha) ?: return false
        val m = minutos(hora); return m >= minutos(d) && m <= minutos(h)
    }

    fun aJson(): JsonObject = JsonObject(mapOf(
        "muchachos" to JsonArray(muchachos.map { JsonObject(mapOf("id" to JsonPrimitive(it.id), "nombre" to JsonPrimitive(it.nombre),
            "apodo" to JsonPrimitive(it.apodo), "activo" to JsonPrimitive(it.activo))) }),
        "precios" to JsonObject(precios.mapValues { JsonPrimitive(it.value) }),
        "horario" to JsonObject(horario.toSortedMap().mapValues { JsonPrimitive(it.value) }),
        "manana" to JsonArray(listOf(JsonPrimitive(manana.first), JsonPrimitive(manana.second))),
        "tarde" to JsonArray(listOf(JsonPrimitive(tarde.first), JsonPrimitive(tarde.second))),
        "aviso" to JsonPrimitive(aviso),
        "cobroManana" to JsonPrimitive(cobroManana), "cobroTarde" to JsonPrimitive(cobroTarde),
        "pedidos" to JsonObject(pedidos.toSortedMap().mapValues { (_, dia) -> JsonObject(dia.mapValues { (_, p) ->
            JsonObject(p.mapValues { JsonPrimitive(it.value) }) }) }),
        "tildes" to JsonObject(tildes.mapValues { JsonArray(it.value.map(::JsonPrimitive)) }),
        "fijos" to JsonObject(fijos.mapValues { (_, p) -> JsonObject(p.mapValues { JsonPrimitive(it.value) }) }),
        "sinFijo" to JsonObject(sinFijo.mapValues { JsonArray(it.value.map(::JsonPrimitive)) }),
    ))

    companion object {
        fun minutos(h: String): Int = h.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }

        private fun par(e: JsonElement?, def: Pair<String, String>): Pair<String, String> {
            val a = (e as? JsonArray)?.mapNotNull { it.texto() } ?: return def
            val ok = a.size == 2 && a.all { Regex("""\d{1,2}:\d{2}""").matches(it) }
            return if (ok) a[0] to a[1] else def
        }

        fun de(crudo: JsonObject?): Taller {
            val o = crudo?.get("taller") as? JsonObject ?: return Taller()
            return Taller(
                muchachos = o.lista("muchachos").map { Muchacho(it["id"].texto() ?: "", it["nombre"].texto() ?: "", it["apodo"].texto() ?: "",
                    (it["activo"] as? JsonPrimitive)?.booleanOrNull != false) }.filter { it.nombre.isNotBlank() },
                precios = (o["precios"] as? JsonObject)?.mapValues { it.value.num() } ?: emptyMap(),
                horario = (o["horario"] as? JsonObject)?.mapNotNull { (k, v) -> v.texto()?.let { k to it } }?.toMap() ?: emptyMap(),
                manana = par(o["manana"], "09:20" to "10:10"),
                tarde = par(o["tarde"], "17:20" to "18:00"),
                aviso = (o["aviso"] as? JsonPrimitive)?.booleanOrNull != false,
                cobroManana = o["cobroManana"].texto()?.takeIf { Regex("""\d{1,2}:\d{2}""").matches(it) } ?: "10:40",
                cobroTarde = o["cobroTarde"].texto()?.takeIf { Regex("""\d{1,2}:\d{2}""").matches(it) } ?: "19:00",
                pedidos = (o["pedidos"] as? JsonObject)?.mapValues { (_, dia) -> (dia as? JsonObject)?.mapValues { (_, p) ->
                    (p as? JsonObject)?.mapValues { it.value.num().toInt() }?.filterValues { it > 0 } ?: emptyMap() } ?: emptyMap() } ?: emptyMap(),
                tildes = (o["tildes"] as? JsonObject)?.mapValues { (_, l) -> (l as? JsonArray)?.mapNotNull { it.texto() } ?: emptyList() } ?: emptyMap(),
                fijos = (o["fijos"] as? JsonObject)?.mapValues { (_, p) ->
                    (p as? JsonObject)?.mapValues { it.value.num().toInt() }?.filterValues { it > 0 } ?: emptyMap() }?.filterValues { it.isNotEmpty() } ?: emptyMap(),
                sinFijo = (o["sinFijo"] as? JsonObject)?.mapValues { (_, l) -> (l as? JsonArray)?.mapNotNull { it.texto() } ?: emptyList() } ?: emptyMap(),
            )
        }

        /** Trae los muchachos y precios de una copia de la app "Compras del taller".
            Se saltea a "yo" (vos mismo). Si ya existía alguien con el mismo nombre, se conserva su apodo. */
        fun importarCompras(texto: String, actual: Taller): Taller? {
            val o = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
            if (o["personas"] !is JsonArray) return null
            val nuevos = o.lista("personas").filter { !it["yo"].verdadero() }.mapNotNull { p ->
                val nombre = p["nombre"].texto()?.trim().orEmpty(); if (nombre.isEmpty()) return@mapNotNull null
                val previo = actual.muchachos.firstOrNull { it.nombre.equals(nombre, true) }
                Muchacho(previo?.id ?: (p["id"].texto() ?: nombre), nombre, previo?.apodo ?: "",
                    (p["activo"] as? JsonPrimitive)?.booleanOrNull != false)
            }
            val resto = actual.muchachos.filter { m -> nuevos.none { it.nombre.equals(m.nombre, true) } }
            val precios = actual.precios + ((o["precios"] as? JsonObject)?.mapValues { it.value.num() } ?: emptyMap())
            return actual.copy(muchachos = nuevos + resto, precios = precios)
        }

        /** Convierte entrada/salida de cada día (leídas de la foto de la planilla) en turnos.
            Entra antes de las 12 = mañana; desde las 12 = tarde; sin horario = franco. */
        fun turnoPorEntrada(entra: String?): String =
            if (entra.isNullOrBlank() || !Regex("""\d{1,2}:\d{2}""").matches(entra.trim())) "F"
            else if (minutos(entra.trim()) < 12 * 60) "M" else "T"
    }
}

/** ¿Ya anotó hoy alguna compra del taller? */
fun Estado.hayCompraTaller(fecha: String): Boolean = spent.any { it.date == fecha && it.extra["taller"] != null }

/** Lo que te deben hoy del taller (todavía sin pagar). */
fun Estado.deudasTallerDe(fecha: String): List<Movimiento> = spent.filter { it.date == fecha && it.dev == 1 && it.extra["taller"] != null }
