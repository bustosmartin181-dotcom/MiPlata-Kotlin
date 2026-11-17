package com.miplata.kotlin.ui

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Bloques de la pantalla Hoy, como el panel de One UI: con ✏️ se suben, se bajan, se ocultan
   y se vuelven a agregar. Los chiquitos van de a dos por fila. La app recuerda cómo los dejaste. */
/** Qué bloques tiene una pantalla, cuáles son chiquitos (van de a dos), sus nombres y cuáles no se pueden ocultar. */
class ConfigBloques(val clave: String, val todos: List<String>, val nombres: Map<String, String>,
                    val chicos: Set<String> = emptySet(), val fijos: Set<String> = emptySet()) {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("bloques_$clave", Context.MODE_PRIVATE)
    fun orden(ctx: Context): List<String> {
        val guardado = prefs(ctx).getString("orden", null)?.split(",")?.filter { it in todos } ?: emptyList()
        return guardado + todos.filter { it !in guardado }          // los bloques nuevos aparecen al final
    }
    fun ocultos(ctx: Context): Set<String> = prefs(ctx).getString("ocultos", "")!!.split(",").filter { it in todos }.toSet()
    fun guardar(ctx: Context, orden: List<String>, ocultos: Set<String>) =
        prefs(ctx).edit().putString("orden", orden.joinToString(",")).putString("ocultos", ocultos.joinToString(",")).apply()
}

object Bloques {
    val HOY = ConfigBloques("hoy",
        listOf("hoy", "saldo", "dias", "cuentas", "accesos", "medeben", "fondo", "reserva", "movimientos", "frase"),
        mapOf("hoy" to "Te queda para hoy", "saldo" to "Saldo / Disponible", "dias" to "Días que faltan / Fondo",
            "cuentas" to "Mis cuentas", "accesos" to "Accesos rápidos", "medeben" to "Me deben",
            "fondo" to "Mi fondo de ahorro", "reserva" to "Para pasar a tu reserva", "movimientos" to "Gastos y entradas",
            "frase" to "Frase del día"),
        chicos = setOf("saldo", "dias", "cuentas"), fijos = setOf("hoy"))
    val PLAN = ConfigBloques("plan",
        listOf("resumen", "ingresos", "pagar", "planes", "cuotas", "metas", "proximos", "cobraste"),
        mapOf("resumen" to "Resumen del período", "ingresos" to "Ingresos", "pagar" to "Pagar al cobrar", "planes" to "Mis planes",
            "cuotas" to "Cuotas", "metas" to "Metas de ahorro", "proximos" to "Para el próximo período", "cobraste" to "¿Cobraste?"),
        fijos = setOf("resumen"))
    val DIAS = ConfigBloques("dias", listOf("objetivo", "lista"),
        mapOf("objetivo" to "Mi objetivo por día", "lista" to "Días del período"), fijos = setOf("lista"))
}

@Composable
fun BloquesHoy(editando: Boolean, alTerminar: () -> Unit, hay: (String) -> Boolean, contenido: @Composable (String) -> Unit) =
    BloquesPantalla(Bloques.HOY, editando, alTerminar, hay, contenido)

