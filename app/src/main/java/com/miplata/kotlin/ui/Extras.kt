package com.miplata.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.*

/* ---------- Me deben ---------- */
/** Algo que te deben (monto > 0) o plata a favor de un compañero (monto < 0: te la dio antes o le debés vuelto). */
data class Deuda(val id: Long, val fecha: String, val monto: Double, val nota: String, val previo: Boolean, val tp: String? = null,
                 val efectivo: Boolean = false) {
    val aFavor get() = monto < 0
}

/** Lo que te entregó: cuánto, si fue en efectivo, y si lo que sobra queda a su favor (true) o le diste el vuelto (false). */
data class Cobro(val efectivo: Boolean, val dado: Double, val aFavor: Boolean)

/** Reparte lo que te dio entre sus deudas, de la más vieja a la más nueva.
    Las que alcanza a pagar enteras van con null (todo); la última, con lo que quedó; las demás siguen debiéndose. */
fun repartirCobro(l: List<Deuda>, monto: Double): List<Pair<Deuda, Double?>> {
    var queda = monto
    return l.sortedWith(compareBy({ it.fecha }, { it.id })).mapNotNull { d ->
        when {
            queda <= 0.005 -> null
            queda >= d.monto - 0.005 -> { queda -= d.monto; d to null }
            else -> { val parte = queda; queda = 0.0; d to parte }
        }
    }
}

/** "Me entregó": ¿cuánto te dio y cómo (efectivo o transferencia)? Si te dio de más: ¿le diste el vuelto o queda a su favor?
    `total` = lo que tiene que pagar (0 = te da plata antes de pedir). `puedeFavor` = es un compañero del taller. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoCobro(quien: String, total: Double, alCerrar: () -> Unit, puedeFavor: Boolean = false, aFavorAntes: Double = 0.0,
                 alListo: (Cobro) -> Unit) {
    val k = LocalColores.current
    var efectivo by remember { mutableStateOf(true) }
    var dio by remember { mutableStateOf(if (total > 0) textoMonto(total) else "") }
    var aFavor by remember { mutableStateOf(true) }
    val n = aNumero(dio)
    val sobra = n - total
    AlertDialog(onDismissRequest = alCerrar,
        title = { Text("¿Cuánto te entregó${if (quien.isNotBlank()) " $quien" else ""}?") },
        text = { Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (total > 0) "Tiene que pagar ${pesos(total)}" else "Queda a su favor para lo que pida", color = k.suave)
            if (aFavorAntes > 0.005) Text("🤝 Ya tenía ${pesos(aFavorAntes)} a favor (ya está descontado)", fontSize = 13.sp, color = k.verde)
            Text("Me pagó con:", fontSize = 13.sp, color = k.suave)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = efectivo, onClick = { efectivo = true }, label = { Text("💵 Efectivo") })
                FilterChip(selected = !efectivo, onClick = { efectivo = false }, label = { Text("📲 Transferencia") })
            }
            CampoMonto(dio, { dio = it }, "Me entregó")
            when {
                n <= 0 -> Text("Escribí cuánto te dio (ej: 1000)", fontSize = 13.sp, color = k.naranja)
                total <= 0 -> Text("🤝 Le quedan ${pesos(n)} a favor", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = k.verde)
                sobra > 0.005 && puedeFavor -> {
                    Text("Sobran ${pesos(sobra)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = k.naranja)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = aFavor, onClick = { aFavor = true }, label = { Text("🤝 Quedan a su favor") })
                        FilterChip(selected = !aFavor, onClick = { aFavor = false }, label = { Text("🪙 Le di el vuelto") })
                    }
                    Text(if (aFavor) "No tenías vuelto: se le descuentan solos en lo próximo que pida." else "Le devolviste ${pesos(sobra)}.",
                        fontSize = 12.sp, color = k.suave)
                }
                sobra > 0.005 -> Text("Dale ${pesos(sobra)} de vuelto 🪙", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = k.naranja)
                sobra < -0.005 -> Text("Te queda debiendo ${pesos(-sobra)} (sigue en \"Me deben\")",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = k.naranja)
                else -> Text("✅ Te pagó justo", fontSize = 14.sp, color = k.verde)
            }
            Text(if (efectivo) "La plata va a tu Efectivo (no a Mercado Pago)." else "La plata va a Mercado Pago.", fontSize = 12.sp, color = k.suave)
        } },
        confirmButton = { TextButton(enabled = n > 0, onClick = { alListo(Cobro(efectivo, n, puedeFavor && aFavor)) }) { Text("Listo") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } })
}

fun deudas(S: Estado): List<Deuda> =
    S.spent.filter { it.dev == 1 }.map { Deuda(it.id, it.date, it.amt, it.note, false, it.extra["tp"].texto(),
        it.extra["med"].texto() == com.miplata.kotlin.datos.MEDIO_EFECTIVO) } +
    S.meDeben.map { Deuda(it.id, it.date, it.amt, it.note, true) }

/** Qué dice cada deuda en el mensaje: "desayuno 17/11: 1 Rockstar" */
private fun lineaDeuda(d: Deuda): String {
    if (d.aFavor) return "A tu favor (me diste de más)"
    val cosas = d.nota.removePrefix("Taller · ").substringAfter(":", "").trim()
    return "${Fechas.diaYMes(d.fecha)}" + (if (cosas.isNotEmpty()) ": $cosas" else if (!d.nota.startsWith("Taller")) ": ${d.nota}" else "")
}

