package com.miplata.kotlin.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.miplata.kotlin.aviso.AvisoReserva
import com.miplata.kotlin.datos.Almacen
import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.Movimiento
import com.miplata.kotlin.nucleo.Nucleo
import com.miplata.kotlin.ui.*
import kotlinx.coroutines.launch

/* La ventanita: se abre desde el widget, encima de la pantalla de inicio, sin abrir la app.
   Como la app es nativa, guarda directo en tus datos: no hay lista de espera.
   Con "rapido" (widget del micrófono): escucha, entiende "2800 negocio Telma" y lo anota solo. */
class VentanitaActivity : ComponentActivity() {
    private lateinit var almacen: Almacen

    /** Después de guardar o borrar: widgets, aviso de la noche y copia automática al día. */
    private fun alDia(cerrar: Boolean) = lifecycleScope.launch {
        Widget.actualizar(applicationContext); runCatching { AvisoReserva.programar(applicationContext) }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.miplata.kotlin.datos.CopiaAutomatica.guardar(applicationContext) }
        if (cerrar) finish()
    }
    private fun quedaHoy() = Nucleo.calcular(almacen.estado!!, Fechas.hoy()).quedaHoy

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        almacen = Almacen(applicationContext)
        val dictar = intent.getBooleanExtra("dictar", false)
        val rapido = intent.getBooleanExtra("rapido", false)
        if (almacen.estado == null) { Toast.makeText(this, "Abrí Mi plata e importá tu copia primero", Toast.LENGTH_LONG).show(); finish(); return }
        setContent {
            val e = almacen.estado!!
            TemaMiPlata(e.color, e.tema) {
                if (rapido) DictadoRapido()
                else Hoja(null, dictar = dictar, monto = intent.getStringExtra("monto") ?: "", nota = intent.getStringExtra("nota") ?: "")
            }
        }
    }

    @Composable
    private fun Hoja(editando: Movimiento?, dictar: Boolean = false, monto: String = "", nota: String = "") {
        HojaAnotar(editando = editando, alCerrar = { finish() }, dictarAlAbrir = dictar, montoInicial = monto, notaInicial = nota,
            alGuardar = { m ->
                almacen.anotar(m.monto, m.nota, m.categoria, m.entrada, m.meLoDevuelven, m.fecha, m.editarId)
                Toast.makeText(this, (if (m.entrada) "Entrada anotada" else if (m.editarId != null) "Cambiado" else "Anotado") +
                    ". Te quedan ${pesos(quedaHoy())} hoy", Toast.LENGTH_LONG).show()
                alDia(cerrar = true)
            },
            alBorrar = { id -> almacen.borrarMovimiento(id); Toast.makeText(this, "Borrado", Toast.LENGTH_SHORT).show(); alDia(cerrar = true) },
            alMarcarDevuelto = {})
    }

    private sealed interface Paso {
        data object Escuchando : Paso
        data class Anotado(val id: Long, val monto: Double, val nota: String, val queda: Double) : Paso
        data class AMano(val monto: String, val nota: String, val error: String?) : Paso
        data class Editar(val m: Movimiento) : Paso
    }

    @Composable
    private fun DictadoRapido() {
        var paso by remember { mutableStateOf<Paso>(Paso.Escuchando) }
        fun entendido(texto: String) {
            val (m, n) = interpretarDictado(texto)
            val monto = aNumero(m)
            if (monto <= 0) { paso = Paso.AMano("", n.ifBlank { texto }, "No entendí el monto. Escribilo y tocá Anotar."); return }
            almacen.anotar(monto, n, null, false, false)
            val id = almacen.estado?.spent?.maxByOrNull { it.id }?.id ?: 0L
            paso = Paso.Anotado(id, monto, n, quedaHoy())
            alDia(cerrar = false)
        }
        when (val p = paso) {
            Paso.Escuchando -> Escuchador(alResultado = ::entendido, alCancelar = { finish() })
            is Paso.Anotado -> Anotado(p,
                alDeshacer = { almacen.borrarMovimiento(p.id); Toast.makeText(this, "Listo, no se anotó", Toast.LENGTH_SHORT).show(); alDia(cerrar = true) },
                alEditar = { almacen.estado?.spent?.find { it.id == p.id }?.let { paso = Paso.Editar(it) } ?: finish() },
                alTerminar = { finish() })
            is Paso.AMano -> {
                LaunchedEffect(p) { p.error?.let { Toast.makeText(this@VentanitaActivity, it, Toast.LENGTH_LONG).show() } }
                Hoja(null, monto = p.monto, nota = p.nota)
            }
            is Paso.Editar -> Hoja(p.m)
        }
    }

    /** Cartelito de confirmación: se cierra solo en 5 segundos. Deshacer por si entendió mal. */
    @Composable
    private fun Anotado(p: Paso.Anotado, alDeshacer: () -> Unit, alEditar: () -> Unit, alTerminar: () -> Unit) {
        val k = LocalColores.current
        val tiempo = remember { Animatable(1f) }
        var tocado by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { tiempo.animateTo(0f, tween(5000, easing = LinearEasing)); if (!tocado) alTerminar() }
        Box(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().background(k.tarjeta, RoundedCornerShape(24.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("✅ Anotado: ${pesos(p.monto)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = k.texto)
                if (p.nota.isNotBlank()) Text(p.nota, fontSize = 16.sp, color = k.texto)
                Text("Te quedan ${pesos(p.queda)} hoy", fontSize = 14.sp, color = if (p.queda >= 0) k.verde else k.rojo, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { tiempo.value }, modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = k.verde, trackColor = k.linea, gapSize = 0.dp, drawStopIndicator = {})
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { tocado = true; alDeshacer() }, Modifier.weight(1f)) { Text("Deshacer") }
                    OutlinedButton(onClick = { tocado = true; alEditar() }, Modifier.weight(1f)) { Text("Editar") }
                    Button(onClick = { tocado = true; alTerminar() }, Modifier.weight(1f)) { Text("Listo") }
                }
            }
        }
    }
}
