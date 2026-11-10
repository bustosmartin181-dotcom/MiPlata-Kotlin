package com.miplata.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.*

private val MESES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
fun mesYAnio(aaaaMm: String): String = runCatching {
    val (a, m) = aaaaMm.split("-"); "${MESES[m.toInt() - 1]} $a" }.getOrDefault(aaaaMm)

/** Etiquetas de un gasto: se repite, monto variable, cuotas, alias, IPC, desde. */
fun etiquetas(it: Item): List<String> = listOfNotNull(
    if (it.f != false) "🔁 se repite" else null,
    if (it.f != false && it.v == true) "✏️ monto variable" else null,
    it.q?.takeIf { q -> q.total > 1 }?.let { q -> "🧾 cuota ${q.actual} de ${q.total}" },
    it.extra["mp"].texto()?.let { a -> "🏷️ $a" },
    (it.extra["ipc"] as? kotlinx.serialization.json.JsonObject)?.get("prox").texto()?.let { p -> "📈 IPC: ${mesYAnio(p)}" },
    it.d?.let { d -> "desde el ${Fechas.diaYMes(d)}" },
)

/** "Pagar al cobrar": lo primero que se paga cuando cobrás. */
@Composable
fun TarjetaPagarAlCobrar(S: Estado, alPagado: (Int, Boolean) -> Unit, alDetalles: (Int) -> Unit, alAgregar: () -> Unit,
                         alPagarMP: (Int) -> Unit = {}, alIPC: (Int) -> Unit = {}) {
    val k = LocalColores.current
    var filtro by remember { mutableIntStateOf(if (S.items.any { !it.p && it.a > 0 }) 0 else 1) }   // 0 pendientes, 1 pagados, 2 todos
    val visibles = S.items.withIndex().filter { (_, it) -> it.n.isNotBlank() || it.a != 0.0 }
    val pendientes = visibles.count { !it.value.p }; val pagados = visibles.count { it.value.p }
    val totalPendiente = visibles.filter { !it.value.p }.sumOf { it.value.a }
    Tarjeta {
        Text("🧾 Pagar al cobrar · ${visibles.size} gastos", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = k.texto)
        Subtitulo("Lo primero que se paga cuando cobrás. Marcá 🔁 en los fijos de todos los meses; si el monto cambia (luz, gas), marcá también ✏️ y el mes que viene aparecen con el monto vacío.")
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF00A6E8), Color(0xFF0B6FB8))), RoundedCornerShape(20.dp)).padding(18.dp)) {
            Text("Total pendiente este mes", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
            Text(dinero(totalPendiente), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().background(k.fondo, RoundedCornerShape(50)).padding(4.dp)) {
            listOf("Pendientes ($pendientes)", "Ya pagados ($pagados)", "Todos (${visibles.size})").forEachIndexed { i, t ->
                Box(Modifier.weight(1f).background(if (filtro == i) k.tarjeta else Color.Transparent, RoundedCornerShape(50))
                    .clickable { filtro = i }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(t, fontSize = 13.sp, fontWeight = if (filtro == i) FontWeight.Bold else FontWeight.Normal,
                        color = if (filtro == i) k.texto else k.suave, textAlign = TextAlign.Center)
                }
            }
        }
        val lista = visibles.filter { filtro == 2 || (filtro == 0 && !it.value.p) || (filtro == 1 && it.value.p) }
        if (lista.isEmpty()) Text(if (filtro == 0) "¡Todo pagado! 🎉" else "Nada por acá.", color = k.suave, modifier = Modifier.padding(8.dp))
        lista.forEach { (i, it) ->
            Column(Modifier.fillMaxWidth().border(1.dp, k.linea, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(it.n.ifBlank { "Sin nombre" }, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = k.texto)
                    Box(Modifier.border(1.5.dp, if (it.p) k.verde else k.suave, RoundedCornerShape(50))
                        .background(if (it.p) k.verdeClaro else Color.Transparent, RoundedCornerShape(50))
                        .clickable { alPagado(i, !it.p) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(if (it.p) "✅ Pagado" else "⬜ Pendiente", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (it.p) k.verde else k.suave)
                    }
                }
                Text(dinero(it.a), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = if (it.p) k.verde else k.texto)
                val et = etiquetas(it)
                if (et.isNotEmpty()) Text(et.joinToString(" · "), fontSize = 13.sp, color = k.suave)
                Ipc.de(it)?.let { d ->
                    val conocidos = Ipc.conocidos(androidx.compose.ui.platform.LocalContext.current)
                    val toca = Ipc.toca(d, Fechas.hoy(), conocidos)
                    if (d.pend == null) Ipc.estimacion(it, conocidos)?.let { r ->
                        Text((if (r.estimados > 0) "📊 Estimado para ${mesYAnio(d.prox)}: " else "📊 Para ${mesYAnio(d.prox)}: ") +
                            dinero(r.cadaUno) + (if (d.partes > 1) " cada uno (${dinero(r.total)} total)" else "") +
                            " · +${"%.1f".format((r.factor - 1) * 100).replace('.', ',')}%" +
                            (if (r.estimados > 0) "\n${6 - r.estimados} de 6 meses ya publicados por el INDEC" else ""),
                            fontSize = 13.sp, color = k.texto, fontWeight = FontWeight.SemiBold)
                    }
                    Text("📈 Se actualiza por IPC cada ${d.cada} meses · próxima: ${mesYAnio(d.prox)}" +
                        (if (d.partes > 1) "\nTotal ${dinero(it.a * d.partes)} entre ${d.partes} (${dinero(it.a)} cada uno)" else "") +
                        (d.pend?.let { (m, desde) -> "\n✅ Ya calculado: ${dinero(m)}${if (d.partes > 1) " cada uno" else ""} desde ${mesYAnio(desde)}" } ?: ""),
                        fontSize = 12.sp, color = k.suave)
                    OutlinedButton(onClick = { alIPC(i) }, Modifier.demo("plan.ipc.$i", accion = { alIPC(i) }), colors = ButtonDefaults.outlinedButtonColors(contentColor = if (toca) k.naranja else k.verde)) {
                        Text(if (toca) "⚠️ Toca actualizar" else "Calcular actualización", fontSize = 13.sp) }
                }
                if (!it.p && it.a > 0) BotonMP("Pagar en Mercado Pago ↗", Modifier.fillMaxWidth()) { alPagarMP(i) }
                OutlinedButton(onClick = { alDetalles(i) }, Modifier.fillMaxWidth()) { Text("Detalles") }
            }
        }
        OutlinedButton(onClick = alAgregar, Modifier.fillMaxWidth()) { Text("+ Agregar gasto importante") }
    }
}

/** Resumen de cuotas: cuántas te quedan y cuánto falta pagar. */
@Composable
fun TarjetaCuotas(S: Estado) {
    val k = LocalColores.current
    val cuotas = S.items.filter { it.q != null && it.q.total > 1 }
    if (cuotas.isEmpty()) return
    Tarjeta {
        Titulo("🧾 Cuotas")
        cuotas.forEach { it ->
            val q = it.q!!; val restan = maxOf(0, q.total - q.actual)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(it.n.ifBlank { "Gasto en cuotas" }, color = k.texto, fontWeight = FontWeight.SemiBold)
                    Text("Cuota ${q.actual} de ${q.total} · " + (if (restan == 0) "es la última 🎉" else "te quedan $restan"), fontSize = 12.sp, color = k.suave)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(dinero(it.a) + " por mes", fontSize = 13.sp, color = k.texto)
                    if (restan > 0) Text("Falta ${dinero(it.a * restan)}", fontSize = 12.sp, color = k.suave)
                }
            }
        }
        Renglon("Total que falta en cuotas", dinero(cuotas.sumOf { it.a * maxOf(0, it.q!!.total - it.q.actual) }))
    }
}

