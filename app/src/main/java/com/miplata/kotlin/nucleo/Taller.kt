package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*

/* ---------- Compras del taller ----------
   Lo que antes iba en la app "Compras del taller", ahora dentro de Mi plata.
   Se guarda en la copia bajo la clave "taller", así no toca nada de las cuentas.

   Cómo se anota una compra: tu parte va como gasto común; la parte de cada muchacho va como
   "Me lo devuelven" (dev = 1) con su nombre. Es el mismo mecanismo que ya usa la app, así que
   las cuentas no cambian: cuando te paga, tocás "Me lo pagaron" y queda devuelto (dev = 2). */

data class Muchacho(val id: String, val nombre: String, val apodo: String = "", val activo: Boolean = true, val tel: String = "") {
    /** "Ariel (Chuña)" o solo "Ariel" */
    val mostrar: String get() = if (apodo.isBlank()) nombre else "$nombre ($apodo)"
}

/** Lo que te cobraron en total por una cosa que se reparte (los sándwiches de la fiambrería), y cuántas unidades eran. */
data class Cobrado(val total: Double, val unidades: Int)

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
    /** Link de cobro de Mercado Pago (sin monto) que va en el mensaje de WhatsApp. */
    val linkCobro: String = LINK_COBRO,
    /** Alias para transferir (sin comisión), también va en el mensaje. */
    val aliasCobro: String = ALIAS_COBRO,
    /** Lo ya pagado por día (quien → cosa → cantidad): se puede pagar por partes (las tortas en un negocio, la fruta en otro). */
    val compradas: Map<String, Map<String, Map<String, Int>>> = emptyMap(),
    /** En qué turno se pide cada cosa del menú: "M" mañana, "T" tarde, "A" las dos. Sin marca = mañana (así quedó todo lo anterior). */
    val turnoCosa: Map<String, String> = emptyMap(),
    /** Cosas sin precio fijo: el negocio dice el total y se reparte según lo que pidió cada uno (los sándwiches de fiambre). */
    val porTotal: Set<String> = emptySet(),
    /** Lo que te cobraron en total por las cosas que se reparten: fecha → cosa → total y unidades. */
    val totales: Map<String, Map<String, Cobrado>> = emptyMap(),
) {
    /** ¿Esta cosa se pide en ese turno? Sin turno (franco o sin horario cargado) se ve todo el menú. */
    fun enMenu(cosa: String, turno: String?): Boolean =
        turno == null || (turnoCosa[cosa] ?: "M").let { it == "A" || it == turno }

    /** Las cosas (con su precio) del menú de ese turno. */
    fun menuDe(turno: String?): Map<String, Double> = precios.filterKeys { enMenu(it, turno) }

    /** ¿Ya se pagó algo de los pedidos de ese día? Hace falta que haya una compra anotada Y que se haya marcado desde los pedidos:
        una compra suelta (bananas, mandarinas) no cuenta como pago de los pedidos. */
    fun hayCompraDe(fecha: String, hayMovimiento: Boolean): Boolean = hayMovimiento && compradas[fecha] != null

    /** Lo ya pagado ese día. Si hay una compra anotada de antes de existir el pago por partes, cuenta como todo pagado. */
    fun compradoDe(fecha: String, hayCompra: Boolean): Map<String, Map<String, Int>> =
        compradas[fecha] ?: if (hayCompra) pedidosDe(fecha) else emptyMap()

    /** Lo que falta pagar ese día, por persona (lo pedido menos lo ya pagado). */
    fun pendientePedidos(fecha: String, hayCompra: Boolean): Map<String, Map<String, Int>> {
        val ya = compradoDe(fecha, hayCompra)
        return pedidosDe(fecha).mapValues { (q, p) -> p.mapValues { (c, n) -> n - (ya[q]?.get(c) ?: 0) }.filterValues { it > 0 } }
            .filterValues { it.isNotEmpty() }
    }

    /** Las cosas que todavía falta pagar ese día. */
    fun pendientesDe(fecha: String, hayCompra: Boolean): Set<String> = pendientePedidos(fecha, hayCompra).values.flatMap { it.keys }.toSet()

    /** Lo que falta pagar, pero solo de estas cosas (para pagar una parte). */
    fun pedidosSolo(fecha: String, cosas: Set<String>, hayCompra: Boolean): Map<String, Map<String, Int>> =
        pendientePedidos(fecha, hayCompra).mapValues { (_, p) -> p.filterKeys { it in cosas } }.filterValues { it.isNotEmpty() }

    /** Suma lo pagado ese día. */
    fun conCompradas(fecha: String, pagado: Map<String, Map<String, Int>>): Taller {
        val dia = (compradas[fecha] ?: emptyMap()).toMutableMap()
        pagado.forEach { (q, p) -> dia[q] = (dia[q] ?: emptyMap()).toMutableMap().apply { p.forEach { (c, n) -> this[c] = (this[c] ?: 0) + n } } }
        return copy(compradas = compradas + (fecha to dia))
    }

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
        val turno = turno(fecha)
        /* lo de siempre solo aparece en el turno donde se pide (las tortitas de la mañana no salen a la tarde) */
        val deSiempre = fijos.filter { (q, p) -> q !in anotado && q !in nada && q in vigentes && p.isNotEmpty() }
            .mapValues { (_, p) -> p.filterKeys { enMenu(it, turno) } }.filterValues { it.isNotEmpty() }
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

    /** Lo que le toca a UNA persona por su pedido de ese día. Si te cobraron un total por una cosa que se reparte,
        se divide por cantidad y se redondea para abajo al peso (lo que sobra queda en tu parte). Si no, precio × cantidad. */
    fun costoDe(fecha: String, pedido: Map<String, Int>): Double = pedido.entries.sumOf { (cosa, n) ->
        val c = totales[fecha]?.get(cosa)
        if (cosa in porTotal && c != null) parte(c.total, n, c.unidades) else (precios[cosa] ?: 0.0) * n
    }

    /** Lo que vale esa cosa en esa cantidad: el total que te cobraron (si se reparte y ya lo pusiste) o precio × cantidad. */
    fun costoCosa(fecha: String, cosa: String, n: Int): Double {
        val c = totales[fecha]?.get(cosa)
        return if (cosa in porTotal && c != null && c.unidades > 0) c.total * n / c.unidades else (precios[cosa] ?: 0.0) * n
    }

    /** Lo que se paga en total por estos pedidos (todas las personas juntas). Es lo que cobra el negocio, sin redondear. */
    fun totalDe(fecha: String, pedidos: Map<String, Map<String, Int>>): Double =
        pedidos.values.flatMap { it.keys }.toSet().sumOf { cosa -> costoCosa(fecha, cosa, pedidos.values.sumOf { it[cosa] ?: 0 }) }

    /** Anota lo que te cobraron en total por una cosa que se reparte. El precio por unidad queda como estimado para la próxima vez. */
    fun conTotal(fecha: String, cosa: String, total: Double, unidades: Int): Taller {
        if (total <= 0 || unidades <= 0) return this
        val dia = (totales[fecha] ?: emptyMap()) + (cosa to Cobrado(total, unidades))
        return copy(totales = totales + (fecha to dia), precios = precios + (cosa to total / unidades))
    }

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
        return copy(pedidos = todos, tildes = tildes.filterKeys { it >= limite }, compradas = compradas.filterKeys { it >= limite },
            totales = totales.filterKeys { it >= limite },
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
            "apodo" to JsonPrimitive(it.apodo), "activo" to JsonPrimitive(it.activo), "tel" to JsonPrimitive(it.tel))) }),
        "linkCobro" to JsonPrimitive(linkCobro), "aliasCobro" to JsonPrimitive(aliasCobro),
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
        "compradas" to JsonObject(compradas.mapValues { (_, dia) -> JsonObject(dia.mapValues { (_, p) ->
            JsonObject(p.mapValues { JsonPrimitive(it.value) }) }) }),
        "turnos" to JsonObject(turnoCosa.mapValues { JsonPrimitive(it.value) }),
        "porTotal" to JsonArray(porTotal.sorted().map(::JsonPrimitive)),
        "totales" to JsonObject(totales.mapValues { (_, dia) -> JsonObject(dia.mapValues { (_, c) ->
            JsonObject(mapOf("total" to JsonPrimitive(c.total), "unidades" to JsonPrimitive(c.unidades))) }) }),
    ))

    companion object {
        /* Sin link por defecto: el Link de pago de Mercado Pago cobra comisión y demora la plata; el alias no. */
        const val LINK_COBRO = ""
        private const val LINK_VIEJO = "https://link.mercadopago.com.ar/appsbust"
        const val ALIAS_COBRO = "MartinchoLujanino"

        /** Número para WhatsApp (formato internacional de Argentina, 549 + área + número). null = no sirve.
            Acepta "261 216-8515", "0261 15 216-8515", "+54 9 261 2168515"… */
        fun numeroWhatsApp(tel: String): String? {
            var d = tel.filter { it.isDigit() }
            if (d.startsWith("00")) d = d.drop(2)
            if (d.startsWith("54")) d = d.drop(2).removePrefix("9")
            d = d.removePrefix("0")
            /* el "15" del celular va después del código de área (2, 3 o 4 cifras): se saca si sobra */
            if (d.length == 12) for (a in 2..4) if (d.substring(a, a + 2) == "15") { d = d.removeRange(a, a + 2); break }
            return if (d.length == 10) "549$d" else null
        }

        /** Mensaje de cobro: "Hola Chuña! 👋 Del desayuno: …" */
        fun mensajeCobro(nombre: String, lineas: List<Pair<String, Double>>, link: String, alias: String, pesos: (Double) -> String): String {
            val total = lineas.sumOf { it.second }
            val detalle = if (lineas.size == 1) "Te paso lo del taller: ${lineas[0].first} → *${pesos(total)}*."
                else "Te paso lo del taller:\n" + lineas.joinToString("\n") { "• ${it.first}: ${pesos(it.second)}" } + "\nTotal: *${pesos(total)}*."
            /* Solo el alias (transferencia sin comisión): el link de Mercado Pago nunca va en el mensaje */
            return "Hola $nombre! 👋 $detalle" +
                (if (alias.isNotBlank()) "\n\nMe lo podés transferir al alias: *$alias*" else "") +
                "\n\n¡Gracias! 🙌"
        }

        fun minutos(h: String): Int = h.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }

        private fun par(e: JsonElement?, def: Pair<String, String>): Pair<String, String> {
            val a = (e as? JsonArray)?.mapNotNull { it.texto() } ?: return def
            val ok = a.size == 2 && a.all { Regex("""\d{1,2}:\d{2}""").matches(it) }
            return if (ok) a[0] to a[1] else def
        }

        /** Los sándwiches de la fiambrería: arrancan en el menú de la tarde, sin precio fijo (se reparte el total que cobran). */
        val SANDWICHES = listOf("Sándwich de queso blando", "Sándwich de salame y queso")

        /** Lo que le toca a quien pidió [n] de [unidades] cuando el negocio cobró [total]: se redondea para abajo al peso. */
        fun parte(total: Double, n: Int, unidades: Int): Double =
            if (unidades <= 0 || n <= 0) 0.0 else Math.floor(total * n / unidades + 1e-9)

        fun de(crudo: JsonObject?): Taller {
            val o = crudo?.get("taller") as? JsonObject ?: return Taller()
            var precios = (o["precios"] as? JsonObject)?.mapValues { it.value.num() } ?: emptyMap()
            var turnoCosa = (o["turnos"] as? JsonObject)?.mapNotNull { (k, v) -> v.texto()?.takeIf { it == "M" || it == "T" || it == "A" }?.let { k to it } }?.toMap() ?: emptyMap()
            var porTotal = (o["porTotal"] as? JsonArray)?.mapNotNull { it.texto() }?.toSet() ?: emptySet()
            /* Primera vez con el menú por turno: lo que ya había queda como "Mañana" (sin marca) y se suman los sándwiches de la tarde */
            if (o["turnos"] !is JsonObject) SANDWICHES.forEach { s ->
                if (s !in precios) precios = precios + (s to 0.0)
                if (s !in turnoCosa) turnoCosa = turnoCosa + (s to "T")
                porTotal = porTotal + s
            }
            return Taller(
                muchachos = o.lista("muchachos").map { Muchacho(it["id"].texto() ?: "", it["nombre"].texto() ?: "", it["apodo"].texto() ?: "",
                    (it["activo"] as? JsonPrimitive)?.booleanOrNull != false, it["tel"].texto() ?: "") }.filter { it.nombre.isNotBlank() },
                linkCobro = o["linkCobro"].texto()?.takeIf { it != LINK_VIEJO } ?: LINK_COBRO,
                aliasCobro = o["aliasCobro"].texto() ?: ALIAS_COBRO,
                precios = precios, turnoCosa = turnoCosa, porTotal = porTotal,
                totales = (o["totales"] as? JsonObject)?.mapValues { (_, dia) -> (dia as? JsonObject)?.mapNotNull { (cosa, c) ->
                    val co = c as? JsonObject ?: return@mapNotNull null
                    val t = co["total"].num(); val u = co["unidades"].num().toInt()
                    if (t > 0 && u > 0) cosa to Cobrado(t, u) else null }?.toMap() ?: emptyMap() } ?: emptyMap(),
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
                compradas = (o["compradas"] as? JsonObject)?.mapValues { (_, dia) -> (dia as? JsonObject)?.mapValues { (_, p) ->
                    (p as? JsonObject)?.mapValues { it.value.num().toInt() }?.filterValues { it > 0 } ?: emptyMap() } ?: emptyMap() } ?: emptyMap(),
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
fun Estado.hayCompraTaller(fecha: String): Boolean = spent.any { it.date == fecha && it.esCompraDesayuno }

/** Gasto de la compra del desayuno / merienda (no las cosas sueltas que le compraste a alguien en el día). */
val Movimiento.esCompraDesayuno: Boolean get() = extra["taller"] != null && extra["taller"].num() != 2.0

/** Lo que te deben hoy del taller (todavía sin pagar). */
fun Estado.deudasTallerDe(fecha: String): List<Movimiento> = spent.filter { it.date == fecha && it.dev == 1 && it.amt > 0 && it.extra["taller"] != null }

/** Plata a favor de cada compañero (te la dio antes, o le debés vuelto): id → monto. */
fun Estado.favoresTaller(): Map<String, Double> = spent.filter { it.dev == 1 && it.amt < 0 && it.extra["tp"] != null }
    .groupBy { it.extra["tp"].texto() ?: "" }.mapValues { (_, l) -> -l.sumOf { it.amt } }
