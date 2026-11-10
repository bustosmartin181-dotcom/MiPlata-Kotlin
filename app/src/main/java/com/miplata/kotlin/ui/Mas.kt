package com.miplata.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.*
import kotlinx.serialization.json.*

/* ---------- Resumen para compartir ---------- */
@Composable
fun HojaResumen(texto: String, alCerrar: () -> Unit, alCompartir: (String) -> Unit, alWhatsApp: (String) -> Unit, alCopiar: (String) -> Unit) {
    var t by remember { mutableStateOf(texto) }
    Hoja(alCerrar, "Resumen para compartir") {
        Subtitulo("Podés editar el texto antes de enviarlo.")
        OutlinedTextField(t, { t = it }, Modifier.fillMaxWidth(), minLines = 8)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { alWhatsApp(t) }, Modifier.weight(1f)) { Text("WhatsApp") }
            OutlinedButton(onClick = { alCompartir(t) }, Modifier.weight(1f)) { Text("Compartir…") }
        }
        TextButton(onClick = { alCopiar(t) }) { Text("Copiar") }
    }
}

/* ---------- Aviso: hace días que no guardás una copia ---------- */
@Composable
fun TarjetaAvisoCopia(dias: Int?, alGuardar: () -> Unit, alAutomatica: () -> Unit, alMasTarde: () -> Unit) {
    val k = LocalColores.current
    Tarjeta(color = k.verdeClaro) {
        Text(if (dias == null) "💾 Todavía no guardaste ninguna copia" else "💾 Hace $dias días que no guardás una copia", fontWeight = FontWeight.Bold, color = k.texto)
        Subtitulo("Si se rompe o perdés el teléfono, con una copia recuperás todo. Lo más cómodo: activá la copia automática.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = alAutomatica, Modifier.weight(1f)) { Text("Automática") }
            OutlinedButton(onClick = alGuardar, Modifier.weight(1f)) { Text("Guardar ahora") }
        }
        TextButton(onClick = alMasTarde) { Text("Más tarde") }
    }
}

/* ---------- Cierre nocturno: copias internas de cada noche ---------- */
@Composable
fun TarjetaCierres(copias: List<String>, alRestaurar: (Int) -> Unit) {
    val k = LocalColores.current
    var confirmar by remember { mutableStateOf<Int?>(null) }
    Tarjeta {
        Titulo("Cierre nocturno")
        Subtitulo("Cada noche la app congela el día y guarda una copia interna (las últimas 14). Si algo salió mal, volvé a como estaba al cierre de un día.")
        if (copias.isEmpty()) Text("Todavía no hay cierres guardados.", color = k.suave, fontSize = 14.sp)
        copias.withIndex().reversed().take(7).forEach { (i, iso) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cierre del ${Fechas.nombreDia(Fechas.dia(iso))} ${Fechas.diaYMes(iso)}", Modifier.weight(1f), color = k.texto)
                TextButton(onClick = { confirmar = i }) { Text("Restaurar") }
            }
        }
    }
    confirmar?.let { i -> AlertDialog(onDismissRequest = { confirmar = null }, title = { Text("¿Restaurar?") },
        text = { Text("Se vuelve al estado del cierre del ${Fechas.diaYMes(copias[i])}. Lo que anotaste después se pierde (salvo que tengas una copia).") },
        confirmButton = { TextButton(onClick = { confirmar = null; alRestaurar(i) }) { Text("Restaurar") } },
        dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } }) }
}

/* ---------- Novedades por versión ---------- */
val NOVEDADES = listOf(
    "10.3" to listOf("🎬 Demo automática para grabar videos: se escribe sola, con cuenta regresiva y frases (Ajustes → Modo demo → Reproducir)",
        "La demo muestra el aviso de IPC del alquiler y calcula el aumento",
        "La app ahora se llama Mi plata: así figura en el cajón de aplicaciones, el widget y los archivos"),
    "10.2" to listOf("Mis cuentas: cada gasto que anotás baja solo el saldo de Mercado Pago (y cada entrada lo sube)",
        "\"Ya pasé a la reserva\" desde Ajustes también mueve la plata de Mercado Pago a la Reserva",
        "Si te olvidaste un gasto, lo anotás desde Mis cuentas sin descontarlo dos veces"),
    "10.1" to listOf("Temas de colores (incluido Argentina 🇦🇷) y modo claro/oscuro", "Copia automática en un solo archivo (también en Drive)",
        "Pagar al cobrar con botón de Mercado Pago y Detalles", "IPC para pagos que se actualizan cada 6 meses", "Exportar a Excel y PDF, resumen para compartir",
        "Recordatorio diario, ocultar montos, cierre nocturno y modo demo"),
    "10.0" to listOf("Mi plata hecha de nuevo, nativa de Android (Kotlin)", "Mismas cuentas que Mi plata, comprobadas con miles de casos",
        "Widget con ventanita para anotar sin abrir la app", "Fondo de ahorro, aviso nocturno de la reserva, cuentas, metas, objetivo, PIN"),
)

