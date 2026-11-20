package com.miplata.kotlin.aviso

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.miplata.kotlin.MainActivity
import com.miplata.kotlin.R
import com.miplata.kotlin.datos.Almacen
import com.miplata.kotlin.nucleo.Taller
import com.miplata.kotlin.nucleo.texto
import com.miplata.kotlin.ui.pesos
import kotlinx.coroutines.launch
import java.text.Normalizer

/* Plata que te entra por Mercado Pago.
   Mercado Pago te avisa con una notificación cuando te transfieren ("Fulano te transfirió $2.800").
   Mi plata la lee (solo las de Mercado Pago), y te pregunta con un toque si la anota como entrada.
   Si el que te pagó es un compañero del taller que te debe, te lo dice para que lo marques cobrado en "Me deben"
   (así no se anota dos veces). */
class EscuchaMP : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName != PAQUETE_MP) return
        val ex = n.notification?.extras ?: return
        val titulo = ex.getCharSequence("android.title")?.toString() ?: ""
        val texto = (ex.getCharSequence("android.bigText") ?: ex.getCharSequence("android.text"))?.toString() ?: ""
        EntradasMP.recibida(applicationContext, titulo, texto, n.postTime)
    }

    companion object {
        const val PAQUETE_MP = "com.mercadopago.wallet"
    }
}

object EntradasMP {
    private const val CANAL = "entradas_mp"
    private const val PREFS = "entradas_mp"
    const val ANOTAR = "com.miplata.kotlin.ENTRADA_MP_ANOTAR"
    const val REPARTIR = "com.miplata.kotlin.ENTRADA_MP_REPARTIR"
    const val NO = "com.miplata.kotlin.ENTRADA_MP_NO"

