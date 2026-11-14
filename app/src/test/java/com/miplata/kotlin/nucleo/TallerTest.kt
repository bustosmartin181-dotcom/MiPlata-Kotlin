package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TallerTest {
    private val copia = """{"app":"compras-taller","personas":[
        {"id":"a1","nombre":"Yo Mismo","yo":true,"activo":true},
        {"id":"b2","nombre":"Pedro","yo":false,"activo":true},
        {"id":"c3","nombre":"Juan","yo":false,"activo":false}],
        "precios":{"Tortitas":350,"Yogur":3100}}"""

    @Test fun importaSinVosYConPrecios() {
        val t = Taller.importarCompras(copia, Taller())!!
        assertEquals(listOf("Pedro", "Juan"), t.muchachos.map { it.nombre })
        assertEquals(1, t.activos.size)
        assertEquals(350.0, t.precios["Tortitas"]!!, 0.0)
    }

    @Test fun reimportarConservaApodo() {
        val t = Taller(muchachos = listOf(Muchacho("x", "Pedro", "Peter")))
        val n = Taller.importarCompras(copia, t)!!
        assertEquals("Pedro (Peter)", n.muchachos.first { it.nombre == "Pedro" }.mostrar)
    }

    @Test fun turnosYVentanas() {
        assertEquals("M", Taller.turnoPorEntrada("07:00"))
        assertEquals("M", Taller.turnoPorEntrada("08:00"))
        assertEquals("T", Taller.turnoPorEntrada("13:00"))
        assertEquals("F", Taller.turnoPorEntrada(""))
        val t = Taller(horario = mapOf("2026-11-14" to "M", "2026-11-10" to "T", "2026-11-08" to "F"))
        assertTrue(t.enVentana("2026-11-14", "09:57"))
        assertFalse(t.enVentana("2026-11-14", "11:00"))
        assertTrue(t.enVentana("2026-11-10", "17:30"))
        assertFalse(t.enVentana("2026-11-08", "09:30"))
        assertEquals("10:40", t.horaCobro("2026-11-14"))
        assertEquals("19:00", t.horaCobro("2026-11-10"))
        assertNull(t.horaCobro("2026-11-08"))
    }

    @Test fun idaYVueltaJson() {
        val t = Taller(listOf(Muchacho("b2", "Pedro", "Peter")), mapOf("Yogur" to 3100.0), mapOf("2026-11-14" to "M"))
        val o = JsonObject(mapOf("taller" to t.aJson()))
        assertEquals(t, Taller.de(o))
    }

    @Test fun pedidosYListaParaComprar() {
        var t = Taller(precios = mapOf("Tortitas" to 350.0, "Yogur" to 3100.0, "Salame" to 1600.0))
        val f = "2026-11-14"
        t = t.conCantidad(f, "ariel", "Yogur", 1)
        t = t.conCantidad(f, "matias", "Tortitas", 4).conCantidad(f, "matias", "Salame", 1)
        t = t.conCantidad(f, "yo", "Tortitas", 3)
        assertEquals(mapOf("Salame" to 1, "Tortitas" to 7, "Yogur" to 1), t.listaCompra(f))
        assertEquals(3000.0, t.costo(t.pedidosDe(f)["matias"]!!), 0.0)
        assertEquals("4 Tortitas, 1 Salame", t.texto(t.pedidosDe(f)["matias"]!!))
        t = t.conCantidad(f, "ariel", "Yogur", 0)
        assertFalse(t.pedidosDe(f).containsKey("ariel"))
        assertEquals(t, Taller.de(JsonObject(mapOf("taller" to t.aJson()))))
        // los pedidos viejos (más de 30 días) se borran solos
        t = t.conCantidad("2026-12-20", "yo", "Yogur", 1)
        assertFalse(t.pedidos.containsKey(f))
    }
}
