package com.miplata.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.datos.MEDIO_EFECTIVO
import com.miplata.kotlin.nucleo.*

/* ---------- El taller, por persona ----------
   Cada compra crea un renglón por compañero (si pagaste en dos negocios, son dos renglones).
   Acá se juntan: una tarjeta por persona y por día, con todo lo que pidió y cómo te pagó. */

/** Todo lo de una persona en un día del taller. `tp` = id del compañero, o "yo" para tu parte. */
data class GrupoTaller(val fecha: String, val tp: String, val nombre: String, val movs: List<Movimiento>) {
    private fun cobroEf(e: Movimiento) = (e.extra["cobMed"].texto() ?: e.extra["med"].texto()) == MEDIO_EFECTIVO
    private val cosas = movs.filter { it.amt > 0 }
    val esYo get() = tp == "yo"
    /** Lo que pidió (el total de sus cosas). */
    val pidio get() = cosas.sumOf { it.amt }
    val debe get() = if (esYo) 0.0 else cosas.filter { it.dev == 1 }.sumOf { it.amt }
    val pagoEfectivo get() = cosas.filter { it.dev == 2 && it.extra["cobMed"] != null && cobroEf(it) }.sumOf { it.amt }
    val pagoTransferencia get() = cosas.filter { it.dev == 2 && it.extra["cobMed"] != null && !cobroEf(it) }.sumOf { it.amt }
    /** Cobrado antes de que la app preguntara cómo. */
    val pagoSinDecir get() = cosas.filter { it.dev == 2 && it.extra["cobMed"] == null }.sumOf { it.amt }
    /** Plata que te dio y todavía le queda a favor. */
    val aFavor get() = -movs.filter { it.amt < 0 && it.dev == 1 }.sumOf { it.amt }
    /** "1 Rockstar · 2 Tortas de manteca" (sumando lo repetido de renglones partidos). */
    val queLlevo: String get() {
        val vistos = linkedSetOf<String>()
        movs.filter { it.amt > 0 }.forEach { e -> textoCosas(e).takeIf { it.isNotBlank() }?.let { vistos += it } }
        return vistos.joinToString(" · ")
    }
    companion object {
        fun textoCosas(e: Movimiento): String =
            if (e.note.startsWith("Taller · mi parte")) e.note.removePrefix("Taller · mi parte").trim()
            else e.note.removePrefix("Taller · ").substringAfter(":", "").trim()
    }
}

/** ¿Este movimiento es del taller (de un compañero, plata a favor o tu parte)? */
fun esDelTaller(e: Movimiento) = e.extra["tp"] != null || (e.extra["taller"] != null && e.note.startsWith("Taller · mi parte"))

/** Junta los movimientos del taller de un día por persona. Los internos (vueltos, plata a favor ya usada) no se muestran sueltos. */
fun gruposTaller(movs: List<Movimiento>, t: Taller): List<GrupoTaller> =
    movs.filter(::esDelTaller).groupBy { (it.date to (it.extra["tp"].texto() ?: "yo")) }
        .map { (clave, l) ->
            val (fecha, tp) = clave
            val nombre = if (tp == "yo") "Yo (mi parte)" else t.muchachos.firstOrNull { it.id == tp }?.mostrar
                ?: l.first().note.removePrefix("Taller · ").removePrefix("a favor de ").substringBefore(":")
            GrupoTaller(fecha, tp, nombre, l.filter { !(it.amt < 0 && it.dev == 2) })
        }
        .filter { it.movs.isNotEmpty() }
        .sortedWith(compareBy({ it.esYo }, { it.nombre }))

/** Circulito con la inicial, del color de cómo está: verde pagó, naranja debe. */
@Composable
private fun Inicial(nombre: String, color: Color, tam: Int = 40) {
    Box(Modifier.size(tam.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Text(nombre.trim().take(1).uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = (tam * 0.45).sp)
    }
}

/** Etiqueta redondeada: "💵 $800", "⏳ Debe $800", "🤝 $200 a favor". */
@Composable
fun Pastilla(texto: String, color: Color) {
    Text(texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PastillasDe(g: GrupoTaller) {
    val k = LocalColores.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (g.esYo) Pastilla("Gasto tuyo", k.suave)
        if (g.debe > 0.005) Pastilla("⏳ Debe ${dinero(g.debe)}", k.naranja)
        if (g.pagoEfectivo > 0.005) Pastilla("💵 ${dinero(g.pagoEfectivo)}", k.verde)
        if (g.pagoTransferencia > 0.005) Pastilla("📲 ${dinero(g.pagoTransferencia)}", k.verde)
        if (g.pagoSinDecir > 0.005) Pastilla("✅ ${dinero(g.pagoSinDecir)} ¿cómo?", k.suave)
        if (g.aFavor > 0.005) Pastilla("🤝 ${dinero(g.aFavor)} a favor", k.verde)
    }
}

/** Una tarjetita por persona en "Gastos y entradas". */
@Composable
fun FilaPersonaTaller(g: GrupoTaller, alTocar: () -> Unit) {
    val k = LocalColores.current
    val color = when { g.esYo -> k.suave; g.debe > 0.005 -> k.naranja; else -> k.verde }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
        .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).clickable(onClick = alTocar).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Inicial(g.nombre, color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔧 " + g.nombre, color = k.texto, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (g.pidio > 0) Text(dinero(g.pidio), fontWeight = FontWeight.Bold, color = k.texto, fontSize = 15.sp)
            }
            if (g.queLlevo.isNotBlank()) Text(g.queLlevo, color = k.suave, fontSize = 13.sp)
            PastillasDe(g)
        }
    }
}