    /* Lo que elegiste la última vez con cada persona (ej.: Mariela → repartir), para proponértelo primero */
    fun clavePersona(nombre: String) = "eleccion_" + Normalizer.normalize(nombre, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase().trim()
    fun recordar(ctx: Context, nombre: String, accion: String) {
        if (nombre.isNotBlank()) ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(clavePersona(nombre), accion).apply()
    }
    private fun recordada(ctx: Context, nombre: String) =
        if (nombre.isBlank()) null else ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(clavePersona(nombre), null)

    data class Leida(val monto: Double, val nombre: String)

    /** ¿Mi plata tiene permiso para leer las notificaciones? */
    fun activa(ctx: Context) = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    fun abrirPermiso(ctx: Context) = runCatching {
        ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    fun reconectar(ctx: Context) = runCatching {
        NotificationListenerService.requestRebind(ComponentName(ctx, EscuchaMP::class.java))
    }

    /** Las últimas notificaciones de Mercado Pago que llegaron (para ver en Ajustes qué leyó la app). */
    fun ultimas(ctx: Context): List<String> =
        (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("ultimas", "") ?: "").split("\n").filter { it.isNotBlank() }

    private fun sinTildes(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

    /** "$ 2.800", "$2.800,50" → 2800.0 / 2800.5 */
    private fun monto(s: String): Double? {
        val m = Regex("\\$\\s?([0-9]{1,3}(?:\\.[0-9]{3})*|[0-9]+)(?:,([0-9]{1,2}))?").find(s) ?: return null
        val entero = m.groupValues[1].replace(".", "").toDoubleOrNull() ?: return null
        val dec = m.groupValues[2].takeIf { it.isNotEmpty() }?.padEnd(2, '0')?.toDouble()?.div(100) ?: 0.0
        return entero + dec
    }

    /** Lee una notificación de Mercado Pago. Solo plata que TE ENTRA; descarta pagos tuyos, rendimientos y publicidad. */
    fun leer(titulo: String, texto: String): Leida? {
        val todo = "$titulo. $texto"
        val t = sinTildes(todo)
        val entra = listOf("recibiste", "te transfirio", "te transfirieron", "te envio", "te enviaron", "te mando", "te pago", "te pagaron",
            "te deposito", "ingreso de dinero", "ingresaron", "te llego", "acreditamos", "cobraste").any { t.contains(it) }
        val noEs = listOf("rendimiento", "rindio", "pagaste", "enviaste", "transferiste", "vence", "vencimiento", "oferta", "descuento", "promo",
            "cuota", "prestamo", "credito", "invert", "reserva", "cashback", "reintegro", "devolucion", "sorteo", "ganaste", "beneficio", "tarjeta")
            .any { t.contains(it) }
        if (!entra || noEs) return null
        val m = monto(todo) ?: return null
        if (m <= 0) return null
        val nombre = listOf(
            Regex("^\\s*([^.$!¡]+?)\\s+te\\s+(?:transfiri[oó]|envi[oó]|mand[oó]|pag[oó]|deposit[oó])", RegexOption.IGNORE_CASE),
            Regex("\\bde\\s+([A-ZÁÉÍÓÚÑ][\\p{L}]+(?:\\s+[A-ZÁÉÍÓÚÑ][\\p{L}]+){0,4})"),
        ).firstNotNullOfOrNull { r -> r.find(texto)?.groupValues?.get(1) ?: r.find(titulo)?.groupValues?.get(1) }
            ?.trim()?.removeSuffix(".")?.takeIf { it.length in 2..60 && !it.contains("$") } ?: ""
        return Leida(m, nombre)
    }

    fun recibida(ctx: Context, titulo: String, texto: String, cuando: Long) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        /* guarda las últimas 6 para poder revisarlas en Ajustes */
        val linea = (titulo + " — " + texto).replace("\n", " ").take(160)
        p.edit().putString("ultimas", (listOf(linea) + ultimas(ctx)).distinct().take(6).joinToString("\n")).apply()
        val l = leer(titulo, texto) ?: return
        /* Mercado Pago a veces repite la misma notificación: no la cuenta dos veces */
        val clave = "${l.monto}|${sinTildes(l.nombre)}|${cuando / 120_000}"
        val vistas = (p.getString("vistas", "") ?: "").split(",").filter { it.isNotBlank() }
        if (clave in vistas) return
        p.edit().putString("vistas", (listOf(clave) + vistas).take(40).joinToString(",")).apply()
        mostrar(ctx, l)
    }

    /** Si el que te pagó es un compañero del taller que te debe: (nombre a mostrar, cuánto te debe). */
    private fun deudor(ctx: Context, nombre: String): Pair<String, Double>? {
        if (nombre.isBlank()) return null
        val a = Almacen(ctx)
        val S = a.estado ?: return null
        val quien = sinTildes(nombre).split(Regex("\\s+")).toSet()
        val m = Taller.de(a.crudo).muchachos.firstOrNull { mu ->
            val partes = sinTildes(mu.nombre).split(Regex("\\s+")).filter { it.length > 1 }
            partes.isNotEmpty() && partes.all { it in quien }
        } ?: return null
        val debe = S.spent.filter { it.dev == 1 && it.extra["tp"].texto() == m.id }.sumOf { it.amt }
        if (debe < 1) return null
        return (if (m.apodo.isNotBlank()) "${m.nombre} (${m.apodo})" else m.nombre) to debe
    }

    private fun mostrar(ctx: Context, l: Leida) {
        if (!AvisoReserva.hayPermiso(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Plata que te entra (Mercado Pago)", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Cuando Mercado Pago te avisa que te transfirieron, te pregunta si lo anota" })
        val id = (System.currentTimeMillis() % 100_000).toInt() + 8000
        fun accion(a: String) = PendingIntent.getBroadcast(ctx, id * 10 + when (a) { ANOTAR -> 1; REPARTIR -> 2; else -> 3 },
            Intent(ctx, ReceptorEntradaMP::class.java).setAction(a).putExtra("monto", l.monto).putExtra("nombre", l.nombre).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val d = deudor(ctx, l.nombre)
        val abrir = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(AvisoTaller.EXTRA_ABRIR, d != null),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val de = if (l.nombre.isNotBlank()) " de ${l.nombre}" else ""
        val titulo = "💰 Te entraron ${pesos(l.monto)}$de"
        val cuerpo = if (d != null)
            "${d.first} te debe ${pesos(d.second)} del taller. Si es ese pago, tocá acá y marcalo cobrado en \"Me deben\" (no lo anotes como entrada, así no se cuenta dos veces)."
        else {
            val r = recordada(ctx, l.nombre)
            val antes = when (r) { REPARTIR -> " La última vez la repartiste en los días."; ANOTAR -> " La última vez la sumaste a tu día."; else -> "" }
            "¿Cómo la anoto en Mi plata?$antes\n• Suma a mi día: se suma a lo de hoy.\n• Repartir: se divide entre hoy y los días que quedan del mes."
        }
        val b = NotificationCompat.Builder(ctx, CANAL).setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(titulo).setContentText(cuerpo).setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setContentIntent(abrir).setAutoCancel(true)
        /* lo que elegiste la última vez con esa persona va primero */
        val orden = if (recordada(ctx, l.nombre) == REPARTIR) listOf(REPARTIR, ANOTAR) else listOf(ANOTAR, REPARTIR)
        orden.forEach { a -> b.addAction(0, when (a) { REPARTIR -> "Repartir en los días"; else -> if (d != null) "Es otra cosa: suma a mi día" else "Suma a mi día" }, accion(a)) }
        b.addAction(0, "No anotar", accion(NO))
        nm.notify(id, b.build())
    }
}

/** Botones de la notificación "Te entraron …". */
class ReceptorEntradaMP : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
        val accion = intent.action ?: return
        val nombre = intent.getStringExtra("nombre").orEmpty()
        EntradasMP.recordar(ctx, nombre, accion)
        if (accion != EntradasMP.ANOTAR && accion != EntradasMP.REPARTIR) return
        val monto = intent.getDoubleExtra("monto", 0.0)
        if (monto <= 0) return
        val fin = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                val nota = if (nombre.isNotBlank()) "Entrada MP · $nombre" else "Entrada MP"
                if (accion == EntradasMP.REPARTIR) Almacen(ctx).entradaRepartida(monto, nota)
                else Almacen(ctx).anotar(monto, nota, null, entrada = true, meLoDevuelven = false)
            }
            runCatching { com.miplata.kotlin.widget.Widget.actualizar(ctx) }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(ctx, if (accion == EntradasMP.REPARTIR) "Anotado: ${pesos(monto)} repartidos en los días que quedan" else "Anotado: entrada de ${pesos(monto)}", android.widget.Toast.LENGTH_SHORT).show()
            }
            fin.finish()
        }
    }
}