/** Abre WhatsApp con el chat de esa persona y el mensaje ya escrito (vos solo tocás Enviar). */
fun abrirWhatsApp(ctx: android.content.Context, numero: String, texto: String): Boolean {
    val uri = android.net.Uri.parse("https://wa.me/$numero?text=" + android.net.Uri.encode(texto))
    return listOf("com.whatsapp", "com.whatsapp.w4b", null).any { pkg ->
        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
            if (pkg != null) setPackage(pkg); addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }); true }.getOrDefault(false)
    }
}

/** Abre WhatsApp con todo lo que te debe esa persona en un solo mensaje. false = no tiene número válido. */
fun mandarCobro(ctx: android.content.Context, tp: String, todas: List<Deuda>, taller: Taller, tel: String? = null): Boolean {
    val m = taller.muchachos.firstOrNull { it.id == tp }
    val num = Taller.numeroWhatsApp(tel ?: m?.tel.orEmpty()) ?: return false
    val suyas = todas.filter { it.tp == tp }
    if (suyas.isEmpty() || suyas.sumOf { it.monto } <= 0.005) return false
    val nombre = m?.let { it.apodo.ifBlank { it.nombre.substringBefore(" ") } } ?: ""
    val texto = Taller.mensajeCobro(nombre, suyas.map { lineaDeuda(it) to it.monto }, taller.linkCobro, taller.aliasCobro, ::pesos)
    if (!abrirWhatsApp(ctx, num, texto)) android.widget.Toast.makeText(ctx, "No encontré WhatsApp", android.widget.Toast.LENGTH_SHORT).show()
    return true
}

