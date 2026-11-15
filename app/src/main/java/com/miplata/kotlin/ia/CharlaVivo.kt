package com.miplata.kotlin.ia

import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Base64
import kotlinx.serialization.json.*
import okhttp3.*
import okio.ByteString
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/* Charla en vivo con Gemini (Live API): tu voz va en tiempo real y la de Gemini vuelve a medida que habla.
   Se puede interrumpir. Usa la misma clave gratis de "Preguntale a tu plata". El audio del teléfono se pone
   en modo llamada (parlante + cancelador de eco) para que Gemini no se escuche a sí mismo. */
class CharlaVivo(private val ctx: Context, private val sistema: String, private val alEvento: (Evento) -> Unit) {

    sealed interface Evento {
        data class Estado(val texto: String) : Evento
        data class Vos(val texto: String) : Evento            // lo que vas diciendo (subtítulo)
        data class Gemini(val texto: String) : Evento         // lo que va diciendo Gemini (subtítulo)
        data class Nivel(val valor: Float, val hablaGemini: Boolean) : Evento
        data object TurnoTerminado : Evento
        data class Error(val texto: String) : Evento
    }

    companion object {
        /* Modelos de voz del plan gratis; si uno no existe más, se prueba el siguiente y se recuerda el que anduvo. */
        val MODELOS = listOf("gemini-3.1-flash-live-preview", "gemini-2.5-flash-native-audio-preview-12-2025",
            "gemini-2.5-flash-native-audio-preview-09-2025")
        private const val URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        private const val ENTRADA_HZ = 16_000
        private const val SALIDA_HZ = 24_000

        fun mensajeSetup(modelo: String, sistema: String, voz: String = "Puck"): String = JsonObject(mapOf("setup" to JsonObject(mapOf(
            "model" to JsonPrimitive("models/$modelo"),
            "generationConfig" to JsonObject(mapOf(
                "responseModalities" to JsonArray(listOf(JsonPrimitive("AUDIO"))),
                "speechConfig" to JsonObject(mapOf("voiceConfig" to JsonObject(mapOf("prebuiltVoiceConfig" to
                    JsonObject(mapOf("voiceName" to JsonPrimitive(voz))))))))),
            "systemInstruction" to JsonObject(mapOf("parts" to JsonArray(listOf(JsonObject(mapOf("text" to JsonPrimitive(sistema))))))),
            "inputAudioTranscription" to JsonObject(emptyMap()),
            "outputAudioTranscription" to JsonObject(emptyMap()),
        )))).toString()

        fun mensajeAudio(pcm: ByteArray, largo: Int): String = JsonObject(mapOf("realtimeInput" to JsonObject(mapOf("audio" to JsonObject(mapOf(
            "data" to JsonPrimitive(Base64.encodeToString(pcm, 0, largo, Base64.NO_WRAP)),
            "mimeType" to JsonPrimitive("audio/pcm;rate=$ENTRADA_HZ"))))))).toString()

        fun mensajeSaludo(): String = JsonObject(mapOf("clientContent" to JsonObject(mapOf(
            "turns" to JsonArray(listOf(JsonObject(mapOf("role" to JsonPrimitive("user"), "parts" to JsonArray(listOf(JsonObject(mapOf(
                "text" to JsonPrimitive("Saludame en una frase corta y preguntame qué quiero saber de mi plata."))))))))),
            "turnComplete" to JsonPrimitive(true))))).toString()

        /** Traduce el motivo de cierre que manda Google a algo entendible. null = probar con otro modelo. */
        fun traducirCierre(motivo: String): String? {
            val m = motivo.lowercase()
            return when {
                "not found" in m || "not supported" in m || "unsupported" in m || "is not available" in m -> null
                "api key" in m || "api_key" in m || "permission" in m || "unauthenticated" in m -> "La clave de Gemini no es válida. Revisala en Ajustes → Inteligencia artificial."
                "quota" in m || "exhausted" in m || "rate" in m || "limit" in m -> "Llegaste al límite gratis de Gemini por ahora. Probá de nuevo en un rato."
                motivo.isBlank() -> "Gemini cortó la charla."
                else -> "Gemini cortó la charla: ${motivo.take(150)}"
            }
        }
    }

    private val cliente = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build()
    private val prefs = ctx.getSharedPreferences("ia", Context.MODE_PRIVATE)
    private var ws: WebSocket? = null
    @Volatile private var activo = false
    @Volatile private var listo = false
    @Volatile var silenciado = false
    private val cola = LinkedBlockingQueue<ByteArray>()
    private var grabadora: AudioRecord? = null
    private var parlante: AudioTrack? = null
    private var eco: AcousticEchoCanceler? = null
    private var ruido: NoiseSuppressor? = null
    private var hiloMic: Thread? = null
    private var hiloParlante: Thread? = null
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var modoAntes = AudioManager.MODE_NORMAL

    fun empezar() {
        if (activo) return
        val clave = Gemini.clave(ctx)
        if (clave.isEmpty()) { alEvento(Evento.Error("Falta tu clave de Gemini: cargala en Ajustes → Inteligencia artificial.")); return }
        activo = true
        val recordado = prefs.getString("modeloVivo", null)
        conectar(clave, listOfNotNull(recordado) + MODELOS.filter { it != recordado }, 0)
    }

