package com.miplata.kotlin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.*
import kotlinx.serialization.json.*

/* Actualización por IPC (INDEC) cada 6 meses — igual que Mi plata v9.9.28+.
   Nuevo monto = monto × (1 + inflación mes 1) × … × (1 + inflación mes 6), con las 6 inflaciones
   publicadas antes del mes en que rige (el INDEC publica con un mes de atraso). */
object Ipc {
    /** % mensual publicado por el INDEC. Los meses que faltan se completan a mano en la ventana. */
    val INDEC = mapOf("2026-01" to 2.9, "2026-02" to 2.9, "2026-03" to 3.4, "2026-04" to 2.6, "2026-05" to 2.1, "2026-06" to 1.9,
        "2026-07" to 2.1, "2026-08" to 1.7, "2026-09" to 1.9)

    fun sumarMeses(am: String, n: Int): String { val (a, m) = am.split("-").map { it.toInt() }; val t = a * 12 + (m - 1) + n
        return "${Math.floorDiv(t, 12)}-${"%02d".format(Math.floorMod(t, 12) + 1)}" }
    data class Datos(val prox: String, val cada: Int, val partes: Int, val pend: Pair<Double, String>?)
    fun de(i: Item): Datos? { val x = i.extra["ipc"] as? JsonObject ?: return null
        val p = x["pend"] as? JsonObject
        return Datos(x["prox"].texto() ?: return null, x["cada"].num().toInt().takeIf { it > 0 } ?: 6, x["partes"].num().toInt().takeIf { it > 0 } ?: 1,
            p?.let { it["monto"].num() to (it["desde"].texto() ?: "") }) }
    fun meses(d: Datos) = (0 until d.cada).map { sumarMeses(d.prox, -d.cada - 1 + it) }
    fun toca(d: Datos, hoy: String) = hoy.take(7) >= d.prox

    /** Guarda la actualización en el JSON del gasto (igual que la app original). */
    fun aplicar(o: JsonObject, k: Int, nuevoCadaUno: Double, factor: Double): JsonObject {
        val items = (o["items"] as JsonArray).toMutableList()
        val it = (items[k] as JsonObject).toMutableMap()
        val x = (it["ipc"] as JsonObject).toMutableMap()
        val rige = x["prox"].texto()!!; val cada = x["cada"].num().toInt().takeIf { it > 0 } ?: 6
        val hist = (x["hist"] as? JsonArray)?.toMutableList() ?: mutableListOf()
        hist += JsonObject(mapOf("desde" to JsonPrimitive(rige), "antes" to num(it["a"].num()), "nuevo" to num(nuevoCadaUno),
            "pct" to num(Math.round((factor - 1) * 10000) / 100.0)))
        x["hist"] = JsonArray(hist)
        val start = o["start"].texto() ?: ""
        if (start.take(7) >= rige) it["a"] = num(nuevoCadaUno)
        else x["pend"] = JsonObject(mapOf("monto" to num(nuevoCadaUno), "desde" to JsonPrimitive(rige)))
        x["prox"] = JsonPrimitive(sumarMeses(rige, cada))
        it["ipc"] = JsonObject(x); items[k] = JsonObject(it)
        return JsonObject(o + ("items" to JsonArray(items)))
    }
}

@Composable
fun HojaIPC(item: Item, alCerrar: () -> Unit, alGuardar: (cadaUno: Double, factor: Double) -> Unit) {
    val k = LocalColores.current
    val d = Ipc.de(item) ?: return
    val meses = Ipc.meses(d)
    val valores = remember { mutableStateListOf(*meses.map { Ipc.INDEC[it]?.let { v -> v.toString().replace('.', ',') } ?: "" }.toTypedArray()) }
    var confirmar by remember { mutableStateOf(false) }
    val factor = valores.fold(1.0) { f, v -> f * (1 + aNumero(v) / 100) }
    val faltan = valores.count { it.isBlank() }
    val totalAct = item.a * d.partes
    val total = Math.round(totalAct * factor).toDouble()
    val cada = Math.round(total / d.partes).toDouble()
    Hoja(alCerrar, "📈 Actualizar ${item.n}") {
        Text("Inflación mensual del INDEC (%). Los que ya salieron vienen cargados; los que faltan, completalos cuando el INDEC los publique (sale a mitad de cada mes).",
            fontSize = 13.sp, color = k.suave)
        meses.forEachIndexed { n, am ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mesYAnio(am), Modifier.weight(1f), color = k.texto)
                OutlinedTextField(valores[n], { valores[n] = it.filter { c -> c.isDigit() || c == ',' || c == '.' } }, Modifier.width(110.dp),
                    singleLine = true, suffix = { Text("%") })
            }
        }
        HorizontalDivider(color = k.linea)
        Renglon("Aumento acumulado", "%.2f%%".format((factor - 1) * 100).replace('.', ','))
        Renglon(if (d.partes > 1) "Total nuevo" else "Monto nuevo", pesos(total))
        if (d.partes > 1) Renglon("Cada uno pone", pesos(cada), colorDer = k.verde)
        if (faltan > 0) Text("Faltan $faltan ${if (faltan == 1) "mes" else "meses"}: es una estimación, no lo guardes todavía.", color = k.naranja, fontSize = 13.sp)
        Text("Rige desde ${mesYAnio(d.prox)}.", fontSize = 13.sp, color = k.suave)
        Button(onClick = { if (faltan > 0) confirmar = true else alGuardar(cada, factor) }, Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar monto nuevo") }
    }
    if (confirmar) AlertDialog(onDismissRequest = { confirmar = false }, title = { Text("Faltan meses de inflación") },
        text = { Text("¿Guardar igual este monto?") },
        confirmButton = { TextButton(onClick = { confirmar = false; alGuardar(cada, factor) }) { Text("Guardar igual") } },
        dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } })
}
