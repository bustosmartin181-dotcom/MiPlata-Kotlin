package com.miplata.kotlin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.*
import kotlinx.serialization.json.*

private val CATEGORIAS_INGRESO = listOf("Sueldo", "Trabajo independiente", "Efectivo", "Changas o trabajo extra", "Alquileres",
    "Jubilación o pensión", "Ventas o negocio", "Ayuda familiar", "Otros")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Hoja(alCerrar: () -> Unit, titulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    val k = LocalColores.current
    ModalBottomSheet(onDismissRequest = alCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = k.tarjeta) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = k.texto)
            contenido()
        }
    }
}

/** Editar un gasto importante. */
@Composable
fun HojaGasto(item: Item, esNuevo: Boolean, alCerrar: () -> Unit,
              alGuardar: (nombre: String, monto: Double, campos: Map<String, JsonElement?>) -> Unit, alQuitar: () -> Unit) {
    val k = LocalColores.current
    var nombre by remember { mutableStateOf(item.n) }
    var monto by remember { mutableStateOf(textoMonto(item.a)) }
    var pagado by remember { mutableStateOf(item.p) }
    var repite by remember { mutableStateOf(item.f != false) }
    var variable by remember { mutableStateOf(item.v == true) }
    var conCuotas by remember { mutableStateOf(item.q != null && item.q.total > 1) }
    var cuotaActual by remember { mutableStateOf((item.q?.actual ?: 1).toString()) }
    var cuotaTotal by remember { mutableStateOf((item.q?.total ?: 12).toString()) }
    var prox by remember { mutableStateOf(item.extra["prox"]?.let { if (it is JsonPrimitive && it.isString && it.content == "") "" else textoMonto(it.num()) } ?: "") }
    var alias by remember { mutableStateOf(item.extra["mp"].texto() ?: "") }
    var codigo by remember { mutableStateOf(item.extra["cod"].texto() ?: "") }
    var confirmarQuitar by remember { mutableStateOf(false) }

    Hoja(alCerrar, if (esNuevo) "Gasto importante nuevo" else "Gasto importante") {
        OutlinedTextField(nombre, { nombre = it }, Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true,
            placeholder = { Text("Ej: Luz") })
        CampoMonto(monto, { monto = it }, "Monto de este período")
        if (item.d != null) Text("Se descuenta desde el ${Fechas.diaYMes(item.d)}, repartido entre los días que faltan.", fontSize = 12.sp, color = k.suave)
        Interruptor("Ya lo pagué", pagado, { pagado = it })
        Interruptor("Se repite el mes que viene", repite, { repite = it; if (!it) variable = false })
        if (repite) {
            Interruptor("El monto cambia cada mes", variable, { variable = it }, "Ej: luz o gas: el próximo período arranca vacío")
            CampoMonto(prox, { prox = it }, "Monto para el próximo período (opcional)")
        }
        Interruptor("Es en cuotas", conCuotas, { conCuotas = it })
        if (conCuotas) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(cuotaActual, { cuotaActual = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Cuota") }, singleLine = true)
            OutlinedTextField(cuotaTotal, { cuotaTotal = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("de") }, singleLine = true)
        }
        OutlinedTextField(alias, { alias = it }, Modifier.fillMaxWidth(), label = { Text("Alias o CVU para transferir (opcional)") }, singleLine = true)
        OutlinedTextField(codigo, { codigo = it }, Modifier.fillMaxWidth(), label = { Text("Código de pago de la factura (opcional)") }, singleLine = true)
        Text("Al tocar \"Pagar en Mercado Pago\" se copia el alias (o el código) para que lo pegues en la app de MP.", fontSize = 12.sp, color = k.suave)
        Button(onClick = {
            val campos = mutableMapOf<String, JsonElement?>(
                "n" to JsonPrimitive(nombre.trim()), "p" to JsonPrimitive(pagado),
                "f" to JsonPrimitive(repite), "v" to JsonPrimitive(repite && variable),
                "prox" to if (repite && prox.isNotBlank()) num(aNumero(prox)) else null,
                "mp" to alias.trim().takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) },
                "cod" to codigo.trim().takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) },
            )
            val total = cuotaTotal.toIntOrNull() ?: 0
            campos["q"] = if (conCuotas && total > 1) {
                val actual = (cuotaActual.toIntOrNull() ?: 1).coerceIn(1, total)
                JsonObject(mapOf("actual" to JsonPrimitive(actual), "total" to JsonPrimitive(total)))
            } else null
            alGuardar(nombre.trim(), aNumero(monto), campos)
        }, Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar") }
        TextButton(onClick = { confirmarQuitar = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColors(contentColor = k.rojo)) {
            Text(if (esNuevo) "Descartar" else "Quitar este gasto")
        }
    }
    if (confirmarQuitar) AlertDialog(onDismissRequest = { confirmarQuitar = false },
        title = { Text("¿Quitar \"${nombre.ifBlank { "este gasto" }}\"?") },
        text = { Text("Se saca de la lista y la plata vuelve a tu disponible.") },
        confirmButton = { TextButton(onClick = { confirmarQuitar = false; alQuitar() }) { Text("Quitar") } },
        dismissButton = { TextButton(onClick = { confirmarQuitar = false }) { Text("Cancelar") } })
}

