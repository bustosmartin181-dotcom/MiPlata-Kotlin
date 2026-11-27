package com.miplata.kotlin.nucleo

import com.miplata.kotlin.datos.Almacen
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AguinaldoTest {
    private fun nuevo(): Almacen {
        val hoy = Fechas.hoy()
        val copia = """{"sueldo":900000,"previo":0,"start":"${Fechas.iso(Fechas.dia(hoy) - 5)}","end":"${Fechas.iso(Fechas.dia(hoy) + 25)}",
          "items":[{"n":"Alquiler","a":60000,"p":false}],"spent":[],"recal":null,"nombre":"Martín"}"""
        val a = Almacen(Files.createTempDirectory("agu").toFile())
        assertNull(a.importar(copia))
        a.guardarAguinaldo(862000.0, listOf(ParteAgui("Ahorro", 430000.0), ParteAgui("Vacaciones", 300000.0), ParteAgui("Fiestas", 132000.0)))
        return a
    }
    private fun hoyDe(a: Almacen) = Nucleo.calcular(a.estado!!, Fechas.hoy())
    private fun agui(a: Almacen) = Aguinaldo.desde(a.estado!!.crudo)
    private val viaje = Vacaciones(5, listOf(Rubro("Viaje", 100000.0, false), Rubro("Comida", 20000.0, true)), 0, true)

    @Test fun cargarloNoTocaElDia() {
        val a = nuevo()
        assertEquals(862000.0, agui(a).queda, 1e-9); assertEquals(0.0, agui(a).sinRepartir, 1e-9)
        assertTrue(a.estado!!.ajustes.isEmpty())
    }

    @Test fun lasVacacionesSalenDeSuParte() {
        val a = nuevo(); val antes = hoyDe(a).quedaHoy
        val k = a.cargarVacaciones(viaje)
        assertEquals(200000.0, agui(a).usadoDe("Vacaciones"), 1e-9)
        assertEquals(100000.0, agui(a).quedaDe("Vacaciones"), 1e-9)
        assertEquals(662000.0, agui(a).queda, 1e-9)
        assertEquals(antes, hoyDe(a).quedaHoy, 1e-6)
        a.cambiarMontoItem(k, 250000.0)                       // cambia el gasto: cambia lo usado
        assertEquals(250000.0, agui(a).usadoDe("Vacaciones"), 1e-9)
        assertEquals(antes, hoyDe(a).quedaHoy, 1e-6)
        a.quitarItem(k)                                       // lo borrás: vuelve a la parte
        assertEquals(0.0, agui(a).usadoDe("Vacaciones"), 1e-9)
        assertEquals(antes, hoyDe(a).quedaHoy, 1e-6)
    }

    @Test fun usarComoGastoNoCambiaElDia() {
        val a = nuevo(); val antes = hoyDe(a).quedaHoy
        a.usarAguinaldo("Fiestas", "Regalos", 80000.0, "gasto")
        assertEquals(52000.0, agui(a).quedaDe("Fiestas"), 1e-9)
        assertTrue(a.estado!!.items.any { it.n == "💰 Regalos" && it.a == 80000.0 && it.f == false })
        assertEquals(antes, hoyDe(a).quedaHoy, 1e-6)
        a.deshacerUsoAguinaldo(agui(a).usos.single().id)
        assertFalse(a.estado!!.items.any { it.n == "💰 Regalos" })
        assertEquals(132000.0, agui(a).quedaDe("Fiestas"), 1e-9)
        assertEquals(antes, hoyDe(a).quedaHoy, 1e-6)
    }

    @Test fun sumarAMisDiasSubeElDia() {
        val a = nuevo(); val antes = hoyDe(a).quedaHoy
        a.usarAguinaldo("Fiestas", "Para diciembre", 50000.0, "dias")
        assertTrue(hoyDe(a).quedaHoy > antes + 100)
        a.deshacerUsoAguinaldo(agui(a).usos.single().id)
        assertEquals(antes, hoyDe(a).quedaHoy, 1e-6)
        assertTrue(agui(a).usos.isEmpty())
    }

    @Test fun temporada() {
        assertTrue(Aguinaldo.esTemporada("2026-12-15")); assertTrue(Aguinaldo.esTemporada("2027-01-10"))
        assertTrue(Aguinaldo.esTemporada("2027-06-30")); assertTrue(Aguinaldo.esTemporada("2027-07-05"))
        assertFalse(Aguinaldo.esTemporada("2026-11-27")); assertFalse(Aguinaldo.esTemporada("2027-03-01"))
    }
}