@Composable
fun BloquesPantalla(cfg: ConfigBloques, editando: Boolean, alTerminar: () -> Unit, hay: (String) -> Boolean = { true },
                    contenido: @Composable (String) -> Unit) {
    val ctx = LocalContext.current
    val k = LocalColores.current
    var orden by remember { mutableStateOf(cfg.orden(ctx)) }
    var ocultos by remember { mutableStateOf(cfg.ocultos(ctx)) }
    fun guardar() = cfg.guardar(ctx, orden, ocultos)
    /* En la demo automática siempre el orden de fábrica, con todo a la vista */
    val ordenUsado = if (Demo.corriendo) cfg.todos else orden
    val ocultosUsados = if (Demo.corriendo) emptySet() else ocultos
    val visibles = ordenUsado.filter { it !in ocultosUsados && hay(it) }

    fun mover(id: String, haciaArriba: Boolean) {
        val i = visibles.indexOf(id); val otro = visibles.getOrNull(if (haciaArriba) i - 1 else i + 1) ?: return
        val l = orden.toMutableList(); l.remove(id)
        val j = l.indexOf(otro); l.add(if (haciaArriba) j else j + 1, id)
        orden = l; guardar()
    }

    @Composable
    fun Envoltura(id: String, modifier: Modifier) {
        if (!editando) { Box(modifier) { contenido(id) }; return }
        val i = visibles.indexOf(id)
        Column(modifier.border(1.5.dp, k.verde.copy(alpha = 0.6f), RoundedCornerShape(24.dp)).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(cfg.nombres[id] ?: id, Modifier.weight(1f).padding(start = 6.dp), fontSize = 12.sp, color = k.suave, maxLines = 1)
                BotonChico(Icons.Outlined.KeyboardArrowUp, "Subir", i > 0) { mover(id, true) }
                BotonChico(Icons.Outlined.KeyboardArrowDown, "Bajar", i < visibles.size - 1) { mover(id, false) }
                if (id !in cfg.fijos) BotonChico(Icons.Outlined.Remove, "Ocultar", true, k.rojo) { ocultos = ocultos + id; guardar() }
            }
            Box { contenido(id) }
        }
    }

    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (editando) Row(Modifier.fillMaxWidth().background(k.verdeClaro, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (cfg.fijos.size < cfg.todos.size) "Usá las flechitas para ordenar y ⊖ para ocultar" else "Usá las flechitas para ordenar", Modifier.weight(1f), fontSize = 13.sp, color = k.verde)
            TextButton(onClick = alTerminar) { Text("Listo", fontWeight = FontWeight.Bold) }
        }
        var n = 0
        while (n < visibles.size) {
            val a = visibles[n]; val b = visibles.getOrNull(n + 1)
            if (a in cfg.chicos && b != null && b in cfg.chicos) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Envoltura(a, Modifier.weight(1f).fillMaxHeight()); Envoltura(b, Modifier.weight(1f).fillMaxHeight())
                }
                n += 2
            } else { Envoltura(a, Modifier.fillMaxWidth()); n += 1 }
        }
        if (editando) {
            val escondidos = orden.filter { it in ocultos }
            Tarjeta {
                Titulo("Bloques ocultos")
                if (escondidos.isEmpty()) Subtitulo("No ocultaste ninguno.")
                escondidos.forEach { id ->
                    Row(Modifier.fillMaxWidth().clickable { ocultos = ocultos - id; guardar() }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AddCircleOutline, null, tint = k.verde)
                        Text("  " + (cfg.nombres[id] ?: id), color = k.texto)
                    }
                }
                TextButton(onClick = { orden = cfg.todos; ocultos = emptySet(); guardar() }) { Text("Volver al orden de fábrica") }
            }
        }
    }
}

@Composable
private fun BotonChico(icono: ImageVector, texto: String, activo: Boolean, color: Color? = null, alTocar: () -> Unit) {
    val k = LocalColores.current
    Box(Modifier.padding(start = 6.dp).size(32.dp).clip(CircleShape).background(if (activo) k.tarjeta else Color.Transparent)
        .clickable(enabled = activo, onClick = alTocar), contentAlignment = Alignment.Center) {
        Icon(icono, texto, tint = if (!activo) k.suave.copy(alpha = 0.3f) else color ?: k.texto, modifier = Modifier.size(20.dp))
    }
}

/** Fila de botones redondos, como los atajos del panel de Samsung. */
@Composable
fun AccesosRapidos(alAnotar: () -> Unit, alDictar: () -> Unit, alMP: () -> Unit, alPlanes: () -> Unit, alIA: () -> Unit, alCompartir: () -> Unit,
                   alCobrar: () -> Unit = {}) {
    val k = LocalColores.current
    Tarjeta {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Atajo("Anotar", Icons.Outlined.Add, k.verde, k.sobreVerde, alAnotar)
            Atajo("Dictar", Icons.Outlined.Mic, k.verdeClaro, k.verde, alDictar)
            Atajo("Mercado\nPago", Icons.Outlined.AccountBalanceWallet, Color(0xFF00A6E8), Color.White, alMP)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Atajo("Mis\nplanes", Icons.Outlined.Flag, k.verdeClaro, k.verde, alPlanes)
            Atajo("Preguntale\na tu plata", Icons.Outlined.AutoAwesome, k.verdeClaro, k.verde, alIA)
            Atajo("Compartir\nresumen", Icons.Outlined.Share, k.verdeClaro, k.verde, alCompartir)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Atajo("Cobrar", Icons.Outlined.Paid, Color(0xFF25D366), Color.White, alCobrar)
            Spacer(Modifier.width(92.dp)); Spacer(Modifier.width(92.dp))
        }
    }
}

@Composable
private fun Atajo(texto: String, icono: ImageVector, fondo: Color, tinta: Color, alTocar: () -> Unit) {
    val k = LocalColores.current
    Column(Modifier.width(92.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = alTocar).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(fondo), contentAlignment = Alignment.Center) {
            Icon(icono, texto.replace("\n", " "), tint = tinta, modifier = Modifier.size(26.dp))
        }
        Text(texto, Modifier.padding(top = 6.dp), fontSize = 12.sp, color = k.texto, textAlign = TextAlign.Center, lineHeight = 14.sp)
    }
}
