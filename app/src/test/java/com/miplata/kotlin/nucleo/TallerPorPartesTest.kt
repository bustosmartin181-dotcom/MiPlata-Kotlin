package com.miplata.kotlin.nucleo

import org.junit.Assert.*
import org.junit.Test

/** Pagar el desayuno por partes: las tortas en un negocio, la fruta en otro. */
class TallerPorPartesTest {
    private val f = "2026-11-17"
    private val t = Taller(muchachos = listOf(Muchacho("a", "Ariel"), Muchacho("b", "Tito")),
        precios = mapOf("Raspaditas" to 400.0, "Bananas" to 600.0, "Mandarinas" to 500.0))
        .conCantidad(f, "a", "Raspaditas", 2).conCantidad(f, "a", "Bananas", 1)
        .conCantidad(f, "b", "Mandarinas", 2).conCantidad(f, "yo", "Raspaditas", 3)

    @Test fun primeroLasTortasDespuesLaFruta() {
        assertEquals(setOf("Raspaditas", "Bananas", "Mandarinas"), t.pendientesDe(f, false))
        val tortas = t.pedidosSolo(f, setOf("Raspaditas"), false)
        assertEquals(mapOf("a" to mapOf("Raspaditas" to 2), "yo" to mapOf("Raspaditas" to 3)), tortas)   // Tito no compró tortas
        assertEquals(2000.0, tortas.values.sumOf { t.costo(it) }, 1e-9)
        val t2 = t.conCompradas(f, tortas)
        assertEquals(setOf("Bananas", "Mandarinas"), t2.pendientesDe(f, true))
        val fruta = t2.pedidosSolo(f, t2.pendientesDe(f, true), true)
        assertEquals(mapOf("a" to mapOf("Bananas" to 1), "b" to mapOf("Mandarinas" to 2)), fruta)
        val t3 = t2.conCompradas(f, fruta)
        assertTrue(t3.pendientesDe(f, true).isEmpty())
        // se guarda y se lee igual
        assertEquals(t3.compradas, Taller.de(kotlinx.serialization.json.JsonObject(mapOf("taller" to t3.aJson()))).compradas)
    }

    @Test fun compraDeAntesCuentaComoTodoPagado() {
        assertTrue(t.pendientesDe(f, hayCompra = true).isEmpty())
        // si después se agrega algo, eso queda pendiente
        // si después de pagar las bananas Tito pide una, esa banana queda pendiente (y solo la de él)
        val mas = t.conCompradas(f, t.pedidosDe(f)).conCantidad(f, "b", "Bananas", 1)
        assertEquals(setOf("Bananas"), mas.pendientesDe(f, true))
        assertEquals(mapOf("b" to mapOf("Bananas" to 1)), mas.pedidosSolo(f, setOf("Bananas"), true))
    }
}