    private fun conectar(clave: String, modelos: List<String>, i: Int) {
        if (!activo) return
        if (i >= modelos.size) { fallar("Google no tiene disponible ahora la charla por voz. Probá más tarde; el chat escrito sigue andando."); return }
        val modelo = modelos[i]
        alEvento(Evento.Estado("Conectando…"))
        var terminado = false     // cada intento reacciona una sola vez a su cierre
        val pedido = Request.Builder().url("$URL?key=$clave").build()
        ws = cliente.newWebSocket(pedido, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send(mensajeSetup(modelo, sistema)) }
            override fun onMessage(webSocket: WebSocket, text: String) = procesar(webSocket, modelo, text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = procesar(webSocket, modelo, bytes.utf8())
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (terminado) return; terminado = true
                if (!activo) return
                val texto = traducirCierre(reason)
                if (texto == null && !listo) conectar(clave, modelos, i + 1) else fallar(texto ?: "Gemini cortó la charla.")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (terminado) return; terminado = true
                if (!activo) return
                if (!listo && response?.code == 404) { conectar(clave, modelos, i + 1); return }
                fallar(if (response != null) traducirCierre(response.message) ?: "No pude conectar la charla (${response.code})." else Gemini.explicarError(ctx, t))
            }
        })
    }

    private fun procesar(webSocket: WebSocket, modelo: String, texto: String) {
        val o = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return
        if (o["setupComplete"] != null && !listo) {
            listo = true
            prefs.edit().putString("modeloVivo", modelo).apply()
            arrancarAudio()
            webSocket.send(mensajeSaludo())
            alEvento(Evento.Estado("Te escucho"))
            return
        }
        o["goAway"]?.let { alEvento(Evento.Estado("La charla se va a cortar en un ratito")) }
        val sc = o["serverContent"]?.jsonObject ?: return
        if (sc["interrupted"]?.jsonPrimitive?.booleanOrNull == true) {      // lo interrumpiste: se calla ya
            cola.clear(); parlante?.let { runCatching { it.pause(); it.flush(); it.play() } }
        }
        sc["modelTurn"]?.jsonObject?.get("parts")?.jsonArray?.forEach { p ->
            p.jsonObject["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull?.let { cola.offer(Base64.decode(it, Base64.DEFAULT)) }
        }
        sc["inputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.let { if (it.isNotEmpty()) alEvento(Evento.Vos(it)) }
        sc["outputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.let { if (it.isNotEmpty()) alEvento(Evento.Gemini(it)) }
        if (sc["turnComplete"]?.jsonPrimitive?.booleanOrNull == true) alEvento(Evento.TurnoTerminado)
    }

    @Suppress("DEPRECATION")
    private fun arrancarAudio() {
        /* Modo llamada con parlante: así funciona el cancelador de eco */
        modoAntes = audio.mode
        runCatching {
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= 31) audio.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { audio.setCommunicationDevice(it) }
            else audio.isSpeakerphoneOn = true
        }
        val tamMic = maxOf(AudioRecord.getMinBufferSize(ENTRADA_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 6400)
        val g = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, ENTRADA_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, tamMic)
        } catch (e: SecurityException) { fallar("Mi plata necesita permiso para usar el micrófono."); return }
        if (g.state != AudioRecord.STATE_INITIALIZED) { g.release(); fallar("No pude usar el micrófono. ¿Lo está usando otra app?"); return }
        grabadora = g
        if (AcousticEchoCanceler.isAvailable()) eco = runCatching { AcousticEchoCanceler.create(g.audioSessionId)?.apply { enabled = true } }.getOrNull()
        if (NoiseSuppressor.isAvailable()) ruido = runCatching { NoiseSuppressor.create(g.audioSessionId)?.apply { enabled = true } }.getOrNull()
        val tamPar = maxOf(AudioTrack.getMinBufferSize(SALIDA_HZ, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), 9600)
        val p = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SALIDA_HZ).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(tamPar).setTransferMode(AudioTrack.MODE_STREAM).build()
        parlante = p; p.play()

        hiloMic = Thread {
            val buf = ByteArray(3200)                  // 100 ms de audio
            runCatching { g.startRecording() }
            while (activo) {
                val n = g.read(buf, 0, buf.size)
                if (n <= 0) continue
                if (!silenciado) { ws?.send(mensajeAudio(buf, n)); alEvento(Evento.Nivel(nivel(buf, n), false)) }
            }
        }.apply { isDaemon = true; start() }
        hiloParlante = Thread {
            while (activo) {
                val trozo = runCatching { cola.poll(200, TimeUnit.MILLISECONDS) }.getOrNull() ?: continue
                runCatching { p.write(trozo, 0, trozo.size) }
                alEvento(Evento.Nivel(nivel(trozo, trozo.size), true))
            }
        }.apply { isDaemon = true; start() }
    }

    /** Volumen de un pedazo de audio, de 0 a 1 (para que la esfera lata). */
    private fun nivel(b: ByteArray, n: Int): Float {
        var suma = 0.0; var i = 0; var c = 0
        while (i + 1 < n) { val s = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xff)).toShort().toDouble(); suma += s * s; i += 2; c++ }
        return if (c == 0) 0f else (sqrt(suma / c) / 6000.0).coerceIn(0.0, 1.0).toFloat()
    }

    private fun fallar(texto: String) { alEvento(Evento.Error(texto)); terminar() }

    @Suppress("DEPRECATION")
    fun terminar() {
        if (!activo && grabadora == null && ws == null) return
        activo = false; listo = false
        runCatching { ws?.close(1000, "chau") }; ws = null
        hiloMic?.join(300); hiloParlante?.join(300)
        runCatching { grabadora?.stop() }; runCatching { grabadora?.release() }; grabadora = null
        runCatching { eco?.release() }; runCatching { ruido?.release() }
        runCatching { parlante?.stop() }; runCatching { parlante?.release() }; parlante = null
        cola.clear()
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice() else audio.isSpeakerphoneOn = false
            audio.mode = modoAntes
        }
    }
}
