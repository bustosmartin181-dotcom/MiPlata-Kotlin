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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
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
            val deHoy = t.menuDe(t.turno(Fechas.hoy()))          // solo lo del turno de hoy (mañana o tarde)
            if (deHoy.isNotEmpty()) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.Add, "Sumar algo de la lista") }
                DropdownMenu(menu, { menu = false }) {
                    deHoy.toSortedMap().forEach { (cosa, precio) ->
                        DropdownMenuItem(text = { Text(if (cosa in t.porTotal) "$cosa  (se reparte el total)" else "$cosa  ${pesos(precio)}") }, onClick = {
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
fun CartelTaller(turno: String, t: Taller, fecha: String, alPedidos: () -> Unit, hayCompra: Boolean = false) {
    val k = LocalColores.current
    val lista = t.listaCompra(fecha)
    val cuantos = t.pedidosDe(fecha).keys.size
    val pendientes = t.pendientesDe(fecha, hayCompra)
    val pagado = hayCompra && pendientes.isEmpty()
    val parte = hayCompra && pendientes.isNotEmpty()
    val color = if (pagado) k.verde else k.naranja
    Row(Modifier.fillMaxWidth().border(1.5.dp, color, RoundedCornerShape(18.dp)).background(k.tarjeta, RoundedCornerShape(18.dp))
        .clickable(onClick = alPedidos).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text((if (pagado) "✅ " else "") + (if (turno == "M") "🥐 Hora del desayuno" else "☕ Hora de la merienda"), fontWeight = FontWeight.Bold, color = color)
            Text(if (pagado) "Compra anotada · la lista sigue acá" else if (parte) "Ya pagaste una parte · falta: " + pendientes.joinToString(", ")
                else if (lista.isEmpty()) "Tomá los pedidos de los muchachos"
                else "$cuantos pedidos · ${lista.values.sum()} cosas · ${pesos(t.totalDe(fecha, t.pedidosDe(fecha)))}",
                fontSize = 13.sp, color = k.suave)
        }
        Button(onClick = alPedidos) { Text(if (pagado) "Ver lista" else if (lista.isEmpty()) "Pedidos" else "Ver") }
    }
}

/** Botón de pagar del desayuno: si tildaste algunas cosas, paga solo esas (una compra por negocio);
    si no tildaste nada, paga todo lo que falta. Lo ya pagado queda marcado y no se vuelve a cobrar. */
@Composable
private fun PagoTaller(t: Taller, fecha: String, hayCompra: Boolean, alPagar: (Set<String>) -> Unit,
                       alGuardar: (Taller) -> Unit, chico: Boolean = false) {
    val k = LocalColores.current
    val compradas = t.compradoDe(fecha, hayCompra).values.flatMap { it.keys }.toSet()
    val pendientes = t.pendientesDe(fecha, hayCompra)
    val tildadas = (t.tildes[fecha] ?: emptyList()).toSet() intersect pendientes
    val cosas = if (tildadas.isNotEmpty()) tildadas else pendientes
    val monto = t.totalDe(fecha, t.pedidosSolo(fecha, cosas, hayCompra))
    var pidiendoTotal by remember { mutableStateOf<Set<String>?>(null) }      // cosas a pagar, cuando alguna se reparte el total
    if (compradas.isNotEmpty()) Text("✅ Ya pagaste: " + compradas.sorted().joinToString(", "), fontSize = 13.sp, color = k.verde)
    /* Si entre lo que pagás hay algo que se reparte (sándwiches), primero se pregunta cuánto te cobraron */
    Button(onClick = { if (cosas.any { it in t.porTotal }) pidiendoTotal = cosas else alPagar(cosas) }, Modifier.fillMaxWidth().height(52.dp)) {
        Text(when {
            tildadas.isNotEmpty() && tildadas.size < pendientes.size -> "💳 Pagar lo tildado · ${pesos(monto)}"
            compradas.isNotEmpty() -> "💳 Pagar lo que falta · ${pesos(monto)}"
            else -> "💳 Anotar y pagar con Mercado Pago"
        }, fontSize = 16.sp)
    }
    if (!chico) Subtitulo(if (tildadas.isNotEmpty() && tildadas.size < pendientes.size)
        "Se anota solo lo tildado (cada uno paga su parte de eso). Lo demás queda para la próxima compra."
        else "¿Comprás en distintos negocios? Tildá solo lo de este negocio y pagás eso; lo demás queda para después.")
    pidiendoTotal?.let { cs ->
        DialogoTotales(t, fecha, hayCompra, cs, alCerrar = { pidiendoTotal = null }) { nuevo ->
            pidiendoTotal = null; alGuardar(nuevo); alPagar(cs)
        }
    }
}

/** "¿Cuánto te cobraron?": para cada cosa que se reparte (sándwiches), el total del negocio. La app lo divide por cantidad. */
@Composable
private fun DialogoTotales(t: Taller, fecha: String, hayCompra: Boolean, cosas: Set<String>, alCerrar: () -> Unit, alListo: (Taller) -> Unit) {
    val k = LocalColores.current
    val pend = t.pedidosSolo(fecha, cosas, hayCompra)                 // quién se lleva qué (solo lo que falta pagar)
    val repartir = cosas.filter { it in t.porTotal }.sorted()
    val escrito = remember { mutableStateMapOf<String, String>() }
    fun nombre(id: String) = if (id == "yo") "Yo" else t.muchachos.firstOrNull { it.id == id }?.mostrar ?: id
    AlertDialog(onDismissRequest = alCerrar, title = { Text("¿Cuánto te cobraron?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Subtitulo("Copiá el total que te dijo el negocio de cada tipo. Vos no dividís nada: la app lo hace.")
            repartir.forEach { cosa ->
                val quienes = pend.mapNotNull { (q, p) -> p[cosa]?.takeIf { it > 0 }?.let { q to it } }
                val unidades = quienes.sumOf { it.second }
                val total = aNumero(escrito[cosa] ?: "")
                CampoMonto(escrito[cosa] ?: "", { escrito[cosa] = it }, "$cosa: total ($unidades)")
                if (total > 0) Text(quienes.joinToString("\n") { (q, n) -> "${nombre(q)} (${n}): ${pesos(Taller.parte(total, n, unidades))}" },
                    fontSize = 13.sp, color = k.suave)
            }
            /* Lo que tiene que pagar cada uno, sumando todos los tipos (lo que le cobrás en el taller) */
            if (repartir.all { aNumero(escrito[it] ?: "") > 0 }) {
                val conTotales = repartir.fold(t) { acc, c ->
                    val u = pend.values.sumOf { it[c] ?: 0 }; acc.conTotal(fecha, c, aNumero(escrito[c] ?: ""), u) }
                HorizontalDivider(color = k.linea)
                Text("Cada uno te paga:", fontWeight = FontWeight.Bold, color = k.texto)
                pend.forEach { (q, p) -> Renglon(nombre(q), pesos(conTotales.costoDe(fecha, p)), negrita = false) }
                Renglon("Total", pesos(conTotales.totalDe(fecha, pend)))
            }
        } },
        confirmButton = { TextButton(enabled = repartir.all { aNumero(escrito[it] ?: "") > 0 }, onClick = {
            var nuevo = t
            repartir.forEach { cosa ->
                nuevo = nuevo.conTotal(fecha, cosa, aNumero(escrito[cosa] ?: ""), pend.values.sumOf { it[cosa] ?: 0 })
            }
            alListo(nuevo)
        }) { Text("Seguir") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } })
}

/** Ya anotaste la compra: en vez del botón de pagar, el aviso y la opción de deshacerla si fue sin querer. */
@Composable
private fun YaAnotada(alDeshacer: () -> Unit, anotado: Double, ahora: Double, alRehacer: () -> Unit) {
    val k = LocalColores.current
    var preguntar by remember { mutableStateOf(false) }
    val cambio = anotado > 0 && Math.abs(anotado - ahora) >= 1
    Column(Modifier.fillMaxWidth().background(k.verdeClaro, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("✅ Ya anotaste la compra de hoy" + (if (anotado > 0) ": ${pesos(anotado)}" else ""), fontWeight = FontWeight.Bold, color = k.verde)
        if (cambio) Text("La lista cambió desde que la anotaste (ahora da ${pesos(ahora)}).", fontSize = 13.sp, color = k.naranja)
        else Text("La lista queda acá para cuando estés en el negocio. Lo de cada uno está en \"Me deben\".", fontSize = 13.sp, color = k.texto)
        /* Volver a anotar con la lista de ahora: borra lo anotado antes y abre "Anotar y pagar" con todo actualizado */
        Button(onClick = alRehacer, Modifier.fillMaxWidth().height(50.dp)) { Text("💳 Volver a anotar con esta lista y pagar", fontSize = 15.sp) }
        TextButton(onClick = { preguntar = true }) { Text("¿La anotaste sin querer? Deshacer", color = k.rojo) }
    }
    if (preguntar) AlertDialog(onDismissRequest = { preguntar = false },
        title = { Text("¿Deshacer la compra anotada?") },
        text = { Text("Se borra el gasto de hoy del taller y lo que te debe cada uno. La lista de pedidos no se toca. Después podés volver a tocar \"Anotar y pagar\".") },
        confirmButton = { TextButton(onClick = { preguntar = false; alDeshacer() }) { Text("Deshacer", color = k.rojo) } },
        dismissButton = { TextButton(onClick = { preguntar = false }) { Text("Cancelar") } })
}

/** El desayuno de hoy: 1) tomar los pedidos, 2) lista para comprar con tildes, 3) "Ya pagué". */
@Composable
fun HojaPedidos(t: Taller, fecha: String, alCerrar: () -> Unit, alGuardar: (Taller) -> Unit, alPagar: (Set<String>) -> Unit,
                hayCompra: Boolean = false, alDeshacer: () -> Unit = {}, anotado: Double = 0.0,
                favores: Map<String, Double> = emptyMap(), alMeDio: (String, Cobro) -> Unit = { _, _ -> }) {
    val k = LocalColores.current
    val dia = t.pedidosDe(fecha)
    val turno = t.turno(fecha)
    var abierto by remember { mutableStateOf<String?>(null) }
    var otraCosa by remember { mutableStateOf<String?>(null) }      // para quién se agrega una cosa nueva
    val gente = listOf("yo" to "Yo") + t.activos.map { it.id to it.mostrar }
    var verTotal by remember { mutableStateOf(false) }
    var meDio by remember { mutableStateOf<Pair<String, String>?>(null) }      // (id, nombre) del que te da plata
    var origen by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }   // centro del botón, en la pantalla
    val vibrar = androidx.compose.ui.platform.LocalHapticFeedback.current
    /* Sin turno cargado (franco o sin horario) no se asume desayuno: se llama simplemente "Pedidos" */
    val titulo = when (turno) { "T" -> "☕ Merienda"; "M" -> "🥐 Desayuno"; else -> "🛒 Pedidos" } + " · " + Fechas.diaYMes(fecha)
    Hoja(alCerrar, titulo, accion = {
        BotonTotalPedidos(t.listaCompra(fecha).values.sum(), Modifier.onGloballyPositioned {
            origen = it.localToScreen(androidx.compose.ui.geometry.Offset(it.size.width / 2f, it.size.height / 2f)) }) {
            vibrar.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); verTotal = true
        }
    }) {
        Subtitulo("Tocá a cada uno y sumá lo que quiere. Abajo te queda la lista para el negocio.")
        gente.forEach { (id, nombre) ->
            val p = dia[id] ?: emptyMap()
            Column(Modifier.fillMaxWidth().bordeBloque(14).background(if (p.isNotEmpty()) k.verdeClaro else k.tarjeta, RoundedCornerShape(14.dp))
                .clickable { abierto = if (abierto == id) null else id }.padding(12.dp)) {
                val fijo = t.esFijo(id)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (fijo) "🔁 " else "") + nombre, fontWeight = FontWeight.SemiBold, color = k.texto, modifier = Modifier.weight(1f))
                    if (p.isNotEmpty()) Text(pesos(t.costoDe(fecha, p)), fontWeight = FontWeight.SemiBold, color = k.texto)
                }
                if (p.isNotEmpty()) Text(t.texto(p), fontSize = 13.sp, color = k.suave)
                val favor = favores[id] ?: 0.0
                if (favor > 0.005) Text("🤝 Tiene ${pesos(favor)} a favor" + (if (p.isNotEmpty() && !hayCompra) " · paga ${pesos(Math.max(0.0, t.costoDe(fecha, p) - favor))}" else ""),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = k.verde)
                /* "Siempre lo mismo": su pedido aparece solo todos los días */
                if (p.isNotEmpty() || fijo) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .clickable { alGuardar(t.conFijo(fecha, id, !fijo)) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(fijo, { alGuardar(t.conFijo(fecha, id, it)) })
                    Column {
                        Text("Siempre lo mismo", fontSize = 14.sp, color = k.texto)
                        if (fijo) Text("De siempre: " + t.texto(t.fijos[id] ?: emptyMap()), fontSize = 12.sp, color = k.suave)
                    }
                }
                if (abierto == id) {
                    Spacer(Modifier.height(6.dp))
                    /* Solo lo que se pide en este turno (la tarde tiene su propia lista), más lo que esta persona ya pidió hoy */
                    (t.menuDe(turno).keys + p.keys).toSortedSet().forEach { cosa ->
                        val precio = t.precios[cosa] ?: 0.0
                        val n = p[cosa] ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(cosa, color = k.texto, fontSize = 14.sp)
                                val repartido = cosa in t.porTotal
                                Text(when {
                                    repartido && precio > 0 -> "se reparte el total · ≈ ${pesos(precio)} c/u"
                                    repartido -> "se reparte el total"
                                    precio > 0 -> pesos(precio)
                                    else -> "sin precio"
                                }, color = if (repartido || precio > 0) k.suave else k.naranja, fontSize = 12.sp)
                            }
                            IconButton(onClick = { alGuardar(t.conCantidad(fecha, id, cosa, n - 1)) }, enabled = n > 0) { Text("−", fontSize = 22.sp) }
                            Text("$n", fontWeight = FontWeight.Bold, color = k.texto, modifier = Modifier.width(24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            IconButton(onClick = { alGuardar(t.conCantidad(fecha, id, cosa, n + 1)) }) { Text("+", fontSize = 22.sp) }
                        }
                    }
                    TextButton(onClick = { otraCosa = id }) { Text("+ Otra cosa (con su precio)") }
                    if (id != "yo") OutlinedButton(onClick = { meDio = id to nombre }, Modifier.fillMaxWidth()) { Text("💵 Me entregó plata") }
                    if (fijo) Text("Lo que cambies acá es solo para hoy. Para cambiar lo de siempre: destildá, armá el pedido nuevo y volvé a tildar.",
                        fontSize = 12.sp, color = k.suave)
                }
            }
        }
        val lista = t.listaCompra(fecha)
        if (lista.isNotEmpty()) {
            HorizontalDivider(color = k.linea)
            Text("🛒 Para comprar", fontWeight = FontWeight.Bold, color = k.texto, fontSize = 17.sp)
            val tildadas = t.tildes[fecha] ?: emptyList()
            val pendientes = t.pendientesDe(fecha, hayCompra)
            lista.forEach { (cosa, n) ->
                val ok = cosa in tildadas
                val ya = hayCompra && cosa !in pendientes
                Row(Modifier.fillMaxWidth().clickable(enabled = !ya) {
                    alGuardar(t.copy(tildes = t.tildes + (fecha to if (ok) tildadas - cosa else tildadas + cosa))) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ok || ya, null, enabled = !ya)
                    Text("$n  $cosa", color = if (ok || ya) k.suave else k.texto, fontSize = 16.sp, modifier = Modifier.weight(1f),
                        textDecoration = if (ok || ya) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                    if (ya) Text("✅ pagado", color = k.verde, fontSize = 13.sp)
                    else Text(pesos(t.costoCosa(fecha, cosa, n)), color = k.suave, fontSize = 14.sp)
                }
            }
            Renglon("Total estimado", pesos(t.totalDe(fecha, dia)))
            if (hayCompra && t.pendientesDe(fecha, hayCompra).isEmpty())
                YaAnotada(alDeshacer, anotado, t.totalDe(fecha, dia), alRehacer = { alDeshacer(); alPagar(lista.keys) })
            else {
                PagoTaller(t, fecha, hayCompra, alPagar, alGuardar)
                if (hayCompra) TextButton(onClick = alDeshacer) { Text("Deshacer lo ya pagado", color = k.rojo) }
            }
        }
    }
    if (verTotal) VentanaQueCrece(origen, alCerrar = { verTotal = false }) { cerrar ->
        TotalPedidos(t, fecha, titulo, alGuardar, alPagar = { cerrar(); alPagar(it) }, alCerrar = cerrar, hayCompra = hayCompra)
    }
    meDio?.let { (id, nombre) ->
        val favor = favores[id] ?: 0.0
        val total = Math.max(0.0, t.costoDe(fecha, dia[id] ?: emptyMap()) - favor)
        DialogoCobro(nombre, total, alCerrar = { meDio = null }, puedeFavor = true, aFavorAntes = favor) { c ->
            meDio = null
            /* si le diste el vuelto, solo queda lo que cuesta su pedido; si no, todo lo que te dio */
            alMeDio(id, if (c.aFavor) c else c.hasta(total))
        }
    }
    otraCosa?.let { quien ->
        var nombre by remember { mutableStateOf("") }; var precio by remember { mutableStateOf("") }
        var falta by remember { mutableStateOf(false) }
        var repartir by remember { mutableStateOf(turno == "T") }       // a la tarde casi todo se reparte (fiambre, fruta)
        AlertDialog(onDismissRequest = { otraCosa = null }, title = { Text("Otra cosa") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nombre, { nombre = it; falta = false }, label = { Text("¿Qué es?") }, placeholder = { Text("Ej: Tortas de hoja") }, singleLine = true,
                    isError = falta, supportingText = if (falta) ({ Text("Escribí qué es") }) else null)
                Interruptor("Se reparte el total", repartir, { repartir = it },
                    "Sin precio: cuando pagás, ponés el total que te cobraron y la app lo divide sola.")
                if (!repartir) CampoMonto(precio, { precio = it }, "Precio de una (si no lo sabés, dejalo vacío)")
                Subtitulo("Queda guardada en la lista de " + (if (turno == "T") "la tarde" else if (turno == "M") "la mañana" else "hoy") +
                    " para la próxima. Lo podés cambiar después en Ajustes → Compras del taller.")
            } },
            confirmButton = { TextButton(onClick = {
                val n = nombre.trim().replaceFirstChar { it.uppercase() }
                if (n.isEmpty()) falta = true
                else {
                    /* si ya existía con precio y no escribiste uno nuevo, se conserva el que tenía */
                    val p = if (repartir) t.precios[n] ?: 0.0 else aNumero(precio).takeIf { it > 0 } ?: t.precios[n] ?: 0.0
                    /* queda en el menú del turno de hoy; si ya estaba en el otro turno, pasa a "las dos" */
                    val hoyEs = turno ?: "M"
                    val marca = t.turnoCosa[n] ?: if (n in t.precios) "M" else hoyEs
                    val nueva = if (marca == hoyEs) marca else "A"
                    alGuardar(t.copy(precios = t.precios + (n to p), turnoCosa = t.turnoCosa + (n to nueva),
                        porTotal = if (repartir) t.porTotal + n else t.porTotal - n)
                        .conCantidad(fecha, quien, n, (dia[quien]?.get(n) ?: 0) + 1)); otraCosa = null
                }
            }) { Text("Agregar") } },
            dismissButton = { TextButton(onClick = { otraCosa = null }) { Text("Cancelar") } })
    }
}

