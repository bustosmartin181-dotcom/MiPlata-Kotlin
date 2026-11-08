package com.miplata.kotlin.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/* Colores de Mi plata: fondo crema, tarjetas blancas, verde. Con modo oscuro. */
data class Colores(
    val fondo: Color, val tarjeta: Color, val texto: Color, val suave: Color, val linea: Color,
    val verde: Color, val sobreVerde: Color, val verdeClaro: Color, val naranja: Color, val rojo: Color,
)

val ColoresClaros = Colores(
    fondo = Color(0xFFF3EFE8), tarjeta = Color.White, texto = Color(0xFF1F1D1A), suave = Color(0xFF5E5850),
    linea = Color(0xFFE6E0D6), verde = Color(0xFF1E6B47), sobreVerde = Color.White, verdeClaro = Color(0xFFE3F1E9),
    naranja = Color(0xFFB4670E), rojo = Color(0xFFC0392B),
)
val ColoresOscuros = Colores(
    fondo = Color(0xFF131512), tarjeta = Color(0xFF1E211D), texto = Color(0xFFF0EEE9), suave = Color(0xFFB3ADA3),
    linea = Color(0xFF2E322C), verde = Color(0xFF5CC994), sobreVerde = Color(0xFF0E2A1B), verdeClaro = Color(0xFF1F3A2B),
    naranja = Color(0xFFF0A54A), rojo = Color(0xFFFF7A6B),
)

val LocalColores = staticCompositionLocalOf { ColoresClaros }
val Serif = FontFamily.Serif

@Composable
fun TemaMiPlata(contenido: @Composable () -> Unit) {
    val c = if (isSystemInDarkTheme()) ColoresOscuros else ColoresClaros
    val esquema = if (isSystemInDarkTheme())
        darkColorScheme(primary = c.verde, onPrimary = c.sobreVerde, background = c.fondo, surface = c.tarjeta, onSurface = c.texto,
            surfaceContainerLow = c.tarjeta, secondaryContainer = c.verdeClaro)
    else lightColorScheme(primary = c.verde, onPrimary = c.sobreVerde, background = c.fondo, surface = c.tarjeta, onSurface = c.texto,
            surfaceContainerLow = c.tarjeta, secondaryContainer = c.verdeClaro)
    androidx.compose.runtime.CompositionLocalProvider(LocalColores provides c) {
        MaterialTheme(colorScheme = esquema, content = contenido)
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
