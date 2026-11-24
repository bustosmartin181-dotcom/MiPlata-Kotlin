package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Menú aparte para la tarde y sándwiches que se reparten el total que cobra la fiambrería. */
class TallerTardeTest {
    private val tarde = "2026-11-24"      // martes: turno tarde
    private val manana = "2026-11-25"     // miércoles: turno mañana
    private val queso = "Sándwich de queso blando"
    private val salame = "Sándwich de salame y queso"

    private val base = Taller(
        muchachos = listOf(Muchacho("n", "Nazaret", "Naza"), Muchacho("g", "Gustavo", "Jefe Colo")),
        precios = mapOf("Tortitas" to 400.0, queso to 0.0, salame to 0.0, "Gaseosa" to 1500.0),
        horario = mapOf(tarde to "T", manana to "M"),
        turnoCosa = mapOf(queso to "T", salame to "T", "Gaseosa" to "A"),     // Tortitas sin marca = mañana
        porTotal = setOf(queso, salame),
    )

    @Test fun elMenuDeCadaTurnoMuestraSoloLoSuyo() {
        assertEquals(setOf(queso, salame, "Gaseosa"), base.menuDe("T").keys)
        assertEquals(setOf("Tortitas", "Gaseosa"), base.menuDe("M").keys)
        assertEquals(setOf("Tortitas", queso, salame, "Gaseosa"), base.menuDe(null).keys)    // franco o sin horario: todo
    }

    @Test fun elEjemploDeLaFiambreria_diezMilEntreTres() {
        // Nazaret pidió 2 y Gustavo 1: la fiambrería dice $10.000
        val t = base.conCantidad(tarde, "n", salame, 2).conCantidad(tarde, "g", salame, 1)
            .conTotal(tarde, salame, 10000.0, 3)
        val n = t.costoDe(tarde, t.pedidosDe(tarde)["n"]!!)
        val g = t.costoDe(tarde, t.pedidosDe(tarde)["g"]!!)
        assertEquals(6666.0, n, 1e-9)
        assertEquals(3333.0, g, 1e-9)
        // lo que se paga es lo que cobró el negocio; el peso que sobra queda en la parte de Martín
        assertEquals(10000.0, t.totalDe(tarde, t.pedidosDe(tarde)), 1e-9)
        assertEquals(1.0, 10000.0 - n - g, 1e-9)
    }

    @Test fun siMartinTambienPideEntraEnLaDivision() {
        val t = base.conCantidad(tarde, "n", salame, 2).conCantidad(tarde, "g", salame, 1).conCantidad(tarde, "yo", salame, 1)
            .conTotal(tarde, salame, 10000.0, 4)
        val p = t.pedidosDe(tarde)
        assertEquals(5000.0, t.costoDe(tarde, p["n"]!!), 1e-9)
        assertEquals(2500.0, t.costoDe(tarde, p["g"]!!), 1e-9)
        assertEquals(2500.0, t.costoDe(tarde, p["yo"]!!), 1e-9)
        assertEquals(10000.0, t.totalDe(tarde, p), 1e-9)
    }

    @Test fun cadaTipoDeSandwichTieneSuPropioTotal() {
        var t = base.conCantidad(tarde, "n", queso, 1).conCantidad(tarde, "n", salame, 2).conCantidad(tarde, "g", salame, 1)
        t = t.conTotal(tarde, queso, 3000.0, 1).conTotal(tarde, salame, 9000.0, 3)
        val p = t.pedidosDe(tarde)
        assertEquals(3000.0 + 6000.0, t.costoDe(tarde, p["n"]!!), 1e-9)
        assertEquals(3000.0, t.costoDe(tarde, p["g"]!!), 1e-9)
        assertEquals(12000.0, t.totalDe(tarde, p), 1e-9)
    }

    @Test fun mezclaConPrecioFijo() {
        // las gaseosas tienen precio fijo y se suman al total de lo que se reparte
        val t = base.conCantidad(tarde, "n", salame, 2).conCantidad(tarde, "n", "Gaseosa", 1).conCantidad(tarde, "g", salame, 1)
            .conTotal(tarde, salame, 10000.0, 3)
        val p = t.pedidosDe(tarde)
        assertEquals(6666.0 + 1500.0, t.costoDe(tarde, p["n"]!!), 1e-9)
        assertEquals(10000.0 + 1500.0, t.totalDe(tarde, p), 1e-9)
    }

    @Test fun antesDePonerElTotalSeUsaElPrecioEstimado_yDespuesQuedaDeReferencia() {
        val sin = base.conCantidad(tarde, "n", salame, 2)
        assertEquals(0.0, sin.costoDe(tarde, sin.pedidosDe(tarde)["n"]!!), 1e-9)
        val con = sin.conCantidad(tarde, "g", salame, 1).conTotal(tarde, salame, 9000.0, 3)
        assertEquals(3000.0, con.precios[salame]!!, 1e-9)            // la próxima vez estima $3.000 cada uno
        val otroDia = con.conCantidad("2026-11-26", "n", salame, 2)
        assertEquals(6000.0, otroDia.costo(otroDia.pedidosDe("2026-11-26")["n"]!!), 1e-9)
    }

    @Test fun lasPruebasNoMezclanMananaYTarde() {
        val t = base.conCantidad(manana, "n", "Tortitas", 3).conCantidad(tarde, "n", salame, 1)
        assertEquals(mapOf("Tortitas" to 3), t.listaCompra(manana))
        assertEquals(mapOf(salame to 1), t.listaCompra(tarde))
    }

