package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.max

/* Compara el núcleo Kotlin contra el núcleo JavaScript ORIGINAL de Mi plata (v9.9.36).
   Los casos los genera herramientas/generar_casos.js corriendo el index.html real.
   - casos_inventados.json.gz: estados inventados con semilla fija (van en el repo).
   - CASOS_REALES (variable de entorno, opcional): ruta a casos con copias reales (NO van al repo). */
class NucleoContraOriginalTest {

    private val errores = mutableListOf<String>()

    private fun igual(nombre: String, esperado: JsonElement?, real: Double?) {
        if (esperado == null || esperado is JsonNull) {
            if (real != null) errores += "$nombre: esperaba null y dio $real"
            return
        }
        val e = esperado.jsonPrimitive.double
        if (real == null) { errores += "$nombre: esperaba $e y dio null"; return }
        val tol = 1e-6 * max(1.0, abs(e))
        if (abs(e - real) > tol) errores += "$nombre: esperaba $e y dio $real"
    }

    private fun comparar(caso: JsonObject) {
        val nom = caso["nombre"]!!.jsonPrimitive.content + " @ " + caso["hoy"]!!.jsonPrimitive.content
        val hoy = caso["hoy"]!!.jsonPrimitive.content
        val S = Estado.desdeJson(caso["estado"]!!.jsonObject)
        val x = caso["esperado"]!!.jsonObject
        val c = Nucleo.calcular(S, hoy)
        fun n(k: String, v: Double?) = igual("$nom · $k", x[k], v)
        n("total", c.total); n("porPagar", c.porPagar); n("disp", c.disp); n("dias", c.dias.toDouble())
        n("hoyIdx", c.hoyIdx.toDouble()); n("base", c.base); n("rendDisp", c.rendDisp); n("baseHoy", c.baseHoy)
        n("baseManana", c.baseManana); n("manana", c.manana); n("gastoHoy", c.gastoHoy); n("quedaHoy", c.quedaHoy)
        n("faltan", c.faltan.toDouble()); n("saldo", c.saldo); n("gastadoAntesDeHoy", c.gastadoAntesDeHoy)
        n("recalDia", c.recal?.day?.toDouble())
        val filas = x["filas"]
        if (filas is JsonArray) {
            if (c.filas == null || c.filas.size != filas.size) errores += "$nom · filas: cantidad distinta"
            else filas.forEachIndexed { i, f ->
                val a = f.jsonArray; val r = c.filas[i]
                igual("$nom · fila $i presupuesto", a[0], r.presupuesto); igual("$nom · fila $i gasto", a[1], r.gasto)
                igual("$nom · fila $i sobra", a[2], r.sobra); igual("$nom · fila $i aporte", a[3], r.aporte)
            }
        } else if (c.filas != null) errores += "$nom · filas: no esperaba filas"
        val fo = x["fondo"]
        if (fo is JsonObject) {
            val f = c.fondo
            if (!f.activo) errores += "$nom · fondo: debería estar activo"
            igual("$nom · fondo.ahorroPeriodo", fo["ahorroPeriodo"], f.ahorroPeriodo); igual("$nom · fondo.usos", fo["usos"], f.usos)
            igual("$nom · fondo.total", fo["total"], f.total); igual("$nom · fondo.rend", fo["rend"], f.rend)
            igual("$nom · fondo.rendPend", fo["rendPend"], f.rendPend); igual("$nom · fondo.totalConRend", fo["totalConRend"], f.totalConRend)
            igual("$nom · fondo.acumulado", fo["acumulado"], f.acumulado); igual("$nom · fondo.hoySuma", fo["hoySuma"], f.hoySuma)
            igual("$nom · fondo.disponible", fo["disponible"], f.disponible)
        } else if (c.fondo.activo) errores += "$nom · fondo: no debería estar activo"

        val dias = Nucleo.diasDelPeriodo(S, hoy)
        val de = x["dias_"]!!.jsonArray
        if (dias.size != de.size) errores += "$nom · días: cantidad distinta"
        else de.forEachIndexed { i, d ->
            val a = d.jsonArray
            if (a[0].jsonPrimitive.content != dias[i].iso) errores += "$nom · día $i fecha"
            igual("$nom · día $i presupuesto", a[1], dias[i].presupuesto); igual("$nom · día $i gasto", a[2], dias[i].gasto)
            igual("$nom · día $i sobra", a[3], dias[i].sobra)
        }
        val sem = Nucleo.semaforo(c, S).first
        if (sem != x["semaforo"]!!.jsonPrimitive.content) errores += "$nom · semáforo: esperaba ${x["semaforo"]} y dio $sem"

        val co = x["conciliar"]!!.jsonObject; val cr = Nucleo.conciliar(S, c)
        if (co["hayCuentas"]!!.jsonPrimitive.boolean != cr.hayCuentas) errores += "$nom · conciliar.hayCuentas"
        igual("$nom · conciliar.total", co["total"], cr.total); igual("$nom · conciliar.diferencia", co["diferencia"], cr.diferencia)

        val ra = x["racha"]!!.jsonObject; val rr = Nucleo.rachaEnVerde(S, hoy)
        igual("$nom · racha", ra["racha"], rr.racha.toDouble())
        if (ra["ultimos"]!!.jsonArray.map { it.jsonPrimitive.boolean } != rr.ultimos) errores += "$nom · racha.ultimos"

        val ob = x["objetivo"]; val or = Nucleo.objetivoDiario(S, hoy)
        if (ob is JsonObject) {
            if (or == null) errores += "$nom · objetivo: dio null"
            else {
                listOf("obj" to or.obj, "faltan" to or.faltan.toDouble(), "gastoHoy" to or.gastoHoy, "disponibleHoy" to or.disponibleHoy,
                    "promedio" to or.promedio, "proyectado" to or.proyectado, "ahorroHoy" to or.ahorroHoy,
                    "ahorradoAntes" to or.ahorradoAntes, "diasPrevios" to or.diasPrevios.toDouble())
                    .forEach { (k, v) -> igual("$nom · objetivo.$k", ob[k], v) }
                val es = ob["escenarios"]!!.jsonArray
                if (es.size != or.escenarios.size) errores += "$nom · objetivo.escenarios cantidad"
                else es.forEachIndexed { i, e ->
                    igual("$nom · esc $i o", e.jsonObject["o"], or.escenarios[i].o)
                    igual("$nom · esc $i sobra", e.jsonObject["sobra"], or.escenarios[i].sobra)
                }
            }
        } else if (or != null) errores += "$nom · objetivo: esperaba null"
    }

    private fun correr(casos: JsonArray, origen: String) {
        casos.forEach { comparar(it.jsonObject) }
        val muestra = errores.take(25).joinToString("\n")
        assertTrue("$origen: ${errores.size} diferencias en ${casos.size} casos:\n$muestra", errores.isEmpty())
        println("$origen: ${casos.size} casos, todos iguales al original")
    }

    @Test fun casosInventados() {
        val txt = GZIPInputStream(javaClass.getResourceAsStream("/casos_inventados.json.gz")!!).bufferedReader().readText()
        correr(Json.parseToJsonElement(txt).jsonArray, "Inventados")
    }

    @Test fun casosReales() {
        val ruta = System.getenv("CASOS_REALES") ?: return
        correr(Json.parseToJsonElement(File(ruta).readText()).jsonArray, "Reales")
    }
}
