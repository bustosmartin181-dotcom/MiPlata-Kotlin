package com.miplata.kotlin.nucleo

import kotlin.math.max
import kotlin.math.min

/* =====================================================================
   NÚCLEO — las reglas del dinero. Traducción fiel de calcular() y compañía
   de Mi plata v9.9.36 (index.html). Funciones puras: reciben el estado y
   la fecha de hoy, y devuelven números. Las pruebas comparan contra el
   JavaScript original con miles de casos.
   ===================================================================== */

data class Fila(val i: Int, val presupuesto: Double, val gasto: Double, val sobra: Double, val aporte: Double)

data class Fondo(
    val activo: Boolean,
    val regla: String = "siguiente",
    val fondoPrev: Double = 0.0,
    val ahorroPeriodo: Double = 0.0,
    val usos: Double = 0.0,
    val total: Double = 0.0,
    val rend: Double = 0.0,
    val rendPend: Double = 0.0,
    val totalConRend: Double = 0.0,
    val rendEnMP: Boolean = false,
    val acumulado: Double = 0.0,
    val hoySuma: Double = 0.0,
    val disponible: Double = 0.0,
)

class Calculo(
    val total: Double, val porPagar: Double, val disp: Double, val dias: Int, val hoyIdx: Int, val base: Double,
    val recal: Recal?, val extra: (Int) -> Double, val presupuestoDia: (Int) -> Double, val rendDisp: Double,
    val baseHoy: Double, val baseManana: Double?, val manana: Double?, val gastoHoy: Double, val quedaHoy: Double,
    val filas: List<Fila>?, val fondo: Fondo, val faltan: Int, val saldo: Double, val gastadoAntesDeHoy: Double,
)

private data class Descuento(val amt: Double, val desde: String?)

object Nucleo {

