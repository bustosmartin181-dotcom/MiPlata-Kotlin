package com.miplata.kotlin.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
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
import com.miplata.kotlin.nucleo.texto

/** Lo que se guarda al tocar "Guardar". */
data class NuevoMovimiento(
    val monto: Double, val nota: String, val categoria: String?, val entrada: Boolean,
    val meLoDevuelven: Boolean, val fecha: String, val editarId: Long?,
    /** Compra del taller: lo que le toca a cada muchacho (vacío = gasto común). */
    val partesTaller: List<Pair<com.miplata.kotlin.nucleo.Muchacho, Double>> = emptyList(),
    /** Qué llevó cada muchacho (id → "1 Yogur"), para verlo en Me deben. */
    val queLlevo: Map<String, String> = emptyMap(),
    /** "Anotar y pagar con Mercado Pago": después de anotar, copia el monto y abre Mercado Pago. */
    val pagarConMP: Boolean = false,
    /** Pagado (o recibido) en efectivo: mueve la cuenta Efectivo y no Mercado Pago. */
    val efectivo: Boolean = false,
)

/** Saca monto y detalle de lo dictado: "2800 negocio Telma", "2 mil 800 en el negocio de la Telma", "2.500 pesos en nafta".
    Google suele dar los números en cifras. Del detalle saca "pesos" y las palabras sueltas del principio (en, el, de…). */
fun interpretarDictado(t: String): Pair<String, String> {
    val r = Regex("""(\d[\d.,]*)(\s*mil(?:\s+(?:y\s+)?(\d{1,3})(?![\d.,]))?)?""", RegexOption.IGNORE_CASE).find(t) ?: return "" to limpiarDetalle(t)
    var n = aNumero(r.groupValues[1])
    if (r.groupValues[2].isNotBlank()) n = n * 1000 + (r.groupValues[3].toIntOrNull() ?: 0)
    val monto = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString().replace('.', ',')
    return monto to limpiarDetalle(t.removeRange(r.range))
}

