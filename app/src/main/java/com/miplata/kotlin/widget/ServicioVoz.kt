package com.miplata.kotlin.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.miplata.kotlin.R
import com.miplata.kotlin.datos.Almacen
import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.Nucleo
import com.miplata.kotlin.ui.aNumero
import com.miplata.kotlin.ui.interpretarDictado
import com.miplata.kotlin.ui.pesos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll

/** Lo que muestra el widget del micrófono mientras escucha (el widget lo lee y se redibuja solo). */
object VozWidget {
    enum class Fase { NADA, ESCUCHANDO, PENSANDO, LISTO, SIN_MONTO, ERROR }
    val fase = mutableStateOf(Fase.NADA)
    val nivel = mutableIntStateOf(0)            // 0 a 4: qué tan grande es el aro de luz
    val texto = mutableStateOf("")              // lo que va entendiendo / el resultado
    val version = mutableIntStateOf(0)          // sube cuando cambian los datos (saldo)
    var ultimoId = 0L
    var sinMonto = "" to ""                     // monto y detalle para escribir a mano
}

/* Escucha desde el widget, sin abrir ninguna ventana. Android exige que sea un "servicio en primer plano"
   con un aviso chiquito mientras usa el micrófono; el aviso se va cuando termina. */
class ServicioVoz : Service() {
    private var oido: SpeechRecognizer? = null
    private val mano = Handler(Looper.getMainLooper())
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ultimoDibujo = 0L

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        actual = this
        escuchar()
        return START_NOT_STICKY
    }

    private fun aviso() = NotificationCompat.Builder(this, CANAL).setSmallIcon(R.drawable.ic_noti)
        .setContentTitle("Mi plata te escucha").setContentText("Decí el monto y en qué, ej: 2800 negocio Telma")
        .setPriority(NotificationCompat.PRIORITY_LOW).setSilent(true).setOngoing(true).build()

    private fun escuchar() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CANAL, "Dictar desde el widget", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        runCatching {
            ServiceCompat.startForeground(this, 7, aviso(),
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        }.onFailure { error("No pude usar el micrófono desde el widget. Abrí Mi plata y probá ahí."); return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { error("Este teléfono no tiene reconocimiento de voz."); return }
        mano.removeCallbacksAndMessages(null)
        runCatching { oido?.destroy() }
        VozWidget.texto.value = ""; VozWidget.nivel.intValue = 0
        mostrar(VozWidget.Fase.ESCUCHANDO)
        oido = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rms: Float) {
                    val n = (((rms + 2f) / 12f).coerceIn(0f, 1f) * 4).toInt()
                    val ahora = System.currentTimeMillis()
                    if (n != VozWidget.nivel.intValue && ahora - ultimoDibujo > 120) { ultimoDibujo = ahora; VozWidget.nivel.intValue = n; redibujar() }
                }
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { VozWidget.nivel.intValue = 0; mostrar(VozWidget.Fase.PENSANDO) }
                override fun onError(codigo: Int) {
                    error(when (codigo) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No te escuché. Tocá y probá de nuevo"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> "Sin conexión. Probá de nuevo"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso de micrófono"
                        else -> "No pude escuchar. Probá de nuevo"
                    })
                }
                override fun onResults(r: Bundle?) {
                    val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (t.isNullOrBlank()) error("No te escuché. Tocá y probá de nuevo") else anotar(t)
                }
                override fun onPartialResults(r: Bundle?) {
                    r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                        VozWidget.texto.value = it; redibujar() }
                }
                override fun onEvent(t: Int, b: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1))
        }
    }

    private fun anotar(dicho: String) {
        val (m, n) = interpretarDictado(dicho)
        val monto = aNumero(m)
        if (monto <= 0) {
            VozWidget.sinMonto = "" to n.ifBlank { dicho }
            VozWidget.texto.value = "No entendí el monto · tocá para escribirlo"
            terminar(VozWidget.Fase.SIN_MONTO, 8000); return
        }
        val almacen = Almacen(applicationContext)
        almacen.anotar(monto, n, null, false, false)
        VozWidget.ultimoId = almacen.estado?.spent?.maxByOrNull { it.id }?.id ?: 0L
        val queda = almacen.estado?.let { Nucleo.calcular(it, Fechas.hoy()).quedaHoy } ?: 0.0
        VozWidget.texto.value = "✅ ${pesos(monto)}" + (if (n.isNotBlank()) " · $n" else "") + "\nTe quedan ${pesos(queda)} hoy"
        VozWidget.version.intValue++
        alcance.launch { runCatching { WidgetHoy().updateAll(applicationContext) } }
        alcance.launch {
            runCatching { com.miplata.kotlin.aviso.AvisoReserva.programar(applicationContext) }
            kotlinx.coroutines.withContext(Dispatchers.IO) { com.miplata.kotlin.datos.CopiaAutomatica.guardar(applicationContext) }
        }
        terminar(VozWidget.Fase.LISTO, 7000)
    }

    private fun error(t: String) { VozWidget.texto.value = t; VozWidget.nivel.intValue = 0; terminar(VozWidget.Fase.ERROR, 5000) }

    /** Muestra el resultado un ratito en el widget y después vuelve a lo normal. */
    private fun terminar(f: VozWidget.Fase, ms: Long) {
        runCatching { oido?.destroy() }; oido = null
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        mostrar(f)
        mano.removeCallbacksAndMessages(null)
        mano.postDelayed({ VozWidget.texto.value = ""; VozWidget.ultimoId = 0L; mostrar(VozWidget.Fase.NADA); mano.postDelayed({ stopSelf() }, 1500) }, ms)
    }

    private fun mostrar(f: VozWidget.Fase) { VozWidget.fase.value = f; redibujar() }
    private fun redibujar() { alcance.launch { runCatching { WidgetVoz().updateAll(applicationContext) } } }

    fun parar() { runCatching { oido?.stopListening() } }

    /** Para "Deshacer": muestra el aviso un ratito y vuelve a lo normal. */
    fun mostrarDeshecho() { VozWidget.texto.value = "Listo, no se anotó"; terminar(VozWidget.Fase.ERROR, 2500) }

    override fun onDestroy() {
        if (actual === this) actual = null
        runCatching { oido?.destroy() }; mano.removeCallbacksAndMessages(null)
        if (VozWidget.fase.value == VozWidget.Fase.ESCUCHANDO || VozWidget.fase.value == VozWidget.Fase.PENSANDO) {
            VozWidget.fase.value = VozWidget.Fase.NADA; VozWidget.texto.value = ""; redibujar() }
        super.onDestroy()
    }

    companion object {
        const val CANAL = "voz"
        const val PARAR = "com.miplata.kotlin.voz.PARAR"
        const val DESHACER = "com.miplata.kotlin.voz.DESHACER"
        @Volatile var actual: ServicioVoz? = null
    }
}

/** Botones del widget mientras escucha o después de anotar: Parar y Deshacer (un aviso directo, siempre permitido). */
class ReceptorVoz : android.content.BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            ServicioVoz.PARAR -> ServicioVoz.actual?.parar()
            ServicioVoz.DESHACER -> {
                if (VozWidget.ultimoId == 0L) return
                Almacen(ctx.applicationContext).borrarMovimiento(VozWidget.ultimoId); VozWidget.ultimoId = 0L
                VozWidget.version.intValue++
                val s = ServicioVoz.actual
                if (s != null) s.mostrarDeshecho() else { VozWidget.fase.value = VozWidget.Fase.NADA; VozWidget.texto.value = "" }
                val listo = goAsync()
                CoroutineScope(Dispatchers.Main).launch {
                    runCatching { Widget.actualizar(ctx.applicationContext) }
                    kotlinx.coroutines.withContext(Dispatchers.IO) { com.miplata.kotlin.datos.CopiaAutomatica.guardar(ctx.applicationContext) }
                    listo.finish()
                }
            }
        }
    }
}
