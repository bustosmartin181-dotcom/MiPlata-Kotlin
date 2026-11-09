package com.miplata.kotlin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.datos.Almacen
import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.Movimiento
import com.miplata.kotlin.nucleo.texto
import com.miplata.kotlin.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val almacen = Almacen(applicationContext)
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        setContent { TemaMiPlata { App(almacen, version) } }
    }
}

enum class Pestana(val titulo: String, val icono: ImageVector) {
    HOY("Hoy", Icons.Outlined.Home), PLAN("Plan", Icons.Outlined.ListAlt),
    DIAS("Días", Icons.Outlined.CalendarMonth), AJUSTES("Ajustes", Icons.Outlined.Settings)
}

@Composable
fun App(almacen: Almacen, version: String) {
    val ctx = LocalContext.current
    val k = LocalColores.current
    var vuelta by remember { mutableIntStateOf(0) }          // sube cada vez que cambian los datos
    val estado = remember(vuelta) { almacen.estado }
    val nombre = remember(vuelta) { almacen.crudo?.get("nombre").texto()?.takeIf { it.isNotBlank() } ?: "" }
    var pestana by rememberSaveable { mutableStateOf(Pestana.HOY) }
    var hojaAbierta by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Movimiento?>(null) }
    fun aviso(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()

    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val texto = runCatching { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText() }.getOrNull()
            val error = if (texto == null) "No pude abrir el archivo." else almacen.importar(texto)
            if (error == null) { vuelta++; aviso("Datos recuperados") } else aviso(error)
        }
    }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(almacen.exportar()) }
        }.onSuccess { aviso("Copia guardada") }.onFailure { aviso("No pude guardar la copia") }
    }
    val abrirImportar = { importar.launch(arrayOf("application/json", "text/plain", "*/*")) }
    val abrirExportar = { exportar.launch("mi-plata-copia-${Fechas.hoy()}.json") }

    Scaffold(containerColor = k.fondo, bottomBar = {
        if (estado != null) BarraAbajo(pestana, { pestana = it }) { editando = null; hojaAbierta = true }
    }) { relleno ->
        Box(Modifier.padding(relleno).fillMaxSize()) {
            if (estado == null) Bienvenida(abrirImportar)
            else {
                val tocar: (Movimiento) -> Unit = { editando = it; hojaAbierta = true }
                when (pestana) {
                    Pestana.HOY -> PantallaHoy(estado, nombre.ifEmpty { "Martín" }, tocar)
                    Pestana.PLAN -> PantallaPlan(estado, almacen, { vuelta++ }, ::aviso)
                    Pestana.DIAS -> PantallaDias(estado, tocar)
                    Pestana.AJUSTES -> PantallaAjustes(version, abrirImportar, abrirExportar)
                }
            }
        }
    }

    if (hojaAbierta) HojaAnotar(
        editando = editando,
        alCerrar = { hojaAbierta = false },
        alGuardar = { m ->
            almacen.anotar(m.monto, m.nota, m.categoria, m.entrada, m.meLoDevuelven, m.fecha, m.editarId)
            hojaAbierta = false; vuelta++
            aviso(when { m.entrada -> "Entrada anotada: +${pesos(m.monto)}"; m.meLoDevuelven -> "Anotado en \"Me deben\""; else -> "Gasto anotado" })
        },
        alBorrar = { id -> almacen.borrarMovimiento(id); hojaAbierta = false; vuelta++; aviso("Borrado") },
        alMarcarDevuelto = { id -> almacen.marcarDevuelto(id); hojaAbierta = false; vuelta++; aviso("Marcado como devuelto") },
    )
}

@Composable
fun BarraAbajo(actual: Pestana, alElegir: (Pestana) -> Unit, alSumar: () -> Unit) {
    val k = LocalColores.current
    Row(Modifier.fillMaxWidth().background(k.tarjeta).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        @Composable fun Boton(p: Pestana) {
            val elegido = p == actual
            Column(Modifier.weight(1f).clickable { alElegir(p) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.background(if (elegido) k.verdeClaro else k.tarjeta, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Icon(p.icono, null, tint = if (elegido) k.verde else k.suave)
                }
                Text(p.titulo, fontSize = 12.sp, color = if (elegido) k.verde else k.suave, fontWeight = if (elegido) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        Boton(Pestana.HOY); Boton(Pestana.PLAN)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            FloatingActionButton(onClick = alSumar, shape = CircleShape, containerColor = k.verde, contentColor = k.sobreVerde,
                elevation = FloatingActionButtonDefaults.elevation(2.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = "Anotar gasto")
            }
        }
        Boton(Pestana.DIAS); Boton(Pestana.AJUSTES)
    }
}

@Composable
fun Bienvenida(alImportar: () -> Unit) {
    val k = LocalColores.current
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Mi plata K", fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, color = k.verde)
        Spacer(Modifier.height(10.dp))
        Text("Para empezar, traé tus datos desde una copia de seguridad (.json) de tu Mi plata de siempre.",
            textAlign = TextAlign.Center, color = k.suave)
        Spacer(Modifier.height(24.dp))
        Button(onClick = alImportar, Modifier.fillMaxWidth().height(52.dp)) { Text("Importar copia de seguridad", fontSize = 16.sp) }
    }
}