@Composable
fun TarjetaNovedades() {
    val k = LocalColores.current
    var abierta by remember { mutableStateOf(false) }
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) { Titulo("✨ Novedades"); Spacer(Modifier.weight(1f))
            TextButton(onClick = { abierta = !abierta }) { Text(if (abierta) "Cerrar" else "Ver") } }
        if (abierta) NOVEDADES.forEach { (v, l) ->
            Text("Versión $v", fontWeight = FontWeight.SemiBold, color = k.texto)
            l.forEach { Text("• $it", fontSize = 14.sp, color = k.suave) }
        }
    }
}

/* ---------- Modo demo (datos de ejemplo para videos o capturas) ---------- */
fun estadoDemo(real: JsonObject?): JsonObject {
    val hoy = Fechas.hoy(); val n = Fechas.dia(hoy); val start = Fechas.iso(n - 11)
    val gastos = listOf(
        Triple(-11, 12500.0, "Supermercado" to "Súper"), Triple(-10, 13500.0, "Cena con amigos" to "Salidas"), Triple(-9, 2500.0, "SUBE" to "Transporte"),
        Triple(-9, 9300.0, "Almuerzo" to "Comida"), Triple(-8, 15200.0, "Supermercado" to "Súper"), Triple(-7, 8400.0, "Delivery" to "Comida"),
        Triple(-7, 3100.0, "Kiosco" to "Otro"), Triple(-6, 7100.0, "Farmacia" to "Salud"), Triple(-6, 6000.0, "Nafta" to "Transporte"),
        Triple(-5, 8800.0, "Almuerzo" to "Comida"), Triple(-5, 2900.0, "Café" to "Comida"), Triple(-4, 14400.0, "Supermercado" to "Súper"),
        Triple(-3, 3000.0, "SUBE" to "Transporte"), Triple(-3, 8900.0, "Panadería y almuerzo" to "Comida"), Triple(-2, 11200.0, "Cine" to "Salidas"),
        Triple(-1, 9400.0, "Almuerzo y café" to "Comida"))
    val ahora = System.currentTimeMillis()
    val m = mutableMapOf<String, JsonElement>(
        "demo" to JsonPrimitive(true), "bienvenida" to JsonPrimitive(true), "nombre" to JsonPrimitive("Martín"),
        "sueldo" to JsonPrimitive(620000), "previo" to JsonPrimitive(0),
        "ingresos" to JsonArray(listOf(JsonObject(mapOf("cat" to JsonPrimitive("Sueldo"), "a" to JsonPrimitive(620000))))),
        "start" to JsonPrimitive(start), "end" to JsonPrimitive(Fechas.mesSiguiente(start)),
        "items" to JsonArray(listOf("Alquiler" to 180000, "Luz" to 25000, "Internet" to 20000).map { (nn, a) ->
            val base = mutableMapOf<String, JsonElement>("n" to JsonPrimitive(nn), "a" to JsonPrimitive(a), "p" to JsonPrimitive(true))
            if (nn == "Alquiler") base["ipc"] = JsonObject(mapOf("prox" to JsonPrimitive(hoy.take(7)), "cada" to JsonPrimitive(6), "partes" to JsonPrimitive(1)))
            JsonObject(base) }),
        "spent" to JsonArray(gastos.mapIndexed { i, (d, amt, nc) -> JsonObject(mapOf("id" to JsonPrimitive(ahora + i),
            "date" to JsonPrimitive(Fechas.iso(n + d)), "amt" to num(amt), "note" to JsonPrimitive(nc.first), "cat" to JsonPrimitive(nc.second))) }),
        "recal" to JsonNull, "ipcMig" to JsonPrimitive(1),
        "metas" to JsonArray(listOf(JsonObject(mapOf("id" to JsonPrimitive(1), "n" to JsonPrimitive("Vacaciones"), "t" to JsonPrimitive(300000), "g0" to JsonPrimitive(60000), "f" to JsonPrimitive(""))))),
    )
    listOf("tema", "color").forEach { k -> real?.get(k)?.let { m[k] = it } }
    return JsonObject(m)
}
