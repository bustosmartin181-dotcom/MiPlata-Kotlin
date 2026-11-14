package com.miplata.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.*
import java.time.LocalDate
import java.time.LocalTime

/* ---------- Compras del taller dentro de Mi plata ---------- */

fun horaAhora(): String = LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }

/** Parte de la ventanita de anotar: quiénes entraron y cuánto le toca a cada uno. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartesTaller(t: Taller, partes: SnapshotStateMap<String, String>, total: Double) {
    val k = LocalColores.current
    val suma = t.activos.sumOf { aNumero(partes[it.id] ?: "") }
    Text("¿Quiénes entraron?", color = k.suave, fontSize = 13.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        t.activos.forEach { m ->
            FilterChip(selected = partes.containsKey(m.id), onClick = { if (partes.containsKey(m.id)) partes.remove(m.id) else partes[m.id] = "" },
                label = { Text(m.mostrar, fontSize = 13.sp) })
        }
    }
    t.activos.filter { partes.containsKey(it.id) }.forEach { m ->
        var menu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(m.mostrar, color = k.texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
            OutlinedTextField(partes[m.id] ?: "", { partes[m.id] = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                Modifier.width(120.dp), prefix = { Text("$") }, singleLine = true, placeholder = { Text("0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (t.precios.isNotEmpty()) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.Add, "Sumar algo de la lista") }
                DropdownMenu(menu, { menu = false }) {
                    t.precios.toSortedMap().forEach { (cosa, precio) ->
                        DropdownMenuItem(text = { Text("$cosa  ${pesos(precio)}") }, onClick = {
                            partes[m.id] = textoMonto(aNumero(partes[m.id] ?: "") + precio); menu = false })
                    }
                }
            }
        }
    }
    val mia = total - suma
    if (partes.isNotEmpty()) Column(Modifier.fillMaxWidth().background(if (mia < 0) k.rojo.copy(alpha = 0.12f) else k.verdeClaro, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Renglon("Lo de los muchachos (te lo deben)", dinero(suma), negrita = false)
        Renglon("Tu parte (gasto tuyo)", dinero(mia), colorDer = if (mia < 0) k.rojo else k.texto)
        if (mia < 0) Text("Lo de los muchachos suma más que el total. Revisá los montos.", color = k.rojo, fontSize = 12.sp)
    }
}

/** Cartel arriba de Hoy en el horario de compra. */
@Composable
fun CartelTaller(turno: String, alAnotar: () -> Unit) {
    val k = LocalColores.current
    Row(Modifier.fillMaxWidth().border(1.5.dp, k.naranja, RoundedCornerShape(18.dp)).background(k.tarjeta, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (turno == "M") "🥐 Hora del desayuno" else "☕ Hora de la merienda", fontWeight = FontWeight.Bold, color = k.naranja)
            Text("¿Qué compraste para los muchachos?", fontSize = 13.sp, color = k.suave)
        }
        Button(onClick = alAnotar) { Text("Anotar") }
    }
}

/** Ajustes → Compras del taller: muchachos, precios, horario y avisos. */
@Composable
fun HojaTaller(t: Taller, alCerrar: () -> Unit, alGuardar: (Taller) -> Unit, alImportar: () -> Unit, alFoto: () -> Unit,
               leyendoFoto: Boolean, alPermisoAvisos: () -> Unit) {
    val k = LocalColores.current
    var editando by remember { mutableStateOf<Muchacho?>(null) }
    var nuevo by remember { mutableStateOf(false) }
    var horario by remember { mutableStateOf(false) }
    Hoja(alCerrar, "🔧 Compras del taller") {
        Subtitulo("Cuando comprás para los muchachos, tu parte va como gasto tuyo y la de cada uno queda en \"Me deben\". Cuando te pagan, tocás \"Me lo pagaron\".")
        Interruptor("Avisarme a la hora de comprar", t.aviso, { alGuardar(t.copy(aviso = it)); if (it) alPermisoAvisos() },
            "Al empezar el desayuno o la merienda, y otra vez al terminar si no anotaste nada")
        OutlinedButton(onClick = { horario = true }, Modifier.fillMaxWidth()) {
            val hoy = Fechas.hoy()
            Text("🗓️ Mi horario" + when (t.turno(hoy)) { "M" -> " · hoy mañana"; "T" -> " · hoy tarde"; else -> if (t.horario.isEmpty()) " · sin cargar" else " · hoy franco" })
        }
        HorizontalDivider(color = k.linea)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Los muchachos (${t.activos.size})", fontWeight = FontWeight.SemiBold, color = k.texto, modifier = Modifier.weight(1f))
            TextButton(onClick = { nuevo = true }) { Text("+ Agregar") }
        }
        if (t.muchachos.isEmpty()) Subtitulo("Todavía no cargaste a nadie. Podés traerlos de tu copia de \"Compras del taller\".")
        t.muchachos.forEach { m ->
            Row(Modifier.fillMaxWidth().clickable { editando = m }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.mostrar, color = if (m.activo) k.texto else k.suave, modifier = Modifier.weight(1f))
                if (!m.activo) Text("no pide más", fontSize = 12.sp, color = k.suave)
            }
        }
        OutlinedButton(onClick = alImportar, Modifier.fillMaxWidth()) { Text("Traer de Compras del taller (.json)") }
        if (t.precios.isNotEmpty()) {
            HorizontalDivider(color = k.linea)
            Text("Precios guardados", fontWeight = FontWeight.SemiBold, color = k.texto)
            t.precios.toSortedMap().forEach { (cosa, p) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cosa, color = k.texto, modifier = Modifier.weight(1f)); Text(pesos(p), color = k.texto)
                    IconButton(onClick = { alGuardar(t.copy(precios = t.precios - cosa)) }) { Icon(Icons.Outlined.Close, "Quitar precio") }
                }
            }
        }
    }
    if (nuevo || editando != null) DialogoMuchacho(editando, alCerrar = { nuevo = false; editando = null }) { m, borrar ->
        val lista = t.muchachos.toMutableList()
        val i = lista.indexOfFirst { it.id == m.id }
        if (borrar) lista.removeAll { it.id == m.id } else if (i >= 0) lista[i] = m else lista += m
        alGuardar(t.copy(muchachos = lista)); nuevo = false; editando = null
    }
    if (horario) HojaHorario(t, { horario = false }, alGuardar, alFoto, leyendoFoto)
}