/** La ficha de una persona: qué pidió, cómo te pagó cada cosa, cuánto debe y el botón "Me entregó". */
@Composable
fun HojaPersonaTaller(g: GrupoTaller, alCerrar: () -> Unit, alTocarMovimiento: (Movimiento) -> Unit,
                      alMeEntrego: (Cobro) -> Unit, alWhatsApp: () -> Unit, alDevolviFavor: (Movimiento) -> Unit) {
    val k = LocalColores.current
    var cobrando by remember { mutableStateOf(false) }
    val color = when { g.esYo -> k.suave; g.debe > 0.005 -> k.naranja; else -> k.verde }
    Hoja(alCerrar, g.nombre) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Inicial(g.nombre, color, 56)
            Column {
                Text(Fechas.diaYMes(g.fecha) + if (g.esYo) " · lo tuyo" else " · desayuno / merienda", color = k.suave, fontSize = 13.sp)
                Text(if (g.esYo) "Gastaste ${dinero(g.pidio)}" else if (g.debe > 0.005) "Te debe ${dinero(g.debe)}" else "✅ Pagó todo",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            }
        }
        /* Resumen grande: pidió / efectivo / transferencia / debe */
        if (!g.esYo) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            @Composable fun Cuadro(t: String, v: Double, c: Color, m: Modifier) =
                Column(m.clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = 0.12f)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t, fontSize = 12.sp, color = k.suave, textAlign = TextAlign.Center)
                    Text(dinero(v), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c)
                }
            Cuadro("Pidió", g.pidio, k.texto, Modifier.weight(1f))
            Cuadro("💵 Efectivo", g.pagoEfectivo, k.verde, Modifier.weight(1f))
            Cuadro("📲 Transf.", g.pagoTransferencia, k.verde, Modifier.weight(1f))
            Cuadro("⏳ Debe", g.debe, if (g.debe > 0.005) k.naranja else k.suave, Modifier.weight(1f))
        }
        if (g.aFavor > 0.005) Text("🤝 Le quedan ${dinero(g.aFavor)} a favor: se descuentan solos en lo próximo que pida.",
            fontSize = 14.sp, color = k.verde, fontWeight = FontWeight.SemiBold)
        if (g.pagoSinDecir > 0.005) Text("Hay ${dinero(g.pagoSinDecir)} cobrados sin decir cómo: tocá ese renglón y elegí efectivo o transferencia.",
            fontSize = 13.sp, color = k.naranja)

        HorizontalDivider(color = k.linea)
        Text("Lo que pidió", fontWeight = FontWeight.SemiBold, color = k.texto)
        g.movs.forEach { e ->
            val cobEf = (e.extra["cobMed"].texto() ?: e.extra["med"].texto()) == MEDIO_EFECTIVO
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { alTocarMovimiento(e) }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (e.amt < 0) "Plata que te dio" else GrupoTaller.textoCosas(e).ifBlank { "Compra" }, color = k.texto, fontSize = 15.sp)
                    when {
                        g.esYo -> Pastilla(if (e.extra["med"].texto() == MEDIO_EFECTIVO) "💵 pagado en efectivo" else "📲 pagado con Mercado Pago", k.suave)
                        e.amt < 0 -> Pastilla("🤝 a favor · " + if (e.extra["med"].texto() == MEDIO_EFECTIVO) "💵 efectivo" else "📲 transferencia", k.verde)
                        e.dev == 1 -> Pastilla("⏳ te lo debe", k.naranja)
                        e.extra["cobMed"] == null -> Pastilla("✅ pagó · tocá para decir cómo", k.suave)
                        cobEf -> Pastilla("💵 pagó en efectivo", k.verde)
                        else -> Pastilla("📲 pagó por transferencia", k.verde)
                    }
                }
                Text(dinero(Math.abs(e.amt)), fontWeight = FontWeight.SemiBold, color = if (e.amt < 0) k.verde else k.texto)
            }
            if (e.amt < 0) TextButton(onClick = { alDevolviFavor(e) }) { Text("Ya se lo devolví", fontSize = 12.sp) }
        }
        if (!g.esYo) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = { cobrando = true }, Modifier.fillMaxWidth().height(54.dp)) {
                Text(if (g.debe > 0.005) "💵 Me entregó plata (debe ${dinero(g.debe)})" else "💵 Me dio plata a cuenta", fontSize = 16.sp)
            }
            if (g.debe > 0.005) OutlinedButton(onClick = alWhatsApp, Modifier.fillMaxWidth()) { Text("📲 Mandarle el cobro por WhatsApp") }
        }
    }
    if (cobrando) DialogoCobro(g.nombre.substringAfter("(").substringBefore(")").takeIf { g.nombre.contains("(") } ?: g.nombre,
        g.debe, alCerrar = { cobrando = false }, puedeFavor = true, aFavorAntes = 0.0) { c -> cobrando = false; alMeEntrego(c) }
}
