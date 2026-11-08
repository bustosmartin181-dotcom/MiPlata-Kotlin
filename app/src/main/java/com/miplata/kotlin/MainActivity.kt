package com.miplata.kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.text.NumberFormat
import java.util.Locale

/* Etapa 1: pantalla de verificación. Importás tu copia .json de Mi plata y la app nueva
   muestra los números calculados con el motor Kotlin, para compararlos con la app vieja.
   Las pantallas lindas vienen en la etapa siguiente. */

private val formato = NumberFormat.getIntegerInstance(Locale("es", "AR"))
fun pesos(n: Double): String = (if (Math.round(n) < 0) "-" else "") + "$" + formato.format(Math.abs(Math.round(n)))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val oscuro = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (oscuro) darkColorScheme(primary = Color(0xFF4CC38A)) else lightColorScheme(primary = Color(0xFF1E7F4F))) {
                Surface(Modifier.fillMaxSize()) { Pantalla() }
            }
        }
    }
}

@Composable
fun Pantalla() {
    val ctx = LocalContext.current
    val archivo = remember { File(ctx.filesDir, "estado.json") }
    var texto by remember { mutableStateOf(if (archivo.exists()) archivo.readText() else null) }
    var error by remember { mutableStateOf<String?>(null) }
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val t = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
            Estado.desdeJson(Json.parseToJsonElement(t).jsonObject)   // valida antes de guardar
            archivo.writeText(t); texto = t; error = null
        }.onFailure { error = "No pude leer esa copia: ${it.message}" }
    }
    val estado = remember(texto) { texto?.let { runCatching { Estado.desdeJson(Json.parseToJsonElement(it).jsonObject) }.getOrNull() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).systemBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Mi plata K", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Etapa 1 · motor de cálculo en Kotlin", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (estado == null) {
            Text("Importá tu copia de seguridad (.json) de Mi plata para ver tus números calculados por la app nueva.")
        } else {
            val hoy = Fechas.hoy()
            val c = Nucleo.calcular(estado, hoy)
            val (clase, _) = Nucleo.semaforo(c, estado)
            val color = when (clase) { "neg" -> Color(0xFFD64545); "warn" -> Color(0xFFE09B2D); else -> MaterialTheme.colorScheme.primary }
            Tarjeta {
                Text("Te queda para hoy", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(pesos(c.quedaHoy), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = color)
                Fila("Gastado hoy", pesos(c.gastoHoy))
                c.manana?.let { Fila("Mañana tendrías", pesos(it)) }
            }
            Tarjeta {
                Fila("Saldo del período", pesos(c.saldo))
                Fila("Días que faltan (con hoy)", c.faltan.toString())
                Fila("Base por día", pesos(c.baseHoy))
                if (c.fondo.activo) {
                    Fila("Disponible para gastar", pesos(c.fondo.disponible))
                    Fila("Fondo de ahorro", pesos(c.fondo.total + if (c.fondo.rendEnMP) c.fondo.rend else 0.0))
                }
            }
            val r = Nucleo.rachaEnVerde(estado, hoy)
            Tarjeta { Fila("🔥 Racha en verde", "${r.racha} días") }
        }
        error?.let { Text(it, color = Color(0xFFD64545)) }
        Button(onClick = { elegir.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.fillMaxWidth()) {
            Text(if (estado == null) "Importar copia de seguridad" else "Importar otra copia")
        }
    }
}

@Composable
fun Tarjeta(contenido: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = contenido) }
}

@Composable
fun Fila(izq: String, der: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(izq); Text(der, fontWeight = FontWeight.Bold)
    }
}
