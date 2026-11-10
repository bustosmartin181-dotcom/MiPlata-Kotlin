package com.miplata.kotlin.ui

import com.miplata.kotlin.nucleo.*
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

/** Los datos de ejemplo de la demo automática: el alquiler tiene IPC y este mes toca actualizar. */
class DemoTest {
    @Test fun alquilerConIpcQueTocaActualizar() {
        val o = estadoDemo(null)
        val S = Estado.desdeJson(o)
        val hoy = Fechas.hoy()
        assertTrue(S.demo)
        val k = S.items.indexOfFirst { it.n == "Alquiler" }
        assertEquals(0, k)                                   // la demo toca "plan.ipc.0"
        val d = Ipc.de(S.items[k])!!
        assertTrue("debe aparecer el aviso en Hoy", Ipc.toca(d, hoy))
        val r = Ipc.estimacion(S.items[k], Ipc.INDEC)!!
        assertEquals(0, r.estimados)                         // los 6 meses publicados: no sale el cartel de "faltan meses"
        assertTrue(r.cadaUno > 180000)
        val nuevo = Estado.desdeJson(Ipc.aplicar(o, k, r.cadaUno, r.factor))
        assertNotNull(Ipc.de(nuevo.items[k]))
        assertFalse("después de calcular ya no avisa", Ipc.toca(Ipc.de(nuevo.items[k])!!, hoy))
    }

    @Test fun elGastoDeMasDejaPocoParaManana() {
        val S = Estado.desdeJson(estadoDemo(null))
        val hoy = Fechas.hoy()
        val c = Nucleo.calcular(S, hoy)
        assertNotNull(c.manana); assertNotNull(c.baseManana)
        val exceso = maxOf(1000.0, Math.round((c.quedaHoy + c.baseManana!! * 0.75) / 1000.0) * 1000.0)
        val o = estadoDemo(null)
        val spent = (o["spent"] as kotlinx.serialization.json.JsonArray) + JsonObject(mapOf(
            "id" to kotlinx.serialization.json.JsonPrimitive(System.currentTimeMillis() + 999), "date" to kotlinx.serialization.json.JsonPrimitive(hoy),
            "amt" to num(exceso), "note" to kotlinx.serialization.json.JsonPrimitive("Cena"), "cat" to kotlinx.serialization.json.JsonPrimitive("Salidas")))
        val c2 = Nucleo.calcular(Estado.desdeJson(JsonObject(o + ("spent" to kotlinx.serialization.json.JsonArray(spent)))), hoy)
        assertTrue("mañana baja", c2.manana!! < c.manana!!)
        assertTrue("mañana queda con poco pero no negativo: ${c2.manana}", c2.manana!! > 0 && c2.manana!! < c.baseManana!! * 0.5)
    }
}
