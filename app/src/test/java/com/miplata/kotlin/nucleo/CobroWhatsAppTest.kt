package com.miplata.kotlin.nucleo

import org.junit.Assert.*
import org.junit.Test

/** Cobrar por WhatsApp: el número y el mensaje. */
class CobroWhatsAppTest {
    @Test fun numeros() {
        for (t in listOf("2612168515", "261 216-8515", "0261 15 216-8515", "261 15 2168515", "+54 9 261 216-8515", "54 261 2168515", "0054 9 2612168515"))
            assertEquals(t, "5492612168515", Taller.numeroWhatsApp(t))
        assertEquals("5491112345678", Taller.numeroWhatsApp("011 15 1234-5678"))
        assertNull(Taller.numeroWhatsApp("2168515"))
        assertNull(Taller.numeroWhatsApp(""))
    }

    @Test fun mensaje() {
        val p = { v: Double -> "$" + v.toLong() }
        val uno = Taller.mensajeCobro("Chuña", listOf("16/11: 1 Rockstar" to 2000.0), Taller.LINK_COBRO, Taller.ALIAS_COBRO, p)
        println("MSG1\n$uno")
        assertTrue(uno.contains("Hola Chuña!") && uno.contains("*$2000*") && !uno.contains("http") && uno.contains("al alias: *MartinchoLujanino*"))
        // el link viejo guardado se descarta solo
        val viejo = kotlinx.serialization.json.Json.parseToJsonElement("""{"taller":{"linkCobro":"https://link.mercadopago.com.ar/appsbust"}}""").let { it as kotlinx.serialization.json.JsonObject }
        assertEquals("", Taller.de(viejo).linkCobro)
        val dos = Taller.mensajeCobro("Rodrigo", listOf("16/11: 2 Torta de hoja" to 800.0, "17/11: 1 Bananas" to 600.0), Taller.LINK_COBRO, Taller.ALIAS_COBRO, p)
        println("MSG2\n$dos")
        assertTrue(dos.contains("Total: *$1400*"))
    }
}
