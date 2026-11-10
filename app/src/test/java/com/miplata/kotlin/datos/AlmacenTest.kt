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
        assertEquals(a.estado!!.spent.size, a.estado!!.spent.map { it.id }.toSet().size)   // ids únicos aunque se guarden en el mismo milisegundo
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

    @Test fun fondoReservaYCuentas() {
        val a = nuevo(); val hoy = Fechas.hoy()
        a.activarFondo(true)
        assertTrue(a.estado!!.modoFondo); assertEquals(hoy, a.estado!!.fondoDesde)
        val c0 = Nucleo.calcular(a.estado!!, hoy)
        a.usarFondo(3000.0, "kiosco", "hoy")
        val c1 = Nucleo.calcular(a.estado!!, hoy)
        assertEquals(c0.quedaHoy + 3000, c1.quedaHoy, 1e-6)          // "para hoy" suma al límite de hoy
        assertEquals(c0.fondo.total - 3000, c1.fondo.total, 1e-6)
        a.quitarUsoFondo(0)
        assertEquals(c0.quedaHoy, Nucleo.calcular(a.estado!!, hoy).quedaHoy, 1e-6)
        a.sumarPasado(1234.5); a.sumarPasado(100.0)
        assertEquals(1334.5, a.estado!!.resPasado, 1e-9)
        a.guardarCuentas(listOf(Almacen.CuentaEditable("Mercado Pago", 561395.49, true), Almacen.CuentaEditable("Reserva MP", 63450.0, true)))
        a.sumarPasado(19059.0)                                   // "Ya lo pasé": de Mercado Pago a la Reserva
        assertEquals(542336.49, a.estado!!.cuentas[0].m, 1e-6); assertEquals(82509.0, a.estado!!.cuentas[1].m, 1e-6)
        a.usarFondo(2000.0, "", "hoy")                           // sacar del fondo: de la Reserva a Mercado Pago
        assertEquals(544336.49, a.estado!!.cuentas[0].m, 1e-6); assertEquals(80509.0, a.estado!!.cuentas[1].m, 1e-6)
        a.quitarUsoFondo(0)
        a.guardarCuentas(listOf(Almacen.CuentaEditable("Mercado Pago", 100.0, true), Almacen.CuentaEditable("Reserva", 999.0, false)))
        val cc = Nucleo.conciliar(a.estado!!, Nucleo.calcular(a.estado!!, hoy))
        assertEquals(100.0, cc.total, 1e-9)
        a.agregarRecomposicion(cc.diferencia)
        assertEquals(1, a.estado!!.ajustes.size); assertEquals(Fechas.manana(hoy), a.estado!!.ajustes[0].desde)
        a.activarRendDisp(true); assertEquals(19.0, a.estado!!.rendTasa, 1e-9)
    }

    @Test fun pinIgualQueLaAppOriginal() {
        // Valores calculados con el mismo código de la app web (crypto.subtle)
        assertEquals("585b70dd3e3f912b04898edebfed4ccb628c9831412f0f89dd6d0f7020959e0a", Seguridad.hashPin("1234", "a1b2c3d4e5f6a1b2c3d4e5f6"))
        assertEquals("f2a81796a71bc2adaa1f70b1529eac08806d9c35fdee4f274df7170325fec2db", Seguridad.hashPin("1234", ""))
        val a = nuevo(); a.ponerPin("4321")
        assertTrue(a.pinCorrecto("4321")); assertFalse(a.pinCorrecto("1111"))
        a.quitarPin(); assertFalse(a.pinCorrecto("4321"))
    }

    @Test fun objetivoRecalibrarMetasYMeDeben() {
        val a = nuevo(); val hoy = Fechas.hoy()
        a.aplicarObjetivo(9000.0)
        var c = Nucleo.calcular(a.estado!!, hoy)
        assertEquals(9000.0, c.baseHoy, 1e-9); assertEquals(hoy, a.estado!!.objetivoDesde)
        a.aplicarObjetivo(0.0); assertNull(a.estado!!.recal)
        a.anotar(5000.0, "x", null, false, false)
        a.recalibrar(); c = Nucleo.calcular(a.estado!!, hoy)
        assertEquals((c.disp - c.gastadoAntesDeHoy) / c.faltan, c.baseHoy, 1e-6)
        a.quitarRecalibracion(); assertNull(a.estado!!.recal)
        a.guardarMeta(null, "Vacaciones", 300000.0, 10000.0, "")
        val m = a.estado!!.metas.single()
        val antes = Nucleo.calcular(a.estado!!, hoy).saldo
        a.aportar(m.id, m.n, 20000.0)
        assertEquals(30000.0, Nucleo.juntadoEnMeta(a.estado!!, a.estado!!.metas.single()), 1e-9)
        assertEquals(antes - 20000, Nucleo.calcular(a.estado!!, hoy).disp - a.estado!!.spent.filter { it.cuentaEnSaldo }.sumOf { it.amt }, 1e-6)
        a.anotar(4000.0, "desayuno", null, false, true)
        assertEquals(a.estado!!.spent.size, a.estado!!.spent.map { it.id }.toSet().size)   // ids únicos aunque se guarden en el mismo milisegundo
        val id = a.estado!!.spent.first { it.dev == 1 }.id
        a.noPagaron(id, false); assertEquals(0, a.estado!!.spent.first { it.id == id }.dev)
    }

    @Test fun dictado() {
        assertEquals("2800" to "Tortas", interpretarDictado("2800 tortas"))
        assertEquals("2500" to "Nafta", interpretarDictado("2500 pesos en nafta"))
        assertEquals("3000" to "Verdulería", interpretarDictado("3 mil verdulería"))
        assertEquals("12500" to "Súper", interpretarDictado("12.500 súper"))
    }

    @Test fun gastosYReservaMuevenLasCuentas() {
        val a = nuevo(); val hoy = Fechas.hoy()
        a.guardarCuentas(listOf(Almacen.CuentaEditable("Mercado Pago", 500000.0, true), Almacen.CuentaEditable("Reserva MP", 80000.0, true)))
        fun mp() = a.estado!!.cuentas[0].m
        fun res() = a.estado!!.cuentas[1].m
        val dif0 = Nucleo.conciliar(a.estado!!, Nucleo.calcular(a.estado!!, hoy)).diferencia
        a.anotar(13400.0, "colgantes", null, entrada = false, meLoDevuelven = false)
        assertEquals(486600.0, mp(), 1e-6)
        a.anotar(3000.0, "Orlando", null, entrada = true, meLoDevuelven = false)
        assertEquals(489600.0, mp(), 1e-6)
        val id = a.estado!!.spent.first { it.note == "colgantes" }.id
        a.anotar(15000.0, "colgantes", null, false, false, hoy, editarId = id)
        assertEquals(488000.0, mp(), 1e-6)
        a.borrarMovimiento(id)
        assertEquals(503000.0, mp(), 1e-6)
        a.anotar(5000.0, "desayuno", null, entrada = false, meLoDevuelven = true)
        assertEquals(498000.0, mp(), 1e-6)
        a.marcarDevuelto(a.estado!!.spent.first { it.dev == 1 }.id)
        assertEquals(503000.0, mp(), 1e-6)
        // la diferencia con la app no cambia: las cuentas se mueven igual que la app
        assertEquals(dif0, Nucleo.conciliar(a.estado!!, Nucleo.calcular(a.estado!!, hoy)).diferencia, 1e-6)
        // "Ya lo pasé" (botón y Ajustes): sale de Mercado Pago y entra a la Reserva
        a.sumarPasado(1959.0)
        assertEquals(501041.0, mp(), 1e-6); assertEquals(81959.0, res(), 1e-6)
        a.fijarPasado(5000.0)
        assertEquals(498000.0, mp(), 1e-6); assertEquals(85000.0, res(), 1e-6); assertEquals(5000.0, a.estado!!.resPasado, 1e-6)
        // gasto olvidado anotado desde Mis cuentas: no toca la cuenta
        a.anotar(700.0, "Gasto sin anotar", null, false, false, moverCuenta = false)
        assertEquals(498000.0, mp(), 1e-6)
    }
}