    fun calcular(S: Estado, hoy: String): Calculo {
        val inicio = Fechas.dia(S.start)
        val total = S.items.sumOf { it.a }
        fun montoBase(i: Item) = i.a - i.x.sumOf { it.amt }
        val totalBase = S.items.filter { it.d == null }.sumOf { montoBase(it) }
        val porPagar = S.items.filter { !it.p }.sumOf { it.a }
        val descuentos = mutableListOf<Descuento>()
        S.ajustes.forEach { descuentos += Descuento(it.amt, it.desde) }
        S.items.filter { it.d != null }.forEach { descuentos += Descuento(-montoBase(it), it.d) }
        S.items.forEach { i -> i.x.forEach { descuentos += Descuento(-it.amt, it.desde) } }
        val aportes = S.spent.filter { it.esMeta }.sumOf { it.amt }
        val dispBase = S.sueldo + S.previo - totalBase - aportes
        var disp = dispBase + descuentos.sumOf { it.amt }

        val dias = max(1L, Fechas.dia(S.end) - inicio).toInt()
        val hoyN = Fechas.dia(hoy)
        val hoyIdx = min(max(hoyN - inicio, 0L), (dias - 1).toLong()).toInt()
        val base = dispBase / dias
        val recal = S.recal?.takeIf { it.day != null && it.day <= hoyIdx }
        val desde = recal?.day ?: 0

        val extra: (Int) -> Double = { i ->
            descuentos.sumOf { x ->
                val d0 = if (x.desde != null) max(0L, Fechas.dia(x.desde) - inicio).toInt() else 0
                if (i >= d0) x.amt / max(1, dias - d0) else 0.0
            }
        }
        val presupuestoDia: (Int) -> Double = { i -> if (recal != null && i >= recal.day!!) recal.base else base + extra(i) }

        fun idxDe(e: Movimiento) = Fechas.dia(e.date) - inicio
        fun gastado(a: Long, b: Long) = S.spent.filter { it.cuentaEnDia && idxDe(it) in a..b }.sumOf { it.amt }
        val gastoHoy = gastado(hoyIdx.toLong(), hoyIdx.toLong())

        /* Rendimiento estimado del dinero disponible (Mercado Pago) */
        val tasaD = min(300.0, max(0.0, S.rendTasa))
        val rendDispActivo = S.rendDispOn && tasaD > 0 && S.rendDispDesde.isNotEmpty()
        val rD = tasaD / 100 / 365
        val kD0 = if (rendDispActivo) max(0L, Fechas.dia(S.rendDispDesde) - inicio).toInt() else 0
        var rendDisp = 0.0
        var pendD = 0.0
        fun sumarRendDisp(k: Int, aportesPrevios: Double, usosHasta: Double) {
            if (!rendDispActivo || k < kD0) return
            val y = max(0.0, disp - gastado(-1_000_000_000L, (k - 1).toLong()) - aportesPrevios + usosHasta) * rD
            pendD += y
            if (Fechas.esHabil(inicio + k + 1)) {
                descuentos += Descuento(pendD, Fechas.iso(inicio + k + 1))
                disp += pendD; rendDisp += pendD; pendD = 0.0
            }
        }
        if (!S.modoFondo) for (k in 0 until hoyIdx) sumarRendDisp(k, 0.0, 0.0)

        var acumulado = 0.0
        for (i in desde..hoyIdx) acumulado += presupuestoDia(i)
        val quedaHoy = acumulado - gastado(desde.toLong(), hoyIdx.toLong())
        val hayManana = hoyIdx < dias - 1

        /* MODO FONDO: lo que sobra de cada día va a un fondo aparte en vez de pasar al día siguiente */
        val modoFondo = S.modoFondo
        val regla = if (S.fondoExceso == "aparte") "aparte" else "siguiente"
        var filas: MutableList<Fila>? = null
        var fondo = Fondo(activo = false)
        var quedaHoyF = quedaHoy
        var mananaF: Double? = null
        if (modoFondo) {
            fun idxUso(x: UsoFondo) = min(max(Fechas.dia(x.date) - inicio, 0L), (dias - 1).toLong()).toInt()
            val iniFondo = if (S.fondoDesde.isNotEmpty()) min(max(Fechas.dia(S.fondoDesde) - inicio, 0L), (dias - 1).toLong()).toInt() else 0
            val extraRetiro = HashMap<Int, Double>()
            S.fondoUsos.forEach { x -> if (x.para != "cubre") { val k = idxUso(x); extraRetiro[k] = (extraRetiro[k] ?: 0.0) + x.amt } }
            fun usosHastaDia(i: Int) = S.fondoUsos.filter { it.para != "cubre" && idxUso(it) <= i }.sumOf { it.amt }
            val fs = mutableListOf<Fila>()
            var arrastre = 0.0
            var aportesAcum = 0.0
            for (i in 0 until dias) {
                if (recal != null && i == recal.day) arrastre = 0.0
                val sin = presupuestoDia(i) + (extraRetiro[i] ?: 0.0)
                val presupuesto = if (i <= hoyIdx + 1) sin + arrastre else sin
                val gasto = gastado(i.toLong(), i.toLong())
                val sobra = presupuesto - gasto
                val previo = i < iniFondo
                val aporte = if (previo) 0.0 else if (regla == "fondo") sobra else max(0.0, sobra)
                fs += Fila(i, presupuesto, gasto, sobra, aporte)
                arrastre = if (previo) sobra else if (regla == "siguiente") min(0.0, sobra) else 0.0
                if (i < hoyIdx) sumarRendDisp(i, aportesAcum, usosHastaDia(i))
                aportesAcum += aporte
            }
            filas = fs
            val saldoPeriodo = disp - S.spent.filter { it.cuentaEnSaldo }.sumOf { it.amt }
            val cerrados = min(dias.toLong(), max(0L, hoyN - inicio)).toInt()
            val ahorroPeriodo = fs.take(cerrados).sumOf { it.aporte }
            val usos = S.fondoUsos.sumOf { it.amt }
            val previo = S.fondoPrev
            val usosParaGastar = S.fondoUsos.filter { it.para != "cubre" }.sumOf { it.amt }
            val tasaRend = min(300.0, max(0.0, S.rendTasa))
            val rendActivo = S.rendOn && tasaRend > 0 && S.rendDesde.isNotEmpty()
            val rendPrev = if (rendActivo) S.rendPrev else 0.0
            var rendPer = 0.0
            var rendPend = 0.0
            if (rendActivo) {
                val r = tasaRend / 100 / 365
                val k0 = max(0L, Fechas.dia(S.rendDesde) - inicio)
                var acum = 0.0
                var pend = 0.0
                for (k in 0 until cerrados) {
                    if (k > 0) acum += fs[k - 1].aporte
                    if (k < k0) continue
                    val usado = S.fondoUsos.filter { idxUso(it) <= k }.sumOf { it.amt }
                    pend += max(0.0, previo + acum - usado + rendPrev + rendPer) * r
                    if (Fechas.esHabil(inicio + k + 1)) { rendPer += pend; pend = 0.0 }
                }
                rendPend = pend
            }
            quedaHoyF = fs[hoyIdx].presupuesto - fs[hoyIdx].gasto
            mananaF = if (hayManana) presupuestoDia(hoyIdx + 1) + (if (regla == "siguiente") min(0.0, quedaHoyF) else 0.0) else null
            fondo = Fondo(
                activo = true, regla = regla, fondoPrev = previo, ahorroPeriodo = ahorroPeriodo, usos = usos,
                total = previo + ahorroPeriodo - usos,
                rend = rendPrev + rendPer, rendPend = rendPend, totalConRend = previo + ahorroPeriodo - usos + rendPrev + rendPer,
                rendEnMP = S.rendEnMP,
                acumulado = previo + S.fondoRetirado + ahorroPeriodo,
                hoySuma = if (regla == "fondo") quedaHoyF else max(0.0, quedaHoyF),
                disponible = saldoPeriodo - ahorroPeriodo + usosParaGastar,
            )
        }

        val saldo = disp - S.spent.filter { it.cuentaEnSaldo }.sumOf { it.amt }
        return Calculo(
            total = total, porPagar = porPagar, disp = disp, dias = dias, hoyIdx = hoyIdx, base = base, recal = recal,
            extra = extra, presupuestoDia = presupuestoDia, rendDisp = rendDisp,
            baseHoy = presupuestoDia(hoyIdx),
            baseManana = if (hayManana) presupuestoDia(hoyIdx + 1) else null,
            manana = if (modoFondo) mananaF else (if (hayManana) presupuestoDia(hoyIdx + 1) + quedaHoy else null),
            gastoHoy = gastoHoy, quedaHoy = if (modoFondo) quedaHoyF else quedaHoy, filas = filas, fondo = fondo,
            faltan = dias - hoyIdx, saldo = saldo,
            gastadoAntesDeHoy = gastado(-1_000_000_000L, (hoyIdx - 1).toLong()),
        )
    }

