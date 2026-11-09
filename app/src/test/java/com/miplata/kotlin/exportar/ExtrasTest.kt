package com.miplata.kotlin.exportar

import com.miplata.kotlin.nucleo.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class ExtrasTest {
    private val base = Json.parseToJsonElement("""{"sueldo":300000,"previo":0,"start":"2026-11-01","end":"2026-12-01",
        "items":[{"n":"Alquiler","a":60000,"p":true,"q":{"actual":2,"total":6}}],
        "spent":[{"id":1,"date":"2026-11-02","amt":5000,"note":"súper & cía <x>","cat":"Súper"},{"id":2,"date":"2026-11-03","amt":-2000,"note":"Orlando"}],
        "recal":null,"pin":"abc","salt":"s"}""").jsonObject

    @Test fun cierreNocturnoYRestaurar() {
        val r = Periodo.congelarDias(base, "2026-11-05")!!
        assertEquals(4, r["cierres"]!!.jsonObject.size)
        assertEquals("2026-11-04", r["copias"]!!.jsonArray.last().jsonObject["iso"]!!.jsonPrimitive.content)
        assertNull(Periodo.congelarDias(r, "2026-11-05"))                       // ya estaba todo cerrado
        val cambiado = JsonObject(r + ("sueldo" to JsonPrimitive(1)) + ("pin" to JsonPrimitive("nuevo")))
        val rest = Periodo.restaurarCierre(cambiado, 0)!!
        assertEquals(300000.0, rest["sueldo"].num(), 0.0)                       // volvió al cierre
        assertEquals("nuevo", rest["pin"]!!.jsonPrimitive.content)              // el PIN no se toca
        assertNotNull(rest["copias"])
    }

    @Test fun cierreIgualQueElOriginal() {
        val f = File("/home/claude/privado/congelar.json"); val d = File("/home/claude/in2/d.json")
        if (!f.exists() || !d.exists()) return
        val esperado = Json.parseToJsonElement(f.readText()).jsonObject
        val o = JsonObject(Json.parseToJsonElement(d.readText()).jsonObject - "cierres" - "copias")
        val r = Periodo.congelarDias(o, "2026-11-08")!!
        val ce = esperado["cierres"]!!.jsonObject; val cr = r["cierres"]!!.jsonObject
        assertEquals(ce.keys, cr.keys)
        ce.forEach { (k, v) -> listOf("b", "g", "l").forEach { c -> assertEquals(v.jsonObject[c].num(), cr[k]!!.jsonObject[c].num(), 1e-6) } }
    }

    @Test fun excelEsUnZipValido() {
        val S = Estado.desdeJson(base)
        val out = ByteArrayOutputStream(); Exportar.xlsx(Exportar.datos(S, "2026-11-05"), out)
        val nombres = mutableListOf<String>(); var hoja3 = ""
        ZipInputStream(out.toByteArray().inputStream()).use { z -> var e = z.nextEntry
            while (e != null) { nombres += e.name; if (e.name.endsWith("sheet3.xml")) hoja3 = z.readBytes().decodeToString(); e = z.nextEntry } }
        assertTrue(nombres.containsAll(listOf("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet4.xml")))
        assertTrue(hoja3.contains("súper &amp; cía &lt;x&gt;")); assertTrue(hoja3.contains("Entrada"))
        val texto = Exportar.resumenTexto(S, "2026-11-05")
        assertTrue(texto.contains("\n")); assertFalse(texto.contains("\\n"))
        assertTrue(Exportar.lineasPdf(Exportar.datos(S, "2026-11-05")).any { it.t.contains("cuota 2 de 6") })
    }
}
