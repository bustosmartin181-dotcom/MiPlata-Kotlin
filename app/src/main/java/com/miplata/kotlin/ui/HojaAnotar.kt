package com.miplata.kotlin.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.Movimiento

/** Lo que se guarda al tocar "Guardar". */
data class NuevoMovimiento(
    val monto: Double, val nota: String, val categoria: String?, val entrada: Boolean,
    val meLoDevuelven: Boolean, val fecha: String, val editarId: Long?,
)

/** Saca monto y detalle de lo dictado: "2800 tortas", "dos mil quinientos en nafta" (Google suele dar números). */
fun interpretarDictado(t: String): Pair<String, String> {
    val r = Regex("""(\d[\d.,]*)\s*(mil)?""").find(t) ?: return "" to t.trim()
    var n = aNumero(r.groupValues[1])
    if (r.groupValues[2].isNotEmpty()) n *= 1000
    val resto = t.removeRange(r.range).replace(Regex("""\b(pesos|en|de)\b""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\s+"), " ").trim()
    val monto = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString().replace('.', ',')
    return monto to resto.replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HojaAnotar(
    editando: Movimiento?,
    alCerrar: () -> Unit,
    alGuardar: (NuevoMovimiento) -> Unit,
    alBorrar: (Long) -> Unit,
    alMarcarDevuelto: (Long) -> Unit,
) {
    val k = LocalColores.current
    val hoy = Fechas.hoy()
    val ayer = Fechas.iso(Fechas.dia(hoy) - 1)
    var entrada by remember { mutableStateOf((editando?.amt ?: 0.0) < 0) }
    var monto by remember { mutableStateOf(editando?.let { val v = Math.abs(it.amt); if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',') } ?: "") }
    var nota by remember { mutableStateOf(editando?.note ?: "") }
    var categoria by remember { mutableStateOf(editando?.cat) }
    var devuelven by remember { mutableStateOf(editando?.dev == 1 || editando?.dev == 2) }
    var fecha by remember { mutableStateOf(editando?.date ?: hoy) }
    var error by remember { mutableStateOf<String?>(null) }
    val foco = remember { FocusRequester() }
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dictar = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val texto = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@rememberLauncherForActivityResult
            val (m, n) = interpretarDictado(texto)
            if (m.isNotEmpty()) monto = m
            if (n.isNotEmpty()) nota = n
        }
    }
    LaunchedEffect(Unit) { if (editando == null) runCatching { foco.requestFocus() } }

    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = estado, containerColor = k.tarjeta) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !entrada, onClick = { entrada = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Gasto") }
                SegmentedButton(selected = entrada, onClick = { entrada = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Entrada") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = monto, onValueChange = { monto = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }; error = null },
                    modifier = Modifier.weight(1f).focusRequester(foco),
                    placeholder = { Text("$0", fontSize = 30.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    prefix = { Text("$", fontSize = 30.sp) },
                    textStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, color = k.texto),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    isError = error != null, supportingText = error?.let { { Text(it) } },
                    label = { Text("Monto") },
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = {
                    runCatching {
                        dictar.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Decí el monto y en qué, ej: 2800 tortas"))
                    }.onFailure { error = "Este teléfono no tiene dictado por voz." }
                }, modifier = Modifier.size(56.dp)) { Icon(Icons.Outlined.Mic, contentDescription = "Dictar por voz") }
            }
            OutlinedTextField(nota, { nota = it }, Modifier.fillMaxWidth(), label = { Text("Detalle") },
                placeholder = { Text(if (entrada) "Ej: me devolvió Orlando" else "Ej: tortas") }, singleLine = true)
            if (!entrada) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIAS.forEach { cat ->
                        FilterChip(selected = categoria == cat, onClick = { categoria = if (categoria == cat) null else cat }, label = { Text(cat) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(devuelven, { devuelven = it })
                    Column {
                        Text("Me lo devuelven", color = k.texto)
                        Text("Compré para otros: no te baja el límite del día", fontSize = 12.sp, color = k.suave)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Día:", color = k.suave)
                FilterChip(selected = fecha == hoy, onClick = { fecha = hoy }, label = { Text("Hoy") })
                FilterChip(selected = fecha == ayer, onClick = { fecha = ayer }, label = { Text("Ayer") })
                if (fecha != hoy && fecha != ayer) FilterChip(selected = true, onClick = {}, label = { Text(Fechas.diaYMes(fecha)) })
            }
            Button(onClick = {
                val n = aNumero(monto)
                if (n <= 0) { error = "Escribí un monto"; return@Button }
                alGuardar(NuevoMovimiento(n, nota.trim(), categoria, entrada, devuelven, fecha, editando?.id))
            }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text(if (editando != null) "Guardar cambios" else if (entrada) "Anotar entrada" else "Anotar gasto", fontSize = 17.sp)
            }
            if (editando != null) {
                if (editando.dev == 1) OutlinedButton(onClick = { alMarcarDevuelto(editando.id) }, Modifier.fillMaxWidth()) { Text("Ya me lo devolvieron") }
                TextButton(onClick = { alBorrar(editando.id) }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = k.rojo)) { Text("Borrar") }
            }
        }
    }
}
