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

/** Estilo visual de toda la app (Ajustes → Apariencia). Es aparte del color y del claro/oscuro.
    - "original": como era Mi plata antes del rediseño (bordes verdes, barra plana, botón + redondo).
    - "moderno": el rediseño (sombras suaves, barra con puntas redondeadas).
    - "oneui": al estilo Samsung One UI (títulos grandes, tarjetas bien redondeadas, barra flotante tipo píldora). */
object Estilo {
    val OPCIONES = listOf("original" to "Original", "moderno" to "Moderno", "oneui" to "One UI")
    var actual by mutableStateOf("moderno")
    fun cargar(ctx: android.content.Context) { actual = ctx.getSharedPreferences("apariencia", 0).getString("estilo", "moderno") ?: "moderno" }
    fun guardar(ctx: android.content.Context, e: String) { actual = e; ctx.getSharedPreferences("apariencia", 0).edit().putString("estilo", e).apply() }
    val original get() = actual == "original"
    val oneUi get() = actual == "oneui"
    /** Título de cada pantalla ("Ajustes", "Plan del período"…) */
    val tituloPantalla get() = when (actual) { "original" -> 22.sp; "oneui" -> 30.sp; else -> 24.sp }
    /** Espacio entre bloques */
    val separacion get() = when (actual) { "original" -> 14.dp; "oneui" -> 18.dp; else -> 16.dp }
    val separacionDias get() = when (actual) { "original" -> 8.dp; "oneui" -> 12.dp; else -> 14.dp }
    /** El cuadro grande de "Te queda para hoy" */
    val radioHero get() = when (actual) { "original" -> 26.dp; "oneui" -> 30.dp; else -> 28.dp }
    val rellenoHero get() = when (actual) { "original" -> 20.dp; else -> 24.dp }
}

/** El título grande de cada pantalla, según el estilo. */
@Composable
fun TituloPantalla(t: String, modifier: Modifier = Modifier) = Text(t, modifier, fontSize = Estilo.tituloPantalla, fontWeight = FontWeight.Bold,
    color = LocalColores.current.texto, letterSpacing = if (Estilo.oneUi) (-0.6).sp else 0.sp,
    lineHeight = if (Estilo.oneUi) 36.sp else androidx.compose.ui.unit.TextUnit.Unspecified)

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
    val k = LocalColores.current
    when (Estilo.actual) {
        "original" -> Column(modifier.fillMaxWidth().bordeBloque(22).background(color, RoundedCornerShape(22.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = contenido)
        "oneui" -> {
            val forma = RoundedCornerShape(26.dp)
            Column(modifier.fillMaxWidth().shadow(4.dp, forma, clip = false, ambientColor = Color(0x33192819), spotColor = Color(0x22192819))
                .clip(forma).background(color)
                .then(if (Bordes.activos) Modifier.border(1.dp, k.verde.copy(alpha = 0.30f), forma) else Modifier)
                .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = contenido)
        }
        else -> {
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
    }
}

@Composable
fun Renglon(izq: String, der: String, colorDer: Color = LocalColores.current.texto, negrita: Boolean = true) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (Estilo.original) Text(izq, color = LocalColores.current.texto, fontSize = 15.sp, modifier = Modifier.weight(1f))
        else Text(izq, color = LocalColores.current.suave, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(der, color = colorDer, fontSize = 15.sp, fontWeight = if (negrita) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun Titulo(t: String) = Text(
    t, fontSize = if (Estilo.oneUi) 19.sp else 18.sp, fontWeight = FontWeight.Bold,
    color = LocalColores.current.texto, lineHeight = 24.sp, letterSpacing = if (Estilo.oneUi) (-0.3).sp else 0.sp
)

@Composable
fun Subtitulo(t: String) = Text(
    t, fontSize = 13.sp, lineHeight = 19.sp,
    color = LocalColores.current.suave
)