    data class Dia(val i: Int, val iso: String, val dia: String, val presupuesto: Double, val gasto: Double, val sobra: Double)

    /** Tabla día por día: presupuesto (con lo que arrastra), gasto y sobrante. */
    fun diasDelPeriodo(S: Estado, hoy: String, c: Calculo = calcular(S, hoy)): List<Dia> {
        val inicio = Fechas.dia(S.start)
        c.filas?.let { filas ->
            return filas.map { f -> val n = inicio + f.i; Dia(f.i, Fechas.iso(n), Fechas.nombreDia(n), f.presupuesto, f.gasto, f.sobra) }
        }
        val salida = mutableListOf<Dia>()
        var arrastre = 0.0
        for (i in 0 until c.dias) {
            if (c.recal != null && i == c.recal.day) arrastre = 0.0
            val sinArrastre = if (c.recal != null && i >= c.recal.day!!) c.recal.base else c.base + c.extra(i)
            val presupuesto = if (i <= c.hoyIdx + 1) sinArrastre + arrastre else sinArrastre
            val n = inicio + i
            val iso = Fechas.iso(n)
            val gasto = S.spent.filter { it.date == iso && it.cuentaEnDia }.sumOf { it.amt }
            val sobra = presupuesto - gasto
            arrastre = sobra
            salida += Dia(i, iso, Fechas.nombreDia(n), presupuesto, gasto, sobra)
        }
        return salida
    }