/** Botón naranja "🛒 Total pedidos" al lado del título del desayuno / merienda. */
@Composable
private fun BotonTotalPedidos(cosas: Int, modifier: Modifier, alTocar: () -> Unit) {
    val k = LocalColores.current
    Row(modifier.clip(RoundedCornerShape(50)).background(k.naranja).clickable(onClick = alTocar)
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🛒", fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Column {
            Text("TOTAL PEDIDOS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            if (cosas > 0) Text("$cosas cosas", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
        }
    }
}

/** Ventana que sale creciendo desde el botón tocado (como el cartel de "Ayudá al creador")
    y al cerrarla se achica de vuelta hacia él. */
@Composable
fun VentanaQueCrece(origen: androidx.compose.ui.geometry.Offset?, alCerrar: () -> Unit, contenido: @Composable (cerrar: () -> Unit) -> Unit) {
    val alcance = rememberCoroutineScope()
    val v = remember { androidx.compose.animation.core.Animatable(0f) }
    var marco by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var cerrando by remember { mutableStateOf(false) }
    val curva = androidx.compose.animation.core.FastOutSlowInEasing
    LaunchedEffect(Unit) { v.animateTo(1f, androidx.compose.animation.core.tween(650, easing = curva)) }
    val cerrar: () -> Unit = {
        if (!cerrando) { cerrando = true
            alcance.launch { v.animateTo(0f, androidx.compose.animation.core.tween(420, easing = curva)); alCerrar() } }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = cerrar, properties = androidx.compose.ui.window.DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f * v.value))
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { cerrar() }
            .systemBarsPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.onGloballyPositioned { c ->
                    val a = c.localToScreen(androidx.compose.ui.geometry.Offset.Zero)
                    marco = androidx.compose.ui.geometry.Rect(a, androidx.compose.ui.geometry.Size(c.size.width.toFloat(), c.size.height.toFloat()))
                }
                .graphicsLayer {
                    val m = marco; val o = origen
                    transformOrigin = if (m != null && o != null && m.width > 0 && m.height > 0)
                        TransformOrigin((o.x - m.left) / m.width, (o.y - m.top) / m.height) else TransformOrigin.Center
                    val e = 0.06f + 0.94f * v.value
                    scaleX = e; scaleY = e
                    alpha = (v.value * 1.6f).coerceAtMost(1f)
                }
                .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}) {
                contenido(cerrar)
            }
        }
    }
}

