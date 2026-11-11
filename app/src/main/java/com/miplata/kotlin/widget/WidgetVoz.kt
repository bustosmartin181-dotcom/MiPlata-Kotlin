package com.miplata.kotlin.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.miplata.kotlin.MainActivity
import com.miplata.kotlin.R
import com.miplata.kotlin.datos.Almacen
import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.ui.pesos

/* Widget "Mi plata · Dictar gasto": lo que te queda hoy y un micrófono.
   Tocás el micrófono y escucha AHÍ MISMO, sin abrir ventanas: el aro alrededor se ilumina con tu voz
   y lo que vas diciendo aparece abajo. Decís "2800 negocio Telma" y se anota solo (tocando el texto, Deshacer). */
class WidgetVoz : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(180.dp, 80.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            /* se lee adentro: así, mientras escucha, el widget se redibuja con cada cambio */
            val version = VozWidget.version.intValue
            val d = androidx.compose.runtime.remember(version) { runCatching { leer(context) }.getOrNull() }
            Contenido(context, d, VozWidget.fase.value, VozWidget.nivel.intValue, VozWidget.texto.value)
        }
    }

    @Composable
    private fun Contenido(ctx: Context, d: Datos?, fase: VozWidget.Fase, nivel: Int, texto: String) {
        val tam = LocalSize.current
        val alto = tam.height >= 110.dp && tam.width < 180.dp      // cuadradito: micrófono abajo
        val fondo = when (d?.clase) {
            "neg" -> ColorProvider(Color(0xFFC0392B), Color(0xFF8E2A20))
            "warn" -> ColorProvider(Color(0xFFB4670E), Color(0xFF8A4E0A))
            else -> (com.miplata.kotlin.ui.PALETAS[d?.color ?: "verde"] ?: com.miplata.kotlin.ui.PALETAS["verde"]!!).let { ColorProvider(it.claro, it.claro) }
        }
        val blanco = ColorProvider(Color.White, Color.White)
        val suave = ColorProvider(Color(0xDDFFFFFF), Color(0xDDFFFFFF))
        val abrirApp = actionStartActivity(Intent(ctx, MainActivity::class.java))
        val escuchando = fase == VozWidget.Fase.ESCUCHANDO || fase == VozWidget.Fase.PENSANDO
        val hayPermiso = androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        /* tocar el micrófono: escucha ahí mismo (o lo corta si ya está escuchando).
           Sin permiso todavía, abre la ventanita que lo pide. */
        val accionMic = when {
            !hayPermiso -> actionStartActivity(Intent(ctx, VentanitaActivity::class.java).putExtra("rapido", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            escuchando -> actionSendBroadcast(Intent(ctx, ReceptorVoz::class.java).setAction(ServicioVoz.PARAR))
            else -> actionStartService(Intent(ctx, ServicioVoz::class.java), isForegroundService = true)
        }
        val accionTexto = when (fase) {
            VozWidget.Fase.LISTO -> actionSendBroadcast(Intent(ctx, ReceptorVoz::class.java).setAction(ServicioVoz.DESHACER))
            VozWidget.Fase.SIN_MONTO -> actionStartActivity(Intent(ctx, VentanitaActivity::class.java)
                .putExtra("monto", VozWidget.sinMonto.first).putExtra("nota", VozWidget.sinMonto.second)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            else -> accionMic
        }

        @Composable fun Saldo() {
            if (d == null) {
                Text("Mi plata", style = TextStyle(color = blanco, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                Text("Abrí la app primero", style = TextStyle(color = suave, fontSize = 12.sp))
            } else {
                Text("Te queda hoy", style = TextStyle(color = suave, fontSize = 13.sp))
                Text(if (d.oculto) "$ ••••" else pesos(d.queda), style = TextStyle(color = blanco, fontSize = 24.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
        /* El micrófono con aro de luz: mientras escucha, el aro crece y brilla con tu voz (nivel 0 a 4) */
        @Composable fun Microfono(lado: Int) {
            val aro = lado + 8 + nivel * 7
            val luz = when {
                !escuchando -> Color.Transparent
                nivel >= 3 -> Color(0x99FFFFFF)
                nivel >= 1 -> Color(0x66FFFFFF)
                else -> Color(0x40FFFFFF)
            }
            val luz2 = if (escuchando) Color(0x2EFFFFFF) else Color.Transparent
            Box(GlanceModifier.size((lado + 8 + 4 * 7 + 10).dp), contentAlignment = Alignment.Center) {
                Box(GlanceModifier.size((aro + 10).dp).cornerRadius(((aro + 10) / 2).dp).background(ColorProvider(luz2, luz2))) {}
                Box(GlanceModifier.size(aro.dp).cornerRadius((aro / 2).dp).background(ColorProvider(luz, luz))) {}
                Box(GlanceModifier.size(lado.dp).cornerRadius((lado / 2).dp)
                    .background(if (escuchando) ColorProvider(Color(0xFFFFFFFF), Color(0xFFFFFFFF)) else ColorProvider(Color.White, Color(0xFFE3F1E9)))
                    .clickable(accionMic), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(R.drawable.ic_mic), contentDescription = if (escuchando) "Dejar de escuchar" else "Dictar un gasto",
                        modifier = GlanceModifier.size((lado * 0.55f).dp))
                }
            }
        }
        val etiqueta = when (fase) {
            VozWidget.Fase.NADA -> "Dictá un gasto"
            VozWidget.Fase.ESCUCHANDO -> texto.ifBlank { "Te escucho…" }
            VozWidget.Fase.PENSANDO -> texto.ifBlank { "Entendiendo…" }
            VozWidget.Fase.LISTO -> "$texto\nTocá acá para deshacer"
            else -> texto
        }
        val estiloEtiqueta = TextStyle(color = if (fase == VozWidget.Fase.NADA) suave else blanco, fontSize = if (fase == VozWidget.Fase.NADA) 11.sp else 12.sp,
            fontWeight = if (fase == VozWidget.Fase.NADA) FontWeight.Normal else FontWeight.Bold, textAlign = TextAlign.Center)

        if (alto) Column(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(fondo).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.clickable(abrirApp), horizontalAlignment = Alignment.CenterHorizontally) { Saldo() }
            if (d != null) {
                Microfono(56)
                Text(etiqueta, style = estiloEtiqueta, maxLines = 3, modifier = GlanceModifier.clickable(accionTexto))
            }
        } else Row(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(fondo).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(if (fase == VozWidget.Fase.NADA) abrirApp else accionTexto)) {
                if (fase == VozWidget.Fase.NADA) Saldo()
                else Text(etiqueta, style = estiloEtiqueta.copy(textAlign = TextAlign.Start), maxLines = 3)
            }
            if (d != null) Microfono(48)
        }
    }
}

class ReceptorWidgetVoz : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetVoz()
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { val a = Almacen(context); a.cambiarSiHace { com.miplata.kotlin.nucleo.Periodo.congelarDias(it, Fechas.hoy()) } }
        super.onReceive(context, intent)
        Widget.programarMedianoche(context)
    }
}
