package com.miplata.kotlin

import com.miplata.kotlin.aviso.EntradasMP
import org.junit.Assert.*
import org.junit.Test

class EntradasMPTest {
    @Test fun transferenciaConNombre() {
        val l = EntradasMP.leer("Recibiste \$ 2.800", "Sergio Adrian Gonzalez te transfirió dinero.")!!
        assertEquals(2800.0, l.monto, 0.001)
        assertEquals("Sergio Adrian Gonzalez", l.nombre)
    }
    @Test fun nombreEnElTitulo() {
        val l = EntradasMP.leer("Mariela Villegas te envió \$ 15.000,50", "Ya está disponible en tu cuenta.")!!
        assertEquals(15000.5, l.monto, 0.001)
        assertEquals("Mariela Villegas", l.nombre)
    }
    @Test fun noLeePagosTuyosNiRendimientos() {
        assertNull(EntradasMP.leer("Pagaste \$ 2.700", "a El Rancho"))
        assertNull(EntradasMP.leer("Rendimientos", "Recibiste \$ 183,72 de rendimientos"))
        assertNull(EntradasMP.leer("¡Aprovechá!", "Hasta 20% de descuento"))
    }

    @Test fun deConMayusculaNoEsMercadoPago() {
        val l = EntradasMP.leer("Recibiste \$ 400", "De Gustavo Martín Lemos desde su cuenta de Mercado Pago.")!!
        assertEquals(400.0, l.monto, 0.001)
        assertEquals("Gustavo Martín Lemos", l.nombre)
    }
    @Test fun nombresParecidos() {
        assertTrue(EntradasMP.coincide("Gustavo Martín Lemos", "Gustavo Lemus", flexible = true))
        assertFalse(EntradasMP.coincide("Marcela Villegas", "Mariela Villegas"))
        assertTrue(EntradasMP.coincide("Mariela Alejandra Villegas", "Mariela Villegas"))
        assertFalse(EntradasMP.coincide("Gustavo Martín Lemos", "Gustavo Bazán", flexible = true))
        assertTrue(EntradasMP.notaEsDe("Tortas Gustavo Lemus", "Gustavo Martín Lemos"))
        assertFalse(EntradasMP.notaEsDe("Tortas para Gustavo", "Gustavo Martín Lemos"))
    }
    @Test fun pagoParcialYCompleto() {
        val deudas = listOf(1L to 1200.0, 2L to 800.0)
        val (enteras, parcial, resto) = EntradasMP.repartirPago(400.0, deudas)
        assertTrue(enteras.isEmpty()); assertEquals(1L to 400.0, parcial); assertEquals(0.0, resto, 0.001)
        val (e2, p2, r2) = EntradasMP.repartirPago(1500.0, deudas)
        assertEquals(listOf(1L), e2); assertEquals(2L to 300.0, p2); assertEquals(0.0, r2, 0.001)
        val (e3, p3, r3) = EntradasMP.repartirPago(2500.0, deudas)
        assertEquals(listOf(1L, 2L), e3); assertNull(p3); assertEquals(500.0, r3, 0.001)
    }
}
