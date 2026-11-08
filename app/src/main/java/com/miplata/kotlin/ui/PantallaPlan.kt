package com.miplata.kotlin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.*

@Composable
fun PantallaPlan(S: Estado, alMarcarPagado: (Int, Boolean) -> Unit) {
    val k = LocalColores.current
    val c = Nucleo.calcular(S, Fechas.hoy())
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 12.dp, 16.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Plan del período", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = k.texto)
        Tarjeta {
            Renglon("Período", "${Fechas.diaYMes(S.start)} al ${Fechas.diaYMes(S.end)}")
            Renglon("Ingresos", pesos(S.sueldo + S.previo))
            Renglon("Gastos importantes", pesos(c.total))
            Renglon("Falta pagar", pesos(c.porPagar), colorDer = if (c.porPagar > 0) k.naranja else k.verde)
            HorizontalDivider(color = k.linea)
            Renglon("Para el día a día", pesos(S.sueldo + S.previo - c.total))
        }
        Tarjeta {
            Titulo("Gastos importantes")
            Subtitulo("Tildá los que ya pagaste")
            S.items.forEachIndexed { i, it ->
                if (it.n.isBlank() && it.a == 0.0) return@forEachIndexed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(it.p, { v -> alMarcarPagado(i, v) })
                    Column(Modifier.weight(1f)) {
                        Text(it.n.ifBlank { "Sin nombre" }, color = k.texto,
                            textDecoration = if (it.p) TextDecoration.LineThrough else null)
                        val extra = listOfNotNull(it.d?.let { d -> "desde el ${Fechas.diaYMes(d)}" },
                            it.q?.let { q -> "cuota ${q.actual} de ${q.total}" }).joinToString(" · ")
                        if (extra.isNotEmpty()) Text(extra, fontSize = 12.sp, color = k.suave)
                    }
                    Text(pesos(it.a), fontWeight = FontWeight.SemiBold, color = if (it.p) k.suave else k.texto)
                }
            }
        }
        Text("Agregar y editar gastos importantes, ingresos y empezar un período nuevo llega en la próxima versión. " +
            "Mientras tanto seguí usando tu Mi plata de siempre para eso.", fontSize = 13.sp, color = k.suave)
    }
}
