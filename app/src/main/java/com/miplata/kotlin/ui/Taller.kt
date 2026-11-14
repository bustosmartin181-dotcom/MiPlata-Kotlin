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
import androidx.compose.runtime.snapshots.SnapshotStateMap
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

/** Cartel arriba de Hoy en el horario del desayuno / la merienda. */
@Composable
fun CartelTaller(turno: String, t: Taller, fecha: String, alPedidos: () -> Unit) {
    val k = LocalColores.current
    val lista = t.listaCompra(fecha)
    val cuantos = t.pedidosDe(fecha).keys.size
    Row(Modifier.fillMaxWidth().border(1.5.dp, k.naranja, RoundedCornerShape(18.dp)).background(k.tarjeta, RoundedCornerShape(18.dp))
        .clickable(onClick = alPedidos).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (turno == "M") "🥐 Hora del desayuno" else "☕ Hora de la merienda", fontWeight = FontWeight.Bold, color = k.naranja)
            Text(if (lista.isEmpty()) "Tomá los pedidos de los muchachos" else "$cuantos pedidos · ${lista.values.sum()} cosas · ${pesos(t.pedidosDe(fecha).values.sumOf { t.costo(it) })}",
                fontSize = 13.sp, color = k.suave)
        }
        Button(onClick = alPedidos) { Text(if (lista.isEmpty()) "Pedidos" else "Ver") }
    }
}

/** El desayuno de hoy: 1) tomar los pedidos, 2) lista para comprar con tildes, 3) "Ya pagué". */
@Composable
fun HojaPedidos(t: Taller, fecha: String, alCerrar: () -> Unit, alGuardar: (Taller) -> Unit, alPagar: () -> Unit) {
    val k = LocalColores.current
    val dia = t.pedidosDe(fecha)
    val turno = t.turno(fecha)
    var abierto by remember { mutableStateOf<String?>(null) }
    var otraCosa by remember { mutableStateOf<String?>(null) }      // para quién se agrega una cosa nueva
    val gente = listOf("yo" to "Yo") + t.activos.map { it.id to it.mostrar }
    Hoja(alCerrar, (if (turno == "T") "☕ Merienda" else "🥐 Desayuno") + " · " + Fechas.diaYMes(fecha)) {
        Subtitulo("Tocá a cada uno y sumá lo que quiere. Abajo te queda la lista para el negocio.")
        gente.forEach { (id, nombre) ->
            val p = dia[id] ?: emptyMap()
            Column(Modifier.fillMaxWidth().bordeBloque(14).background(if (p.isNotEmpty()) k.verdeClaro else k.tarjeta, RoundedCornerShape(14.dp))
                .clickable { abierto = if (abierto == id) null else id }.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(nombre, fontWeight = FontWeight.SemiBold, color = k.texto, modifier = Modifier.weight(1f))
                    if (p.isNotEmpty()) Text(pesos(t.costo(p)), fontWeight = FontWeight.SemiBold, color = k.texto)
                }
                if (p.isNotEmpty()) Text(t.texto(p), fontSize = 13.sp, color = k.suave)
                if (abierto == id) {
                    Spacer(Modifier.height(6.dp))
                    t.precios.toSortedMap().forEach { (cosa, precio) ->
                        val n = p[cosa] ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(cosa, color = k.texto, fontSize = 14.sp); Text(pesos(precio), color = k.suave, fontSize = 12.sp)
                            }
                            IconButton(onClick = { alGuardar(t.conCantidad(fecha, id, cosa, n - 1)) }, enabled = n > 0) { Text("−", fontSize = 22.sp) }
                            Text("$n", fontWeight = FontWeight.Bold, color = k.texto, modifier = Modifier.width(24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            IconButton(onClick = { alGuardar(t.conCantidad(fecha, id, cosa, n + 1)) }) { Text("+", fontSize = 22.sp) }
                        }
                    }
                    TextButton(onClick = { otraCosa = id }) { Text("+ Otra cosa (con su precio)") }
                }
            }
        }
        val lista = t.listaCompra(fecha)
        if (lista.isNotEmpty()) {
            HorizontalDivider(color = k.linea)
            Text("🛒 Para comprar", fontWeight = FontWeight.Bold, color = k.texto, fontSize = 17.sp)
            val tildadas = t.tildes[fecha] ?: emptyList()
            lista.forEach { (cosa, n) ->
                val ok = cosa in tildadas
                Row(Modifier.fillMaxWidth().clickable {
                    alGuardar(t.copy(tildes = t.tildes + (fecha to if (ok) tildadas - cosa else tildadas + cosa))) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ok, null)
                    Text("$n  $cosa", color = if (ok) k.suave else k.texto, fontSize = 16.sp, modifier = Modifier.weight(1f),
                        textDecoration = if (ok) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                    Text(pesos((t.precios[cosa] ?: 0.0) * n), color = k.suave, fontSize = 14.sp)
                }
            }
            Renglon("Total estimado", pesos(dia.values.sumOf { t.costo(it) }))
            Button(onClick = alPagar, Modifier.fillMaxWidth().height(52.dp)) { Text("💳 Ya pagué: anotar", fontSize = 16.sp) }
            Subtitulo("Te muestra cuánto le toca a cada uno. Si pagaste un poco distinto, corregí el total y la diferencia va a tu parte.")
        }
    }
    otraCosa?.let { quien ->
        var nombre by remember { mutableStateOf("") }; var precio by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { otraCosa = null }, title = { Text("Otra cosa") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("¿Qué es?") }, placeholder = { Text("Ej: Medialunas") }, singleLine = true)
                CampoMonto(precio, { precio = it }, "Precio de una")
                Subtitulo("Queda guardada en la lista de precios para la próxima.")
            } },
            confirmButton = { TextButton(onClick = {
                val n = nombre.trim().replaceFirstChar { it.uppercase() }
                if (n.isNotEmpty() && aNumero(precio) > 0) {
                    alGuardar(t.copy(precios = t.precios + (n to aNumero(precio))).conCantidad(fecha, quien, n, (dia[quien]?.get(n) ?: 0) + 1)); otraCosa = null }
            }) { Text("Agregar") } },
            dismissButton = { TextButton(onClick = { otraCosa = null }) { Text("Cancelar") } })
    }
}

