package com.miplata.kotlin.aviso

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.miplata.kotlin.MainActivity
import com.miplata.kotlin.R
import com.miplata.kotlin.datos.Almacen
import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.Nucleo
import com.miplata.kotlin.ui.pesos
import java.util.Calendar
import kotlinx.serialization.json.booleanOrNull

/* Aviso de la reserva: cada noche, a la hora elegida, una notificación con lo que te sobró del día
   para que lo pases a tu reserva de Mercado Pago. A diferencia de la versión web, el monto se calcula
   EN EL MOMENTO del aviso, con tus datos de ese instante: siempre es el número real. */
object AvisoReserva {
    private const val CANAL = "reserva"
    private const val ID_NOTI = 7100
    private const val ID_PRUEBA = 7199

    fun hayPermiso(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun canal(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Aviso de la reserva", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Cada noche, cuánto pasar a tu reserva de Mercado Pago" })
    }

    private fun pendiente(ctx: Context, accion: String) = PendingIntent.getBroadcast(ctx, 0,
        Intent(ctx, ReceptorAviso::class.java).setAction(accion), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Programa (o cancela) el próximo aviso según los datos guardados. */
    fun programar(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendiente(ctx, "com.miplata.kotlin.AVISO_RESERVA")
        am.cancel(pi)
        val prefs = ctx.getSharedPreferences("aviso", Context.MODE_PRIVATE)
        prefs.edit().remove("proximo").apply()
        val S = Almacen(ctx).estado ?: return
        if (!S.modoFondo || !S.resAviso) return
        val (hh, mm) = S.resHora.split(":").map { it.toIntOrNull() ?: 0 }
        val cuando = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hh); set(Calendar.MINUTE, mm); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 5_000) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi)
        prefs.edit().putLong("proximo", cuando).apply()
    }

    /** Cuándo suena el próximo aviso (0 = ninguno). */
    fun proximo(ctx: Context): Long = ctx.getSharedPreferences("aviso", Context.MODE_PRIVATE).getLong("proximo", 0)

    fun alarmasExactas(ctx: Context) = Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Texto del aviso con los números de este momento. null = hoy no hay nada para pasar. */
    fun texto(ctx: Context): String? {
        val S = Almacen(ctx).estado ?: return null
        val c = Nucleo.calcular(S, Fechas.hoy())
        if (!c.fondo.activo) return null
        val sobra = c.fondo.hoySuma
        val total = c.fondo.total - S.resPasado + sobra
        if (sobra < 1) return null
        return "Hoy te sobraron ${pesos(sobra)}. Pasalos a tu reserva de Mercado Pago" +
            (if (total - sobra >= 1) " (en total, ${pesos(total)})." else ".")
    }

    /* ---- Recordatorio diario: "¿Anotaste tus gastos de hoy?" ---- */
    fun programarRecordatorio(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 3, Intent(ctx, ReceptorAviso::class.java).setAction("com.miplata.kotlin.RECORDATORIO"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        val S = Almacen(ctx).estado ?: return
        if (!S.crudo["recOn"].let { it is kotlinx.serialization.json.JsonPrimitive && it.booleanOrNull == true }) return
        val hora = (S.crudo["recHora"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { Regex("""\d{1,2}:\d{2}""").matches(it) } ?: "21:00"
        val (hh, mm) = hora.split(":").map { it.toInt() }
        val cuando = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hh); set(Calendar.MINUTE, mm); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 5_000) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi)
    }

    fun textoRecordatorio(ctx: Context): String? {
        val S = Almacen(ctx).estado ?: return null
        val c = Nucleo.calcular(S, Fechas.hoy())
        return if (c.gastoHoy == 0.0) "¿Anotaste tus gastos de hoy? Todavía no cargaste nada. Te quedan ${pesos(c.quedaHoy)}."
        else "¿Anotaste todo lo de hoy? Llevás ${pesos(c.gastoHoy)} y te quedan ${pesos(c.quedaHoy)}."
    }

    fun mostrar(ctx: Context, cuerpo: String, prueba: Boolean = false, recordatorio: Boolean = false) {
        if (!hayPermiso(ctx)) return
        canal(ctx)
        val abrir = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(if (prueba) "Mi plata · Prueba de aviso" else if (recordatorio) "Mi plata · Recordatorio" else "Mi plata · A tu reserva")
            .setContentText(cuerpo).setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setContentIntent(abrir).setAutoCancel(true).build()
        ctx.getSystemService(NotificationManager::class.java).notify(if (prueba) ID_PRUEBA else if (recordatorio) 7001 else ID_NOTI, n)
    }

    fun probar(ctx: Context) {
        val S = Almacen(ctx).estado
        val sobra = S?.let { val c = Nucleo.calcular(it, Fechas.hoy()); if (c.fondo.activo) c.fondo.hoySuma else c.quedaHoy } ?: 0.0
        mostrar(ctx, "Así te va a llegar cada noche. Hoy te sobran ${pesos(maxOf(0.0, sobra))}.", prueba = true)
    }
}

class ReceptorAviso : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            "com.miplata.kotlin.AVISO_RESERVA" -> AvisoReserva.texto(ctx)?.let { AvisoReserva.mostrar(ctx, it) }
            "com.miplata.kotlin.RECORDATORIO" -> AvisoReserva.textoRecordatorio(ctx)?.let { AvisoReserva.mostrar(ctx, it, recordatorio = true) }
        }
        AvisoReserva.programar(ctx)   // los de mañana (y vuelve a programar al prender el teléfono)
        AvisoReserva.programarRecordatorio(ctx)
    }
}
