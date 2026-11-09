package com.miplata.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.*
import java.time.LocalTime

@Composable
fun PantallaHoy(S: Estado, nombre: String, alTocarMovimiento: (Movimiento) -> Unit,
                alAbrirFondo: () -> Unit = {}, alAbrirCuentas: () -> Unit = {}, alYaPase: (Double) -> Unit = {}, alAbrirMP: () -> Unit = {},
                alCobrar: (Deuda) -> Unit = {}, alNoPagaron: (Deuda) -> Unit = {}) {
    val k = LocalColores.current
    val hoy = Fechas.hoy()
    val c = Nucleo.calcular(S, hoy)
    val (clase, _) = Nucleo.semaforo(c, S)
    val racha = Nucleo.rachaEnVerde(S, hoy)
    val presupuesto = c.quedaHoy + c.gastoHoy
    val fraccion = if (presupuesto > 0) (c.quedaHoy / presupuesto).coerceIn(0.0, 1.0).toFloat() else 0f
    val fondoHero = when (clase) { "neg" -> k.rojo; "warn" -> k.naranja; else -> k.verde }
    val sobreHero = if (clase == "") k.sobreVerde else Color.White

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${saludoSegunHora(LocalTime.now().hour)}, $nombre", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = k.texto)
                Text(fraseDeEstado(c, clase), fontSize = 14.sp, color = k.suave)
            }
            if (racha.racha > 0) Row(
                Modifier.background(k.verdeClaro, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocalFireDepartment, null, tint = k.verde, modifier = Modifier.size(18.dp))
                Text(" ${racha.racha}", color = k.verde, fontWeight = FontWeight.Bold)
            }
        }

        /* Lo principal: cuánto te queda hoy */
        Column(Modifier.fillMaxWidth().background(fondoHero, RoundedCornerShape(26.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Te queda para hoy", color = sobreHero.copy(alpha = 0.85f), fontSize = 15.sp)
            Text(pesosCentavos(c.quedaHoy), color = sobreHero, fontSize = 40.sp, fontFamily = Serif, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { fraccion }, modifier = Modifier.fillMaxWidth().height(8.dp),
                color = sobreHero, trackColor = sobreHero.copy(alpha = 0.25f), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {})
            Text("Presupuesto ${pesos(presupuesto)} · " + (if (c.gastoHoy < 0) "entró ${pesos(-c.gastoHoy)}" else "gastaste ${pesos(c.gastoHoy)}"), color = sobreHero.copy(alpha = 0.85f), fontSize = 14.sp)
            c.manana?.let { Text("Mañana tendrás ${pesos(it)}", color = sobreHero, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            if (c.fondo.activo && c.fondo.hoySuma > 0)
                Text("Lo que no gastes hoy va a tu fondo: ${pesos(c.fondo.hoySuma)}", color = sobreHero.copy(alpha = 0.85f), fontSize = 13.sp)
        }

        /* Dos números de un vistazo */
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (c.fondo.activo) {
                Mini("Disponible", pesos(c.fondo.disponible), Modifier.weight(1f).clickable(onClick = alAbrirCuentas))
                Mini("Fondo de ahorro", pesos(c.fondo.total + if (c.fondo.rendEnMP) c.fondo.rend else 0.0), Modifier.weight(1f).clickable(onClick = alAbrirFondo),
                    extra = if (S.rendOn && S.rendTasa > 0) "↑ Rinde ${formatoTasa(S.rendTasa)}%" else null)
            } else {
                Mini("Saldo del período", pesos(c.saldo), Modifier.weight(1f).clickable(onClick = alAbrirCuentas),
                    extra = if (S.rendDispOn && S.rendTasa > 0) "↑ Rinde ${formatoTasa(S.rendTasa)}%" else null)
                Mini("Días que faltan", c.faltan.toString(), Modifier.weight(1f))
            }
        }
        Text("Faltan ${c.faltan} días (con hoy) · base del día ${pesos(c.baseHoy)}", fontSize = 13.sp, color = k.suave,
            modifier = Modifier.padding(horizontal = 4.dp))

        /* Para pasar a tu reserva (fondo de ahorro) */
        if (c.fondo.activo) {
            val pendiente = c.fondo.total - S.resPasado
            if (pendiente >= 1 || c.fondo.hoySuma >= 1 || pendiente <= -1) Tarjeta(color = k.verdeClaro) {
                Text("Para pasar a tu reserva", color = k.verde, fontWeight = FontWeight.SemiBold)
                if (pendiente >= 1) {
                    Text(pesos(pendiente), fontSize = 28.sp, fontFamily = Serif, fontWeight = FontWeight.Bold, color = k.texto)
                    Text("Es lo que fue al fondo y todavía no pasaste." + (if (c.fondo.hoySuma >= 1) " Esta noche se suman ${pesos(c.fondo.hoySuma)} de hoy." else ""),
                        fontSize = 13.sp, color = k.suave)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = alAbrirMP, Modifier.weight(1f)) { Text("Abrir Mercado Pago") }
                        Button(onClick = { alYaPase(pendiente) }, Modifier.weight(1f)) { Text("Ya lo pasé") }
                    }
                } else if (pendiente <= -1) Text("Sacaste del fondo más de lo que tenías sin pasar: retirá ${pesos(-pendiente)} de la reserva para que coincida.",
                    fontSize = 14.sp, color = k.naranja)
                else Text("Esta noche vas a tener ${pesos(c.fondo.hoySuma)} para pasar (lo que te sobra de hoy).", fontSize = 14.sp, color = k.texto)
            }
        }

        /* Comparar con las cuentas */
        val cc = Nucleo.conciliar(S, c)
        Row(Modifier.fillMaxWidth().background(k.tarjeta, RoundedCornerShape(18.dp)).clickable(onClick = alAbrirCuentas).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mis cuentas", fontWeight = FontWeight.SemiBold, color = k.texto)
                Text(if (cc.hayCuentas) "En cuentas: ${pesos(cc.total)}" else "Cargá tu saldo de Mercado Pago para comparar", fontSize = 13.sp, color = k.suave)
            }
            if (cc.hayCuentas) {
                val ok = Math.abs(cc.diferencia) < 1
                Text(if (ok) "Coincide" else pesos(cc.diferencia), fontWeight = FontWeight.Bold, color = if (ok) k.verde else k.naranja)
            }
        }

        /* Me deben */
        val debo = deudas(S)
        if (debo.isNotEmpty()) TarjetaMeDeben(debo, alCobrar, alNoPagaron)

        /* Movimientos de hoy */
        val deHoy = S.spent.filter { it.date == hoy && !it.esMeta }.sortedByDescending { it.id }
        Tarjeta {
            Titulo("Hoy")
            if (deHoy.isEmpty()) Text("Sin movimientos todavía. Tocá + para anotar.", color = k.suave, fontSize = 14.sp)
            deHoy.forEach { e -> FilaMovimiento(e) { alTocarMovimiento(e) } }
        }

        Text("“${fraseDelDia(hoy)}”", fontStyle = FontStyle.Italic, color = k.suave, fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        Spacer(Modifier.height(90.dp))
    }
}

