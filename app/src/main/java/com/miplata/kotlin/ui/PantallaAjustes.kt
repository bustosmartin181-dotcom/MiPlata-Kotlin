package com.miplata.kotlin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.Estado

/** Lo que Ajustes necesita poder hacer. */
class AccionesAjustes(
    val importar: () -> Unit, val exportar: () -> Unit,
    val nombre: (String) -> Unit, val aviso: (Double) -> Unit,
    val fondo: (Boolean) -> Unit, val excesoAparte: (Boolean) -> Unit, val fondoPrev: (Double) -> Unit,
    val avisoReserva: (Boolean) -> Unit, val horaReserva: (String) -> Unit, val probarAviso: () -> Unit, val pasado: (Double) -> Unit,
    val rendDisp: (Boolean) -> Unit, val rendFondo: (Boolean) -> Unit, val tasa: (Double) -> Unit, val rendEnMP: (Boolean) -> Unit,
    val estadoAviso: () -> String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaAjustes(S: Estado, version: String, a: AccionesAjustes) {
    val k = LocalColores.current
    var nombre by remember(S.nombre) { mutableStateOf(S.nombre) }
    var avisoPct by remember(S.aviso) { mutableStateOf(((S.aviso ?: 20.0).toInt()).toString()) }
    var fondoPrev by remember(S.fondoPrev) { mutableStateOf(textoMonto(S.fondoPrev)) }
    var pasado by remember(S.resPasado) { mutableStateOf(textoMonto(S.resPasado)) }
    var tasa by remember(S.rendTasa) { mutableStateOf(textoMonto(S.rendTasa)) }
    var eligiendoHora by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 12.dp, 16.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Ajustes", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = k.texto)

        Tarjeta {
            Titulo("Vos")
            OutlinedTextField(nombre, { nombre = it }, Modifier.fillMaxWidth(), label = { Text("Tu nombre (para el saludo)") }, singleLine = true)
            OutlinedTextField(avisoPct, { avisoPct = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Avisarme (naranja) cuando me quede menos de este % del día") }, suffix = { Text("%") })
            Button(onClick = { a.nombre(nombre.trim()); a.aviso((avisoPct.toDoubleOrNull() ?: 20.0).coerceIn(0.0, 100.0)) }) { Text("Guardar") }
        }

        Tarjeta {
            Titulo("Fondo de ahorro")
            Subtitulo("Lo que no gastás cada día no pasa al día siguiente: se guarda en un fondo aparte. Cuenta desde el día que lo activás.")
            Interruptor("Activar el fondo de ahorro", S.modoFondo, a.fondo)
            if (S.modoFondo) {
                Interruptor("Si me paso, no descontarlo de ningún lado", S.fondoExceso == "aparte", a.excesoAparte,
                    "Apagado: lo que te pasás se descuenta del día siguiente")
                CampoMonto(fondoPrev, { fondoPrev = it }, "Fondo que ya tenías juntado de antes")
                CampoMonto(pasado, { pasado = it }, "Ya pasé a la reserva (en total)")
                Button(onClick = { a.fondoPrev(aNumero(fondoPrev)); a.pasado(aNumero(pasado)) }) { Text("Guardar montos") }
                HorizontalDivider(color = k.linea)
                Interruptor("Avisarme cada noche cuánto pasar a la reserva", S.resAviso, a.avisoReserva)
                if (S.resAviso) {
                    OutlinedButton(onClick = { eligiendoHora = true }, Modifier.fillMaxWidth()) { Text("Hora del aviso: ${S.resHora}") }
                    Text(a.estadoAviso(), fontSize = 13.sp, color = k.suave)
                    TextButton(onClick = a.probarAviso) { Text("Probar aviso ahora") }
                }
            }
        }

        Tarjeta {
            Titulo("Rendimiento de Mercado Pago")
            Subtitulo("Tu plata va sumando lo que rendiría con la tasa que pongas. Se acredita en días hábiles (lo del fin de semana llega el lunes) y respeta los feriados. Es una estimación.")
            Interruptor("Sumar rendimiento a mi dinero disponible", S.rendDispOn, a.rendDisp)
            if (S.modoFondo) Interruptor("Sumar rendimiento a mi fondo de ahorro", S.rendOn, a.rendFondo)
            if (S.rendDispOn || S.rendOn) {
                OutlinedTextField(tasa, { tasa = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Tasa anual (TNA)") }, suffix = { Text("%") })
                Button(onClick = { a.tasa(aNumero(tasa).coerceIn(0.0, 300.0)) }) { Text("Guardar tasa") }
            }
            if (S.rendOn && S.modoFondo) Interruptor("Mi fondo ya está en Mercado Pago", S.rendEnMP, a.rendEnMP,
                "Así el rendimiento del fondo cuenta al comparar con tus cuentas")
        }

        Tarjeta {
            Titulo("Copias de seguridad")
            Subtitulo("Tus datos se guardan solo en este teléfono. Hacé una copia cada tanto.")
            Button(onClick = a.exportar, Modifier.fillMaxWidth()) { Text("Guardar copia (.json)") }
            OutlinedButton(onClick = a.importar, Modifier.fillMaxWidth()) { Text("Recuperar desde una copia") }
            Text("Las copias son compatibles con tu Mi plata de siempre: podés pasar datos de una a otra.", fontSize = 12.sp, color = k.suave)
        }
        Tarjeta {
            Titulo("Acerca de")
            Renglon("Versión", version, negrita = false)
            Renglon("Motor de cálculo", "igual a Mi plata v9.9.36", negrita = false)
        }
    }

    if (eligiendoHora) {
        val (h, m) = S.resHora.split(":").map { it.toIntOrNull() ?: 0 }
        val estado = rememberTimePickerState(h, m, is24Hour = true)
        AlertDialog(onDismissRequest = { eligiendoHora = false }, title = { Text("Hora del aviso") },
            text = { TimePicker(estado) },
            confirmButton = { TextButton(onClick = { a.horaReserva("%02d:%02d".format(estado.hour, estado.minute)); eligiendoHora = false }) { Text("Listo") } },
            dismissButton = { TextButton(onClick = { eligiendoHora = false }) { Text("Cancelar") } })
    }
}
