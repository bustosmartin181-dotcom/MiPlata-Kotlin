package com.miplata.kotlin.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/* "Ayudá al creador" flotante: una burbuja que brilla y late, aparece sola un ratito
   (como mucho una vez por día, solo en Hoy, cuando ya usás la app, nunca en modo demo).
   Al tocarla vibra, se estira hasta convertirse en el cartel grande, el fondo se pone
   borroso, salen corazones y el borde brilla girando. Al cerrar, se achica y se va. */
object Colabora {
    const val CADA = 24 * 60 * 60 * 1000L          // como mucho una vez por día
    const val SILENCIO = 30 * CADA                 // "Ya colaboré": un mes sin mostrarla
    const val ESPERA = 6_000L                      // después de abrir Hoy
    const val VISIBLE = 12_000L                    // cuánto queda la burbuja en pantalla

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("colabora", Context.MODE_PRIVATE)
    fun toca(ctx: Context, ahora: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(ctx)
        return ahora - p.getLong("ultima", 0) >= CADA && ahora >= p.getLong("silencio", 0)
    }
    fun mostrada(ctx: Context) = prefs(ctx).edit().putLong("ultima", System.currentTimeMillis()).apply()
    fun yaColaboro(ctx: Context) = prefs(ctx).edit().putLong("silencio", System.currentTimeMillis() + SILENCIO).apply()
}

private enum class Fase { NADA, BURBUJA, CARTEL }

private val VERDE_1 = Color(0xFF0E9F6E)
private val VERDE_2 = Color(0xFF22C55E)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ColaboraFlotante(puede: Boolean, alCopiar: () -> Unit, alAbrirMP: () -> Unit, alYaColabore: () -> Unit,
                     alCambiarAbierto: (Boolean) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val vibrar = LocalHapticFeedback.current
    var fase by remember { mutableStateOf(Fase.NADA) }
    val restante = remember { Animatable(1f) }

    LaunchedEffect(puede) {
        if (!puede) { if (fase == Fase.BURBUJA) fase = Fase.NADA; return@LaunchedEffect }
        if (fase != Fase.NADA || !Colabora.toca(ctx)) return@LaunchedEffect
        delay(Colabora.ESPERA)
        Colabora.mostrada(ctx)
        fase = Fase.BURBUJA
        restante.snapTo(1f)
        restante.animateTo(0f, tween(Colabora.VISIBLE.toInt(), easing = LinearEasing))
        if (fase == Fase.BURBUJA) fase = Fase.NADA
    }
    LaunchedEffect(fase) { alCambiarAbierto(fase == Fase.CARTEL) }

    val abrir = { vibrar.performHapticFeedback(HapticFeedbackType.LongPress); fase = Fase.CARTEL }
    val cerrar = { fase = Fase.NADA }

    /* fondo oscuro detrás del cartel (el borroso lo pone la app sobre todo lo demás) */
    val sinOnda = remember { MutableInteractionSource() }
    val oscuro by animateFloatAsState(if (fase == Fase.CARTEL) 0.45f else 0f, tween(400), label = "oscuro")
    if (oscuro > 0.01f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = oscuro))
        .clickable(sinOnda, null) { cerrar() })

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        AnimatedContent(fase, Modifier.fillMaxSize(), label = "colabora",
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) using SizeTransform(clip = false) }) { f ->
            when (f) {
                Fase.NADA -> Box(Modifier.fillMaxSize())
                Fase.BURBUJA -> Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 96.dp, start = 20.dp, end = 20.dp),
                    contentAlignment = Alignment.BottomCenter) {
                    Burbuja(restante.value, abrir, cerrar,
                        Modifier.sharedBounds(rememberSharedContentState("colabora"), this@AnimatedContent,
                            boundsTransform = { _, _ -> spring(dampingRatio = 0.75f, stiffness = 260f) },
                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds))
                }
                Fase.CARTEL -> Box(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp), contentAlignment = Alignment.Center) {
                    Cartel(alCopiar, alAbrirMP, { alYaColabore(); cerrar() }, cerrar,
                        Modifier.sharedBounds(rememberSharedContentState("colabora"), this@AnimatedContent,
                            boundsTransform = { _, _ -> spring(dampingRatio = 0.75f, stiffness = 260f) },
                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds))
                    Corazones()
                }
            }
        }
    }
}

