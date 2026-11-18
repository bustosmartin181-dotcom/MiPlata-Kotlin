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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
                alCobrar: (Deuda, Cobro) -> Unit = { _, _ -> }, alNoPagaron: (Deuda) -> Unit = {}, alDevolviFavor: (Deuda) -> Unit = {}, alPeriodoNuevo: () -> Unit = {}, alIrAPlan: () -> Unit = {},
                alOjo: () -> Unit = {}, alCompartir: () -> Unit = {}, alSalirDemo: () -> Unit = {}, alReproducirDemo: () -> Unit = {},
                avisoCopia: (@Composable () -> Unit)? = null, alIA: () -> Unit = {},
                alAnotar: () -> Unit = {}, alDictar: () -> Unit = {}, alPlanes: () -> Unit = {}, alCobrarSeccion: () -> Unit = {},
                editandoBloques: Boolean = false, alEditarBloques: () -> Unit = {}, alTerminarEdicion: () -> Unit = {},
                taller: Taller = Taller(), alAnotarTaller: () -> Unit = {}, alGuardarTaller: (Taller) -> Unit = {}) {
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
        Row(Modifier.demo("hoy.arriba"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${saludoSegunHora(LocalTime.now().hour)}, $nombre", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = k.texto)
                Text(fraseDeEstado(c, clase), fontSize = 14.sp, color = k.suave)
            }
            if (S.ocultar) IconButton(onClick = alOjo) { Icon(if (Privacidad.oculto) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                contentDescription = if (Privacidad.oculto) "Mostrar montos" else "Ocultar montos", tint = k.suave) }
            IconButton(onClick = alEditarBloques) { Icon(Icons.Outlined.Edit, contentDescription = "Ordenar bloques", tint = if (editandoBloques) k.verde else k.suave) }
            IconButton(onClick = alIA) { Text("✨", fontSize = 20.sp) }
            IconButton(onClick = alCompartir) { Icon(Icons.Outlined.Share, contentDescription = "Compartir resumen", tint = k.suave) }
            if (racha.racha > 0) Row(
                Modifier.background(k.verdeClaro, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocalFireDepartment, null, tint = k.verde, modifier = Modifier.size(18.dp))
                Text(" ${racha.racha}", color = k.verde, fontWeight = FontWeight.Bold)
            }
        }

        if (S.demo && !Demo.corriendo) Row(Modifier.fillMaxWidth().background(k.naranja, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🎬 Modo demo: datos de ejemplo", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Tocá ▶️ para que la demo se reproduzca sola", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
            }
            Button(onClick = alReproducirDemo, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = k.naranja)) { Text("▶️ Reproducir") }
            TextButton(onClick = alSalirDemo, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("Salir") }
        }
        avisoCopia?.invoke()

        /* Compras del taller: en el horario del desayuno o la merienda aparece solo (se revisa cada 30 segundos) */
        var hora by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(horaAhora()) }
        androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); hora = horaAhora() } }
        val turnoHoy = taller.turno(hoy)
        /* Desde que empieza el desayuno/merienda hasta que se termina de estirar (10:40 / 19:00), mientras no anotaste la compra */
        val desdeTaller = taller.ventana(hoy)?.first
        val hastaTaller = taller.horaCobro(hoy)
        if (turnoHoy != null && desdeTaller != null && hastaTaller != null && taller.activos.isNotEmpty() && !S.demo &&
            Taller.minutos(hora) >= Taller.minutos(desdeTaller) && Taller.minutos(hora) <= Taller.minutos(hastaTaller))
            CartelTaller(turnoHoy, taller, hoy, alAnotarTaller, hayCompra = S.hayCompraTaller(hoy))

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
        val ipcConocidos = Ipc.conocidos(androidx.compose.ui.platform.LocalContext.current)
        S.items.filter { it -> Ipc.de(it)?.let { d -> Ipc.toca(d, hoy, ipcConocidos) } == true }.forEach { it ->
            Row(Modifier.fillMaxWidth().demo("hoy.ipc")
                .then(if (Demo.resaltado == "hoy.ipc") Modifier.border(3.dp, k.naranja, RoundedCornerShape(18.dp)) else Modifier)
                .bordeBloque(18).background(k.tarjeta, RoundedCornerShape(18.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("📈 Toca actualizar: ${it.n}", fontWeight = FontWeight.Bold, color = k.naranja)
                    Text("Ya salió la inflación que se usa. Calculá el monto nuevo para pagar desde ${mesYAnio(Ipc.de(it)!!.prox)}.", fontSize = 13.sp, color = k.suave)
                }
                TextButton(onClick = alIrAPlan, Modifier.demo("hoy.ipc.ver", alIrAPlan)) { Text("Ver") }
            }
        }

        /* ---------- Bloques, como el panel de One UI: con ✏️ se suben, bajan, ocultan o agregan ---------- */
        val debo = deudas(S)
        val cc = Nucleo.conciliar(S, c)
        val anteriores = Nucleo.faltaPasar(S, c)              // de días ya cerrados, sin pasar a la reserva
        /* Lo de hoy se suma recién desde 2 horas antes del aviso de la noche (con aviso a las 22, desde las 20):
           a la mañana o a la madrugada todavía es tu plata para gastar en el día. */
        val (hAviso, mAviso) = S.resHora.split(":").map { it.toIntOrNull() ?: 0 }
        val ahora = LocalTime.now()
        val esDeNoche = ahora >= LocalTime.of(hAviso.coerceIn(0, 23), mAviso.coerceIn(0, 59)).minusHours(2) && ahora.hour >= 12
        val conHoy = anteriores + (if (esDeNoche) c.fondo.hoySuma else 0.0)
        val hayBloque: (String) -> Boolean = { id -> when (id) {
            "medeben" -> debo.isNotEmpty()
            "fondo" -> c.fondo.activo
            "reserva" -> c.fondo.activo && (conHoy >= 1 || conHoy <= -1)
            else -> true } }

        BloquesHoy(editandoBloques, alTerminar = alTerminarEdicion, hay = hayBloque) { id ->
            when (id) {
                /* Lo principal: cuánto te queda hoy */
                "hoy" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.fillMaxWidth().background(fondoHero, RoundedCornerShape(26.dp)).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Te queda para hoy", color = sobreHero.copy(alpha = 0.85f), fontSize = 15.sp)
                        Text(dineroCentavos(numeroAnimado(c.quedaHoy)), color = sobreHero, fontSize = 40.sp, fontFamily = Serif, fontWeight = FontWeight.Bold)
                        val barra by androidx.compose.animation.core.animateFloatAsState(fraccion, androidx.compose.animation.core.tween(900), label = "barra")
                        LinearProgressIndicator(progress = { barra }, modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = sobreHero, trackColor = sobreHero.copy(alpha = 0.25f), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {})
                        Text("Presupuesto ${dinero(presupuesto)} · " + (if (c.gastoHoy < 0) "entró ${dinero(-c.gastoHoy)}" else "gastaste ${dinero(c.gastoHoy)}"), color = sobreHero.copy(alpha = 0.85f), fontSize = 14.sp)
                        c.manana?.let {
                            val luz = Demo.resaltado == "hoy.manana"
                            Text("Mañana tendrás ${dinero(it)}", Modifier.demo("hoy.manana")
                                .then(if (luz) Modifier.border(3.dp, sobreHero, RoundedCornerShape(10.dp)).background(sobreHero.copy(alpha = 0.18f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp) else Modifier),
                                color = sobreHero, fontSize = if (luz) 21.sp else 16.sp, fontWeight = if (luz) FontWeight.Bold else FontWeight.SemiBold)
                        }
                        if (c.fondo.activo && c.fondo.hoySuma > 0)
                            Text("Lo que no gastes hoy va a tu fondo: ${dinero(c.fondo.hoySuma)}", color = sobreHero.copy(alpha = 0.85f), fontSize = 13.sp)
                    }
                    Text("Faltan ${c.faltan} días (con hoy) · base del día ${dinero(c.baseHoy)}", fontSize = 13.sp, color = k.suave,
                        modifier = Modifier.padding(horizontal = 4.dp))
                }
                /* Dos números de un vistazo */
                "saldo" -> if (c.fondo.activo)
                    Mini("Disponible", dinero(c.fondo.disponible), Modifier.fillMaxWidth().clickable(onClick = alAbrirCuentas))
                else Mini("Saldo del período", dinero(c.saldo), Modifier.fillMaxWidth().demo("hoy.minis").luz("hoy.minis").clickable(onClick = alAbrirCuentas),
                    extra = if (S.rendDispOn && S.rendTasa > 0) "↑ Rinde ${formatoTasa(S.rendTasa)}%" else null)
                "dias" -> if (c.fondo.activo)
                    Mini("Fondo de ahorro", dinero(c.fondo.total + if (c.fondo.rendEnMP) c.fondo.rend else 0.0),
                        Modifier.fillMaxWidth().demo("hoy.minis").luz("hoy.minis").clickable(onClick = alAbrirFondo),
                        extra = if (S.rendOn && S.rendTasa > 0) "↑ Rinde ${formatoTasa(S.rendTasa)}%" else null)
                else Mini("Días que faltan", c.faltan.toString(), Modifier.fillMaxWidth())
                /* Comparar con las cuentas */
                "cuentas" -> {
                    val ok = cc.hayCuentas && Math.abs(cc.diferencia) < 1
                    val casi = cc.hayCuentas && !ok && Math.abs(cc.diferencia) < CASI_COINCIDE
                    Mini("Mis cuentas", if (!cc.hayCuentas) "Cargá tu saldo" else if (ok) "Coincide" else if (casi) "Casi coincide" else dinero(cc.diferencia),
                        Modifier.fillMaxWidth().demo("hoy.cuentas", alAbrirCuentas).luz("hoy.cuentas").clickable(onClick = alAbrirCuentas),
                        extra = if (cc.hayCuentas) "En cuentas: ${dinero(cc.total)}" else "de Mercado Pago para comparar",
                        colorValor = if (!cc.hayCuentas) k.suave else if (ok || casi) k.verde else k.naranja)
                }
                "accesos" -> AccesosRapidos(alAnotar, alDictar, alAbrirMP, alPlanes, alIA, alCompartir, alCobrarSeccion)
                "medeben" -> Box(Modifier.demo("hoy.medeben").luz("hoy.medeben")) { TarjetaMeDeben(debo, alCobrar, alNoPagaron, alDevolviFavor, taller, alGuardarTaller) }
                /* Mi fondo de ahorro, con cómo fue creciendo */
                "fondo" -> Box(Modifier.demo("hoy.fondo").luz("hoy.fondo")) { TarjetaFondo(S, c, alAbrirFondo) }
                /* Para pasar a tu reserva: "Ya lo pasé" se puede tocar también a la noche, antes de que cierre el día */
                "reserva" -> Column(Modifier.fillMaxWidth().demo("hoy.reserva").luz("hoy.reserva")
                    .border(1.5.dp, k.verde, RoundedCornerShape(22.dp)).background(k.tarjeta, RoundedCornerShape(22.dp)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🏦 Para pasar a tu reserva", color = k.suave, fontSize = 16.sp)
                    if (conHoy >= 1) {
                        Text(dinero(conHoy), fontSize = 34.sp, fontFamily = Serif, fontWeight = FontWeight.Bold, color = k.verde)
                        Text(when {
                            anteriores >= 1 && esDeNoche && c.fondo.hoySuma >= 1 -> "${dinero(anteriores)} de días anteriores + ${dinero(c.fondo.hoySuma)} que te sobran hoy."
                            anteriores >= 1 -> "Es lo que fue al fondo y todavía no pasaste a tu reserva."
                            anteriores <= -1 -> "Ya pasaste una parte de lo de hoy. Te falta esto."
                            else -> "Es lo que te sobra hoy. Pasalo a la noche, cuando ya no vayas a gastar."
                        }, fontSize = 14.sp, color = k.suave)
                        BotonMP("💙 Abrir Mercado Pago", Modifier.fillMaxWidth(), alAbrirMP)
                        Button(onClick = { alYaPase(conHoy) }, Modifier.fillMaxWidth()) { Text("✅ Ya lo pasé") }
                    } else Text("Pasaste a la reserva ${dinero(-conHoy)} de más (o sacaste del fondo). Retirá esa plata de la reserva para que coincida.",
                        fontSize = 14.sp, color = k.naranja)
                }
                /* Gastos y entradas del período */
                "movimientos" -> TarjetaGastosYEntradas(S, alTocarMovimiento)
                "frase" -> Text("“${fraseDelDia(hoy)}”", fontStyle = FontStyle.Italic, color = k.suave, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

fun formatoTasa(t: Double): String = if (t % 1.0 == 0.0) t.toLong().toString() else t.toString().replace('.', ',')

@Composable
fun Mini(titulo: String, valor: String, modifier: Modifier = Modifier, extra: String? = null, colorValor: Color? = null) {
    val k = LocalColores.current
    Column(modifier.fillMaxHeight().bordeBloque(18).background(k.tarjeta, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(titulo, fontSize = 13.sp, color = k.suave)
        Text(valor, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colorValor ?: k.texto)
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
            /* cómo se pagó o cobró: efectivo (billetera) o transferencia (Mercado Pago) */
            val ef = com.miplata.kotlin.datos.MEDIO_EFECTIVO
            val pagoEf = e.extra["med"].texto() == ef
            val cobroEf = (e.extra["cobMed"].texto() ?: e.extra["med"].texto()) == ef
            val favor = e.amt < 0 && e.extra["tp"] != null
            val estado = when {
                favor && e.dev == 1 -> "🤝 te lo dio, queda a su favor" + if (pagoEf) " · 💵 en efectivo" else " · 📲 por transferencia"
                favor && e.dev == 2 -> if (e.extra["vuelto"] != null) "🪙 le diste el vuelto" else "✅ ya se usó para pagar lo suyo"
                e.dev == 1 -> "te lo deben"
                e.dev == 2 && e.extra["cobMed"] == null && e.extra["med"] == null -> "✅ me pagó (tocá para decir cómo)"
                e.dev == 2 -> if (cobroEf) "✅ me pagó en efectivo 💵" else "✅ me pagó por transferencia 📲"
                pagoEf -> if (entrada) "💵 entró en efectivo" else "💵 pagado en efectivo"
                else -> null
            }
            val detalle = listOfNotNull(e.cat.takeIf { !entrada }, estado).joinToString(" · ")
            if (detalle.isNotEmpty()) Text(detalle, color = when { favor && e.dev == 1 -> k.verde; e.dev == 1 -> k.naranja; else -> k.suave }, fontSize = 12.sp)
        }
        Text(if (entrada) "+" + dinero(-e.amt) else dinero(e.amt), fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            color = if (entrada) k.verde else k.texto)
    }
}
