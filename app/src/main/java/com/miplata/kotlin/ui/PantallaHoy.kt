package com.miplata.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Share
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
                alCobrar: (Deuda) -> Unit = {}, alNoPagaron: (Deuda) -> Unit = {}, alPeriodoNuevo: () -> Unit = {}, alIrAPlan: () -> Unit = {},
                alOjo: () -> Unit = {}, alCompartir: () -> Unit = {}, alSalirDemo: () -> Unit = {},
                avisoCopia: (@Composable () -> Unit)? = null) {
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
            if (S.ocultar) IconButton(onClick = alOjo) { Icon(if (Privacidad.oculto) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                contentDescription = if (Privacidad.oculto) "Mostrar montos" else "Ocultar montos", tint = k.suave) }
            IconButton(onClick = alCompartir) { Icon(Icons.Outlined.Share, contentDescription = "Compartir resumen", tint = k.suave) }
            if (racha.racha > 0) Row(
                Modifier.background(k.verdeClaro, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocalFireDepartment, null, tint = k.verde, modifier = Modifier.size(18.dp))
                Text(" ${racha.racha}", color = k.verde, fontWeight = FontWeight.Bold)
            }
        }

        if (S.demo) Row(Modifier.fillMaxWidth().background(k.naranja, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("🎬 Modo demo: datos de ejemplo", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = alSalirDemo, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Salir") }
        }
        avisoCopia?.invoke()

        /* Cerca del cobro: atajo para empezar el período nuevo */
        val diasAlFin = Fechas.dia(S.end) - Fechas.dia(hoy)
        if (diasAlFin <= 2) Row(Modifier.fillMaxWidth().background(k.verdeClaro, RoundedCornerShape(18.dp)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (diasAlFin <= 0) "¡Ya cobraste!" else "Cobrás el ${Fechas.diaYMes(S.end)}", fontWeight = FontWeight.Bold, color = k.verde)
                Text("Cuando cobres, empezá el período nuevo.", fontSize = 13.sp, color = k.suave)
            }
            Button(onClick = alPeriodoNuevo) { Text("Empezar") }
        }

        /* Aviso de IPC: toca actualizar un gasto (por ejemplo, el pago a Alejandro) */
        S.items.filter { it -> Ipc.de(it)?.let { d -> Ipc.toca(d, hoy) } == true }.forEach { it ->
            Row(Modifier.fillMaxWidth().background(k.tarjeta, RoundedCornerShape(18.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("📈 Toca actualizar: ${it.n}", fontWeight = FontWeight.Bold, color = k.naranja)
                    Text("Desde ${mesYAnio(Ipc.de(it)!!.prox)} el monto se actualiza por IPC del INDEC.", fontSize = 13.sp, color = k.suave)
                }
                TextButton(onClick = alIrAPlan) { Text("Ver") }
            }
        }

        /* Lo principal: cuánto te queda hoy */
        Column(Modifier.fillMaxWidth().background(fondoHero, RoundedCornerShape(26.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Te queda para hoy", color = sobreHero.copy(alpha = 0.85f), fontSize = 15.sp)
            Text(dineroCentavos(c.quedaHoy), color = sobreHero, fontSize = 40.sp, fontFamily = Serif, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { fraccion }, modifier = Modifier.fillMaxWidth().height(8.dp),
                color = sobreHero, trackColor = sobreHero.copy(alpha = 0.25f), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {})
            Text("Presupuesto ${dinero(presupuesto)} · " + (if (c.gastoHoy < 0) "entró ${dinero(-c.gastoHoy)}" else "gastaste ${dinero(c.gastoHoy)}"), color = sobreHero.copy(alpha = 0.85f), fontSize = 14.sp)
            c.manana?.let { Text("Mañana tendrás ${dinero(it)}", color = sobreHero, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            if (c.fondo.activo && c.fondo.hoySuma > 0)
                Text("Lo que no gastes hoy va a tu fondo: ${dinero(c.fondo.hoySuma)}", color = sobreHero.copy(alpha = 0.85f), fontSize = 13.sp)
        }

        /* Dos números de un vistazo */
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (c.fondo.activo) {
                Mini("Disponible", dinero(c.fondo.disponible), Modifier.weight(1f).clickable(onClick = alAbrirCuentas))
                Mini("Fondo de ahorro", dinero(c.fondo.total + if (c.fondo.rendEnMP) c.fondo.rend else 0.0), Modifier.weight(1f).clickable(onClick = alAbrirFondo),
                    extra = if (S.rendOn && S.rendTasa > 0) "↑ Rinde ${formatoTasa(S.rendTasa)}%" else null)
            } else {
                Mini("Saldo del período", dinero(c.saldo), Modifier.weight(1f).clickable(onClick = alAbrirCuentas),
                    extra = if (S.rendDispOn && S.rendTasa > 0) "↑ Rinde ${formatoTasa(S.rendTasa)}%" else null)
                Mini("Días que faltan", c.faltan.toString(), Modifier.weight(1f))
            }
        }
        Text("Faltan ${c.faltan} días (con hoy) · base del día ${dinero(c.baseHoy)}", fontSize = 13.sp, color = k.suave,
            modifier = Modifier.padding(horizontal = 4.dp))

        /* Comparar con las cuentas */
        val cc = Nucleo.conciliar(S, c)
        Row(Modifier.fillMaxWidth().background(k.tarjeta, RoundedCornerShape(18.dp)).clickable(onClick = alAbrirCuentas).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Mis cuentas", fontWeight = FontWeight.SemiBold, color = k.texto)
                Text(if (cc.hayCuentas) "En cuentas: ${dinero(cc.total)}" else "Cargá tu saldo de Mercado Pago para comparar", fontSize = 13.sp, color = k.suave)
            }
            if (cc.hayCuentas) {
                val ok = Math.abs(cc.diferencia) < 1
                Text(if (ok) "Coincide" else dinero(cc.diferencia), fontWeight = FontWeight.Bold, color = if (ok) k.verde else k.naranja)
            }
        }

        /* Me deben */
        val debo = deudas(S)
        if (debo.isNotEmpty()) TarjetaMeDeben(debo, alCobrar, alNoPagaron)

        /* Mi fondo de ahorro, con cómo fue creciendo */
        if (c.fondo.activo) TarjetaFondo(S, c, alAbrirFondo)

        /* Para pasar a tu reserva (fondo de ahorro), justo abajo del fondo, como en Mi plata.
           "Ya lo pasé" se puede tocar también a la noche, cuando pasás lo de hoy (antes de que cierre el día). */
        if (c.fondo.activo) {
            val anteriores = c.fondo.total - S.resPasado          // de días ya cerrados, sin pasar
            val conHoy = anteriores + c.fondo.hoySuma               // + lo que sobra hoy
            if (conHoy >= 1 || conHoy <= -1) Column(Modifier.fillMaxWidth()
                .border(1.5.dp, k.verde, RoundedCornerShape(22.dp)).background(k.tarjeta, RoundedCornerShape(22.dp)).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🏦 Para pasar a tu reserva", color = k.suave, fontSize = 16.sp)
                if (conHoy >= 1) {
                    Text(dinero(conHoy), fontSize = 34.sp, fontFamily = Serif, fontWeight = FontWeight.Bold, color = k.verde)
                    Text(when {
                        anteriores >= 1 && c.fondo.hoySuma >= 1 -> "${dinero(anteriores)} de días anteriores + ${dinero(c.fondo.hoySuma)} que te sobran hoy."
                        anteriores >= 1 -> "Es lo que fue al fondo y todavía no pasaste a tu reserva."
                        anteriores <= -1 -> "Ya pasaste una parte de lo de hoy. Te falta esto."
                        else -> "Es lo que te sobra hoy. Pasalo a la noche, cuando ya no vayas a gastar."
                    }, fontSize = 14.sp, color = k.suave)
                    BotonMP("💙 Abrir Mercado Pago", Modifier.fillMaxWidth(), alAbrirMP)
                    Button(onClick = { alYaPase(conHoy) }, Modifier.fillMaxWidth()) { Text("✅ Ya lo pasé") }
                } else Text("Pasaste a la reserva ${dinero(-conHoy)} de más (o sacaste del fondo). Retirá esa plata de la reserva para que coincida.",
                    fontSize = 14.sp, color = k.naranja)
            }
        }

        /* Gastos y entradas del período */
        TarjetaGastosYEntradas(S, alTocarMovimiento)

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
        Text(if (entrada) "+" + dinero(-e.amt) else dinero(e.amt), fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            color = if (entrada) k.verde else k.texto)
    }
}
