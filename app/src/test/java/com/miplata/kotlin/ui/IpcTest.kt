package com.miplata.kotlin.ui

import com.miplata.kotlin.nucleo.Estado
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test

class IpcTest {
    @Test fun igualQueLaAppOriginal() {
        val d = Ipc.Datos("2027-03", 6, 2, null)
        assertEquals("2026-08,2026-09,2026-10,2026-11,2026-12,2027-01", Ipc.meses(d).joinToString(","))
        assertEquals("2027-09", Ipc.sumarMeses("2027-03", 6)); assertEquals("2026-12", Ipc.sumarMeses("2027-01", -1))
        val v = listOf(1.7, 1.9, 2.3, 2.1, 2.0, 2.2)
        val factor = v.fold(1.0) { f, n -> f * (1 + n / 100) }
        val total = Math.round(138310 * 2 * factor).toDouble(); val cada = Math.round(total / 2).toDouble()
        val o = Json.parseToJsonElement("""{"start":"2026-11-05","end":"2026-12-05","sueldo":1,"items":[{"n":"Alejandro Bustos","a":138310,"p":true,"ipc":{"prox":"2027-03","cada":6,"partes":2}}],"spent":[]}""").jsonObject
        val r = Ipc.aplicar(o, 0, cada, factor)
        val ipc = r["items"]!!.jsonArray[0].jsonObject["ipc"]!!.jsonObject
        assertEquals("2027-09", ipc["prox"]!!.jsonPrimitive.content)
        assertEquals(cada, ipc["pend"]!!.jsonObject["monto"]!!.jsonPrimitive.double, 0.0)        // rige más adelante: queda pendiente
        assertEquals(138310.0, Estado.desdeJson(r).items[0].a, 0.0)
        println("factor=$factor total=$total cada=$cada")
    }
}

class IpcLeerTest {
    @Test fun leeLaSerieDeArgentinaDatos() {
        val m = Ipc.leer("""[{"fecha":"2026-08-31","valor":1.7},{"fecha":"2026-09-30","valor":1.9},{"fecha":"mal"}]""")
        assertEquals(1.7, m["2026-08"]!!, 0.0); assertEquals(1.9, m["2026-09"]!!, 0.0)
    }
}