/** Sección Cobrar: le compraste algo a un compañero en cualquier momento (una Coca, un sánguche…)
    → queda en "Me deben" y le mandás el WhatsApp. Abajo, todos los que te deben, con su botón. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HojaCobrar(S: Estado, t: Taller, alCerrar: () -> Unit, alAnotar: (Muchacho, Double, String) -> Unit,
               alGuardarTaller: (Taller) -> Unit, alMeDio: (String, Cobro) -> Unit) {
    val k = LocalColores.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var quien by remember { mutableStateOf<Muchacho?>(null) }
    var monto by remember { mutableStateOf("") }
    var detalle by remember { mutableStateOf("") }
    var tel by remember(quien?.id) { mutableStateOf(quien?.tel.orEmpty()) }
    val telOk = Taller.numeroWhatsApp(tel) != null
    val todas = deudas(S)
    var cobrando by remember { mutableStateOf<Pair<Muchacho, Double>?>(null) }   // a quién y cuánto debe (neto)
    Hoja(alCerrar, "📲 Cobrar") {
        Subtitulo("¿Le compraste algo a un compañero? Elegilo, poné cuánto y qué. Queda en \"Me deben\" y le sale el WhatsApp con el mensaje listo.")
        if (t.activos.isEmpty()) Text("Primero cargá a los muchachos en Ajustes → Compras del taller.", color = k.naranja)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            t.activos.forEach { m -> FilterChip(selected = quien?.id == m.id, onClick = { quien = if (quien?.id == m.id) null else m },
                label = { Text(m.mostrar, fontSize = 13.sp) }) }
        }
        quien?.let { m ->
            if (Taller.numeroWhatsApp(m.tel) == null) OutlinedTextField(tel, { tel = it }, Modifier.fillMaxWidth(),
                label = { Text("WhatsApp de ${m.apodo.ifBlank { m.nombre }}") }, placeholder = { Text("Ej: 261 216-8515") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), isError = tel.isNotBlank() && !telOk,
                supportingText = { Text("Código de área + número, sin 0 ni 15. Queda guardado.") })
            CampoMonto(monto, { monto = it }, "¿Cuánto?")
            OutlinedTextField(detalle, { detalle = it }, Modifier.fillMaxWidth(), label = { Text("¿Qué le compraste?") },
                placeholder = { Text("Ej: 1 Coca-Cola") }, singleLine = true)
            TextButton(onClick = { cobrando = m to 0.0 }) { Text("💵 Me dio plata a cuenta (antes de comprar)") }
            Button(onClick = {
                if (tel.trim() != m.tel) alGuardarTaller(t.copy(muchachos = t.muchachos.map { if (it.id == m.id) it.copy(tel = tel.trim()) else it }))
                alAnotar(m.copy(tel = tel.trim()), aNumero(monto), detalle.trim())
                quien = null; monto = ""; detalle = ""
            }, Modifier.fillMaxWidth().height(52.dp), enabled = aNumero(monto) > 0 && telOk) { Text("Anotar y mandar WhatsApp", fontSize = 16.sp) }
        }
        /* Todos los que te deben ahora, uno por persona */
        val porPersona = todas.filter { it.tp != null }.groupBy { it.tp!! }
        if (porPersona.isNotEmpty()) {
            HorizontalDivider(color = k.linea)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Te deben", fontWeight = FontWeight.Bold, color = k.texto, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text(dinero(porPersona.values.sumOf { l -> Math.max(0.0, l.sumOf { it.monto }) }), fontWeight = FontWeight.Bold, color = k.naranja)
            }
            porPersona.forEach { (tp, l) ->
                val m = t.muchachos.firstOrNull { it.id == tp }
                Column(Modifier.fillMaxWidth().background(k.tarjeta, RoundedCornerShape(14.dp)).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m?.mostrar ?: l.first().nota.removePrefix("Taller · ").substringBefore(":"), fontWeight = FontWeight.SemiBold,
                            color = k.texto, modifier = Modifier.weight(1f))
                        val neto = l.sumOf { it.monto }
                        if (neto > 0.005) Text(dinero(neto), fontWeight = FontWeight.SemiBold, color = k.texto)
                        else Text("🤝 ${dinero(-neto)} a favor", fontWeight = FontWeight.SemiBold, color = k.verde)
                    }
                    Text(l.joinToString(" · ") { lineaDeuda(it) }, fontSize = 12.sp, color = k.suave)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (l.sumOf { it.monto } > 0.005) OutlinedButton(onClick = {
                            if (!mandarCobro(ctx, tp, todas, t)) { quien = m; android.widget.Toast.makeText(ctx, "Poné su WhatsApp arriba", android.widget.Toast.LENGTH_SHORT).show() }
                        }) { Text("📲 Cobrar") }
                        if (m != null) FilledTonalButton(onClick = { cobrando = m to Math.max(0.0, l.sumOf { it.monto }) }) { Text("💵 Me entregó") }
                    }
                }
            }
        }
    }
    cobrando?.let { (m, debe) ->
        DialogoCobro(m.apodo.ifBlank { m.nombre }, debe, alCerrar = { cobrando = null }, puedeFavor = true) { c ->
            cobrando = null; alMeDio(m.id, c)
        }
    }
}

