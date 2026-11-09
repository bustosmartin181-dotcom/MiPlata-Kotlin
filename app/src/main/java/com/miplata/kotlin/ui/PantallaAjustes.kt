package com.miplata.kotlin.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
    val abrirPin: () -> Unit, val pinAlAbrir: (Boolean) -> Unit,
    val tema: (String) -> Unit, val color: (String) -> Unit,
    val copiaElegir: () -> Unit, val copiaDesactivar: () -> Unit, val copiaEstado: () -> String?,
    val copiarAlias: () -> Unit = {}, val abrirMP: () -> Unit = {},
    val borrarTodo: (pin: String) -> String? = { null },
    val ocultar: (Boolean) -> Unit = {}, val recordatorio: (Boolean) -> Unit = {}, val horaRecordatorio: (String) -> Unit = {},
    val excel: () -> Unit = {}, val pdf: () -> Unit = {}, val copias: List<String> = emptyList(), val restaurarCierre: (Int) -> Unit = {},
    val demo: (Boolean) -> Unit = {},
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
            Titulo("Apariencia")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("auto" to "Automático", "claro" to "Claro", "oscuro" to "Oscuro").forEachIndexed { i, (v, t) ->
                    SegmentedButton(S.tema == v, { a.tema(v) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(t, fontSize = 13.sp) }
                }
            }
            Text("Color", color = k.suave, fontSize = 13.sp)
            SelectorColor(S.color, a.color)
        }

        var eligiendoHoraRec by remember { mutableStateOf(false) }
        Tarjeta {
            Titulo("Recordatorio y privacidad")
            Interruptor("Recordarme anotar los gastos", S.recOn, a.recordatorio, "Una notificación cada día a la hora que elijas")
            if (S.recOn) OutlinedButton(onClick = { eligiendoHoraRec = true }, Modifier.fillMaxWidth()) { Text("Hora del recordatorio: ${S.recHora}") }
            Interruptor("Ocultar los montos al abrir", S.ocultar, a.ocultar, "Se ven como $ •••• hasta que tocás el ojito en Hoy")
        }
        if (eligiendoHoraRec) {
            val (h, m) = S.recHora.split(":").map { it.toIntOrNull() ?: 0 }
            val est = rememberTimePickerState(h, m, is24Hour = true)
            AlertDialog(onDismissRequest = { eligiendoHoraRec = false }, title = { Text("Hora del recordatorio") }, text = { TimePicker(est) },
                confirmButton = { TextButton(onClick = { a.horaRecordatorio("%02d:%02d".format(est.hour, est.minute)); eligiendoHoraRec = false }) { Text("Listo") } },
                dismissButton = { TextButton(onClick = { eligiendoHoraRec = false }) { Text("Cancelar") } })
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
            Titulo("Seguridad")
            Subtitulo(if (S.pin.isNotEmpty()) "Tenés un PIN puesto." else "Poné un PIN para que nadie vea tu plata si agarra el teléfono.")
            OutlinedButton(onClick = a.abrirPin, Modifier.fillMaxWidth()) { Text(if (S.pin.isNotEmpty()) "Cambiar o quitar PIN" else "Poner PIN") }
            if (S.pin.isNotEmpty()) Interruptor("Pedir el PIN al abrir la app", S.pinApp, a.pinAlAbrir)
        }

        Tarjeta {
            Titulo("Copia automática")
            val estadoCopia = a.copiaEstado()
            Subtitulo(estadoCopia ?: "Elegí un archivo una sola vez (en el teléfono o en Drive) y la app lo actualiza sola cada vez que anotás algo. Siempre el mismo archivo, con todo.")
            Button(onClick = a.copiaElegir, Modifier.fillMaxWidth()) { Text(if (estadoCopia == null) "Activar copia automática" else "Cambiar archivo") }
            if (estadoCopia != null) TextButton(onClick = a.copiaDesactivar) { Text("Desactivar") }
        }

        Tarjeta {
            Titulo("Copias de seguridad")
            Subtitulo("Tus datos se guardan solo en este teléfono. Hacé una copia cada tanto.")
            Button(onClick = a.exportar, Modifier.fillMaxWidth()) { Text("Guardar copia (.json)") }
            OutlinedButton(onClick = a.importar, Modifier.fillMaxWidth()) { Text("Recuperar desde una copia") }
            Text("Las copias son compatibles con tu Mi plata de siempre: podés pasar datos de una a otra.", fontSize = 12.sp, color = k.suave)
        }
        Tarjeta {
            Titulo("Exportar")
            Subtitulo("Resumen, día por día, gastos anotados y gastos importantes.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = a.excel, Modifier.weight(1f)) { Text("Excel") }
                OutlinedButton(onClick = a.pdf, Modifier.weight(1f)) { Text("PDF") }
            }
        }

        TarjetaCierres(a.copias, a.restaurarCierre)

        Tarjeta {
            Titulo("Modo demo")
            Subtitulo(if (S.demo) "Estás viendo datos de ejemplo. Tus datos reales están guardados aparte." else "Muestra datos de ejemplo para grabar un video o sacar capturas sin mostrar tu plata. Tus datos quedan guardados aparte.")
            if (S.demo) Button(onClick = { a.demo(false) }, Modifier.fillMaxWidth()) { Text("Salir del modo demo") }
            else OutlinedButton(onClick = { a.demo(true) }, Modifier.fillMaxWidth()) { Text("Activar modo demo") }
        }

        TarjetaNovedades()

        TarjetaColabora(a.copiarAlias, a.abrirMP)

        var borrando by remember { mutableStateOf(false) }
        var pinBorrar by remember { mutableStateOf("") }
        var errorBorrar by remember { mutableStateOf<String?>(null) }
        Tarjeta {
            Titulo("Borrar todo")
            Subtitulo("Deja la app vacía, como recién instalada. Antes guardá una copia por si la necesitás.")
            OutlinedButton(onClick = { borrando = true }, Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = k.rojo)) { Text("Borrar todos mis datos") }
        }
        if (borrando) AlertDialog(onDismissRequest = { borrando = false; pinBorrar = ""; errorBorrar = null },
            title = { Text("¿Borrar todo?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se borran tus gastos, el plan, el fondo y los ajustes de este teléfono. No se puede deshacer (salvo con una copia).")
                if (S.pin.isNotEmpty()) CampoPin(pinBorrar, { pinBorrar = it }, "Tu PIN")
                errorBorrar?.let { Text(it, color = k.rojo) }
            } },
            confirmButton = { TextButton(onClick = { errorBorrar = a.borrarTodo(pinBorrar); if (errorBorrar == null) borrando = false },
                colors = ButtonDefaults.textButtonColors(contentColor = k.rojo)) { Text("Borrar todo") } },
            dismissButton = { TextButton(onClick = { borrando = false; pinBorrar = ""; errorBorrar = null }) { Text("Cancelar") } })

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectorColor(actual: String, alElegir: (String) -> Unit) {
    val k = LocalColores.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PALETAS.forEach { (clave, p) ->
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                val borde = if (clave == actual) k.texto else androidx.compose.ui.graphics.Color.Transparent
                androidx.compose.foundation.Canvas(Modifier.size(44.dp)
                    .border(3.dp, borde, androidx.compose.foundation.shape.CircleShape).padding(5.dp)
                    .clickable { alElegir(clave) }) {
                    if (clave == "argentina") {
                        val cel = androidx.compose.ui.graphics.Color(0xFF74ACDF)
                        drawCircle(androidx.compose.ui.graphics.Color.White)
                        drawArc(cel, 180f + 30f, 120f, false)    // franja de arriba
                        drawArc(cel, 30f, 120f, false)            // franja de abajo
                        drawCircle(androidx.compose.ui.graphics.Color(0xFFF6B40E), size.minDimension * 0.14f)
                    } else drawCircle(if (k.oscuro) p.oscuro else p.claro)
                }
                Text(p.nombre, fontSize = 11.sp, color = k.suave)
            }
        }
    }
}
