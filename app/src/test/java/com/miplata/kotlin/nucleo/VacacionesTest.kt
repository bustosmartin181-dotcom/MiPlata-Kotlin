package com.miplata.kotlin.nucleo

import com.miplata.kotlin.datos.Almacen
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class VacacionesTest {
    private fun nuevo(): Almacen {
        val hoy = Fechas.hoy()
        val copia = """{"sueldo":900000,"previo":0,"start":"${Fechas.iso(Fechas.dia(hoy) - 5)}","end":"${Fechas.iso(Fechas.dia(hoy) + 25)}",
          "items":[{"n":"Alquiler","a":60000,"p":false}],"spent":[],"recal":null,"nombre":"Martín"}"""
        val a = Almacen(Files.createTempDirectory("vac").toFile())
        assertNull(a.importar(copia))
        return a
    }
    private val sim = Vacaciones(7, listOf(Rubro("Viaje", 120000.0, false), Rubro("Alojamiento", 45000.0, true),
        Rubro("Comida", 25000.0, true), Rubro("Salidas", 15000.0, true)), 10, true)
    private fun hoyDe(a: Almacen) = Nucleo.calcular(a.estado!!, Fechas.hoy())

    @Test fun cuentaElTotal() {
        assertEquals(715000.0, sim.subtotal, 1e-9)
        assertEquals(71500.0, sim.montoImprevistos, 1e-9)
        assertEquals(786500.0, sim.total, 1e-9)
    }

    @Test fun guardarNoTocaLosNumeros() {
        val a = nuevo(); val antes = hoyDe(a).quedaHoy
        a.guardarVacaciones(sim)
        assertEquals(antes, hoyDe(a).quedaHoy, 1e-6)
        assertEquals(sim, Vacaciones.desde(a.estado!!.crudo))
    }

    @Test fun conPlataAparteElDiaNoCambia() {
        val a = nuevo(); val antes = hoyDe(a)
        a.cargarVacaciones(sim)
        val s = a.estado!!; val d = hoyDe(a)
        val g = s.items.single { it.n == Vacaciones.NOMBRE_GASTO }
        assertEquals(786500.0, g.a, 1e-9); assertEquals(false, g.f)
        assertEquals(antes.quedaHoy, d.quedaHoy, 1e-6)
        assertEquals(antes.saldo, d.saldo, 1e-6)
        assertEquals(antes.total + 786500.0, d.total, 1e-6)
        // cambiar el monto: la plata aparte lo sigue y el día sigue igual
        val k = s.items.indexOf(g)
        a.cambiarMontoItem(k, 900000.0); assertEquals(antes.quedaHoy, hoyDe(a).quedaHoy, 1e-6)
        a.cambiarMontoItem(k, 500000.0); assertEquals(antes.quedaHoy, hoyDe(a).quedaHoy, 1e-6)
        // borrar el gasto: también se va la plata aparte
        a.quitarItem(k)
        assertTrue(a.estado!!.ajustes.isEmpty())
        assertEquals(antes.quedaHoy, hoyDe(a).quedaHoy, 1e-6)
    }

    @Test fun delSueldoBajaElDia() {
        val a = nuevo(); val antes = hoyDe(a).quedaHoy
        a.cargarVacaciones(sim.copy(aparte = false))
        assertTrue(a.estado!!.ajustes.isEmpty())
        assertTrue(hoyDe(a).quedaHoy < antes - 1000)
    }
}
