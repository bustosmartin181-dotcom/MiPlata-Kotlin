package com.miplata.kotlin.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/** La tarjeta de una persona de "Me deben" levantada al frente (como el cartel de "Ayudá al creador"):
    la app queda borrosa y oscura detrás y solo se opera con esa persona. */
object FocoDeuda {
    /** De quién es la tarjeta levantada (null = ninguna). */
    var clave by mutableStateOf<String?>(null)
    /** true mientras está abierta o abriéndose (la app pone el borroso con esto). */
    var abierto by mutableStateOf(false)
    /** Dónde estaba la tarjeta chica, para que salga y vuelva desde ahí. */
    var origen by mutableStateOf<Rect?>(null)
    /** Pedido de cerrar con animación (lo atiende la capa). */
    var pedirCierre by mutableStateOf(false)

    fun abrir(c: String, desde: Rect?) { origen = desde; clave = c; abierto = true; pedirCierre = false }
    fun cerrar() { if (clave != null) pedirCierre = true }
    /** Sin animación: por ejemplo, si ya no le debe nada y la tarjeta desapareció. */
    fun cerrarYa() { clave = null; abierto = false; pedirCierre = false }
}

/** La tarjeta levantada, en una ventanita propia encima de toda la app (la app queda borrosa detrás).
    `contenido` es la tarjeta grande; se dibuja desde "Me deben", así siempre tiene los datos al día. */
@Composable
fun CapaFocoDeuda(contenido: @Composable () -> Unit) {
    val clave = FocoDeuda.clave ?: return
    androidx.compose.ui.window.Popup(
        popupPositionProvider = object : androidx.compose.ui.window.PopupPositionProvider {
            override fun calculatePosition(anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
                layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize) =
                androidx.compose.ui.unit.IntOffset.Zero
        },
        onDismissRequest = { FocoDeuda.cerrar() },
        properties = androidx.compose.ui.window.PopupProperties(focusable = true)
    ) { Capa(clave, contenido) }
}

@Composable
private fun Capa(clave: String, cont: @Composable () -> Unit) {
    val v = remember(clave) { Animatable(0f) }
    var marco by remember(clave) { mutableStateOf<Rect?>(null) }
    LaunchedEffect(clave) { v.snapTo(0f); v.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    LaunchedEffect(FocoDeuda.pedirCierre) {
        if (!FocoDeuda.pedirCierre) return@LaunchedEffect
        FocoDeuda.abierto = false
        v.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        FocoDeuda.cerrarYa()
    }

    /* fondo oscuro: tocar afuera cierra */
    val sinOnda = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f * v.value)).clickable(sinOnda, null) { FocoDeuda.cerrar() })
    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 48.dp), contentAlignment = Alignment.Center) {
        /* el marco se mide en una caja quieta (la que se mueve es la de adentro), si no se persiguen entre sí */
        Box(Modifier.fillMaxWidth().onGloballyPositioned { val p = it.positionOnScreen()
            val r = Rect(p, androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()))
            if (r != marco) marco = r }) {
        Box(Modifier.fillMaxWidth()
            .clickable(sinOnda, null) { }   // tocar la tarjeta no la cierra
            .graphicsLayer {
                val p = v.value; val m = marco; val o = FocoDeuda.origen
                if (m != null && o != null && m.width > 0f) {
                    /* sale de donde estaba la tarjeta chica y crece hasta el centro */
                    val s0 = (o.width / m.width).coerceIn(0.3f, 1f)
                    val s = s0 + (1f - s0) * p
                    scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin.Center
                    translationX = (o.center.x - m.center.x) * (1f - p)
                    translationY = (o.center.y - m.center.y) * (1f - p)
                } else { val s = 0.85f + 0.15f * p; scaleX = s; scaleY = s }
                alpha = (p * 5f).coerceAtMost(1f)   // se desvanece recién al llegar a su lugar
                shadowElevation = 24f * p
            }) { cont() }
        }
    }
}
