package com.miplata.kotlin.datos

import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.Nucleo
import com.miplata.kotlin.nucleo.hayCompraTaller
import com.miplata.kotlin.nucleo.deudasTallerDe
import com.miplata.kotlin.nucleo.favoresTaller
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

    /** "Ya lo pasé" desde el aviso usa otra copia del almacén: la app abierta tiene que enterarse y no pisarlo. */
    @Test fun yaLoPaseDesdeElAvisoNoSePierde() {
        val dir = Files.createTempDirectory("mp2").toFile()
        val app = Almacen(dir); assertNull(app.importar(nuevo().exportar()))
        app.guardarCuentas(listOf(Almacen.CuentaEditable("Mercado Pago", 400000.0, true), Almacen.CuentaEditable("Reserva MP", 40000.0, true)))
        Thread.sleep(1100)                                   // que cambie la fecha del archivo
        val aviso = Almacen(dir); aviso.sumarPasado(8506.0)
        assertTrue(app.recargarSiCambio())
        assertEquals(8506.0, app.estado!!.resPasado, 1e-9)
        assertEquals(391494.0, app.estado!!.cuentas[0].m, 1e-6); assertEquals(48506.0, app.estado!!.cuentas[1].m, 1e-6)
        assertFalse(app.recargarSiCambio())
        app.anotar(1000.0, "x", null, false, false)          // lo que hace la app después ya no pisa lo del aviso
        assertEquals(8506.0, Almacen(dir).estado!!.resPasado, 1e-9)
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

    /** Compra del taller de $9.650 con tu parte de $1.050: baja el límite solo tu parte,
        la cuenta baja el total, y cuando te pagan la cuenta queda como si nunca hubieras puesto su parte. */
    @Test fun compraDelTaller() {
        val a = nuevo(); val hoy = Fechas.hoy()
        a.guardarCuentas(listOf(Almacen.CuentaEditable("Mercado Pago", 100000.0, true)))
        val antes = Nucleo.calcular(a.estado!!, hoy)
        val ariel = com.miplata.kotlin.nucleo.Muchacho("a", "Ariel", "Chuña")
        val pedro = com.miplata.kotlin.nucleo.Muchacho("p", "Pedro")
        a.anotarTaller(hoy, 1050.0, "", listOf(ariel to 3100.0, pedro to 700.0))
        val S = a.estado!!
        val c = Nucleo.calcular(S, hoy)
        assertEquals(antes.quedaHoy - 1050, c.quedaHoy, 1e-6)
        assertEquals(100000.0 - 4850, S.cuentas[0].m, 1e-6)
        assertTrue(S.hayCompraTaller(hoy))
        val deben = S.deudasTallerDe(hoy)
        assertEquals(listOf("Taller · Ariel (Chuña)", "Taller · Pedro"), deben.map { it.note })
        a.marcarDevuelto(deben[0].id)
        assertEquals(100000.0 - 4850 + 3100, a.estado!!.cuentas[0].m, 1e-6)
        assertEquals(1, a.estado!!.deudasTallerDe(hoy).size)
    }

    /** Te pagan en efectivo: la plata va a "Efectivo" (que se crea sola) y no a Mercado Pago.
        Si pagan una parte, lo demás sigue en Me deben. El total de las cuentas sigue coincidiendo con la app. */
    @Test fun cobroEnEfectivoYPorPartes() {
        val a = nuevo(); val hoy = Fechas.hoy()
        a.guardarCuentas(listOf(Almacen.CuentaEditable("Mercado Pago", 100000.0, true), Almacen.CuentaEditable("Reserva MP", 5000.0, true)))
        val sergio = com.miplata.kotlin.nucleo.Muchacho("s", "Sergio", "Mordillo")
        val ariel = com.miplata.kotlin.nucleo.Muchacho("a", "Ariel", "Chuña")
        a.anotarTaller(hoy, 1200.0, "", listOf(sergio to 800.0, ariel to 2000.0))
        fun cuenta(n: String) = a.estado!!.cuentas.firstOrNull { it.n == n }?.m
        assertEquals(96000.0, cuenta("Mercado Pago")!!, 1e-6)
        val saldoAntes = Nucleo.calcular(a.estado!!, hoy).saldo
        val deben = a.estado!!.deudasTallerDe(hoy)
        /* Sergio paga justo en efectivo */
        a.cobrado(deben[0].id, false, efectivo = true)
        assertEquals(96000.0, cuenta("Mercado Pago")!!, 1e-6)
        assertEquals(800.0, cuenta("Efectivo")!!, 1e-6)
        assertEquals(5000.0, cuenta("Reserva MP")!!, 1e-6)
        /* Ariel da 1500 de 2000 por transferencia: debe 500 todavía */
        a.cobrado(deben[1].id, false, efectivo = false, monto = 1500.0)
        assertEquals(97500.0, cuenta("Mercado Pago")!!, 1e-6)
        val quedan = a.estado!!.deudasTallerDe(hoy)
        assertEquals(1, quedan.size); assertEquals(500.0, quedan[0].amt, 1e-6); assertEquals("Taller · Ariel (Chuña)", quedan[0].note)
        assertTrue(a.estado!!.hayCompraTaller(hoy))
        /* lo cobrado sube el saldo igual que antes (la app no cambia sus cuentas, solo dónde está la plata) */
        assertEquals(saldoAntes + 800 + 1500, Nucleo.calcular(a.estado!!, hoy).saldo, 1e-6)
        /* gasto en efectivo: baja Efectivo, no Mercado Pago; al borrarlo vuelve */
        a.anotar(300.0, "coca", "Comida", entrada = false, meLoDevuelven = false, efectivo = true)
        assertEquals(500.0, cuenta("Efectivo")!!, 1e-6); assertEquals(97500.0, cuenta("Mercado Pago")!!, 1e-6)
        a.borrarMovimiento(a.estado!!.spent.first { it.note == "coca" }.id)
        assertEquals(800.0, cuenta("Efectivo")!!, 1e-6); assertEquals(97500.0, cuenta("Mercado Pago")!!, 1e-6)
        /* "No me lo pagan" sobre algo cobrado en efectivo no deja marcas viejas */
        a.noPagaron(quedan[0].id, false)
        assertEquals(97500.0, cuenta("Mercado Pago")!!, 1e-6)
    }

    /** Sergio te da $1000 en efectivo antes de comprar; sus 2 tortas salen $800; no tenés vuelto: le quedan $200 a favor,
        que se le descuentan solos en la próxima compra. Las cuentas siempre coinciden con la app. */
    @Test fun plataAFavorDeUnCompanero() {
        val a = nuevo(); val hoy = Fechas.hoy()
        a.guardarCuentas(listOf(Almacen.CuentaEditable("Mercado Pago", 100000.0, true)))
        fun cuenta(n: String) = a.estado!!.cuentas.firstOrNull { it.n == n }?.m ?: 0.0
        val sergio = com.miplata.kotlin.nucleo.Muchacho("s", "Sergio", "Mordillo")
        val dia0 = Nucleo.calcular(a.estado!!, hoy).quedaHoy
        a.cobrarPersona("s", "Sergio (Mordillo)", 1000.0, efectivo = true, dejarAFavor = true)
        assertEquals(1000.0, cuenta("Efectivo"), 1e-6)
        assertEquals(1000.0, a.estado!!.favoresTaller()["s"]!!, 1e-6)
        assertEquals(dia0, Nucleo.calcular(a.estado!!, hoy).quedaHoy, 1e-6)          // no es plata tuya: no sube el día
        a.anotarTaller(hoy, 1200.0, "", listOf(sergio to 800.0))                     // pagás todo por Mercado Pago
        assertEquals(98000.0, cuenta("Mercado Pago"), 1e-6)
        assertEquals(1000.0, cuenta("Efectivo"), 1e-6)
        assertTrue(a.estado!!.deudasTallerDe(hoy).isEmpty())                         // ya pagó con lo que te dio
        assertEquals(200.0, a.estado!!.favoresTaller()["s"]!!, 1e-6)                 // le quedan 200 a favor
        assertTrue(a.estado!!.hayCompraTaller(hoy))
        /* próxima compra: otras 2 tortas, se usan los 200 → debe 600 */
        a.anotarTaller(hoy, 0.0, "", listOf(sergio to 800.0))
        assertEquals(600.0, a.estado!!.deudasTallerDe(hoy).sumOf { it.amt }, 1e-6)
        assertNull(a.estado!!.favoresTaller()["s"])
        /* te da 1000 en efectivo y le das 400 de vuelto */
        a.cobrarPersona("s", "Sergio (Mordillo)", 1000.0, efectivo = true, dejarAFavor = false)
        assertTrue(a.estado!!.deudasTallerDe(hoy).isEmpty())
        assertNull(a.estado!!.favoresTaller()["s"])
        assertEquals(1600.0, cuenta("Efectivo"), 1e-6)                              // 1000 + 600 (los 400 se los devolviste)
        assertEquals(97200.0, cuenta("Mercado Pago"), 1e-6)
        assertEquals(dia0 - 1200, Nucleo.calcular(a.estado!!, hoy).quedaHoy, 1e-6)  // solo tu parte baja el día
    }

    /** Algo cobrado antes de que la app preguntara cómo: se corrige a parte y parte (500 efectivo + 1000 transferencia). */
    @Test fun corregirComoTePagoYParteYParte() {
        val a = nuevo(); val hoy = Fechas.hoy()
        a.guardarCuentas(listOf(Almacen.CuentaEditable("Mercado Pago", 100000.0, true)))
        fun cuenta(n: String) = a.estado!!.cuentas.firstOrNull { it.n == n }?.m ?: 0.0
        a.anotarTaller(hoy, 0.0, "", listOf(com.miplata.kotlin.nucleo.Muchacho("a", "Ariel", "Chuña") to 1500.0))
        val id = a.estado!!.deudasTallerDe(hoy)[0].id
        a.marcarDevuelto(id)                                       // como antes: sin decir cómo
        assertEquals(100000.0, cuenta("Mercado Pago"), 1e-6)
        val saldo = Nucleo.calcular(a.estado!!, hoy).saldo
        a.cambiarCobro(id, 500.0)
        assertEquals(99500.0, cuenta("Mercado Pago"), 1e-6); assertEquals(500.0, cuenta("Efectivo"), 1e-6)
        assertEquals(saldo, Nucleo.calcular(a.estado!!, hoy).saldo, 1e-6)     // la plata es la misma, solo cambia dónde está
        assertEquals(2, a.estado!!.spent.count { it.dev == 2 && it.extra["tp"].let { t -> t != null } })
        /* después: todo fue en efectivo */
        a.estado!!.spent.filter { it.dev == 2 }.forEach { a.cambiarCobro(it.id, it.amt) }
        assertEquals(98500.0, cuenta("Mercado Pago"), 1e-6); assertEquals(1500.0, cuenta("Efectivo"), 1e-6)
        /* "parte y parte" con vuelto: te da 1000 efectivo + 1000 transferencia por algo de 1500 → vuelto de 500 en efectivo */
        val c = com.miplata.kotlin.ui.Cobro(2000.0, 1000.0, aFavor = false).hasta(1500.0)
        assertEquals(1500.0, c.dado, 1e-9); assertEquals(500.0, c.enEfectivo, 1e-9); assertEquals(1000.0, c.transferencia, 1e-9)
    }

    @Test fun repartirLoQueTeDio() {
        val l = listOf(com.miplata.kotlin.ui.Deuda(2, "2026-11-18", 800.0, "", false, "s"),
            com.miplata.kotlin.ui.Deuda(1, "2026-11-17", 1000.0, "", false, "s"))
        val r = com.miplata.kotlin.ui.repartirCobro(l, 1500.0)
        assertEquals(listOf(1L to null, 2L to 500.0), r.map { it.first.id to it.second })
    }
}