/** Ajustes → Compras del taller: muchachos, precios, horario y avisos. */
@Composable
fun HojaTaller(t: Taller, alCerrar: () -> Unit, alGuardar: (Taller) -> Unit, alImportar: () -> Unit, alFoto: () -> Unit,
               leyendoFoto: Boolean, alPermisoAvisos: () -> Unit, alPedidos: () -> Unit = {}, alProbarAviso: () -> Unit = {}) {
    val k = LocalColores.current
    var editando by remember { mutableStateOf<Muchacho?>(null) }
    var nuevo by remember { mutableStateOf(false) }
    var horario by remember { mutableStateOf(false) }
    Hoja(alCerrar, "🔧 Compras del taller") {
        Subtitulo("Cuando comprás para los muchachos, tu parte va como gasto tuyo y la de cada uno queda en \"Me deben\". Cuando te pagan, tocás \"Me lo pagaron\".")
        Button(onClick = alPedidos, Modifier.fillMaxWidth()) { Text("🥐 Abrir los pedidos de hoy") }
        Subtitulo("Se abre solo a la hora del desayuno o la merienda; con este botón lo abrís cuando quieras (por si compran fuera de horario).")
        Interruptor("Avisarme a la hora de comprar", t.aviso, { alGuardar(t.copy(aviso = it)); if (it) alPermisoAvisos() },
            "Al empezar el desayuno o la merienda, y otra vez al terminar si no anotaste nada")
        if (t.aviso) OutlinedButton(onClick = alProbarAviso, Modifier.fillMaxWidth()) { Text("Probar el aviso ahora") }
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