    @Test fun losPedidosDeSiempreSoloSalenEnSuTurno() {
        // Nazaret pide siempre 2 tortitas (cosa de mañana) y una gaseosa (las dos)
        val t = base.copy(fijos = mapOf("n" to mapOf("Tortitas" to 2, "Gaseosa" to 1)))
        assertEquals(mapOf("Tortitas" to 2, "Gaseosa" to 1), t.pedidosDe(manana)["n"])
        assertEquals(mapOf("Gaseosa" to 1), t.pedidosDe(tarde)["n"])         // las tortitas no aparecen a la tarde
        val soloTortitas = base.copy(fijos = mapOf("g" to mapOf("Tortitas" to 1)))
        assertNull(soloTortitas.pedidosDe(tarde)["g"])
    }

    @Test fun seGuardaYSeLeeIgual() {
        val t = base.conCantidad(tarde, "n", salame, 2).conCantidad(tarde, "g", salame, 1).conTotal(tarde, salame, 10000.0, 3)
        val leido = Taller.de(JsonObject(mapOf("taller" to t.aJson())))
        assertEquals(t.turnoCosa, leido.turnoCosa)
        assertEquals(t.porTotal, leido.porTotal)
        assertEquals(t.totales, leido.totales)
        assertEquals(t.precios, leido.precios)
        assertEquals(6666.0, leido.costoDe(tarde, leido.pedidosDe(tarde)["n"]!!), 1e-9)
    }

    @Test fun unaCopiaViejaSeLeeIgual_yAgregaLosSandwichesDeLaTarde() {
        // copia de antes de este cambio: sin turnos, sin porTotal, sin totales
        val vieja = JsonObject(mapOf("taller" to JsonObject(mapOf(
            "muchachos" to JsonArray(listOf(JsonObject(mapOf("id" to JsonPrimitive("a"), "nombre" to JsonPrimitive("Ariel"))))),
            "precios" to JsonObject(mapOf("Tortitas" to JsonPrimitive(400.0), "Bananas" to JsonPrimitive(600.0))),
            "horario" to JsonObject(mapOf(tarde to JsonPrimitive("T"), manana to JsonPrimitive("M"))),
            "pedidos" to JsonObject(mapOf(manana to JsonObject(mapOf("a" to JsonObject(mapOf("Tortitas" to JsonPrimitive(2)))))))))))
        val t = Taller.de(vieja)
        // lo anterior queda igual y cuenta como "mañana"
        assertEquals(400.0, t.precios["Tortitas"]!!, 1e-9)
        assertEquals(mapOf("Tortitas" to 2), t.pedidosDe(manana)["a"])
        assertEquals(setOf("Tortitas", "Bananas"), t.menuDe("M").keys)
        // y se suman los dos sándwiches, solo para la tarde y sin precio fijo
        assertEquals(setOf("Sándwich de queso blando", "Sándwich de salame y queso"), t.menuDe("T").keys)
        assertTrue(t.porTotal.containsAll(Taller.SANDWICHES))
        // una vez guardada, ya no se vuelven a agregar si alguien los borra
        val borrado = Taller.SANDWICHES[0]
        val sinUno = t.copy(precios = t.precios - borrado, turnoCosa = t.turnoCosa - borrado, porTotal = t.porTotal - borrado)
        val releido = Taller.de(JsonObject(mapOf("taller" to sinUno.aJson())))
        assertFalse(Taller.SANDWICHES[0] in releido.precios)
        assertTrue(Taller.SANDWICHES[1] in releido.precios)
    }

    @Test fun elRepartoSeRedondeaParaAbajoAlPeso() {
        assertEquals(6666.0, Taller.parte(10000.0, 2, 3), 1e-9)
        assertEquals(3333.0, Taller.parte(10000.0, 1, 3), 1e-9)
        assertEquals(3000.0, Taller.parte(9000.0, 1, 3), 1e-9)            // sin errores de coma flotante
        assertEquals(0.0, Taller.parte(10000.0, 0, 3), 1e-9)
        assertEquals(0.0, Taller.parte(10000.0, 1, 0), 1e-9)
    }
}

class TallerCompraSueltaTest {
    private val hoy = "2026-11-24"
    private val salame = "Sándwich de salame y queso"
    private fun base() = Taller(
        muchachos = listOf(Muchacho("n", "Nazaret", "Naza")),
        precios = mapOf(salame to 0.0), horario = mapOf(hoy to "T"),
        turnoCosa = mapOf(salame to "T"), porTotal = setOf(salame),
    ).conCantidad(hoy, "n", salame, 1)

    @Test fun compraSueltaNoMarcaLosPedidosComoPagados() {
        val t = base()
        val hay = t.hayCompraDe(hoy, true)          // hay una compra anotada hoy (bananas) pero no se pagó desde los pedidos
        assertFalse(hay)
        assertEquals(setOf(salame), t.pendientesDe(hoy, hay))
    }

    @Test fun pagoDesdePedidosSiMarcaPagado() {
        val t = base().let { it.conCompradas(hoy, it.pedidosDe(hoy)) }
        val hay = t.hayCompraDe(hoy, true)
        assertTrue(hay)
        assertTrue(t.pendientesDe(hoy, hay).isEmpty())
    }
}
