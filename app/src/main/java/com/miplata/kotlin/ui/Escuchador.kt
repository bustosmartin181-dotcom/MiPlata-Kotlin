package com.miplata.kotlin.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/* Micrófono propio de Mi plata, como el del teclado Samsung: un aro de luz que se agranda
   y brilla con el volumen de tu voz, y lo que vas diciendo aparece escrito mientras hablás.
   Usa el reconocimiento de voz del teléfono (sin la ventanita de Google). Necesita permiso
   de micrófono; si no lo das, usa la ventanita de Google como antes. */

private enum class Estado { PIDIENDO, ESCUCHANDO, PENSANDO, ERROR }

@Composable
fun Escuchador(alResultado: (String) -> Unit, alCancelar: () -> Unit, ejemplo: String = "Ej: 2800 negocio Telma") {
    val ctx = LocalContext.current
    val k = LocalColores.current
    var estado by remember { mutableStateOf(Estado.PIDIENDO) }
    var parcial by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var volumen by remember { mutableFloatStateOf(0f) }        // 0 = silencio, 1 = fuerte
    var vuelta by remember { mutableIntStateOf(0) }           // para volver a escuchar
    val alResultadoAct by rememberUpdatedState(alResultado)
    val alCancelarAct by rememberUpdatedState(alCancelar)
    val oidoActual = remember { mutableStateOf<SpeechRecognizer?>(null) }

    /* Plan B: la ventanita de Google (si no hay permiso o el teléfono no tiene reconocimiento) */
    val google = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = if (r.resultCode == Activity.RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() else null
        if (t.isNullOrBlank()) alCancelarAct() else alResultadoAct(t)
    }
    fun usarGoogle() = runCatching {
        google.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Decí el monto y en qué, ej: 2800 negocio Telma"))
    }.onFailure { error = "Este teléfono no tiene dictado por voz."; estado = Estado.ERROR }

    var hayPermiso by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) hayPermiso = true else usarGoogle()
    }
    LaunchedEffect(Unit) { if (!hayPermiso) pedir.launch(Manifest.permission.RECORD_AUDIO) }

    /* Escuchar con el reconocimiento del teléfono */
    if (hayPermiso) DisposableEffect(vuelta) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { usarGoogle(); return@DisposableEffect onDispose { } }
        val oido = SpeechRecognizer.createSpeechRecognizer(ctx)
        oidoActual.value = oido
        var terminado = false
        oido.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { estado = Estado.ESCUCHANDO }
            override fun onBeginningOfSpeech() { estado = Estado.ESCUCHANDO }
            override fun onRmsChanged(rms: Float) { volumen = ((rms + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { estado = Estado.PENSANDO; volumen = 0f }
            override fun onError(codigo: Int) {
                if (terminado) return
                volumen = 0f
                error = when (codigo) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No te escuché bien. Tocá el micrófono y probá de nuevo."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> "Sin conexión para entender la voz. Probá de nuevo."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso de micrófono."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El micrófono está ocupado. Probá de nuevo."
                    else -> "No pude escuchar. Tocá el micrófono y probá de nuevo."
                }
                estado = Estado.ERROR
            }
            override fun onResults(r: Bundle?) {
                val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                terminado = true
                if (t.isNullOrBlank()) { error = "No te escuché bien. Tocá el micrófono y probá de nuevo."; estado = Estado.ERROR }
                else alResultadoAct(t)
            }
            override fun onPartialResults(r: Bundle?) {
                r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { parcial = it }
            }
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        parcial = ""; error = ""; estado = Estado.ESCUCHANDO
        oido.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1))
        onDispose { terminado = true; runCatching { oido.cancel() }; runCatching { oido.destroy() } }
    }

    /* ---- Lo que se ve ---- */
    val sinOnda = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(sinOnda, null) { alCancelarAct() },
        contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)
            .background(k.tarjeta, RoundedCornerShape(28.dp)).clickable(sinOnda, null) { }.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(when (estado) {
                Estado.PIDIENDO -> "Preparando el micrófono…"
                Estado.ESCUCHANDO -> "Te escucho…"
                Estado.PENSANDO -> "Entendiendo…"
                Estado.ERROR -> "Uy"
            }, fontSize = 15.sp, color = k.suave)
            MicrofonoConLuz(volumen, estado == Estado.ESCUCHANDO, estado == Estado.ERROR, k.verde) {
                when (estado) {
                    Estado.ERROR -> { vuelta++ }                       // probar de nuevo
                    Estado.ESCUCHANDO -> { estado = Estado.PENSANDO; runCatching { oidoActual.value?.stopListening() } }   // ya terminé de hablar
                    else -> {}
                }
            }
            Text(when {
                estado == Estado.ERROR -> error
                parcial.isNotBlank() -> parcial
                else -> ejemplo
            }, fontSize = if (parcial.isNotBlank() && estado != Estado.ERROR) 22.sp else 15.sp,
                fontWeight = if (parcial.isNotBlank() && estado != Estado.ERROR) FontWeight.Bold else FontWeight.Normal,
                color = if (estado == Estado.ERROR) k.naranja else if (parcial.isNotBlank()) k.texto else k.suave,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp))
            TextButton(onClick = alCancelarAct) { Text("Cancelar") }
        }
    }
}

/** El micrófono redondo con el aro de luz: crece y brilla con el volumen, y "respira" despacio en silencio. */
@Composable
private fun MicrofonoConLuz(volumen: Float, activo: Boolean, conError: Boolean, color: Color, alTocar: () -> Unit) {
    val nivel by animateFloatAsState(if (activo) volumen else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "nivel")
    val respira = rememberInfiniteTransition(label = "respira")
    val pulso by respira.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulso")
    val onda by respira.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing)), label = "onda")
    val luz = if (conError) Color(0xFFE67E22) else color
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r0 = 44.dp.toPx()
            if (activo) {
                // onda que sale sola, como en el teclado
                drawCircle(luz.copy(alpha = 0.25f * (1f - onda)), radius = r0 + (size.minDimension / 2 - r0) * onda)
                // el aro que sigue tu voz
                val r = r0 + 10.dp.toPx() + nivel * (size.minDimension / 2 - r0 - 10.dp.toPx())
                drawCircle(Brush.radialGradient(listOf(luz.copy(alpha = 0.55f), luz.copy(alpha = 0.18f), Color.Transparent),
                    center = center, radius = r), radius = r)
                drawCircle(luz.copy(alpha = 0.35f + 0.4f * nivel), radius = r0 + 6.dp.toPx() + nivel * 18.dp.toPx(),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = (3 + 3 * nivel).dp.toPx()))
            } else {
                drawCircle(luz.copy(alpha = 0.15f + 0.1f * pulso), radius = r0 + 8.dp.toPx() + 6.dp.toPx() * pulso)
            }
        }
        Box(Modifier.size(88.dp).scale(1f + nivel * 0.08f).clip(CircleShape).background(luz).clickable(onClick = alTocar),
            contentAlignment = Alignment.Center) {
            Icon(if (conError) Icons.Outlined.MicOff else Icons.Outlined.Mic, contentDescription = "Micrófono",
                tint = Color.White, modifier = Modifier.size(42.dp))
        }
    }
}