/** La lista para el negocio: todo lo que hay que comprar, con tildes, grande para leer en el mostrador. */
@Composable
private fun TotalPedidos(t: Taller, fecha: String, titulo: String, alGuardar: (Taller) -> Unit, alPagar: (Set<String>) -> Unit, alCerrar: () -> Unit,
                         hayCompra: Boolean = false) {
    val pendientes = t.pendientesDe(fecha, hayCompra)
    val k = LocalColores.current
    val lista = t.listaCompra(fecha)
    val tildadas = t.tildes[fecha] ?: emptyList()
    val dia = t.pedidosDe(fecha)
    Column(Modifier.fillMaxWidth().widthIn(max = 520.dp).heightIn(max = 680.dp)
        .border(2.dp, k.naranja, RoundedCornerShape(24.dp)).background(k.tarjeta, RoundedCornerShape(24.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🛒 Total pedidos", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = k.texto)
                Text("$titulo · ${dia.keys.size} pedidos", fontSize = 13.sp, color = k.suave)
            }
            IconButton(onClick = alCerrar) { Icon(Icons.Outlined.Close, "Cerrar") }
        }
        Spacer(Modifier.height(8.dp))
        if (lista.isEmpty()) {
            Text("Todavía no hay pedidos. Tocá a cada muchacho y sumá lo que quiere.", color = k.suave, fontSize = 15.sp,
                modifier = Modifier.padding(vertical = 20.dp))
        } else {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                lista.forEach { (cosa, n) ->
                    val ok = cosa in tildadas
                    val ya = hayCompra && cosa !in pendientes
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = !ya) {
                        alGuardar(t.copy(tildes = t.tildes + (fecha to if (ok) tildadas - cosa else tildadas + cosa))) }
                        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(ok || ya, null, enabled = !ya)
                        Text("$n", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (ok || ya) k.suave else k.naranja, modifier = Modifier.width(34.dp))
                        Text(cosa, color = if (ok || ya) k.suave else k.texto, fontSize = 19.sp, modifier = Modifier.weight(1f),
                            textDecoration = if (ok || ya) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                        if (ya) Text("✅ pagado", color = k.verde, fontSize = 13.sp)
                        else Text(pesos(t.costoCosa(fecha, cosa, n)), color = k.suave, fontSize = 14.sp)
                    }
                }
            }
            HorizontalDivider(color = k.linea, modifier = Modifier.padding(vertical = 8.dp))
            Renglon("Total estimado", pesos(t.totalDe(fecha, dia)))
            Text("Tildá lo de este negocio y pagás solo eso; lo demás queda para la próxima compra.", fontSize = 12.sp, color = k.suave)
            Spacer(Modifier.height(10.dp))
            if (hayCompra && t.pendientesDe(fecha, hayCompra).isEmpty()) Text("✅ Todo pagado", fontWeight = FontWeight.Bold, color = k.verde,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            else PagoTaller(t, fecha, hayCompra, alPagar, alGuardar, chico = true)
        }
    }
}

