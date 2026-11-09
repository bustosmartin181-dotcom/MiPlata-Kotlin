package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.max

/* Período nuevo y cambio de montos: el JSON que deja Kotlin tiene que ser igual al del JavaScript original. */
class PeriodoContraOriginalTest {
    private val errores = mutableListOf<String>()

    private fun iguales(a: JsonElement?, b: JsonElement?, ruta: String) {
        val na = a == null || a is JsonNull; val nb = b == null || b is JsonNull
        if (na || nb) { if (na != nb) errores += "$ruta: $a vs $b"; return }
        when {
            a is JsonObject && b is JsonObject -> {
                (a.keys + b.keys).forEach { k ->
                    // JSON.stringify de JS omite claves undefined; Kotlin puede no tenerlas: se comparan como null
                    iguales(a[k], b[k], "$ruta.$k")
                }
            }
            a is JsonArray && b is JsonArray -> {
                if (a.size != b.size) errores += "$ruta: largo ${a.size} vs ${b.size}"
                else a.indices.forEach { iguales(a[it], b[it], "$ruta[$it]") }
            }
            a is JsonPrimitive && b is JsonPrimitive -> {
                val da = if (a.isString) null else a.doubleOrNull; val db = if (b.isString) null else b.doubleOrNull
                if (da != null && db != null) { if (abs(da - db) > 1e-6 * max(1.0, abs(da))) errores += "$ruta: $da vs $db" }
                else if (a.content != b.content || a.isString != b.isString) errores += "$ruta: $a vs $b"
            }
            else -> errores += "$ruta: tipos distintos"
        }
    }

    @Test fun periodoYMontos() {
        val txt = GZIPInputStream(javaClass.getResourceAsStream("/casos_periodo.json.gz")!!).bufferedReader().readText()
        val casos = Json.parseToJsonElement(txt).jsonArray
        casos.forEachIndexed { n, e ->
            val c = e.jsonObject
            val hoy = c["hoy"]!!.jsonPrimitive.content
            val antes = c["antes"]!!.jsonObject
            iguales(Periodo.migrarEstado(antes), antes, "caso $n migrar")
            var s1 = antes
            c["cambios"]!!.jsonArray.forEach { x -> val a = x.jsonArray
                s1 = Periodo.cambiarMonto(s1, a[0].jsonPrimitive.int, a[1].jsonPrimitive.double, hoy) }
            iguales(s1, c["trasCambios"], "caso $n montos")
            val p = c["periodo"]!!.jsonObject
            val r = Periodo.iniciar(antes, hoy, p["previo"]!!.jsonPrimitive.double, p["inicio"]!!.jsonPrimitive.content, p["fin"]!!.jsonPrimitive.content)
            iguales(r.estado, c["despues"], "caso $n período")
            if (r.terminadas != c["terminadas"]!!.jsonArray.map { it.jsonPrimitive.content }) errores += "caso $n terminadas"
        }
        assertTrue("${errores.size} diferencias:\n" + errores.take(25).joinToString("\n"), errores.isEmpty())
        println("Período: ${casos.size} casos, todos iguales al original")
    }
}