fun formatoTasa(t: Double): String = if (t % 1.0 == 0.0) t.toLong().toString() else t.toString().replace('.', ',')

@Composable
fun Mini(titulo: String, valor: String, modifier: Modifier = Modifier, extra: String? = null) {
    val k = LocalColores.current
    Column(modifier.fillMaxHeight().background(k.tarjeta, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(titulo, fontSize = 13.sp, color = k.suave)
        Text(valor, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = k.texto)
        extra?.let { Text(it, fontSize = 12.sp, color = k.verde, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun FilaMovimiento(e: Movimiento, alTocar: () -> Unit) {
    val k = LocalColores.current
    val entrada = e.amt < 0
    Row(Modifier.fillMaxWidth().clickable(onClick = alTocar).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(e.note.ifBlank { if (entrada) "Entrada" else (e.cat ?: "Gasto") }, color = k.texto, fontSize = 15.sp)
            val detalle = listOfNotNull(e.cat.takeIf { !entrada },
                when (e.dev) { 1 -> "te lo deben"; 2 -> "devuelto"; else -> null }).joinToString(" · ")
            if (detalle.isNotEmpty()) Text(detalle, color = if (e.dev == 1) k.naranja else k.suave, fontSize = 12.sp)
        }
        Text(if (entrada) "+" + pesos(-e.amt) else pesos(e.amt), fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            color = if (entrada) k.verde else k.texto)
    }
}