/** Editar los ingresos (el total pasa a ser el sueldo del período). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaIngresos(ingresos: List<Ingreso>, alCerrar: () -> Unit, alGuardar: (List<Pair<String, Double>>) -> Unit) {
    val k = LocalColores.current
    val filas = remember { mutableStateListOf(*ingresos.map { it.cat to textoMonto(it.a) }.ifEmpty { listOf("Sueldo" to "") }.toTypedArray()) }
    Hoja(alCerrar, "Ingresos del período") {
        Text("¿De dónde viene tu plata este período?", color = k.suave, fontSize = 14.sp)
        filas.forEachIndexed { i, (cat, monto) ->
            var abierto by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(abierto, { abierto = it }, Modifier.weight(1.2f)) {
                    OutlinedTextField(cat, {}, readOnly = true, singleLine = true, label = { Text("De dónde") },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable))
                    ExposedDropdownMenu(abierto, { abierto = false }) {
                        CATEGORIAS_INGRESO.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { filas[i] = c to monto; abierto = false }) }
                    }
                }
                CampoMonto(monto, { filas[i] = cat to it }, "Monto", Modifier.weight(1f))
                if (filas.size > 1) IconButton(onClick = { filas.removeAt(i) }) { Icon(Icons.Outlined.Close, "Quitar") }
            }
        }
        TextButton(onClick = { filas += "Otros" to "" }) { Text("+ Agregar otro ingreso") }
        Renglon("Total", pesos(filas.sumOf { aNumero(it.second) }))
        Button(onClick = { alGuardar(filas.map { it.first to aNumero(it.second) }) }, Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar") }
    }
}

/** Gastos que ya sabés que vas a tener en el período que viene. */
@Composable
fun HojaProximos(proximos: List<Proximo>, alCerrar: () -> Unit, alGuardar: (List<Pair<String, Double>>) -> Unit) {
    val k = LocalColores.current
    val filas = remember { mutableStateListOf(*proximos.map { it.n to textoMonto(it.a) }.toTypedArray()) }
    Hoja(alCerrar, "Para el próximo período") {
        Text("Anotá gastos que ya sabés que vas a tener cuando cobres. Al empezar el período nuevo pasan a tus gastos importantes.",
            color = k.suave, fontSize = 14.sp)
        filas.forEachIndexed { i, (n, a) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { filas[i] = it to a }, Modifier.weight(1.2f), label = { Text("Qué") }, singleLine = true)
                CampoMonto(a, { filas[i] = n to it }, "Monto", Modifier.weight(1f))
                IconButton(onClick = { filas.removeAt(i) }) { Icon(Icons.Outlined.Close, "Quitar") }
            }
        }
        TextButton(onClick = { filas += "" to "" }) { Text("+ Agregar") }
        Button(onClick = { alGuardar(filas.filter { it.first.isNotBlank() || aNumero(it.second) > 0 }.map { it.first.trim() to aNumero(it.second) }) },
            Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar") }
    }
}

