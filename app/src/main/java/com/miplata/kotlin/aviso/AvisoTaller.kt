package com.miplata.kotlin.aviso

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.miplata.kotlin.MainActivity
import com.miplata.kotlin.R
import com.miplata.kotlin.datos.Almacen
import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.Taller
import com.miplata.kotlin.nucleo.hayCompraTaller
import com.miplata.kotlin.nucleo.deudasTallerDe
import java.util.Calendar

/* Avisos del taller, según "Mi horario":
   - al empezar la ventana de compra (9:20 en turno mañana, 17:20 en turno tarde): "Hora del desayuno"
   - 5 minutos después de que termina (10:15 / 18:05): si todavía no anotaste nada, te lo recuerda.
   - cuando se termina de estirar el desayuno o la merienda (10:40 / 19:00): a quién le falta pagarte.
   Tocando el aviso se abre Mi plata directo en modo Taller. */
object AvisoTaller {
    private const val CANAL = "taller"
    const val EXTRA_ABRIR = "abrir_taller"
    private const val ACCION = "com.miplata.kotlin.AVISO_TALLER"

    private fun pendiente(ctx: Context, tipo: String = "") = PendingIntent.getBroadcast(ctx, 11,
        Intent(ctx, ReceptorAviso::class.java).setAction(ACCION).putExtra("tipo", tipo),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun esAccion(a: String?) = a == ACCION

    /** Programa el próximo aviso (empieza o termina una ventana de compra). */
    fun programar(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pendiente(ctx))
        val a = Almacen(ctx)
        val t = Taller.de(a.crudo)
        if (!t.aviso || t.horario.isEmpty() || t.activos.isEmpty()) return
        val ahora = System.currentTimeMillis() + 5_000
        val hoy = Fechas.dia(Fechas.hoy())
        for (d in hoy..hoy + 45) {
            val iso = Fechas.iso(d)
            val (desde, hasta) = t.ventana(iso) ?: continue
            val eventos = listOfNotNull(desde to "inicio", sumar(hasta, 5) to "fin", t.horaCobro(iso)?.let { it to "cobro" })
                .sortedBy { Taller.minutos(it.first) }
            for ((hora, tipo) in eventos) {
                val cuando = momento(iso, hora)
                if (cuando > ahora) {
                    val pi = pendiente(ctx, tipo)
                    if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi)
                    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cuando, pi)
                    return
                }
            }
        }
    }

    private fun sumar(h: String, min: Int): String { val m = Taller.minutos(h) + min; return "%02d:%02d".format(m / 60, m % 60) }

    private fun momento(iso: String, hora: String): Long {
        val (y, mo, d) = iso.split("-").map { it.toInt() }
        val (hh, mm) = hora.split(":").map { it.toInt() }
        return Calendar.getInstance().apply { set(y, mo - 1, d, hh, mm, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    }

    /** Llega la alarma: muestra el aviso si corresponde. */
    fun alRecibir(ctx: Context, tipo: String?) {
        val a = Almacen(ctx)
        val S = a.estado ?: return
        val t = Taller.de(a.crudo)
        val hoy = Fechas.hoy()
        val turno = t.turno(hoy) ?: return
        val comida = if (turno == "M") "desayuno" else "merienda"
        if (tipo == "cobro") {
            val deben = S.deudasTallerDe(hoy)
            if (deben.isEmpty()) return
            val quienes = deben.joinToString(", ") { it.note.removePrefix("Taller · ") + " " + com.miplata.kotlin.ui.pesos(it.amt) }
            mostrar(ctx, "💸 Todavía te deben ${com.miplata.kotlin.ui.pesos(deben.sumOf { it.amt })}", "Del $comida de hoy: $quienes. Si ya te pagaron, tocá y marcalo.", abrirTaller = false)
            return
        }
        if (S.hayCompraTaller(hoy)) return            // ya lo anotaste: no molesta
        when (tipo) {
            "inicio" -> mostrar(ctx, if (turno == "M") "🥐 Hora del desayuno" else "☕ Hora de la merienda", "Tocá para tomar los pedidos de los muchachos.")
            "fin" -> mostrar(ctx, "¿Anotaste la compra del taller?", "Todavía no anotaste lo que pagaste del $comida de hoy. Tocá y poné \"Anotar y pagar con Mercado Pago\".")
        }
    }

    fun mostrar(ctx: Context, titulo: String, cuerpo: String, abrirTaller: Boolean = true) {
        if (!AvisoReserva.hayPermiso(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Compras del taller", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "Te avisa a la hora del desayuno y la merienda del taller" })
        val abrir = PendingIntent.getActivity(ctx, 12, Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(EXTRA_ABRIR, abrirTaller),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(7300, NotificationCompat.Builder(ctx, CANAL).setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(titulo).setContentText(cuerpo).setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setContentIntent(abrir).setAutoCancel(true).build())
    }
}