@Composable
fun TarjetaMeDeben(lista: List<Deuda>, alCobrar: (Deuda, Cobro) -> Unit, alNoPagaron: (Deuda) -> Unit, alDevolviFavor: (Deuda) -> Unit,
                   taller: Taller = Taller(), alGuardarTaller: (Taller) -> Unit = {}) {
    val k = LocalColores.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var confirmar by remember { mutableStateOf<Deuda?>(null) }
    var pedirTel by remember { mutableStateOf<Deuda?>(null) }
    var cobrando by remember { mutableStateOf<Deuda?>(null) }
    /* Le manda todo lo que te debe esa persona junto, en un solo mensaje */
    fun cobrar(d: Deuda, tel: String) {
        if (d.tp != null) { mandarCobro(ctx, d.tp, lista, taller, tel); return }
        val num = Taller.numeroWhatsApp(tel) ?: return
        val texto = Taller.mensajeCobro("", listOf(lineaDeuda(d) to d.monto), taller.linkCobro, taller.aliasCobro, ::pesos)
        if (!abrirWhatsApp(ctx, num, texto)) android.widget.Toast.makeText(ctx, "No encontré WhatsApp", android.widget.Toast.LENGTH_SHORT).show()
    }
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Titulo("Me deben"); Spacer(Modifier.weight(1f))
            Text(dinero(lista.filter { !it.aFavor }.sumOf { it.monto }), fontWeight = FontWeight.Bold, color = k.naranja)
        }
        lista.forEach { d ->
            if (d.aFavor) Column(Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.nota, color = k.texto)
                        Text(Fechas.diaYMes(d.fecha) + " · se descuenta solo en lo próximo que pida", fontSize = 12.sp, color = k.suave)
                    }
                    Text("🤝 " + dinero(-d.monto), fontWeight = FontWeight.SemiBold, color = k.verde)
                }
                TextButton(onClick = { alDevolviFavor(d) }) { Text("Ya se lo devolví", fontSize = 12.sp) }
            } else Column(Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.nota.ifBlank { "Compra para otros" }, color = k.texto)
                        Text(Fechas.diaYMes(d.fecha) + if (d.previo) " · del período anterior" else "", fontSize = 12.sp, color = k.suave)
                    }
                    Text(dinero(d.monto), fontWeight = FontWeight.SemiBold, color = k.texto)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { cobrando = d }) { Text("Me lo pagaron") }
                    OutlinedButton(onClick = {
                        val tel = taller.muchachos.firstOrNull { it.id == d.tp }?.tel.orEmpty()
                        if (Taller.numeroWhatsApp(tel) != null) cobrar(d, tel) else pedirTel = d
                    }) { Text("📲 Cobrar") }
                    TextButton(onClick = { confirmar = d }) { Text("No me lo pagan", fontSize = 12.sp) }
                }
            }
        }
    }
    cobrando?.let { d ->
        val m = taller.muchachos.firstOrNull { it.id == d.tp }
        DialogoCobro(m?.apodo?.ifBlank { m.nombre } ?: "", d.monto, alCerrar = { cobrando = null }, puedeFavor = d.tp != null && !d.previo) { c ->
            cobrando = null; alCobrar(d, c) }
    }
    pedirTel?.let { d ->
        val m = taller.muchachos.firstOrNull { it.id == d.tp }
        var tel by remember(d.id) { mutableStateOf(m?.tel.orEmpty()) }
        val ok = Taller.numeroWhatsApp(tel) != null
        AlertDialog(onDismissRequest = { pedirTel = null },
            title = { Text("WhatsApp de " + (m?.mostrar ?: "esta persona")) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(tel, { tel = it }, label = { Text("Celular con código de área") }, placeholder = { Text("Ej: 261 216-8515") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = tel.isNotBlank() && !ok, supportingText = { Text(if (tel.isNotBlank() && !ok) "Tiene que ser área + número, sin 0 ni 15" else "Sin 0 ni 15") })
                if (m != null) Text("Queda guardado para la próxima.", fontSize = 12.sp, color = k.suave)
            } },
            confirmButton = { TextButton(enabled = ok, onClick = {
                if (m != null) alGuardarTaller(taller.copy(muchachos = taller.muchachos.map { if (it.id == m.id) it.copy(tel = tel.trim()) else it }))
                pedirTel = null; cobrar(d, tel)
            }) { Text("Abrir WhatsApp") } },
            dismissButton = { TextButton(onClick = { pedirTel = null }) { Text("Cancelar") } })
    }
    confirmar?.let { d -> AlertDialog(onDismissRequest = { confirmar = null }, title = { Text("¿No te lo van a pagar?") },
        text = { Text("Pasa a ser un gasto tuyo.") },
        confirmButton = { TextButton(onClick = { confirmar = null; alNoPagaron(d) }) { Text("Sí, es mío") } },
        dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } }) }
}

