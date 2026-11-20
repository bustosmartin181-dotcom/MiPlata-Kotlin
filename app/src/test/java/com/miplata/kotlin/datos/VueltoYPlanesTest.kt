package com.miplata.kotlin.datos

import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.Muchacho
import com.miplata.kotlin.nucleo.Nucleo
import com.miplata.kotlin.nucleo.deudasTallerDe
import com.miplata.kotlin.nucleo.favoresTaller
import com.miplata.kotlin.ui.Cobro
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** El caso de Wachito: debía $1.000, te dio $2.000 en efectivo y le diste $1.000 de vuelto por Mercado Pago. */
class VueltoYPlanesTest {
    private val wachito = Muchacho("w", "Santiago Carbajal", "Wachito")

    private fun nuevo(): Almacen {
        val hoy = Fechas.hoy()
        val copia = """{"sueldo":300000,"previo":0,"start":"${Fechas.iso(Fechas.dia(hoy) - 5)}","end":"${Fechas.iso(Fechas.dia(hoy) + 25)}",
          "items":[],"spent":[],"recal":null,"nombre":"Martín"}"""
        val a = Almacen(Files.createTempDirectory("mpv").toFile())
        assertNull(a.importar(copia))
        a.guardarCuentas(listOf(Almacen.CuentaEditable("Mercado Pago", 100000.0, true), Almacen.CuentaEditable("Efectivo", 1200.0, true)))
        a.anotarTaller(hoy, 0.0, "", listOf(wachito to 1000.0))       // le compraste sus tortas por MP
        return a
    }
    private fun Almacen.mp() = estado!!.cuentas.first { it.n == "Mercado Pago" }.m
    private fun Almacen.ef() = estado!!.cuentas.first { it.n == "Efectivo" }.m
    private fun Almacen.dif() = Nucleo.conciliar(estado!!, Nucleo.calcular(estado!!, Fechas.hoy())).diferencia
    private fun Almacen.quedaHoy() = Nucleo.calcular(estado!!, Fechas.hoy()).quedaHoy

    /** "Me lo pagaron" desde Me deben / la ficha: el vuelto se marca con el medio que elegiste. */
    @Test fun vueltoPorMPDeUnPagoEnEfectivo() {
        val a = nuevo()
        assertEquals(99000.0, a.mp(), 1e-6)
        val dif0 = a.dif(); val hoy0 = a.quedaHoy()
        a.cobrarPersona("w", "Wachito", 2000.0, efectivo = true, dejarAFavor = false, medioVuelto = MEDIO_MP)
        assertEquals(3200.0, a.ef(), 1e-6)          // 1.200 + los 2.000 que te dio
        assertEquals(98000.0, a.mp(), 1e-6)         // 99.000 − los 1.000 de vuelto
        assertEquals(dif0, a.dif(), 1e-6)           // las cuentas se movieron igual que la realidad
        assertEquals(hoy0, a.quedaHoy(), 1e-6)      // tu plata del día no cambia
        assertTrue(a.estado!!.deudasTallerDe(Fechas.hoy()).isEmpty())
        assertTrue(a.estado!!.favoresTaller().isEmpty())
    }

    /** Sin elegir medio (como antes): el vuelto sale del mismo lado con el que te pagó. */
    @Test fun vueltoSinMedioSaleDelMismoLado() {
        val a = nuevo()
        a.cobrarPersona("w", "Wachito", 2000.0, efectivo = true, dejarAFavor = false)
        assertEquals(2200.0, a.ef(), 1e-6); assertEquals(99000.0, a.mp(), 1e-6)
    }

