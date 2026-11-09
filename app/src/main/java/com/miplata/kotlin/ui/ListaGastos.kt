package com.miplata.kotlin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.*

/** Gastos y entradas del período, del más nuevo al más viejo, agrupados por día. */
@Composable
fun TarjetaGastosYEntradas(S: Estado, alTocar: (Movimiento) -> Unit) {
    val k = LocalColores.current
    val hoy = Fechas.hoy()
    var filtro by remember { mutableIntStateOf(0) }   // 0 todos, 1 gastos, 2 entradas, 3 me deben
    var verTodos by remember { mutableStateOf(false) }
    val todos = S.spent.filter { !it.esMeta }.filter { e ->
        when (filtro) { 1 -> e.amt > 0 && e.dev == 0; 2 -> e.amt < 0; 3 -> e.dev != 0; else -> true }
    }.sortedWith(compareByDescending<Movimiento> { it.date }.thenByDescending { it.id })
    val mostrar = if (verTodos) todos else todos.take(12)
    Tarjeta {
        Titulo("Gastos y entradas")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Todos", "Gastos", "Entradas", "Me deben").forEachIndexed { i, t ->
                FilterChip(filtro == i, { filtro = i }, label = { Text(t, fontSize = 12.sp) })
            }
        }
        if (todos.isEmpty()) Text("Todavía no hay movimientos. Tocá + para anotar.", color = k.suave, fontSize = 14.sp)
        var fechaAnterior = ""
        mostrar.forEach { e ->
            if (e.date != fechaAnterior) {
                fechaAnterior = e.date
                val n = Fechas.dia(e.date)
                Text(when (e.date) { hoy -> "Hoy"; Fechas.iso(Fechas.dia(hoy) - 1) -> "Ayer"
                    else -> Fechas.nombreDia(n).replaceFirstChar { it.uppercase() } + " " + Fechas.diaYMes(e.date) },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = k.suave, modifier = Modifier.padding(top = 6.dp))
            }
            FilaMovimiento(e) { alTocar(e) }
        }
        if (todos.size > 12) TextButton(onClick = { verTodos = !verTodos }) { Text(if (verTodos) "Ver menos" else "Ver todos (${todos.size})") }
    }
}
