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

/* Widget "Mi plata · Dictar gasto": lo que te queda hoy y un micrófono grande.
   Tocás el micrófono, decís "2800 negocio Telma" y se anota solo (con Deshacer por si entendió mal). */
class WidgetVoz : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(180.dp, 80.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = runCatching { leer(context) }.getOrNull()
        provideContent { Contenido(context, d) }
    }

    @Composable
    private fun Contenido(ctx: Context, d: Datos?) {
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
        val dictar = actionStartActivity(Intent(ctx, VentanitaActivity::class.java).putExtra("rapido", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))

        @Composable fun Saldo() {
            if (d == null) {
                Text("Mi plata", style = TextStyle(color = blanco, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                Text("Abrí la app primero", style = TextStyle(color = suave, fontSize = 12.sp))
            } else {
                Text("Te queda hoy", style = TextStyle(color = suave, fontSize = 13.sp))
                Text(if (d.oculto) "$ ••••" else pesos(d.queda), style = TextStyle(color = blanco, fontSize = 24.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
        @Composable fun Microfono(lado: Int) {
            Box(GlanceModifier.size(lado.dp).cornerRadius((lado / 2).dp).background(ColorProvider(Color.White, Color(0xFFE3F1E9)))
                .clickable(dictar), contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.ic_mic), contentDescription = "Dictar un gasto", modifier = GlanceModifier.size((lado * 0.55f).dp))
            }
        }

        if (alto) Column(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(fondo).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.clickable(abrirApp), horizontalAlignment = Alignment.CenterHorizontally) { Saldo() }
            if (d != null) {
                Spacer(GlanceModifier.height(8.dp))
                Microfono(60)
                Spacer(GlanceModifier.height(4.dp))
                Text("Dictá un gasto", style = TextStyle(color = suave, fontSize = 11.sp, textAlign = TextAlign.Center))
            }
        } else Row(GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(fondo).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(abrirApp)) { Saldo() }
            if (d != null) Microfono(56)
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