private val SUELTAS = setOf("en", "el", "la", "los", "las", "de", "del", "por", "para", "al", "a", "y")
private fun limpiarDetalle(t: String): String {
    val palabras = t.replace("$", " ").replace(Regex("""\bpesos?\b""", RegexOption.IGNORE_CASE), " ")
        .trim(' ', '.', ',', ';', ':').split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
    while (palabras.isNotEmpty() && palabras.first().lowercase() in SUELTAS) palabras.removeAt(0)
    return palabras.joinToString(" ").replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HojaAnotar(
    editando: Movimiento?,
    alCerrar: () -> Unit,
    alGuardar: (NuevoMovimiento) -> Unit,
    alBorrar: (Long) -> Unit,
    alMarcarDevuelto: (Long) -> Unit,
    /** "Me lo pagaron" sobre algo que te deben: cuánto te entregó y cómo. */
    alCobrar: (Movimiento, Cobro) -> Unit = { m, _ -> alMarcarDevuelto(m.id) },
    /** Corregir cómo te pagó algo ya cobrado: cuánto te entregó, cuánto en efectivo, y si lo que sobra queda a su favor. */
    alCambiarCobro: (Movimiento, Cobro) -> Unit = { _, _ -> },
    dictarAlAbrir: Boolean = false,
    montoInicial: String = "",
    notaInicial: String = "",
    taller: com.miplata.kotlin.nucleo.Taller = com.miplata.kotlin.nucleo.Taller(),
    tallerAlAbrir: Boolean = false,
    partesIniciales: Map<String, String> = emptyMap(),
    queLlevo: Map<String, String> = emptyMap(),
) {
    val k = LocalColores.current
    val hoy = Fechas.hoy()
    val ayer = Fechas.iso(Fechas.dia(hoy) - 1)
    var entrada by remember { mutableStateOf((editando?.amt ?: 0.0) < 0) }
    var monto by remember { mutableStateOf(editando?.let { val v = Math.abs(it.amt); if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',') } ?: montoInicial) }
    var nota by remember { mutableStateOf(editando?.note ?: notaInicial) }
    var categoria by remember { mutableStateOf(editando?.cat) }
    var devuelven by remember { mutableStateOf(editando?.dev == 1 || editando?.dev == 2) }
    var fecha by remember { mutableStateOf(editando?.date ?: hoy) }
    var efectivo by remember { mutableStateOf(editando?.extra?.get("med").texto() == com.miplata.kotlin.datos.MEDIO_EFECTIVO) }
    var calendario by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val hayTaller = editando == null && taller.activos.isNotEmpty() && !Demo.corriendo
    var modoTaller by remember { mutableStateOf(hayTaller && tallerAlAbrir) }
    val partes = remember { mutableStateMapOf<String, String>().apply { putAll(partesIniciales) } }
    val foco = remember { FocusRequester() }
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var escuchando by remember { mutableStateOf(false) }
    fun lanzarDictado() { escuchando = true }
    LaunchedEffect(Unit) { if (Demo.corriendo) return@LaunchedEffect; if (dictarAlAbrir) lanzarDictado() else if (editando == null) runCatching { foco.requestFocus() } }
    var conMP = false
    var cobrando by remember { mutableStateOf(false) }
    if (cobrando && editando != null) {
        val quien = taller.muchachos.firstOrNull { it.id == editando.extra["tp"].texto() }
        DialogoCobro(quien?.let { it.apodo.ifBlank { it.nombre } } ?: "", editando.amt, alCerrar = { cobrando = false },
            puedeFavor = quien != null) { c -> cobrando = false; alCobrar(editando, c) }
    }
    fun listaTaller() = if (modoTaller && !entrada) taller.activos.filter { partes.containsKey(it.id) }.map { it to aNumero(partes[it.id] ?: "") }.filter { it.second > 0 } else emptyList()
    /** Revisa que esté todo para anotar; si falta algo, muestra el error y devuelve false. */
    fun validar(): Boolean {
        val n = aNumero(monto); val lista = listaTaller()
        error = when {
            n <= 0 -> if (modoTaller) "Escribí el total que pagaste" else "Escribí un monto"
            modoTaller && !entrada && lista.isEmpty() -> "Elegí quiénes entraron y cuánto le toca a cada uno"
            lista.sumOf { it.second } > n + 0.005 -> "Lo de los muchachos suma más que el total"
            else -> null
        }
        return error == null
    }
    val guardarClick: () -> Unit = {
        if (validar()) {
            val lista = listaTaller()
            alGuardar(NuevoMovimiento(aNumero(monto), nota.trim(), categoria, entrada, devuelven && lista.isEmpty(), fecha, editando?.id, lista, queLlevo, conMP && !efectivo, efectivo))
        }
        conMP = false
    }
    /* Anotar algo nuevo: primero la ventanita "¿Transferencia o efectivo?", así la plata sale (o entra) de la cuenta correcta */
    var preguntandoMedio by remember { mutableStateOf(false) }
    if (preguntandoMedio) {
        fun elegir(enEfectivo: Boolean, abrirMP: Boolean) {
            preguntandoMedio = false; efectivo = enEfectivo; conMP = abrirMP; guardarClick()
        }
        AlertDialog(onDismissRequest = { preguntandoMedio = false },
            title = { Text(if (entrada) "¿Cómo te entraron ${pesos(aNumero(monto))}?" else "¿Cómo pagaste ${pesos(aNumero(monto))}?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { elegir(false, !entrada) }, Modifier.fillMaxWidth().height(54.dp)) {
                    Text(if (entrada) "📲 Transferencia (a Mercado Pago)" else "📲 Transferencia · abre Mercado Pago", fontSize = 16.sp) }
                FilledTonalButton(onClick = { elegir(true, false) }, Modifier.fillMaxWidth().height(54.dp)) { Text("💵 Efectivo", fontSize = 16.sp) }
                if (!entrada) TextButton(onClick = { elegir(false, false) }, Modifier.fillMaxWidth()) {
                    Text("Ya lo pagué con Mercado Pago (no abrir)", fontSize = 13.sp) }
            } },
            confirmButton = {}, dismissButton = { TextButton(onClick = { preguntandoMedio = false }) { Text("Cancelar") } })
    }

    if (escuchando) androidx.compose.ui.window.Dialog(onDismissRequest = { escuchando = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Escuchador(alResultado = { texto ->
            val (m, n) = interpretarDictado(texto)
            if (m.isNotEmpty()) monto = m
            if (n.isNotEmpty()) nota = n
            escuchando = false
        }, alCancelar = { escuchando = false })
    }
    val contenido: @Composable () -> Unit = {
        /* Los botones quedan fijos abajo, siempre encima del teclado; lo de arriba se desplaza si no entra */
        Column(Modifier.fillMaxWidth().imePadding()) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !entrada, onClick = { entrada = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Gasto") }
                SegmentedButton(selected = entrada, onClick = { entrada = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Entrada") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = monto, onValueChange = { monto = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }; error = null },
                    modifier = Modifier.weight(1f).focusRequester(foco).demo("an.monto", escribir = { monto = it }),
                    visualTransformation = cursorDemo("an.monto"),
                    placeholder = { Text("0", fontSize = 30.sp) },
                    prefix = { Text("$", fontSize = 30.sp) },
                    textStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, color = k.texto),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    isError = error != null, supportingText = error?.let { { Text(it) } },
                    label = { Text(if (modoTaller && !entrada) "Total que pagaste" else "Monto") },
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = { lanzarDictado() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Outlined.Mic, contentDescription = "Dictar por voz") }
            }
            OutlinedTextField(nota, { nota = it }, Modifier.fillMaxWidth().demo("an.nota", escribir = { nota = it }), label = { Text("Detalle") }, visualTransformation = cursorDemo("an.nota"),
                placeholder = { Text(if (entrada) "Ej: me devolvió Orlando" else "Ej: tortas") }, singleLine = true)
            if (!entrada && hayTaller) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(modoTaller, { modoTaller = it; error = null })
                Column {
                    Text("🔧 Compra del taller", color = k.texto, fontWeight = FontWeight.SemiBold)
                    Text("Para los muchachos: cada uno queda en \"Me deben\"", fontSize = 12.sp, color = k.suave)
                }
            }
            if (!entrada && modoTaller) PartesTaller(taller, partes, aNumero(monto))
            if (!entrada && !modoTaller) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIAS.forEach { cat ->
                        FilterChip(selected = categoria == cat, onClick = { categoria = if (categoria == cat) null else cat }, label = { Text(cat) },
                            modifier = Modifier.demo("an.cat.$cat", accion = { categoria = cat }))
                    }
                }
                if (com.miplata.kotlin.Extras.on || devuelven) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(devuelven, { devuelven = it }, Modifier.demo("an.devuelven", accion = { devuelven = true }))
                    Column {
                        Text("Me lo devuelven", color = k.texto)
                        Text("Compré para otros: no te baja el límite del día", fontSize = 12.sp, color = k.suave)
                    }
                }
            }
            /* Con qué pagaste (o en qué te entró): así el efectivo no se mezcla con Mercado Pago */
            /* (al corregir algo que te deben no se muestra: eso se cobra con "Me lo pagaron") */
            /* (algo nuevo: se pregunta en la ventanita al tocar Anotar) */
            if (editando != null && editando.dev == 0 && com.miplata.kotlin.Edicion.personal) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (entrada) "Entró en:" else if (devuelven) "En el negocio pagué con:" else "Pagué con:", color = k.suave, modifier = Modifier.align(Alignment.CenterVertically))
                FilterChip(selected = !efectivo, onClick = { efectivo = false }, label = { Text("📲 Mercado Pago") })
                FilterChip(selected = efectivo, onClick = { efectivo = true }, label = { Text("💵 Efectivo") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Día:", color = k.suave)
                FilterChip(selected = fecha == hoy, onClick = { fecha = hoy }, label = { Text("Hoy") })
                FilterChip(selected = fecha == ayer, onClick = { fecha = ayer }, label = { Text("Ayer") })
                FilterChip(selected = fecha != hoy && fecha != ayer, onClick = { calendario = true },
                    label = { Text(if (fecha != hoy && fecha != ayer) "📅 " + Fechas.diaYMes(fecha) else "📅 Otro día") })
            }
            if (calendario) {
                val inicial = runCatching { java.time.LocalDate.parse(fecha).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
                val est = rememberDatePickerState(initialSelectedDateMillis = inicial)
                DatePickerDialog(onDismissRequest = { calendario = false },
                    confirmButton = { TextButton(onClick = { est.selectedDateMillis?.let { fecha = Fechas.iso(it / 86_400_000L) }; calendario = false }) { Text("Listo") } },
                    dismissButton = { TextButton(onClick = { calendario = false }) { Text("Cancelar") } }) { DatePicker(est) }
            }
        }
        Column(Modifier.fillMaxWidth().background(k.tarjeta).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            /* Un solo botón: todo gasto nuevo (también la compra del taller) se anota y abre Mercado Pago para pagarlo.
               Excepciones: entradas, cambios y la demo. */
            /* La versión pública no pregunta "¿Transferencia o efectivo?": anota y listo */
            val principal: () -> Unit = { if (editando == null && !Demo.corriendo && com.miplata.kotlin.Edicion.personal) { if (validar()) preguntandoMedio = true } else guardarClick() }
            Button(onClick = principal, modifier = Modifier.fillMaxWidth().height(54.dp).demo("an.guardar", guardarClick)) {
                Text(when { editando != null -> "Guardar cambios"; entrada -> "Anotar entrada"
                    modoTaller -> "Anotar compra del taller"; else -> "Anotar gasto" }, fontSize = 17.sp)
            }

            /* Ya cobrado: ¿cómo te pagó? (se puede corregir, y también parte y parte) */
            if (editando != null && editando.dev == 2 && editando.amt > 0) {
                val ef = com.miplata.kotlin.datos.MEDIO_EFECTIVO
                val cob = editando.extra["cobMed"].texto() ?: editando.extra["med"].texto()
                var total by remember { mutableStateOf(editando.amt) }
                var enEf by remember { mutableStateOf(if (cob == ef) editando.amt else 0.0) }
                Column(Modifier.fillMaxWidth().background(k.verdeClaro, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✅ ¿Cómo te pagó estos ${pesos(editando.amt)}?", fontWeight = FontWeight.Bold, color = k.texto)
                    if (editando.extra["cobMed"] == null) Text("Lo marcaste antes de que la app lo preguntara: elegí cómo fue.", fontSize = 12.sp, color = k.naranja)
                    ElegirComoPago(editando.amt, enEf) { t, e -> total = t; enEf = e }
                    val sobra = total - editando.amt
                    val esCompanero = editando.extra["tp"] != null
                    var aFavor by remember { mutableStateOf(true) }
                    var vueltoEf by remember { mutableStateOf<Boolean?>(null) }
                    val hayVuelto = total > 0 && sobra > 0.005 && !(esCompanero && aFavor)
                    when {
                        total <= 0 -> Text("Escribí cuánto te entregó", fontSize = 12.sp, color = k.naranja)
                        sobra > 0.005 && esCompanero -> {
                            Text("Sobran ${pesos(sobra)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = k.naranja)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = aFavor, onClick = { aFavor = true }, label = { Text("🤝 Quedan a su favor") })
                                FilterChip(selected = !aFavor, onClick = { aFavor = false }, label = { Text("🪙 Le di el vuelto") })
                            }
                            Text(if (aFavor) "Se le descuentan solos en lo próximo que pida." else "Le devolviste ${pesos(sobra)}.", fontSize = 12.sp, color = k.suave)
                            if (!aFavor) ElegirVuelto(sobra, vueltoEf) { vueltoEf = it }
                        }
                        sobra > 0.005 -> {
                            Text("Le diste ${pesos(sobra)} de vuelto", fontSize = 13.sp, color = k.naranja)
                            ElegirVuelto(sobra, vueltoEf) { vueltoEf = it }
                        }
                        sobra < -0.005 -> Text("Te queda debiendo ${pesos(-sobra)}: vuelve a \"Me deben\"", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = k.naranja)
                        else -> Text("✅ Te pagó justo", fontSize = 13.sp, color = k.verde)
                    }
                    OutlinedButton(onClick = { alCambiarCobro(editando, Cobro(total, enEf.coerceAtMost(total), esCompanero && aFavor,
                            vueltoEfectivo = if (hayVuelto) vueltoEf else null)) },
                        Modifier.fillMaxWidth(), enabled = total > 0 && (!hayVuelto || vueltoEf != null)) { Text("Guardar cómo me pagó") }
                }
            }
            if (editando != null) {
                if (editando.dev == 1 && editando.amt > 0) FilledTonalButton(onClick = { cobrando = true }, Modifier.fillMaxWidth().height(50.dp)) {
                    Text("💵 Me lo pagaron (¿cuánto me entregó?)", fontSize = 15.sp) }
                TextButton(onClick = { alBorrar(editando.id) }, Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = k.rojo)) { Text("Borrar") }
            }
        }
        }
    }
    if (Demo.corriendo) HojaEnLinea(contenido)
    else ModalBottomSheet(onDismissRequest = alCerrar, sheetState = estado, containerColor = k.tarjeta) { contenido() }
}
