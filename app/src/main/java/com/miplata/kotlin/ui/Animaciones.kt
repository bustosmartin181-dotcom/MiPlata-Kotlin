package com.miplata.kotlin.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

/* Animaciones chiquitas para que las pantallas se vayan "llenando" como en Mi plata original. */

/** El contenido aparece de a poco (sube y se aclara). [orden] escalona: 0 primero, 1 después, etc. */
@Composable
fun Aparece(orden: Int = 0, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    val baja = with(LocalDensity.current) { 22.dp.toPx() }
    LaunchedEffect(Unit) { delay(120L + 110L * orden); a.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    Box(modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * baja }) { contenido() }
}

/** Un número que corre hasta su valor cuando cambia (o desde 0 si [desdeCero]). Al terminar queda exacto. */
@Composable
fun numeroAnimado(objetivo: Double, ms: Int = 900, desdeCero: Boolean = false): Double {
    val a = remember { Animatable(if (desdeCero) 0f else objetivo.toFloat()) }
    var llego by remember { mutableStateOf(!desdeCero) }
    LaunchedEffect(objetivo) { a.animateTo(objetivo.toFloat(), tween(ms, easing = FastOutSlowInEasing)); llego = true }
    return if ((llego && !a.isRunning) || abs(a.value - objetivo) < 0.01) objetivo else a.value.toDouble()
}