/* ---------- Objetivo por día y recalibrar (en Días) ---------- */
@Composable
fun TarjetaObjetivo(S: Estado, alPoner: (Double) -> Unit, alRecalibrar: () -> Unit, alQuitarRecal: () -> Unit) {
    val k = LocalColores.current
    val hoy = Fechas.hoy()
    val o = Nucleo.objetivoDiario(S, hoy)
    var valor by remember(S.objetivo) { mutableStateOf(textoMonto(S.objetivo)) }
    var preguntarRecal by remember { mutableStateOf(false) }
    Tarjeta {
        Titulo("Mi objetivo por día")
        Subtitulo("Cuánto querés gastar por día. Pasa a ser tu límite desde hoy, y lo que no gastes se suma al día siguiente.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoMonto(valor, { valor = it }, "Objetivo por día", Modifier.weight(1f), demoClave = "obj.campo")
            Button(onClick = { alPoner(aNumero(valor)) }, Modifier.demo("obj.usar", accion = { alPoner(aNumero(valor)) })) { Text("Usar") }
        }
        if (o != null) {
            Renglon("Podrías gastar parejo", dinero(o.promedio) + " por día", negrita = false)
            Renglon("Al final te sobraría", dinero(o.proyectado), colorDer = if (o.proyectado >= 0) k.verde else k.rojo)
            if (o.diasPrevios > 0) Renglon("Ahorrado vs. objetivo", dinero(o.ahorradoAntes), colorDer = if (o.ahorradoAntes >= 0) k.verde else k.rojo)
            Text("Si gastaras por día…", fontSize = 13.sp, color = k.suave)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                o.escenarios.forEach { e ->
                    Column(Modifier.weight(1f).background(if (e.actual) k.verdeClaro else k.fondo, RoundedCornerShape(12.dp)).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dinero(e.o), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = k.texto)
                        Text("sobra " + dinero(e.sobra), fontSize = 11.sp, color = if (e.sobra >= 0) k.verde else k.rojo, textAlign = TextAlign.Center)
                    }
                }
            }
            TextButton(onClick = { alPoner(0.0) }) { Text("Quitar objetivo") }
        } else {
            HorizontalDivider(color = k.linea)
            if (S.recal != null && !S.recal.obj) {
                Text("Recalibrado: cada día vale ${dinero(S.recal.base)}.", color = k.texto, fontSize = 14.sp)
                TextButton(onClick = alQuitarRecal) { Text("Quitar recalibración") }
            } else {
                Text("¿Te desordenaste? Recalibrar reparte lo que te queda en partes iguales entre los días que faltan.", fontSize = 13.sp, color = k.suave)
                OutlinedButton(onClick = { preguntarRecal = true }) { Text("Recalibrar desde hoy") }
            }
        }
    }
    if (preguntarRecal) AlertDialog(onDismissRequest = { preguntarRecal = false }, title = { Text("¿Recalibrar?") },
        text = { Text("Desde hoy cada día vale lo mismo: lo que te queda dividido los días que faltan. Lo que sobró o faltó antes deja de arrastrarse.") },
        confirmButton = { TextButton(onClick = { preguntarRecal = false; alRecalibrar() }) { Text("Recalibrar") } },
        dismissButton = { TextButton(onClick = { preguntarRecal = false }) { Text("Cancelar") } })
}

