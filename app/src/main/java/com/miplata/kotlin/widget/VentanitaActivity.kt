package com.miplata.kotlin.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.miplata.kotlin.aviso.AvisoReserva
import com.miplata.kotlin.datos.Almacen
import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.Nucleo
import com.miplata.kotlin.ui.HojaAnotar
import com.miplata.kotlin.ui.TemaMiPlata
import com.miplata.kotlin.ui.pesos
import kotlinx.coroutines.launch

/* La ventanita: se abre desde el widget, encima de la pantalla de inicio, sin abrir la app.
   Como la app es nativa, guarda directo en tus datos: no hay lista de espera. */
class VentanitaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val almacen = Almacen(applicationContext)
        val dictar = intent.getBooleanExtra("dictar", false)
        if (almacen.estado == null) { Toast.makeText(this, "Abrí Mi plata K e importá tu copia primero", Toast.LENGTH_LONG).show(); finish(); return }
        setContent {
            TemaMiPlata {
                HojaAnotar(editando = null, alCerrar = { finish() }, dictarAlAbrir = dictar,
                    alGuardar = { m ->
                        almacen.anotar(m.monto, m.nota, m.categoria, m.entrada, m.meLoDevuelven, m.fecha, null)
                        val c = Nucleo.calcular(almacen.estado!!, Fechas.hoy())
                        Toast.makeText(this, (if (m.entrada) "Entrada anotada" else "Anotado") + ". Te quedan ${pesos(c.quedaHoy)} hoy", Toast.LENGTH_LONG).show()
                        lifecycleScope.launch { Widget.actualizar(applicationContext); runCatching { AvisoReserva.programar(applicationContext) }; finish() }
                    },
                    alBorrar = {}, alMarcarDevuelto = {})
            }
        }
    }
}
