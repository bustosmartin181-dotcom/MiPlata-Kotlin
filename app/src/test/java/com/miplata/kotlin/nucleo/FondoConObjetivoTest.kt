package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** Con fondo de ahorro y objetivo por día: al fondo va la diferencia segura (lo que te toca − el objetivo)
    más lo que no gastes del objetivo. */
class FondoConObjetivoTest {
    @Test fun laDiferenciaVaAlFondo() {
        // 30 días, $630.000 → te tocan $21.000 por día; objetivo $15.000 desde el día 0
        val o = Json.parseToJsonElement("""{"sueldo":630000,"previo":0,"start":"2026-11-01","end":"2026-12-01","items":[],
            "spent":[{"id":1,"date":"2026-11-01","amt":12000,"note":"x"},{"id":2,"date":"2026-11-02","amt":15000,"note":"y"}],
            "recal":{"day":0,"base":15000,"obj":true},"objetivo":15000,"objetivoDesde":"2026-11-01",
            "modoFondo":true,"fondoDesde":"2026-11-01"}""").jsonObject
        val S = Estado.desdeJson(o)
        val c = Nucleo.calcular(S, "2026-11-03")
        assertEquals(21000.0, c.base, 1e-6)
        assertEquals(15000.0, c.baseHoy, 1e-6)                      // el límite del día sigue siendo el objetivo
        val f = c.filas!!
        assertEquals(3000.0 + 6000.0, f[0].aporte, 1e-6)            // sobraron 3.000 + 6.000 seguros
        assertEquals(0.0 + 6000.0, f[1].aporte, 1e-6)               // gastó justo: solo los 6.000 seguros
        assertEquals(15000.0, c.fondo.total, 1e-6)                  // dos días cerrados
        assertEquals(15000.0 + 6000.0, c.fondo.hoySuma, 1e-6)       // hoy: si no gasta nada, 15.000 + 6.000
        // la plata no se pierde: disponible + fondo = saldo
        assertEquals(c.saldo, c.fondo.disponible + c.fondo.total, 1e-6)
        // calculadora: gastando 15.000 por los 28 días que faltan → fondo mínimo = 15.000 + 6.000 × 28
        assertEquals(15000.0 + 6000.0 * 28, Nucleo.probarObjetivo(S, "2026-11-03", 15000.0), 1e-6)
        assertEquals(21000.0, Nucleo.tocaPorDia(S, "2026-11-03"), 1e-6)
    }
}