/* ---------- Metas (en Plan) ---------- */
@Composable
fun TarjetaMetas(S: Estado, alAbrir: (Meta?) -> Unit) {
    val k = LocalColores.current
    val metas = S.metas.filter { it.n.isNotBlank() || it.t > 0 }
    val pleg = rememberPlegado("metas")
    Tarjeta {
        CabeceraPlegable(pleg) { Titulo("Metas de ahorro"); Spacer(Modifier.weight(1f)) }
        if (!pleg.abierto) {
            Text(if (metas.isEmpty()) "Sin metas" else "${metas.size} meta${if (metas.size > 1) "s" else ""} · juntado ${dinero(metas.sumOf { Nucleo.juntadoEnMeta(S, it) })}",
                color = k.suave, fontWeight = FontWeight.SemiBold)
            return@Tarjeta
        }
        if (metas.isEmpty()) Subtitulo("Juntá para algo: vacaciones, un arreglo del auto… Lo que apartás sale de tu plata disponible.")
        metas.forEach { m ->
            val juntado = Nucleo.juntadoEnMeta(S, m)
            Column(Modifier.fillMaxWidth().clickable { alAbrir(m) }.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { Text(m.n.ifBlank { "Meta" }, Modifier.weight(1f), color = k.texto, fontWeight = FontWeight.SemiBold)
                    Text("${dinero(juntado)} de ${dinero(m.t)}", color = k.texto) }
                if (m.t > 0) LinearProgressIndicator(progress = { (juntado / m.t).coerceIn(0.0, 1.0).toFloat() }, Modifier.fillMaxWidth().height(6.dp),
                    color = k.verde, trackColor = k.linea, gapSize = 0.dp, drawStopIndicator = {})
                val sug = Nucleo.aporteMensualSugerido(S, m, Fechas.hoy())
                if (sug > 0) Text("Para llegar al ${Fechas.diaYMes(m.f)}: ${dinero(sug)} por mes", fontSize = 12.sp, color = k.suave)
            }
        }
        OutlinedButton(onClick = { alAbrir(null) }, Modifier.fillMaxWidth()) { Text("+ Nueva meta") }
    }
}