    /** Desde la lista de pedidos: la app cobra "hasta" lo que debía y después pasa el vuelto de cuenta. */
    @Test fun vueltoCruzadoDesdePedidos() {
        val a = nuevo()
        val dif0 = a.dif(); val hoy0 = a.quedaHoy()
        val c = Cobro(2000.0, 2000.0, aFavor = false, vueltoEfectivo = false).hasta(1000.0)
        assertEquals(1000.0, c.dado, 1e-9); assertEquals(1000.0, c.enEfectivo, 1e-9); assertEquals(1000.0, c.vueltoCruzado, 1e-9)
        a.cobrarPersona("w", "Wachito", c.enEfectivo, efectivo = true, dejarAFavor = true)
        a.corregirVuelto("w", "Wachito", c.vueltoCruzado, vueltoEnEfectivo = false)
        assertEquals(3200.0, a.ef(), 1e-6); assertEquals(98000.0, a.mp(), 1e-6)
        assertEquals(dif0, a.dif(), 1e-6); assertEquals(hoy0, a.quedaHoy(), 1e-6)
        assertTrue(a.estado!!.favoresTaller().isEmpty())
    }

    /** Al revés: te transfirió $2.000 y le diste $1.000 en efectivo. */
    @Test fun vueltoEnEfectivoDeUnaTransferencia() {
        val a = nuevo()
        val c = Cobro(2000.0, 0.0, aFavor = false, vueltoEfectivo = true).hasta(1000.0)
        assertEquals(1000.0, c.vueltoCruzado, 1e-9)
        a.cobrarPersona("w", "Wachito", c.transferencia, efectivo = false, dejarAFavor = true)
        a.corregirVuelto("w", "Wachito", c.vueltoCruzado, vueltoEnEfectivo = true)
        assertEquals(101000.0, a.mp(), 1e-6)        // 99.000 + 2.000
        assertEquals(200.0, a.ef(), 1e-6)           // 1.200 − 1.000
    }

    /** Si el vuelto sale del mismo lado que la app ya descontó, no hay nada que corregir. */
    @Test fun vueltoDelMismoLadoNoCorrige() {
        assertEquals(0.0, Cobro(2000.0, 2000.0, false, vueltoEfectivo = true).hasta(1000.0).vueltoCruzado, 1e-9)
        assertEquals(0.0, Cobro(2000.0, 0.0, false, vueltoEfectivo = false).hasta(1000.0).vueltoCruzado, 1e-9)
        assertEquals(0.0, Cobro(1000.0, 1000.0, false).hasta(1000.0).vueltoCruzado, 1e-9)
    }

    /** ✅ Pagado en un plan: baja la cuenta elegida; pendiente: vuelve; el día no cambia. */
    @Test fun planPagadoMueveLaCuenta() {
        val a = nuevo()
        a.guardarPlan(null, "cerradura baulera moto", 15322.0, 1)
        val id = a.estado!!.planes.first().id
        a.activarPlan(id, true)
        val hoy0 = a.quedaHoy()
        a.marcarPlanPagado(id, true, MEDIO_MP, a.estado!!.planes.first().cuota)
        assertEquals(99000.0 - 15322.0, a.mp(), 1e-6)
        assertTrue(a.estado!!.planes.first().p); assertEquals(MEDIO_MP, a.estado!!.planes.first().pMed)
        assertEquals(hoy0, a.quedaHoy(), 1e-6)
        a.marcarPlanPagado(id, true, MEDIO_MP, 15322.0)            // tocarlo dos veces no descuenta dos veces
        assertEquals(99000.0 - 15322.0, a.mp(), 1e-6)
        a.marcarPlanPagado(id, false)
        assertEquals(99000.0, a.mp(), 1e-6); assertFalse(a.estado!!.planes.first().p); assertNull(a.estado!!.planes.first().pMed)
        a.marcarPlanPagado(id, true, MEDIO_EFECTIVO, 1000.0)
        assertEquals(200.0, a.ef(), 1e-6)
        a.marcarPlanPagado(id, false)
        assertEquals(1200.0, a.ef(), 1e-6)
        a.marcarPlanPagado(id, true)                               // "solo marcar": no toca las cuentas
        assertEquals(99000.0, a.mp(), 1e-6); assertEquals(1200.0, a.ef(), 1e-6)
        a.marcarPlanPagado(id, false)
        assertEquals(99000.0, a.mp(), 1e-6)
    }
}