/** Ajustar el período actual: fechas y saldo previo. */
@Composable
fun HojaPeriodoActual(S: Estado, alCerrar: () -> Unit, alGuardar: (inicio: String, fin: String, previo: Double) -> Unit) {
    val k = LocalColores.current
    var inicio by remember { mutableStateOf(S.start) }
    var fin by remember { mutableStateOf(S.end) }
    var previo by remember { mutableStateOf(textoMonto(S.previo)) }
    val valido = inicio.length == 10 && fin.length == 10 && Fechas.dia(fin) > Fechas.dia(inicio)
    Hoja(alCerrar, "Período actual") {
        CampoFecha("Día que cobrás (inicio)", inicio, { inicio = it })
        CampoFecha("Próximo cobro (fin)", fin, { fin = it })
        Text("El día de fin ya es del período siguiente (ese día cobrás de nuevo).", fontSize = 12.sp, color = k.suave)
        CampoMonto(previo, { previo = it }, "Saldo previo en cuenta (lo que te quedó del anterior)")
        if (!valido) Text("La fecha de fin tiene que ser después del inicio.", color = k.rojo, fontSize = 13.sp)
        Button(onClick = { alGuardar(inicio, fin, aNumero(previo)) }, enabled = valido, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar") }
    }
}

/** Empezar el período nuevo (cuando cobrás). */
@Composable
fun HojaPeriodoNuevo(S: Estado, c: Calculo, alCerrar: () -> Unit, alEmpezar: (previo: Double, inicio: String, fin: String) -> Unit) {
    val k = LocalColores.current
    var previo by remember { mutableStateOf(textoMonto(Math.max(0.0, Math.round(c.saldo - (if (c.fondo.activo) c.fondo.ahorroPeriodo else 0.0)).toDouble()))) }
    var inicio by remember { mutableStateOf(S.end) }
    var fin by remember { mutableStateOf(Fechas.mesSiguiente(S.end)) }
    var confirmar by remember { mutableStateOf(false) }
    val valido = Fechas.dia(fin) > Fechas.dia(inicio)
    Hoja(alCerrar, "Empezar período nuevo") {
        Text("Se guarda el período actual en el historial y empieza uno nuevo. Quedan tus ingresos y los gastos importantes que se repiten " +
            "(los de monto variable, vacíos). Las cuotas avanzan una. Lo que te deben pasa a \"Me deben\".", color = k.suave, fontSize = 14.sp)
        if (c.fondo.activo) Text("Lo que ahorraste en el fondo queda guardado aparte.", color = k.suave, fontSize = 14.sp)
        CampoMonto(previo, { previo = it }, "Saldo previo en cuenta")
        CampoFecha("Día que cobrás (inicio)", inicio, { inicio = it })
        CampoFecha("Próximo cobro (fin)", fin, { fin = it })
        if (!valido) Text("Revisá las fechas.", color = k.rojo, fontSize = 13.sp)
        Button(onClick = { confirmar = true }, enabled = valido, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Empezar período nuevo") }
    }
    if (confirmar) AlertDialog(onDismissRequest = { confirmar = false },
        title = { Text("¿Empezar el período nuevo?") },
        text = { Text("Te recomiendo guardar una copia antes (Ajustes → Guardar copia). Los gastos del día a día de este período pasan al historial.") },
        confirmButton = { TextButton(onClick = { confirmar = false; alEmpezar(aNumero(previo), inicio, fin) }) { Text("Empezar") } },
        dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } })
}
