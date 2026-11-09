package com.miplata.kotlin.datos

import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.Nucleo
import com.miplata.kotlin.ui.interpretarDictado
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AlmacenTest {
    private fun nuevo(): Almacen {
        val hoy = Fechas.hoy()
        val copia = """{"sueldo":300000,"previo":0,"start":"${Fechas.iso(Fechas.dia(hoy) - 5)}","end":"${Fechas.iso(Fechas.dia(hoy) + 25)}",
          "items":[{"n":"Alquiler","a":60000,"p":false,"mp":"alias.x","ipc":{"prox":"2027-03"}}],"spent":[],"recal":null,
          "nombre":"Martín","copias":[{"iso":"2026-01-01","json":"{}"}],"cierres":{"2026-01-01":{"b":1}},"algoNuevo":[1,2,3]}"""
        val a = Almacen(Files.createTempDirectory("mp").toFile())
        assertNull(a.importar(copia))
        return a
    }

    @Test fun importarYExportarNoPierdeNada() {
        val a = nuevo()
        val o = Json.parseToJsonElement(a.exportar()).jsonObject
        assertEquals("alias.x", o["items"]!!.jsonArray[0].jsonObject["mp"]!!.jsonPrimitive.content)
        assertNotNull(o["copias"]); assertNotNull(o["cierres"]); assertNotNull(o["algoNuevo"])
    }

    @Test fun anotarGastoEntradaYMeDeben() {
        val a = nuevo(); val hoy = Fechas.hoy()
        val antes = Nucleo.calcular(a.estado!!, hoy)
        a.anotar(1500.0, "tortas", "Comida", entrada = false, meLoDevuelven = false)
        var c = Nucleo.calcular(a.estado!!, hoy)
        assertEquals(antes.quedaHoy - 1500, c.quedaHoy, 1e-6); assertEquals(antes.saldo - 1500, c.saldo, 1e-6)
        a.anotar(3000.0, "Orlando", null, entrada = true, meLoDevuelven = false)
        c = Nucleo.calcular(a.estado!!, hoy)
        assertEquals(antes.quedaHoy + 1500, c.quedaHoy, 1e-6)
        a.anotar(5000.0, "desayuno grupo", "Comida", entrada = false, meLoDevuelven = true)
        val c2 = Nucleo.calcular(a.estado!!, hoy)
        assertEquals(c.quedaHoy, c2.quedaHoy, 1e-6)            // no baja el límite del día
        assertEquals(c.saldo - 5000, c2.saldo, 1e-6)           // pero la plata salió
        val id = a.estado!!.spent.first { it.dev == 1 }.id
        a.marcarDevuelto(id)
        assertEquals(c.saldo, Nucleo.calcular(a.estado!!, hoy).saldo, 1e-6)
        val idT = a.estado!!.spent.first { it.note == "tortas" }.id
        a.anotar(2000.0, "tortas", "Comida", false, false, hoy, editarId = idT)
        assertEquals(1, a.estado!!.spent.count { it.note == "tortas" })
        a.borrarMovimiento(idT)
        assertEquals(0, a.estado!!.spent.count { it.note == "tortas" })
        a.marcarPagado(0, true); assertTrue(a.estado!!.items[0].p)
    }

    @Test fun gastoNuevoSeDescuentaDesdeHoyYPeriodoNuevo() {
        val a = nuevo(); val hoy = Fechas.hoy()
        val antes = Nucleo.calcular(a.estado!!, hoy)
        val k = a.agregarItem()
        a.cambiarItem(k, mapOf("n" to JsonPrimitive("Luz"), "f" to JsonPrimitive(true), "v" to JsonPrimitive(true)))
        a.cambiarMontoItem(k, 25000.0)
        val c = Nucleo.calcular(a.estado!!, hoy)
        assertEquals(hoy, a.estado!!.items[k].d)
        assertEquals(antes.quedaHoy - 25000.0 / antes.faltan, c.quedaHoy, 1e-6)    // repartido entre los días que faltan
        assertEquals(antes.saldo - 25000, c.saldo, 1e-6)
        a.guardarIngresos(listOf("Sueldo" to 300000.0, "Changas o trabajo extra" to 50000.0))
        assertEquals(350000.0, a.estado!!.sueldo, 1e-9)
        val fin = a.estado!!.end
        val terminadas = a.periodoNuevo(1000.0, fin, Fechas.mesSiguiente(fin))
        val e = a.estado!!
        assertEquals(fin, e.start); assertEquals(0, e.spent.size); assertEquals(1000.0, e.previo, 1e-9)
        assertEquals(0.0, e.items.first { it.n == "Luz" }.a, 1e-9)        // monto variable: arranca vacío
        assertTrue(terminadas.isEmpty())
    }

    @Test fun dictado() {
        assertEquals("2800" to "Tortas", interpretarDictado("2800 tortas"))
        assertEquals("2500" to "Nafta", interpretarDictado("2500 pesos en nafta"))
        assertEquals("3000" to "Verdulería", interpretarDictado("3 mil verdulería"))
        assertEquals("12500" to "Súper", interpretarDictado("12.500 súper"))
    }
}
