package com.miplata.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.Calculo
import com.miplata.kotlin.nucleo.Estado
import com.miplata.kotlin.nucleo.Plan

/* "Mis planes": gastos que tenés en mente (el radiador, un regalo, una compra grande).
   Desactivados no tocan tu plata. Al activarlos se descuentan como un gasto prioritario,
   repartidos entre los días que quedan. Se pueden pagar en varios meses (una cuota por período). */
@Composable
fun TarjetaPlanes(S: Estado, c: Calculo, alAgregar: () -> Unit, alEditar: (Plan) -> Unit,
                  alActivar: (Plan, Boolean) -> Unit, alPagado: (Plan, Boolean) -> Unit) {
    val k = LocalColores.current
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Titulo("🎯 Mis planes"); Spacer(Modifier.weight(1f))
            TextButton(onClick = alAgregar) { Text("+ Agregar", fontWeight = FontWeight.SemiBold) }
        }
        Subtitulo("Gastos que tenés en mente. Con ❌ no tocan tu plata; con ✅ se descuentan como un gasto prioritario, repartidos entre los días que quedan.")
        if (S.planes.isEmpty()) Text("Ej.: arreglar el radiador del auto · $100.000", fontSize = 13.sp, color = k.suave)
        val activos = S.planes.filter { it.activo && !it.fin }
        if (activos.isNotEmpty()) Text("Activos este período: ${dinero(activos.sumOf { it.cuota })}", fontWeight = FontWeight.SemiBold, color = k.verde)
        S.planes.sortedWith(compareBy({ it.fin }, { !it.activo })).forEach { p -> FilaPlan(p, c, { alEditar(p) }, { alActivar(p, it) }, { alPagado(p, it) }) }
    }
}

@Composable
private fun FilaPlan(p: Plan, c: Calculo, alEditar: () -> Unit, alActivar: (Boolean) -> Unit, alPagado: (Boolean) -> Unit) {
    val k = LocalColores.current
    Column(Modifier.fillMaxWidth().alpha(if (p.fin) 0.6f else 1f).background(k.fondo, RoundedCornerShape(16.dp))
        .clickable(onClick = alEditar).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.n.ifBlank { "Plan" }, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = k.texto, fontSize = 16.sp)
            if (p.total > 0) Text(dinero(p.total), fontWeight = FontWeight.Bold, color = k.texto)
            else Text("Monto a definir", fontWeight = FontWeight.SemiBold, color = k.naranja, fontSize = 14.sp)
        }
        Text(when {
            p.fin -> "✅ Cumplido" + if (p.meses > 1) " · ${p.meses} cuotas" else ""
            p.total <= 0 -> "Cuando sepas cuánto sale, tocalo y cargá el monto para poder activarlo."
            p.meses > 1 -> "En ${p.meses} meses · cuota ${p.cuotaNumero} de ${p.meses}: ${dinero(p.cuota)}"
            else -> "Un solo pago"
        }, fontSize = 13.sp, color = k.suave)
        if (p.activo && !p.fin) {
            val dias = if (p.desde != null) c.faltan else c.dias
            Text("Se descuenta ${dinero(p.cuota)} en este período (≈ ${dinero(p.cuota / dias.coerceAtLeast(1))} por día)", fontSize = 13.sp, color = k.verde, fontWeight = FontWeight.SemiBold)
        }
        if (!p.fin) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p.activo) {
                OutlinedButton(onClick = { alActivar(false) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = k.rojo)) { Text("❌ Desactivar") }
                FilterChip(selected = p.p, onClick = { alPagado(!p.p) }, label = { Text(if (p.p) "✅ Pagado" else "Pendiente") })
            } else if (p.total > 0) Button(onClick = { alActivar(true) }) { Text("✅ Activar") }
            else OutlinedButton(onClick = alEditar) { Text("✏️ Poner monto") }
        }
    }
}

@Composable
fun HojaPlanEditar(plan: Plan?, alCerrar: () -> Unit, alGuardar: (String, Double, Int) -> Unit, alBorrar: () -> Unit) {
    val k = LocalColores.current
    var nombre by remember { mutableStateOf(plan?.n ?: "") }
    var monto by remember { mutableStateOf(plan?.total?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var meses by remember { mutableIntStateOf(plan?.meses ?: 1) }
    Hoja(alCerrar, if (plan == null) "Nuevo plan" else "Editar plan") {
        OutlinedTextField(nombre, { nombre = it }, Modifier.fillMaxWidth(), label = { Text("¿Qué querés hacer o comprar?") }, singleLine = true)
        CampoMonto(monto, { monto = it }, "¿Cuánto sale? (si no sabés, dejalo vacío)")
        Text("¿En cuántos meses lo querés pagar?", color = k.texto, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 2, 3, 4, 6, 12).forEach { m -> FilterChip(selected = meses == m, onClick = { meses = m }, label = { Text(if (m == 1) "1 mes" else "$m") }) }
        }
        val total = aNumero(monto)
        if (total > 0) Text(if (meses == 1) "Se descuenta todo en el período en que lo actives." else "≈ ${dinero(total / meses)} por mes, durante $meses períodos.", fontSize = 13.sp, color = k.suave)
        if (plan?.activo == true) Text("Está activo: si cambiás el monto, se ajusta lo que se descuenta.", fontSize = 13.sp, color = k.naranja)
        if (total <= 0 && nombre.isNotBlank()) Text("Se guarda como \"Monto a definir\". Lo vas a poder activar cuando le cargues cuánto sale.", fontSize = 13.sp, color = k.suave)
        val sePuede = total > 0 || nombre.isNotBlank()
        Button(onClick = { if (sePuede) alGuardar(nombre.trim(), total.coerceAtLeast(0.0), meses) }, Modifier.fillMaxWidth(), enabled = sePuede) { Text("Guardar") }
        if (plan != null) TextButton(onClick = alBorrar, Modifier.fillMaxWidth()) { Text("Borrar plan", color = k.rojo) }
    }
}
