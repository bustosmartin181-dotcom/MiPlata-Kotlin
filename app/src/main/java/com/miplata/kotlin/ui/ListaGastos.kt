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
fun TarjetaGastosYEntradas(S: Estado, alTocar: (Movimiento) -> Unit, taller: Taller = Taller(), alAbrirPersona: (GrupoTaller) -> Unit = {}) {
    val k = LocalColores.current
    val hoy = Fechas.hoy()
    var filtro by remember { mutableIntStateOf(0) }   // 0 todos, 1 gastos, 2 entradas, 3 me deben
    var verTodos by remember { mutableStateOf(false) }
    val todos = S.spent.filter { !it.esMeta }.filter { e ->
        when (filtro) { 1 -> e.amt > 0 && e.dev == 0; 2 -> e.amt < 0; 3 -> e.dev != 0; else -> true }
    }.sortedWith(compareByDescending<Movimiento> { it.date }.thenByDescending { it.id })
    /* Lo del taller se junta: una tarjeta por compañero y por día (aunque hayas pagado en varios negocios) */
    val juntar = filtro == 0 || filtro == 3
    val filas: List<Pair<String, Any>> = todos.groupBy { it.date }.toSortedMap(compareByDescending { it }).flatMap { (fecha, l) ->
        val delTaller = if (juntar) l.filter(::esDelTaller) else emptyList()
        l.filter { it !in delTaller }.map { fecha to (it as Any) } + gruposTaller(delTaller, taller).map { fecha to (it as Any) }
    }
    val mostrar = if (verTodos) filas else filas.take(12)
    val corrido = remember(S, hoy) { saldoCorrido(S, hoy) }
    Tarjeta {
        Titulo("Gastos y entradas")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Todos", "Gastos", "Entradas", "Me deben").forEachIndexed { i, t ->
                FilterChip(filtro == i, { filtro = i }, label = { Text(t, fontSize = 12.sp) })
            }
        }
        if (todos.isEmpty()) Text("Todavía no hay movimientos. Tocá + para anotar.", color = k.suave, fontSize = 14.sp)
        var fechaAnterior = ""
        mostrar.forEach { (fecha, fila) ->
            if (fecha != fechaAnterior) {
                fechaAnterior = fecha
                val n = Fechas.dia(fecha)
                Text(when (fecha) { hoy -> "Hoy"; Fechas.iso(Fechas.dia(hoy) - 1) -> "Ayer"
                    else -> Fechas.nombreDia(n).replaceFirstChar { it.uppercase() } + " " + Fechas.diaYMes(fecha) },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = k.suave, modifier = Modifier.padding(top = 6.dp))
            }
            when (fila) {
                is GrupoTaller -> FilaPersonaTaller(fila) { alAbrirPersona(fila) }
                is Movimiento -> FilaMovimiento(fila, corrido[fila.id]) { alTocar(fila) }
            }
        }
        if (filas.size > 12) TextButton(onClick = { verTodos = !verTodos }) { Text(if (verTodos) "Ver menos" else "Ver todos (${filas.size})") }
    }
}
