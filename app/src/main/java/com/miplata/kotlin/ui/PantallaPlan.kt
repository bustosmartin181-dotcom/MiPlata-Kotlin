package com.miplata.kotlin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.datos.Almacen
import com.miplata.kotlin.nucleo.*

private sealed interface HojaPlan {
    data class Gasto(val k: Int, val nuevo: Boolean) : HojaPlan
    data object Ingresos : HojaPlan
    data object Proximos : HojaPlan
    data object Actual : HojaPlan
    data object Nuevo : HojaPlan
    data class UnaMeta(val m: Meta?) : HojaPlan
}

@Composable
fun PantallaPlan(S: Estado, almacen: Almacen, alCambiar: () -> Unit, aviso: (String) -> Unit, alPagarMP: (Int) -> Unit = {}) {
    val k = LocalColores.current
    val c = Nucleo.calcular(S, Fechas.hoy())
    var hoja by remember { mutableStateOf<HojaPlan?>(null) }
    var filtro by rememberSaveable { mutableIntStateOf(0) }   // 0 todos, 1 pendientes, 2 pagados

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 12.dp, 16.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Plan del período", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = k.texto)

        Tarjeta(Modifier.clickable { hoja = HojaPlan.Actual }) {
            Renglon("Período", "${Fechas.diaYMes(S.start)} al ${Fechas.diaYMes(S.end)}")
            Renglon("Ingresos", pesos(S.sueldo))
            if (S.previo != 0.0) Renglon("Saldo previo", pesos(S.previo))
            Renglon("Gastos importantes", pesos(c.total))
            Renglon("Falta pagar", pesos(c.porPagar), colorDer = if (c.porPagar > 0) k.naranja else k.verde)
            HorizontalDivider(color = k.linea)
            Renglon("Para el día a día", pesos(S.sueldo + S.previo - c.total))
            Text("Tocá para cambiar fechas o saldo previo", fontSize = 12.sp, color = k.suave)
        }

        Tarjeta(Modifier.clickable { hoja = HojaPlan.Ingresos }) {
            Row(verticalAlignment = Alignment.CenterVertically) { Titulo("Ingresos"); Spacer(Modifier.weight(1f)); Text("Editar", color = k.verde, fontWeight = FontWeight.SemiBold) }
            S.ingresos.forEach { Renglon(it.cat, pesos(it.a), negrita = false) }
            if (S.ingresos.size > 1) Renglon("Total", pesos(S.sueldo))
        }

        Tarjeta {
            Titulo("Gastos importantes")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todos", "Pendientes", "Pagados").forEachIndexed { i, t -> FilterChip(filtro == i, { filtro = i }, label = { Text(t) }) }
            }
            S.items.forEachIndexed { i, it ->
                if (filtro == 1 && it.p || filtro == 2 && !it.p) return@forEachIndexed
                Row(Modifier.fillMaxWidth().clickable { hoja = HojaPlan.Gasto(i, false) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(it.p, { v -> almacen.marcarPagado(i, v); alCambiar() })
                    Column(Modifier.weight(1f)) {
                        Text(it.n.ifBlank { "Sin nombre" }, color = k.texto, textDecoration = if (it.p) TextDecoration.LineThrough else null)
                        val marcas = listOfNotNull(
                            if (it.f != false) (if (it.v == true) "se repite · monto variable" else "se repite") else null,
                            it.q?.takeIf { q -> q.total > 1 }?.let { q -> "cuota ${q.actual} de ${q.total}" },
                            it.d?.let { d -> "desde el ${Fechas.diaYMes(d)}" })
                        if (marcas.isNotEmpty()) Text(marcas.joinToString(" · "), fontSize = 12.sp, color = k.suave)
                    }
                    Text(pesos(it.a), fontWeight = FontWeight.SemiBold, color = if (it.p) k.suave else k.texto)
                }
                if (!it.p && it.a > 0) Row(Modifier.padding(start = 48.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BotonMP("Pagar en Mercado Pago ↗") { alPagarMP(i) }
                    val dato = it.extra["mp"].texto() ?: it.extra["cod"].texto()
                    if (dato != null) Text("  copia " + dato.take(18), fontSize = 12.sp, color = k.suave)
                }
            }
            OutlinedButton(onClick = { val n = almacen.agregarItem(); alCambiar(); hoja = HojaPlan.Gasto(n, true) }, Modifier.fillMaxWidth()) {
                Text("+ Agregar gasto importante")
            }
        }

        TarjetaMetas(S) { hoja = HojaPlan.UnaMeta(it) }

        Tarjeta(Modifier.clickable { hoja = HojaPlan.Proximos }) {
            Row(verticalAlignment = Alignment.CenterVertically) { Titulo("Para el próximo período"); Spacer(Modifier.weight(1f)); Text("Editar", color = k.verde, fontWeight = FontWeight.SemiBold) }
            if (S.proximos.isEmpty()) Subtitulo("Anotá gastos que ya sabés que vas a tener cuando cobres.")
            S.proximos.forEach { Renglon(it.n.ifBlank { "Sin nombre" }, pesos(it.a), negrita = false) }
            S.items.filter { it.f != false && it.extra["prox"] != null }.forEach {
                Renglon(it.n + " (próximo)", pesos(it.extra["prox"].num()), negrita = false)
            }
        }

        Tarjeta {
            Titulo("¿Cobraste?")
            Subtitulo("Cuando cobres, empezá el período nuevo. El actual termina el ${Fechas.diaYMes(S.end)}.")
            Button(onClick = { hoja = HojaPlan.Nuevo }, Modifier.fillMaxWidth()) { Text("Empezar período nuevo") }
        }
    }

    when (val h = hoja) {
        is HojaPlan.Gasto -> S.items.getOrNull(h.k)?.let { item ->
            HojaGasto(item, h.nuevo,
                alCerrar = {
                    if (h.nuevo && item.n.isBlank() && item.a == 0.0) { almacen.quitarItem(h.k); alCambiar() }
                    hoja = null
                },
                alGuardar = { _, monto, campos ->
                    almacen.cambiarItem(h.k, campos)
                    if (monto != item.a) almacen.cambiarMontoItem(h.k, monto)
                    alCambiar(); hoja = null; aviso("Guardado")
                },
                alQuitar = { almacen.quitarItem(h.k); alCambiar(); hoja = null; aviso("Gasto quitado") })
        }
        HojaPlan.Ingresos -> HojaIngresos(S.ingresos, { hoja = null }) { almacen.guardarIngresos(it); alCambiar(); hoja = null; aviso("Ingresos guardados") }
        HojaPlan.Proximos -> HojaProximos(S.proximos, { hoja = null }) { almacen.guardarProximos(it); alCambiar(); hoja = null; aviso("Guardado") }
        HojaPlan.Actual -> HojaPeriodoActual(S, { hoja = null }) { ini, fin, previo ->
            almacen.cambiarTodo { o -> kotlinx.serialization.json.JsonObject(o + mapOf("start" to kotlinx.serialization.json.JsonPrimitive(ini),
                "end" to kotlinx.serialization.json.JsonPrimitive(fin), "previo" to num(previo))) }
            alCambiar(); hoja = null; aviso("Período actualizado")
        }
        HojaPlan.Nuevo -> HojaPeriodoNuevo(S, c, { hoja = null }) { previo, ini, fin ->
            val terminadas = almacen.periodoNuevo(previo, ini, fin)
            alCambiar(); hoja = null
            aviso(if (terminadas.isEmpty()) "¡Período nuevo! Revisá tus gastos importantes." else "Período nuevo. Terminaste de pagar: " + terminadas.joinToString(", "))
        }
        is HojaPlan.UnaMeta -> HojaMeta(S, h.m, { hoja = null },
            alGuardar = { n, t, g0, f -> almacen.guardarMeta(h.m?.id, n, t, g0, f); alCambiar(); hoja = null; aviso("Meta guardada") },
            alAportar = { monto -> h.m?.let { almacen.aportar(it.id, it.n, monto) }; alCambiar(); hoja = null; aviso("Apartaste ${pesos(monto)}") },
            alQuitar = { h.m?.let { almacen.quitarMeta(it.id) }; alCambiar(); hoja = null; aviso("Meta borrada") })
        null -> {}
    }
}