/** Ajustes → Compras del taller: muchachos, precios, horario y avisos. */
@Composable
fun HojaTaller(t: Taller, alCerrar: () -> Unit, alGuardar: (Taller) -> Unit, alImportar: () -> Unit, alFoto: () -> Unit,
               leyendoFoto: Boolean, alPermisoAvisos: () -> Unit) {
    val k = LocalColores.current
    var cambiarPrecio by remember { mutableStateOf<String?>(null) }
    var editando by remember { mutableStateOf<Muchacho?>(null) }
    var nuevo by remember { mutableStateOf(false) }
    var horario by remember { mutableStateOf(false) }
    Hoja(alCerrar, "🔧 Compras del taller") {
        Subtitulo("Cuando comprás para los muchachos, tu parte va como gasto tuyo y la de cada uno queda en \"Me deben\". Cuando te pagan, tocás \"Me lo pagaron\".")
        /* Cobrar por WhatsApp: el alias que va en el mensaje (el link de Mercado Pago ya no se manda) */
        var alias by remember(t.aliasCobro) { mutableStateOf(t.aliasCobro) }
        Text("📲 Cobrar por WhatsApp", fontWeight = FontWeight.SemiBold, color = k.texto)
        Subtitulo("En \"Me deben\" tocás \"📲 Cobrar\" y se abre WhatsApp con el mensaje listo: cuánto te debe, de qué y tu alias (la transferencia por alias no tiene comisión).")
        OutlinedTextField(alias, { alias = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Alias (transferencia sin comisión)") }, singleLine = true)
        if (alias != t.aliasCobro) Button(onClick = { alGuardar(t.copy(aliasCobro = alias)) }, Modifier.fillMaxWidth()) { Text("Guardar alias") }
        HorizontalDivider(color = k.linea)
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
            Text("Menú y precios", fontWeight = FontWeight.SemiBold, color = k.texto)
            Text("Tocá uno para cambiarle el precio o la lista (desayuno, merienda o las dos).", fontSize = 12.sp, color = k.suave)
            t.precios.toSortedMap().forEach { (cosa, p) ->
                val repartido = cosa in t.porTotal
                val turnoTxt = when (t.turnoCosa[cosa] ?: "M") { "T" -> "☕ tarde"; "A" -> "🥐☕ las dos"; else -> "🥐 mañana" }
                Row(Modifier.fillMaxWidth().clickable { cambiarPrecio = cosa }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(cosa, color = k.texto)
                        Text(turnoTxt + if (repartido) " · se reparte el total" else "", fontSize = 12.sp, color = k.suave)
                    }
                    Text(if (repartido) (if (p > 0) "≈ ${pesos(p)}" else "—") else if (p > 0) pesos(p) else "sin precio",
                        color = if (repartido || p > 0) k.texto else k.naranja)
                    IconButton(onClick = { alGuardar(t.copy(precios = t.precios - cosa, turnoCosa = t.turnoCosa - cosa, porTotal = t.porTotal - cosa)) }) {
                        Icon(Icons.Outlined.Close, "Quitar de la lista") }
                }
            }
        }
    }
    cambiarPrecio?.let { cosa ->
        var valor by remember(cosa) { mutableStateOf((t.precios[cosa] ?: 0.0).let { if (it > 0) textoMonto(it) else "" }) }
        var marca by remember(cosa) { mutableStateOf(t.turnoCosa[cosa] ?: "M") }
        var repartir by remember(cosa) { mutableStateOf(cosa in t.porTotal) }
        AlertDialog(onDismissRequest = { cambiarPrecio = null }, title = { Text(cosa) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("¿En qué lista aparece?", color = LocalColores.current.suave, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("M" to "🥐 Desayuno", "T" to "☕ Merienda", "A" to "Las dos").forEach { (v, txt) ->
                        FilterChip(selected = marca == v, onClick = { marca = v }, label = { Text(txt, fontSize = 13.sp) })
                    }
                }
                Interruptor("Se reparte el total", repartir, { repartir = it },
                    "Acá no ponés precio. Cuando pagás, la app te pregunta el total que te cobraron y lo divide sola según cuántos pidió cada uno.")
                if (!repartir) CampoMonto(valor, { valor = it }, "Precio de una")
            } },
            confirmButton = { TextButton(onClick = {
                alGuardar(t.copy(precios = t.precios + (cosa to (if (repartir) (t.precios[cosa] ?: 0.0) else aNumero(valor))), turnoCosa = t.turnoCosa + (cosa to marca),
                    porTotal = if (repartir) t.porTotal + cosa else t.porTotal - cosa))
                cambiarPrecio = null }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { cambiarPrecio = null }) { Text("Cancelar") } })
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
    var tel by remember { mutableStateOf(m?.tel ?: "") }
    AlertDialog(onDismissRequest = alCerrar, title = { Text(if (m == null) "Agregar" else "Editar") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true)
            OutlinedTextField(apodo, { apodo = it }, label = { Text("Apodo (opcional)") }, placeholder = { Text("Ej: Chuña") }, singleLine = true)
            OutlinedTextField(tel, { tel = it }, label = { Text("WhatsApp (opcional)") }, placeholder = { Text("Ej: 261 216-8515") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = tel.isNotBlank() && Taller.numeroWhatsApp(tel) == null,
                supportingText = { Text("Código de área + número, sin 0 ni 15. Sirve para cobrarle por WhatsApp.") })
            if (m != null) Interruptor("Sigue pidiendo", activo, { activo = it })
            if (m != null) TextButton(onClick = { alListo(m, true) }, colors = ButtonDefaults.textButtonColors(contentColor = LocalColores.current.rojo)) { Text("Borrar") }
        } },
        confirmButton = { TextButton(onClick = { if (nombre.isNotBlank()) alListo(Muchacho(m?.id ?: "m${System.currentTimeMillis()}", nombre.trim(), apodo.trim(), activo, tel.trim()), false) }) { Text("Guardar") } },
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
