package com.miplata.kotlin.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.*

@Composable
fun PantallaDias(S: Estado, alTocarMovimiento: (Movimiento) -> Unit,
                 alObjetivo: (Double) -> Unit = {}, alRecalibrar: () -> Unit = {}, alQuitarRecal: () -> Unit = {}) {
    val k = LocalColores.current
    val hoy = Fechas.hoy()
    val c = Nucleo.calcular(S, hoy)
    val dias = Nucleo.diasDelPeriodo(S, hoy, c)
    var abierto by remember { mutableStateOf(c.hoyIdx) }
    var ordenando by remember { mutableStateOf(false) }      // ✏️: ordenar los bloques
    val scroll = rememberScrollState()
    var contenido by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var tarjetaHoy by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    var yaFue by remember { mutableStateOf(false) }
    /* Al abrir, la lista arranca mostrando el día de hoy (como antes) */
    LaunchedEffect(contenido, tarjetaHoy) {
        val col = contenido; val h = tarjetaHoy
        if (!yaFue && !Demo.corriendo && col != null && h != null && h.isAttached && col.isAttached) {
            yaFue = true; scroll.scrollTo((col.localPositionOf(h, androidx.compose.ui.geometry.Offset.Zero).y - 40).toInt().coerceAtLeast(0))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).onGloballyPositioned { contenido = it }.padding(16.dp, 12.dp, 16.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Días del período", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = k.texto)
                Text("Del ${Fechas.diaYMes(S.start)} al ${Fechas.diaYMes(S.end)} · lo que sobra pasa al día siguiente", fontSize = 13.sp, color = k.suave)
            }
            IconButton(onClick = { ordenando = !ordenando }) {
                Icon(androidx.compose.material.icons.Icons.Outlined.Edit, "Ordenar bloques", tint = if (ordenando) k.verde else k.suave) }
        }
        BloquesPantalla(Bloques.DIAS, ordenando && !Demo.corriendo, alTerminar = { ordenando = false }) { id ->
            when (id) {
                "objetivo" -> TarjetaObjetivo(S, alObjetivo, alRecalibrar, alQuitarRecal)
                "lista" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { dias.forEach { d ->
            val futuro = d.i > c.hoyIdx
            val esHoy = d.i == c.hoyIdx
            val colorSobra = when { futuro -> k.suave; d.sobra < 0 -> k.rojo; else -> k.verde }
            Column(Modifier.fillMaxWidth()
                .then(if (esHoy) Modifier.onGloballyPositioned { tarjetaHoy = it } else Modifier)
                .background(if (esHoy) k.verdeClaro else k.tarjeta, RoundedCornerShape(16.dp))
                .clickable { abierto = if (abierto == d.i) -1 else d.i }
                .padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((if (esHoy) "Hoy · " else "") + d.dia.replaceFirstChar { it.uppercase() } + " " + Fechas.diaYMes(d.iso),
                            fontWeight = FontWeight.SemiBold, color = if (futuro) k.suave else k.texto)
                        val movsDia = S.spent.filter { it.date == d.iso && it.cuentaEnDia }
                        val gastos = movsDia.filter { it.amt > 0 }.sumOf { it.amt }
                        val entradas = -movsDia.filter { it.amt < 0 }.sumOf { it.amt }
                        Text(if (futuro) "Base ${dinero(d.presupuesto)}"
                            else "Tenías ${dinero(d.presupuesto)} · gastaste ${dinero(gastos)}" + (if (entradas > 0) " · entró ${dinero(entradas)}" else ""),
                            fontSize = 13.sp, color = k.suave)
                    }
                    if (!futuro) Text((if (d.sobra >= 0) "+" else "") + dinero(d.sobra), fontWeight = FontWeight.Bold, color = colorSobra)
                }
                Fechas.feriado(d.iso)?.let { Text("Feriado: ${it.nombre}", fontSize = 12.sp, color = k.suave) }
                if (abierto == d.i) {
                    val movs = S.spent.filter { it.date == d.iso && !it.esMeta }.sortedByDescending { it.id }
                    if (movs.isEmpty()) Text("Sin movimientos", fontSize = 13.sp, color = k.suave, modifier = Modifier.padding(top = 6.dp))
                    movs.forEach { FilaMovimiento(it) { alTocarMovimiento(it) } }
                }
            }
                } }
            }
        }
    }
}
