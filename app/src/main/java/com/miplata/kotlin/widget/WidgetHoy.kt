package com.miplata.kotlin.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.miplata.kotlin.MainActivity
import com.miplata.kotlin.R
import com.miplata.kotlin.datos.Almacen
import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.Nucleo
import com.miplata.kotlin.ui.pesos
import java.util.Calendar

/* Widget "Mi plata K · Hoy": lo que te queda hoy, una barrita, mañana y el fondo,
   con un botón + y un micrófono que abren la ventanita para anotar sin abrir la app. */

private data class Datos(val queda: Double, val fraccion: Float, val manana: Double?, val fondo: Double?, val clase: String,
                         val gastoHoy: Double, val oculto: Boolean)

private fun leer(ctx: Context): Datos? {
    val S = Almacen(ctx).estado ?: return null
    val c = Nucleo.calcular(S, Fechas.hoy())
    val presupuesto = c.quedaHoy + c.gastoHoy
    return Datos(c.quedaHoy, if (presupuesto > 0) (c.quedaHoy / presupuesto).coerceIn(0.0, 1.0).toFloat() else 0f, c.manana,
        if (c.fondo.activo) c.fondo.total + (if (c.fondo.rendEnMP) c.fondo.rend else 0.0) else null,
        Nucleo.semaforo(c, S).first, c.gastoHoy, S.pin.isNotEmpty() && S.pinApp)
}

class WidgetHoy : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 80.dp), DpSize(250.dp, 110.dp), DpSize(300.dp, 160.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = runCatching { leer(context) }.getOrNull()
        provideContent { Contenido(context, d) }
    }

    @Composable
    private fun Contenido(ctx: Context, d: Datos?) {
        val tam = LocalSize.current
        val chico = tam.height < 110.dp
        val fondo = when (d?.clase) {
            "neg" -> ColorProvider(Color(0xFFC0392B), Color(0xFF8E2A20))
            "warn" -> ColorProvider(Color(0xFFB4670E), Color(0xFF8A4E0A))
            else -> ColorProvider(Color(0xFF1E6B47), Color(0xFF184F36))
        }
        val blanco = ColorProvider(Color.White, Color.White)
        val suave = ColorProvider(Color(0xDDFFFFFF), Color(0xDDFFFFFF))
        val abrirApp = actionStartActivity(Intent(ctx, MainActivity::class.java))
        Row(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(fondo).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(abrirApp)) {
                if (d == null) {
                    Text("Mi plata K", style = TextStyle(color = blanco, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                    Text("Abrí la app e importá tu copia", style = TextStyle(color = suave, fontSize = 13.sp))
                } else {
                    Text("Te queda hoy", style = TextStyle(color = suave, fontSize = 13.sp))
                    Text(if (d.oculto) "$ ••••" else pesos(d.queda), style = TextStyle(color = blanco, fontSize = if (chico) 24.sp else 30.sp, fontWeight = FontWeight.Bold))
                    if (!chico) {
                        Spacer(GlanceModifier.height(4.dp))
                        LinearProgressIndicator(progress = if (d.oculto) 0f else d.fraccion, modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                            color = blanco, backgroundColor = ColorProvider(Color(0x40FFFFFF), Color(0x40FFFFFF)))
                        Spacer(GlanceModifier.height(4.dp))
                        val partes = listOfNotNull(d.manana?.let { "Mañana ${if (d.oculto) "••••" else pesos(it)}" },
                            d.fondo?.let { "Fondo ${if (d.oculto) "••••" else pesos(it)}" })
                        if (partes.isNotEmpty()) Text(partes.joinToString("  ·  "), style = TextStyle(color = suave, fontSize = 12.sp), maxLines = 1)
                    }
                }
            }
            if (d != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Boton(ctx, R.drawable.ic_mas, "Anotar gasto", false)
                if (!chico) { Spacer(GlanceModifier.height(8.dp)); Boton(ctx, R.drawable.ic_mic, "Dictar gasto", true) }
            }
        }
    }

    @Composable
    private fun Boton(ctx: Context, icono: Int, descripcion: String, dictar: Boolean) {
        val intent = Intent(ctx, VentanitaActivity::class.java).putExtra("dictar", dictar)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        Box(GlanceModifier.size(48.dp).cornerRadius(24.dp).background(ColorProvider(Color.White, Color(0xFFE3F1E9)))
            .clickable(actionStartActivity(intent)), contentAlignment = Alignment.Center) {
            Image(ImageProvider(icono), contentDescription = descripcion, modifier = GlanceModifier.size(26.dp))
        }
    }
}

class ReceptorWidgetHoy : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetHoy()
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Widget.programarMedianoche(context)
    }
}

object Widget {
    /** Redibuja el widget con los datos de ahora. */
    suspend fun actualizar(ctx: Context) {
        runCatching { WidgetHoy().updateAll(ctx) }
        programarMedianoche(ctx)
    }

    /** A la medianoche cambia el día: el widget se actualiza solo. */
    fun programarMedianoche(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 2, Intent(ctx, ReceptorWidgetHoy::class.java)
            .setAction(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cuando = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 1); set(Calendar.SECOND, 0) }
        am.setAndAllowWhileIdle(AlarmManager.RTC, cuando.timeInMillis, pi)
    }
}