/** Detalles de un gasto, estilo Mercado Pago: total, estado, código y alias para copiar. */
@Composable
fun HojaDetalleGasto(it: Item, alCerrar: () -> Unit, alPagarMP: () -> Unit, alPagado: (Boolean) -> Unit, alEditar: () -> Unit, alCopiar: (String, String) -> Unit) {
    val k = LocalColores.current
    Hoja(alCerrar, "") {
        Box(Modifier.background(MercadoPago.AZUL.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text("● MERCADO PAGO", color = MercadoPago.AZUL, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text(it.n.ifBlank { "Gasto" }, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = k.texto)
        Column(Modifier.fillMaxWidth().background(k.fondo, RoundedCornerShape(20.dp)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TOTAL A PAGAR", fontSize = 13.sp, color = k.suave, letterSpacing = 2.sp)
            Text(dinero(it.a), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = k.verde)
            Text(if (it.p) "✅ Pagado" else "⏳ Pendiente", fontWeight = FontWeight.SemiBold, color = if (it.p) k.verde else k.naranja)
        }
        @Composable fun Dato(titulo: String, valor: String) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(titulo, fontSize = 13.sp, color = k.suave)
                    Text(valor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = k.texto)
                }
                OutlinedButton(onClick = { alCopiar(titulo, valor) }) { Text("📋 Copiar") }
            }
        }
        it.extra["cod"].texto()?.let { Dato("Código de barras / factura", it) }
        it.extra["mp"].texto()?.let { Dato("Alias / CVU / CBU", it) }
        if (!it.p) BotonMP("Pagar en Mercado Pago ↗", Modifier.fillMaxWidth(), alPagarMP)
        OutlinedButton(onClick = { alPagado(!it.p) }, Modifier.fillMaxWidth()) { Text(if (it.p) "↩️ Marcar como pendiente" else "✅ Marcar como pagado") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = alEditar) { Text("✏️ Editar datos") }
            Text("🛡️ Seguro: no se conecta a tu cuenta", fontSize = 13.sp, color = k.suave)
        }
        Text("Para buscar facturas de luz, gas o internet con tu DNI o número de cliente, usá \"Pagar servicios\" adentro de Mercado Pago.",
            fontSize = 12.sp, color = k.suave)
    }
}
