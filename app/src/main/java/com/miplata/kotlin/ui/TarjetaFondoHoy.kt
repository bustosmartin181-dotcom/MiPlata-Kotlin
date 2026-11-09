package com.miplata.kotlin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.*

/** Cómo fue creciendo el fondo día a día en este período (cierre de cada día). */
fun curvaFondo(S: Estado, c: Calculo): List<Double> {
    val filas = c.filas ?: return emptyList()
    val cerrados = (Fechas.dia(Fechas.hoy()) - Fechas.dia(S.start)).toInt().coerceIn(0, filas.size)
    var acum = c.fondo.fondoPrev
    val puntos = mutableListOf(acum)
    for (i in 0 until cerrados) { acum += filas[i].aporte; puntos += acum }
    return puntos
}

@Composable
fun TarjetaFondo(S: Estado, c: Calculo, alUsar: () -> Unit) {
    val k = LocalColores.current
    val f = c.fondo
    Tarjeta {
        Text("Mi fondo de ahorro", color = k.suave, fontSize = 15.sp)
        Text(pesos(f.totalConRend), fontSize = 40.sp, fontFamily = Serif, fontWeight = FontWeight.Bold, color = k.verde)
        if (S.rendOn && S.rendTasa > 0) Text("↑ Rinde ${formatoTasa(S.rendTasa)}%", color = k.verde, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text("Este período: ${pesos(f.ahorroPeriodo)}" + (if (f.fondoPrev != 0.0) " · De antes: ${pesos(f.fondoPrev)}" else ""), color = k.suave, fontSize = 14.sp)
        val puntos = curvaFondo(S, c)
        if (puntos.size >= 2) {
            val color = k.verde
            Canvas(Modifier.fillMaxWidth().height(70.dp).padding(vertical = 6.dp)) {
                val min = puntos.min(); val max = puntos.max(); val rango = (max - min).takeIf { it > 0 } ?: 1.0
                val camino = Path()
                puntos.forEachIndexed { i, v ->
                    val x = size.width * i / (puntos.size - 1)
                    val y = (size.height - (v - min) / rango * size.height).toFloat()
                    if (i == 0) camino.moveTo(x, y) else camino.lineTo(x, y)
                }
                drawPath(camino, color, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                val ult = puntos.last(); drawCircle(color, 5.dp.toPx(), Offset(size.width, (size.height - (ult - min) / rango * size.height).toFloat()))
            }
        }
        OutlinedButton(onClick = alUsar) { Text("Usar del fondo") }
    }
}
