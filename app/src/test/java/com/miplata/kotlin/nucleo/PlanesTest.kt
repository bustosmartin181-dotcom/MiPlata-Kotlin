package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/* "Mis planes": desactivados no tocan nada; activados se descuentan como un gasto prioritario. */
class PlanesTest {
    private fun estado(planes: String) = Json.parseToJsonElement("""
        {"sueldo":600000,"previo":0,"start":"2026-11-01","end":"2026-12-01","items":[],"spent":[],"planes":$planes}
    """).jsonObject

    private fun calc(planes: String, hoy: String = "2026-11-11") = Nucleo.calcular(Estado.desdeJson(estado(planes)), hoy)

    @Test fun desactivadoNoCambiaNada() {
        val sin = calc("[]"); val con = calc("""[{"id":1,"n":"Radiador","total":100000,"meses":1,"activo":false}]""")
        assertEquals(sin.presupuestoDia(10), con.presupuestoDia(10), 0.001)
        assertEquals(20000.0, con.presupuestoDia(10), 0.001)
    }

    @Test fun activadoHoyBajaDesdeHoy() {
        // 30 días, hoy es el día 11 (índice 10): quedan 20 días -> 100.000 / 20 = 5.000 menos por día
        val c = calc("""[{"id":1,"n":"Radiador","total":100000,"meses":1,"activo":true,"desde":"2026-11-11"}]""")
        assertEquals(20000.0, c.presupuestoDia(5), 0.001)     // los días anteriores no cambian
        assertEquals(15000.0, c.presupuestoDia(10), 0.001)
        assertEquals(15000.0, c.presupuestoDia(29), 0.001)
    }

    @Test fun activadoDesdeElCobroSeRepartePorTodoElPeriodo() {
        val c = calc("""[{"id":1,"n":"Regalo","total":90000,"meses":1,"activo":true}]""")
        assertEquals((600000.0 - 90000) / 30, c.presupuestoDia(0), 0.001)
    }

    @Test fun cuotasEnVariosMeses() {
        val p = Plan(1, "Heladera", 100000.0, meses = 3)
        assertEquals(33333.0, p.cuota, 0.001)
        assertEquals(33333.0, p.copy(hechas = 1).cuota, 0.001)
        assertEquals(33334.0, p.copy(hechas = 2).cuota, 0.001)  // la última cierra justo
    }

    @Test fun periodoNuevoAvanzaCuotasYCumple() {
        var o = estado("""[{"id":1,"n":"Heladera","total":90000,"meses":2,"activo":true,"desde":"2026-11-11","p":true},
                           {"id":2,"n":"Viaje","total":50000,"meses":1,"activo":false}]""")
        o = Periodo.iniciar(o, "2026-12-01", 0.0, "2026-12-01", "2027-01-01").estado
        var S = Estado.desdeJson(o)
        val h = S.planes.first { it.id == 1L }
        assertEquals(1, h.hechas); assertTrue(h.activo); assertEquals(null, h.desde); assertEquals(false, h.p)
        assertEquals(0, S.planes.first { it.id == 2L }.hechas)      // el desactivado no avanza
        val r = Periodo.iniciar(o, "2027-01-01", 0.0, "2027-01-01", "2027-02-01")
        S = Estado.desdeJson(r.estado)
        assertTrue(S.planes.none { it.id == 1L })                  // cumplido: se borra solo en el período nuevo
        assertTrue(S.planes.any { it.id == 2L })                   // el que no se activó sigue esperando
        assertTrue(r.terminadas.any { it.contains("Heladera") })
    }
}