@Composable
private fun DialogoMuchacho(m: Muchacho?, alCerrar: () -> Unit, alListo: (Muchacho, Boolean) -> Unit) {
    var nombre by remember { mutableStateOf(m?.nombre ?: "") }
    var apodo by remember { mutableStateOf(m?.apodo ?: "") }
    var activo by remember { mutableStateOf(m?.activo ?: true) }
    AlertDialog(onDismissRequest = alCerrar, title = { Text(if (m == null) "Agregar" else "Editar") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true)
            OutlinedTextField(apodo, { apodo = it }, label = { Text("Apodo (opcional)") }, placeholder = { Text("Ej: Chuña") }, singleLine = true)
            if (m != null) Interruptor("Sigue pidiendo", activo, { activo = it })
            if (m != null) TextButton(onClick = { alListo(m, true) }, colors = ButtonDefaults.textButtonColors(contentColor = LocalColores.current.rojo)) { Text("Borrar") }
        } },
        confirmButton = { TextButton(onClick = { if (nombre.isNotBlank()) alListo(Muchacho(m?.id ?: "m${System.currentTimeMillis()}", nombre.trim(), apodo.trim(), activo), false) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } })
}

/** Mi horario: cada día del mes, mañana / tarde / franco. Se carga con una foto de la planilla o tocando cada día. */
@Composable
fun HojaHorario(t: Taller, alCerrar: () -> Unit, alGuardar: (Taller) -> Unit, alFoto: () -> Unit, leyendo: Boolean) {
    val k = LocalColores.current
    var mes by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    val nombresMes = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
    Hoja(alCerrar, "🗓️ Mi horario") {
        Subtitulo("Turno mañana: compra de ${t.manana.first} a ${t.manana.second}. Turno tarde: de ${t.tarde.first} a ${t.tarde.second}. En esos horarios la app se pone en modo Taller sola.")
        Button(onClick = alFoto, Modifier.fillMaxWidth(), enabled = !leyendo) {
            Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(8.dp))
            Text(if (leyendo) "Leyendo la planilla…" else "Sacar foto a la planilla del mes")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { mes = mes.minusMonths(1) }) { Text("◀") }
            Text("${nombresMes[mes.monthValue - 1].replaceFirstChar { it.uppercase() }} ${mes.year}", fontWeight = FontWeight.Bold, color = k.texto,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            TextButton(onClick = { mes = mes.plusMonths(1) }) { Text("▶") }
        }
        val hoy = Fechas.hoy()
        for (d in 1..mes.lengthOfMonth()) {
            val f = mes.withDayOfMonth(d); val iso = f.toString()
            val actual = t.horario[iso] ?: ""
            Row(Modifier.fillMaxWidth().then(if (iso == hoy) Modifier.background(k.verdeClaro, RoundedCornerShape(10.dp)) else Modifier).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("${Fechas.nombreDia(f.toEpochDay()).take(3)} $d" + (Fechas.feriado(iso)?.let { " 🇦🇷" } ?: ""), color = k.texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
                listOf("M" to "Mañana", "T" to "Tarde", "F" to "Franco").forEach { (v, txt) ->
                    FilterChip(selected = actual == v, onClick = {
                        alGuardar(t.copy(horario = if (actual == v) t.horario - iso else t.horario + (iso to v))) },
                        label = { Text(txt, fontSize = 12.sp) }, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
