package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test

class CuotasTest {
    @Test fun valorSinInteres() {
        assertEquals(20000.0, CuentaCuotas.valor(80000.0, 4), 0.0)
        assertEquals(3333.0, CuentaCuotas.valor(10000.0, 3), 0.0)
        assertEquals(0.0, CuentaCuotas.valor(0.0, 4), 0.0)
        assertEquals(0.0, CuentaCuotas.valor(5000.0, 0), 0.0)
    }

    @Test fun faltaYPagadas() {
        // 4 cuotas de 20.000, la 1 ya pagada: faltan 3
        assertEquals(60000.0, CuentaCuotas.falta(20000.0, 1, 4, true), 0.0)
        assertEquals(80000.0, CuentaCuotas.falta(20000.0, 1, 4, false), 0.0)
        assertEquals(1, CuentaCuotas.pagadas(1, true))
        assertEquals(0, CuentaCuotas.pagadas(1, false))
        assertEquals(3, CuentaCuotas.pagadas(3, true))
    }

    @Test fun elTotalDeLaCompraSeLeeYSeConservaAlPasarDePeriodo() {
        val o = buildJsonObject {
            put("sueldo", 500000); put("previo", 0); put("start", "2026-11-04"); put("end", "2026-12-04")
            putJsonArray("items") { addJsonObject {
                put("n", "Remedios"); put("a", 20000); put("p", true); put("d", "2026-11-26")
                putJsonObject("q") { put("actual", 1); put("total", 4); put("compra", 80000) }
            } }
            putJsonArray("spent") {}
        }
        val q = Estado.desdeJson(o).items.single().q!!
        assertEquals(Cuotas(1, 4, 80000.0), q)
        // Al cobrar: pasa a la cuota 2 de 4, pendiente, con el mismo valor y el total guardado
        val nuevo = Periodo.iniciar(o, "2026-12-04", 0.0, "2026-12-04", "2027-01-05").estado
        val it = Estado.desdeJson(nuevo).items.single()
        assertEquals(Cuotas(2, 4, 80000.0), it.q)
        assertEquals(20000.0, it.a, 0.0)
        assertEquals(false, it.p)
    }
}