@Composable
fun HojaMeta(S: Estado, meta: Meta?, alCerrar: () -> Unit, alGuardar: (String, Double, Double, String) -> Unit,
             alAportar: (Double) -> Unit, alQuitar: () -> Unit) {
    val k = LocalColores.current
    var nombre by remember { mutableStateOf(meta?.n ?: "") }
    var objetivo by remember { mutableStateOf(textoMonto(meta?.t ?: 0.0)) }
    var previo by remember { mutableStateOf(textoMonto(meta?.g0 ?: 0.0)) }
    var fecha by remember { mutableStateOf(meta?.f ?: "") }
    var aporte by remember { mutableStateOf("") }
    var borrar by remember { mutableStateOf(false) }
    Hoja(alCerrar, if (meta == null) "Meta nueva" else "Meta") {
        if (meta != null) {
            Text("Juntaste ${dinero(Nucleo.juntadoEnMeta(S, meta))}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = k.verde)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoMonto(aporte, { aporte = it }, "Apartar hoy", Modifier.weight(1f))
                Button(onClick = { val n = aNumero(aporte); if (n > 0) alAportar(n) }) { Text("Apartar") }
            }
            HorizontalDivider(color = k.linea)
        }
        OutlinedTextField(nombre, { nombre = it }, Modifier.fillMaxWidth(), label = { Text("¿Para qué juntás?") }, singleLine = true)
        CampoMonto(objetivo, { objetivo = it }, "¿Cuánto necesitás?")
        CampoMonto(previo, { previo = it }, "Lo que ya tenías juntado")
        CampoFecha("¿Para cuándo? (opcional)", fecha, { fecha = it })
        Button(onClick = { alGuardar(nombre.trim(), aNumero(objetivo), aNumero(previo), fecha) }, Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar") }
        if (meta != null) TextButton(onClick = { borrar = true }, Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = k.rojo)) { Text("Borrar meta") }
    }
    if (borrar) AlertDialog(onDismissRequest = { borrar = false }, title = { Text("¿Borrar la meta?") },
        text = { Text("Los aportes ya hechos quedan descontados de tu plata.") },
        confirmButton = { TextButton(onClick = { borrar = false; alQuitar() }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { borrar = false }) { Text("Cancelar") } })
}

/* ---------- PIN ---------- */
@Composable
fun CampoPin(valor: String, alCambiar: (String) -> Unit, etiqueta: String) {
    OutlinedTextField(valor, { alCambiar(it.filter(Char::isDigit).take(8)) }, Modifier.fillMaxWidth(), label = { Text(etiqueta) },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, textAlign = TextAlign.Center, letterSpacing = 6.sp))
}

@Composable
fun PantallaBloqueo(error: String?, alProbar: (String) -> Unit) {
    val k = LocalColores.current
    var pin by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(k.fondo).padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Mi plata", fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, color = k.verde)
        Spacer(Modifier.height(20.dp))
        CampoPin(pin, { pin = it }, "Ingresá tu PIN")
        error?.let { Text(it, color = k.rojo, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { alProbar(pin); pin = "" }, Modifier.fillMaxWidth().height(52.dp)) { Text("Entrar") }
    }
}

@Composable
fun HojaPin(hayPin: Boolean, alCerrar: () -> Unit, alGuardar: (actual: String, nuevo: String) -> String?, alQuitar: (actual: String) -> String?) {
    val k = LocalColores.current
    var actual by remember { mutableStateOf("") }
    var nuevo by remember { mutableStateOf("") }
    var repetir by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Hoja(alCerrar, if (hayPin) "Cambiar PIN" else "Poner PIN") {
        if (hayPin) CampoPin(actual, { actual = it }, "PIN actual")
        CampoPin(nuevo, { nuevo = it }, "PIN nuevo (4 a 8 números)")
        CampoPin(repetir, { repetir = it }, "Repetilo")
        error?.let { Text(it, color = k.rojo, fontSize = 14.sp) }
        Button(onClick = {
            error = when { nuevo.length < 4 -> "El PIN tiene que tener al menos 4 números."; nuevo != repetir -> "Los PIN no coinciden."
                else -> alGuardar(actual, nuevo) }
        }, Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar PIN") }
        if (hayPin) TextButton(onClick = { error = alQuitar(actual) }, Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = k.rojo)) { Text("Quitar PIN (escribí el actual arriba)") }
        Text("Si te olvidás el PIN, la única forma de entrar es desinstalar y recuperar una copia. Guardá copias seguido.", fontSize = 12.sp, color = k.suave)
    }
}
