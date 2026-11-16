package com.miplata.kotlin.nucleo

import org.junit.Assert.*
import org.junit.Test

/** "Siempre lo mismo" en los pedidos del taller. */
class TallerFijoTest {
    private val base = Taller(muchachos = listOf(Muchacho("a", "Ariel", "Chuña"), Muchacho("b", "Gustavo")),
        precios = mapOf("Medialunas" to 600.0, "Café" to 1800.0))

    @Test fun seRepiteCadaDiaYSeDestilda() {
        var t = base.conCantidad("2026-11-16", "a", "Medialunas", 2).conCantidad("2026-11-16", "a", "Café", 1)
        t = t.conFijo("2026-11-16", "a", true)
        assertTrue(t.esFijo("a"))
        // al día siguiente aparece solo, sin anotar nada
        assertEquals(mapOf("Medialunas" to 2, "Café" to 1), t.pedidosDe("2026-11-17")["a"])
        assertEquals(mapOf("Café" to 1, "Medialunas" to 2), t.listaCompra("2026-11-17"))
        // un día cambia: solo ese día
        val cambio = t.conCantidad("2026-11-17", "a", "Café", 0)
        assertEquals(mapOf("Medialunas" to 2), cambio.pedidosDe("2026-11-17")["a"])
        assertEquals(mapOf("Medialunas" to 2, "Café" to 1), cambio.pedidosDe("2026-11-18")["a"])
        // un día no pide nada: no vuelve a aparecer ese día
        val nada = t.conCantidad("2026-11-17", "a", "Café", 0).conCantidad("2026-11-17", "a", "Medialunas", 0)
        assertNull(nada.pedidosDe("2026-11-17")["a"])
        assertNotNull(nada.pedidosDe("2026-11-18")["a"])
        // destildar: lo de hoy queda, desde mañana ya no se repite
        val sin = t.conFijo("2026-11-17", "a", false)
        assertFalse(sin.esFijo("a"))
        assertEquals(mapOf("Medialunas" to 2, "Café" to 1), sin.pedidosDe("2026-11-17")["a"])
        assertNull(sin.pedidosDe("2026-11-18")["a"])
        // se guarda y se lee igual
        val leido = Taller.de(kotlinx.serialization.json.JsonObject(mapOf("taller" to nada.aJson())))
        assertEquals(nada.fijos, leido.fijos); assertEquals(nada.sinFijo, leido.sinFijo)
    }
}
