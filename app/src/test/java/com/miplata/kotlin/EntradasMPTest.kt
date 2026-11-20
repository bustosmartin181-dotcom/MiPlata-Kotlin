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
}
