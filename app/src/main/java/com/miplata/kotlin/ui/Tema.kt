package com.miplata.kotlin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/* Colores de Mi plata. "verde" es el color principal (el nombre quedó de la primera versión):
   con otro tema puede ser azul, violeta, etc. */
data class Colores(
    val fondo: Color, val tarjeta: Color, val texto: Color, val suave: Color, val linea: Color,
    val verde: Color, val sobreVerde: Color, val verdeClaro: Color, val naranja: Color, val rojo: Color,
    val argentina: Boolean = false, val oscuro: Boolean = false,
)

/** Un color de tema: [principal claro, principal oscuro, suave claro, suave oscuro]. */
data class Paleta(val nombre: String, val claro: Color, val oscuro: Color, val suaveClaro: Color, val suaveOscuro: Color)

val PALETAS = linkedMapOf(
    "verde" to Paleta("Verde", Color(0xFF1E6B47), Color(0xFF5CC994), Color(0xFFE3F1E9), Color(0xFF1F3A2B)),
    "azul" to Paleta("Azul", Color(0xFF0B5CAD), Color(0xFF6FB2F7), Color(0xFFE1EDFA), Color(0xFF14304F)),
    "violeta" to Paleta("Violeta", Color(0xFF6D3FC0), Color(0xFFB794F6), Color(0xFFEDE5FA), Color(0xFF2E1F4D)),
    "naranja" to Paleta("Naranja", Color(0xFFB4530A), Color(0xFFFB9A4F), Color(0xFFFBEADB), Color(0xFF45260E)),
    "rojo" to Paleta("Rojo", Color(0xFFB42318), Color(0xFFF68A80), Color(0xFFFBE4E2), Color(0xFF4A1A16)),
    "rosa" to Paleta("Rosa", Color(0xFFB0306A), Color(0xFFF58AB8), Color(0xFFFBE4EE), Color(0xFF4A1A30)),
    "grafito" to Paleta("Grafito", Color(0xFF3A3F45), Color(0xFFC5CBD2), Color(0xFFE6E8EB), Color(0xFF2A2E33)),
    "argentina" to Paleta("Argentina", Color(0xFF2C77B8), Color(0xFF7DBDEB), Color(0xFFDCEBF8), Color(0xFF173A5C)),
)

fun colores(color: String, oscuro: Boolean): Colores {
    val p = PALETAS[color] ?: PALETAS["verde"]!!
    val arg = color == "argentina"
    return if (!oscuro) Colores(
        fondo = if (arg) Color(0xFFF4F8FC) else Color(0xFFF3EFE8), tarjeta = Color.White, texto = Color(0xFF1F1D1A),
        suave = Color(0xFF5E5850), linea = Color(0xFFE6E0D6),
        verde = p.claro, sobreVerde = Color.White, verdeClaro = p.suaveClaro, naranja = Color(0xFFB4670E), rojo = Color(0xFFC0392B), argentina = arg)
    else Colores(
        fondo = if (arg) Color(0xFF0E1A26) else Color(0xFF131512), tarjeta = if (arg) Color(0xFF172636) else Color(0xFF1E211D),
        texto = Color(0xFFF0EEE9), suave = Color(0xFFB3ADA3), linea = Color(0xFF2E322C),
        verde = p.oscuro, sobreVerde = Color(0xFF101418), verdeClaro = p.suaveOscuro, naranja = Color(0xFFF0A54A), rojo = Color(0xFFFF7A6B),
        argentina = arg, oscuro = true)
}

val LocalColores = staticCompositionLocalOf { colores("verde", false) }
val Serif = FontFamily.Serif

/** tema: "auto", "claro" u "oscuro". color: una clave de PALETAS. */
@Composable
fun TemaMiPlata(color: String = "verde", tema: String = "auto", contenido: @Composable () -> Unit) {
    val oscuro = when (tema) { "oscuro" -> true; "claro" -> false; else -> isSystemInDarkTheme() }
    val c = colores(color, oscuro)
    val esquema = if (oscuro)
        darkColorScheme(primary = c.verde, onPrimary = c.sobreVerde, background = c.fondo, surface = c.tarjeta, onSurface = c.texto,
            surfaceContainerLow = c.tarjeta, surfaceContainerHigh = c.tarjeta, surfaceContainerHighest = c.linea, secondaryContainer = c.verdeClaro,
            onSurfaceVariant = c.suave, outline = c.suave)
    else lightColorScheme(primary = c.verde, onPrimary = c.sobreVerde, background = c.fondo, surface = c.tarjeta, onSurface = c.texto,
            surfaceContainerLow = c.tarjeta, surfaceContainerHigh = c.tarjeta, surfaceContainerHighest = c.linea, secondaryContainer = c.verdeClaro,
            onSurfaceVariant = c.suave, outline = c.suave)
    CompositionLocalProvider(LocalColores provides c) { MaterialTheme(colorScheme = esquema, content = contenido) }
}

/** Fondo del tema Argentina: franjas celeste, blanca y celeste, con el Sol de Mayo, bien suaves. */
@Composable
fun FondoArgentina() {
    val k = LocalColores.current
    if (!k.argentina) return
    val celeste = if (k.oscuro) Color(0xFF1C3E5E) else Color(0xFFCFE4F6)
    val sol = Color(0xFFF6B40E).copy(alpha = if (k.oscuro) 0.22f else 0.30f)
    Canvas(Modifier.fillMaxSize()) {
        val franja = size.height / 3
        drawRect(celeste, Offset.Zero, Size(size.width, franja))
        drawRect(celeste, Offset(0f, franja * 2), Size(size.width, franja))
        val centro = Offset(size.width / 2, size.height / 2)
        val r = minOf(size.width, franja) * 0.16f
        for (i in 0 until 32) rotate(i * 360f / 32, centro) {
            val largo = if (i % 2 == 0) r * 1.9f else r * 1.55f
            drawLine(sol, Offset(centro.x, centro.y - r * 1.05f), Offset(centro.x, centro.y - largo), strokeWidth = if (i % 2 == 0) r * 0.16f else r * 0.09f)
        }
        drawCircle(sol, r, centro)
    }
}

private val simbolos = DecimalFormatSymbols(Locale("es", "AR")).apply { groupingSeparator = '.'; decimalSeparator = ',' }
private val sinCentavos = DecimalFormat("#,##0", simbolos)
private val conCentavos = DecimalFormat("#,##0.00", simbolos)

/** $20.859 (redondeado, como la app original). */
fun pesos(n: Double): String = (if (n.roundToLong() < 0) "-" else "") + "$" + sinCentavos.format(abs(n.roundToLong()))
/** $20.859,07 */
fun pesosCentavos(n: Double): String = (if (n < 0) "-" else "") + "$" + conCentavos.format(abs(n))

/** Lee un monto escrito a mano: "3.500,50" -> 3500.5 (igual que la app original). */
fun aNumero(t: String): Double =
    t.replace(".", "").replace(",", ".").filter { it.isDigit() || it == '.' || it == '-' }.toDoubleOrNull() ?: 0.0
