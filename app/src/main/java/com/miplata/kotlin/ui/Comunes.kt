package com.miplata.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Línea fina del color del tema en el borde de cada bloque, para ver bien dónde empieza y termina
    (se apaga en Ajustes → Apariencia). */
object Bordes {
    var activos by mutableStateOf(true)
    fun cargar(ctx: android.content.Context) { activos = ctx.getSharedPreferences("apariencia", 0).getBoolean("bordes", true) }
    fun guardar(ctx: android.content.Context, on: Boolean) { activos = on; ctx.getSharedPreferences("apariencia", 0).edit().putBoolean("bordes", on).apply() }
}

@Composable
fun Modifier.bordeBloque(radio: Int = 22): Modifier =
    if (Bordes.activos) this.border(1.5.dp, LocalColores.current.verde.copy(alpha = 0.45f), RoundedCornerShape(radio.dp)) else this

/** Tarjeta base: sombra muy suave, borde del color del tema y radios consistentes en toda la app. */
@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    color: Color = LocalColores.current.tarjeta,
    contenido: @Composable ColumnScope.() -> Unit
) {
    val forma = RoundedCornerShape(24.dp)
    val base = modifier
        .fillMaxWidth()
        .shadow(2.dp, forma, clip = false)
        .clip(forma)
        .background(color)
    Column(
        base.then(if (Bordes.activos) Modifier.border(1.5.dp, LocalColores.current.verde.copy(alpha = 0.45f), forma) else Modifier)
            .padding(horizontal = 20.dp, vertical = 19.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = contenido
    )
}

@Composable
fun Renglon(izq: String, der: String, colorDer: Color = LocalColores.current.texto, negrita: Boolean = true) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(izq, color = LocalColores.current.suave, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(der, color = colorDer, fontSize = 15.sp, fontWeight = if (negrita) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun Titulo(t: String) = Text(
    t, fontSize = 18.sp, fontWeight = FontWeight.Bold,
    color = LocalColores.current.texto, lineHeight = 24.sp
)

@Composable
fun Subtitulo(t: String) = Text(
    t, fontSize = 13.sp, lineHeight = 19.sp,
    color = LocalColores.current.suave
)
