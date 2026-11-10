package com.miplata.kotlin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.Fechas
import java.time.LocalDate
import java.time.ZoneOffset

/** Texto de un monto para un campo: 1500 -> "1500", 1500.5 -> "1500,5", 0 -> "". */
fun textoMonto(n: Double): String = if (n == 0.0) "" else if (n % 1.0 == 0.0) n.toLong().toString() else n.toString().replace('.', ',')

@Composable
fun CampoMonto(valor: String, alCambiar: (String) -> Unit, etiqueta: String, modifier: Modifier = Modifier.fillMaxWidth(), demoClave: String? = null) {
    val m = if (demoClave != null) modifier.demo(demoClave, escribir = alCambiar) else modifier
    OutlinedTextField(valor, { alCambiar(it.filter { c -> c.isDigit() || c == ',' || c == '.' }) }, m,
        label = { Text(etiqueta) }, prefix = { Text("$") }, singleLine = true,
        visualTransformation = if (demoClave != null) cursorDemo(demoClave) else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
}

@Composable
fun Interruptor(texto: String, valor: Boolean, alCambiar: (Boolean) -> Unit, detalle: String? = null) {
    val k = LocalColores.current
    Row(Modifier.fillMaxWidth().clickable { alCambiar(!valor) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(texto, color = k.texto)
            detalle?.let { Text(it, fontSize = 12.sp, color = k.suave) }
        }
        Switch(valor, alCambiar)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFecha(etiqueta: String, iso: String, alCambiar: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var abierto by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { abierto = true }, modifier) {
        Text("$etiqueta: ${if (iso.length >= 10) Fechas.diaYMes(iso) + "/" + iso.take(4) else "elegir"}")
    }
    if (abierto) {
        val inicial = runCatching { LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val estado = rememberDatePickerState(initialSelectedDateMillis = inicial)
        DatePickerDialog(onDismissRequest = { abierto = false },
            confirmButton = { TextButton(onClick = {
                estado.selectedDateMillis?.let { alCambiar(Fechas.iso(it / 86_400_000L)) }; abierto = false }) { Text("Listo") } },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar") } }) {
            DatePicker(estado)
        }
    }
}
