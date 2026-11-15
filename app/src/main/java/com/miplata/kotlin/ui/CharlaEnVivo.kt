package com.miplata.kotlin.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.miplata.kotlin.ia.CharlaVivo
import com.miplata.kotlin.ia.Gemini
import com.miplata.kotlin.nucleo.Estado

/** "🎙️ Charlar en vivo": hablás con Gemini como en una llamada, sobre tus números. */
@Composable
fun CharlaEnVivo(S: Estado, alCerrar: () -> Unit) {
    val k = LocalColores.current
    val ctx = LocalContext.current
    val vista = LocalView.current
    var estado by remember { mutableStateOf("Preparando…") }
    var vos by remember { mutableStateOf("") }
    var gemini by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var nivel by remember { mutableFloatStateOf(0f) }
    var hablaGemini by remember { mutableStateOf(false) }
    var silencio by remember { mutableStateOf(false) }
    var nuevoTurnoVos by remember { mutableStateOf(true) }
    var nuevoTurnoGemini by remember { mutableStateOf(true) }
    var hayPermiso by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var pidio by remember { mutableStateOf(false) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        pidio = true; hayPermiso = ok
        if (!ok) error = "Sin permiso del micrófono no puedo escucharte. Podés darlo en Ajustes del teléfono → Aplicaciones → Mi plata → Permisos."
    }

    val principal = remember { Handler(Looper.getMainLooper()) }
    val charla = remember {
        CharlaVivo(ctx, Gemini.instrucciones(Gemini.contexto(S), porVoz = true)) { ev ->
            principal.post {
                when (ev) {
                    is CharlaVivo.Evento.Estado -> estado = ev.texto
                    is CharlaVivo.Evento.Vos -> {
                        if (nuevoTurnoVos) { vos = ""; nuevoTurnoVos = false; nuevoTurnoGemini = true }
                        vos += ev.texto; estado = "Te escucho"
                    }
                    is CharlaVivo.Evento.Gemini -> {
                        if (nuevoTurnoGemini) { gemini = ""; nuevoTurnoGemini = false }
                        gemini += ev.texto; estado = "Gemini está hablando"
                    }
                    is CharlaVivo.Evento.Nivel -> { nivel = nivel * 0.6f + ev.valor * 0.4f; hablaGemini = ev.hablaGemini }
                    CharlaVivo.Evento.TurnoTerminado -> { nuevoTurnoVos = true; nuevoTurnoGemini = true; estado = "Te escucho" }
                    is CharlaVivo.Evento.Error -> error = ev.texto
                }
            }
        }
    }

    LaunchedEffect(Unit) { if (!hayPermiso) pedir.launch(Manifest.permission.RECORD_AUDIO) }
    LaunchedEffect(hayPermiso) { if (hayPermiso) charla.empezar() }
    DisposableEffect(Unit) {
        vista.keepScreenOn = true      // que la pantalla no se apague mientras charlás
        onDispose { vista.keepScreenOn = false; Thread { charla.terminar() }.start() }
    }
    fun cerrar() { alCerrar() }
    BackHandler(onBack = ::cerrar)

    /* Esfera que respira sola y late con la voz */
    val respira by rememberInfiniteTransition(label = "respira").animateFloat(0.96f, 1.04f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "r")
    val tam by animateFloatAsState(respira + nivel * 0.45f, tween(90), label = "tam")
    val colorA = if (hablaGemini) Color(0xFF7C4DFF) else k.verde
    val colorB = if (hablaGemini) Color(0xFF40C4FF) else k.verdeClaro

    Box(Modifier.fillMaxSize().background(k.fondo)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎙️ Charlar en vivo", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = k.texto)
            Text("Hablá tranquilo, como en una llamada. Podés interrumpirlo cuando quieras.", fontSize = 12.sp,
                color = k.suave, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(0.6f))
            Box(Modifier.size(190.dp).scale(if (error == null) tam else 0.9f)
                .background(Brush.radialGradient(listOf(colorB, colorA, colorA.copy(alpha = 0.15f))), CircleShape))
            Spacer(Modifier.height(18.dp))
            Text(when { error != null -> "Se cortó"; silencio -> "Micrófono silenciado"; else -> estado },
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = k.texto)
            Spacer(Modifier.height(14.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text("⚠️ $it", color = k.rojo, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                if (vos.isNotBlank()) Text("Vos: ${vos.trim()}", color = k.suave, fontSize = 14.sp)
                if (gemini.isNotBlank()) Text(gemini.trim(), color = k.texto, fontSize = 16.sp)
            }
            Text("Tus datos del período se mandan a Google para responder.", fontSize = 11.sp, color = k.suave)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { silencio = !silencio; charla.silenciado = silencio }, Modifier.weight(1f).height(54.dp),
                    enabled = error == null) { Text(if (silencio) "🎤 Activar micrófono" else "🔇 Silenciar") }
                Button(onClick = ::cerrar, Modifier.weight(1f).height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = k.rojo, contentColor = Color.White)) { Text("Terminar") }
            }
        }
    }
}