    /** Semáforo del día: "neg" (te pasaste), "warn" (cerca del límite) o "" (bien). */
    fun semaforo(c: Calculo, S: Estado): Pair<String, String> {
        val presupuesto = c.quedaHoy + c.gastoHoy
        val umbral = (S.aviso ?: 20.0) / 100
        if (c.quedaHoy < 0) return "neg" to "Te pasaste del límite de hoy."
        if (presupuesto > 0 && c.quedaHoy <= presupuesto * umbral) return "warn" to "Ya vas llegando al límite de hoy."
        return "" to ""
    }

    data class Conciliacion(val hayCuentas: Boolean, val total: Double, val diferencia: Double)

    /** Compara lo que hay en las cuentas con lo que dice la app. */
    fun conciliar(S: Estado, c: Calculo): Conciliacion {
        val cuentas = S.cuentas.filter { !it.excluida }
        val total = cuentas.sumOf { it.m }
        val apartadoEnMetas = S.spent.filter { it.esMeta }.sumOf { it.amt }
        val f = c.fondo
        return Conciliacion(
            hayCuentas = cuentas.any { it.m > 0 }, total = total,
            diferencia = total - c.saldo - apartadoEnMetas - (if (f.activo) f.fondoPrev - f.usos + (if (f.rendEnMP) f.rend else 0.0) else 0.0),
        )
    }

    data class Racha(val racha: Int, val ultimos: List<Boolean>)

    /** Días seguidos (hasta ayer) sin pasarse; y los últimos 7 días cerrados. */
    fun rachaEnVerde(S: Estado, hoy: String): Racha {
        val c = calcular(S, hoy)
        val dias = diasDelPeriodo(S, hoy, c)
        var racha = 0
        var i = c.hoyIdx - 1
        while (i >= 0 && dias[i].sobra >= 0) { racha++; i-- }
        val ultimos = (max(0, c.hoyIdx - 7) until c.hoyIdx).map { dias[it].sobra >= 0 }
        return Racha(racha, ultimos)
    }

    data class Escenario(val o: Double, val sobra: Double, val actual: Boolean)
    data class Objetivo(
        val obj: Double, val faltan: Int, val gastoHoy: Double, val disponibleHoy: Double, val promedio: Double,
        val proyectado: Double, val ahorroHoy: Double, val ahorradoAntes: Double, val diasPrevios: Int, val escenarios: List<Escenario>,
    )

    /** Objetivo diario: cuánto querés gastar por día y cuánto te sobra si lo cumplís. */
    fun objetivoDiario(S: Estado, hoy: String): Objetivo? {
        val obj = S.objetivo
        if (!(obj > 0)) return null
        val c = calcular(S, hoy)
        val dias = diasDelPeriodo(S, hoy, c)
        val disponibleHoy = c.saldo + c.gastoHoy
        fun proyectadoCon(o: Double) = disponibleHoy - o * c.faltan
        val desdeIdx = if (S.objetivoDesde.isNotEmpty())
            min(c.hoyIdx.toLong(), max(0L, Fechas.dia(S.objetivoDesde) - Fechas.dia(S.start))).toInt() else c.hoyIdx
        var ahorradoAntes = 0.0
        for (i in desdeIdx until c.hoyIdx) ahorradoAntes += obj - dias[i].gasto
        val paso = max(500.0, Math.round(obj * 0.1 / 500).toDouble() * 500)
        val escenarios = listOf(-2, -1, 0, 1).map { obj + it * paso }.filter { it > 0 }
            .map { Escenario(it, proyectadoCon(it), it == obj) }
        return Objetivo(obj, c.faltan, c.gastoHoy, disponibleHoy, disponibleHoy / c.faltan, proyectadoCon(obj),
            obj - c.gastoHoy, ahorradoAntes, c.hoyIdx - desdeIdx, escenarios)
    }

    /* Metas */
    fun juntadoEnMeta(S: Estado, m: Meta) = m.g0 + S.spent.filter { it.meta == m.id }.sumOf { it.amt }
    fun aporteMensualSugerido(S: Estado, m: Meta, hoy: String): Double {
        val falta = max(0.0, m.t - juntadoEnMeta(S, m))
        if (!(m.t > 0 && falta > 0 && m.f.isNotEmpty())) return 0.0
        return Math.round(falta / max(1.0, (Fechas.dia(m.f) - Fechas.dia(hoy)) / 30.4)).toDouble()
    }
}
