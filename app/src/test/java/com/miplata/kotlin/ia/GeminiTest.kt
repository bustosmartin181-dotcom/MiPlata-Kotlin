package com.miplata.kotlin.ia

import com.miplata.kotlin.nucleo.Estado
import kotlinx.serialization.json.*
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTest {
    @Test fun elContextoTieneLosDatos() {
        val S = Estado.desdeJson(Json.parseToJsonElement("""{"sueldo":300000,"previo":0,"start":"2026-11-01","end":"2026-12-01","nombre":"Martín",
            "ingresos":[{"cat":"Sueldo","a":300000}],"items":[{"n":"Alquiler","a":60000,"p":true}],
            "spent":[{"id":1,"date":"2026-11-02","amt":5000,"note":"tortas","cat":"Comida"},{"id":2,"date":"2026-11-03","amt":-2000,"note":"Orlando"}],"recal":null}""").jsonObject)
        val c = Gemini.contexto(S)
        listOf("Martín", "Alquiler $60.000 (pagado)", "2026-11-02 $5.000 Comida tortas", "-$2.000", "Base por día").forEach {
            assertTrue("falta: $it\n$c", c.contains(it)) }
    }
}