/** La burbuja chica: verde, con un reflejo que pasa y el corazón latiendo. */
@Composable
private fun Burbuja(restante: Float, alTocar: () -> Unit, alCerrar: () -> Unit, modifier: Modifier) {
    val brillo = rememberInfiniteTransition(label = "brillo")
    val x by brillo.animateFloat(-300f, 900f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "x")
    val latido by brillo.animateFloat(1f, 1.25f, infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "latido")
    val reflejo = Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
        start = Offset(x, 0f), end = Offset(x + 160f, 120f))
    Column(modifier.shadow(14.dp, RoundedCornerShape(50), ambientColor = VERDE_2, spotColor = VERDE_2)
        .clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(VERDE_1, VERDE_2, VERDE_1))).background(reflejo)
        .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(50))
        .clickable(onClick = alTocar)) {
        Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("💚", fontSize = 22.sp, modifier = Modifier.scale(latido))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Ayudá al creador", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Mi plata es gratis y sin publicidad", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = alCerrar), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
            }
        }
        /* rayita que se achica: lo que le queda en pantalla */
        Box(Modifier.padding(horizontal = 22.dp).padding(bottom = 6.dp).fillMaxWidth(restante).height(2.dp)
            .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(1.dp)))
    }
}

/** El cartel grande, con el borde que brilla girando. */
@Composable
private fun Cartel(alCopiar: () -> Unit, alAbrirMP: () -> Unit, alYaColabore: () -> Unit, alCerrar: () -> Unit, modifier: Modifier) {
    val k = LocalColores.current
    val giro = rememberInfiniteTransition(label = "giro")
    val angulo by giro.animateFloat(0f, 360f, infiniteRepeatable(tween(3500, easing = LinearEasing)), label = "angulo")
    val latido by giro.animateFloat(1f, 1.18f, infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "latido")
    val colores = intArrayOf(VERDE_2.toArgb(), Color.White.toArgb(), VERDE_1.toArgb(), Color(0xFFFFD54F).toArgb(), VERDE_2.toArgb())
    Column(modifier.fillMaxWidth().shadow(24.dp, RoundedCornerShape(28.dp), ambientColor = VERDE_2, spotColor = VERDE_2)
        .clip(RoundedCornerShape(28.dp)).background(k.tarjeta)
        .drawWithContent {
            drawContent()
            /* borde que brilla girando: un degradé circular que rota alrededor del cartel */
            val cx = size.width / 2; val cy = size.height / 2
            val sombra = android.graphics.SweepGradient(cx, cy, colores, null)
                .apply { setLocalMatrix(android.graphics.Matrix().apply { setRotate(angulo, cx, cy) }) }
            val r = 28.dp.toPx()
            drawRoundRect(ShaderBrush(sombra), cornerRadius = CornerRadius(r, r), style = Stroke(width = 4.dp.toPx()))
        }
        .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💚", fontSize = 26.sp, modifier = Modifier.scale(latido))
            Spacer(Modifier.width(10.dp))
            Text("Ayudá al creador", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = k.texto)
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = alCerrar), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = k.suave)
            }
        }
        ContenidoColabora(alCopiar, alAbrirMP)
        TextButton(onClick = alYaColabore, Modifier.fillMaxWidth()) { Text("Ya colaboré 💚 · no mostrar por un mes") }
    }
}

/** Corazones que salen flotando hacia arriba cuando se abre el cartel. */
@Composable
private fun Corazones() {
    val corazones = remember {
        List(16) { Triple(Random.nextFloat() * 2 - 1, 0.6f + Random.nextFloat() * 0.8f, Random.nextInt(0, 450)) }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        corazones.forEachIndexed { i, (dx, tam, demora) ->
            val t = remember { Animatable(0f) }
            LaunchedEffect(Unit) { delay(demora.toLong()); t.animateTo(1f, tween(1600, easing = LinearOutSlowInEasing)) }
            if (t.value > 0f && t.value < 1f) {
                val v = t.value
                Text(listOf("💚", "💛", "💚", "✨")[i % 4], fontSize = (18 * tam).sp,
                    modifier = Modifier.offset { IntOffset((dx * 420 * v + 30 * kotlin.math.sin(v * 9 + i)).toInt(), (-700 * v + 120).toInt()) }
                        .alpha(1f - v).scale(0.6f + v * 0.8f).rotate(dx * 40 * v))
            }
        }
    }
}
