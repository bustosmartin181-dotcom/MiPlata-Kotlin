package com.miplata.kotlin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.Estado
import com.miplata.kotlin.nucleo.Rubro
import com.miplata.kotlin.nucleo.Vacaciones

/* 🏖️ Vacaciones: una "app chiquita" dentro de Mi plata. Simulás tranquilo y no toca tus números
   hasta que tocás "Cargar". Entra como un solo gasto importante del período. */

/** ¿Ya hay unas vacaciones cargadas en este período? */
fun vacacionesCargadas(S: Estado) = S.items.any { it.n == Vacaciones.NOMBRE_GASTO }

/** La tarjeta de la pantalla Plan que abre la ventanita. */
@Composable
fun TarjetaVacaciones(S: Estado, alAbrir: () -> Unit) {
    val k = LocalColores.current
    val v = Vacaciones.desde(S.crudo)
    Tarjeta(Modifier.clickable(onClick = alAbrir)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Titulo("🏖️ Vacaciones"); Spacer(Modifier.weight(1f))
            Text("Abrir", color = k.verde, fontWeight = FontWeight.SemiBold)
        }
        Text(when {
            vacacionesCargadas(S) -> "Cargadas en este período como gasto importante"
            v.total > 0 -> "Simulación: ${v.dias} días · ${dinero(v.total)}"
            else -> "Simulá cuánto vas a gastar antes de irte"
        }, fontSize = 14.sp, color = if (vacacionesCargadas(S)) k.verde else k.suave, fontWeight = FontWeight.SemiBold)
    }
}

/** La ventanita emergente con el simulador. */
@Composable
fun HojaVacaciones(S: Estado, alCerrar: () -> Unit, alGuardar: (Vacaciones) -> Unit, alCargar: (Vacaciones) -> Unit) {
    val k = LocalColores.current
    val guardada = remember { Vacaciones.desde(S.crudo) }
    var dias by remember { mutableIntStateOf(guardada.dias) }
    var imp by remember { mutableIntStateOf(guardada.imprevistos) }
    var aparte by remember { mutableStateOf(guardada.aparte) }
    val nombres = remember { mutableStateListOf(*guardada.rubros.map { it.n }.toTypedArray()) }
    val montos = remember { mutableStateListOf(*guardada.rubros.map { textoMonto(it.monto) }.toTypedArray()) }
    val porDia = remember { mutableStateListOf(*guardada.rubros.map { it.porDia }.toTypedArray()) }
    val yaCargada = vacacionesCargadas(S)

    fun armar() = Vacaciones(dias, nombres.indices.map { Rubro(nombres[it].trim(), aNumero(montos[it]), porDia[it]) }
        .filter { it.n.isNotBlank() || it.monto > 0 }, imp, aparte)
    val v = armar()

    Hoja(alCerrar, "🏖️ Vacaciones") {
        Subtitulo("Simulá antes de gastar. Nada de esto toca tus números hasta que tocás \"Cargar\".")

        Tarjeta {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("¿Cuántos días?", fontSize = 13.sp, color = k.suave)
                    Text("$dias día${if (dias > 1) "s" else ""}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = k.texto)
                }
                OutlinedButton(onClick = { if (dias > 1) dias-- }, Modifier.size(52.dp), contentPadding = PaddingValues(0.dp)) { Text("−", fontSize = 22.sp) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (dias < 60) dias++ }, Modifier.size(52.dp), contentPadding = PaddingValues(0.dp)) { Text("+", fontSize = 22.sp) }
            }
        }

        nombres.indices.forEach { i ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(nombres[i], { nombres[i] = it }, Modifier.weight(1.3f), label = { Text("Gasto") }, singleLine = true)
                    CampoMonto(montos[i], { montos[i] = it }, "Monto", Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !porDia[i], onClick = { porDia[i] = false }, label = { Text("Una vez") })
                    FilterChip(selected = porDia[i], onClick = { porDia[i] = true }, label = { Text("Por día") })
                    Spacer(Modifier.weight(1f))
                    val r = Rubro(nombres[i], aNumero(montos[i]), porDia[i])
                    Text(dinero(v.totalRubro(r)), fontWeight = FontWeight.Bold, color = k.texto)
                    TextButton(onClick = { nombres.removeAt(i); montos.removeAt(i); porDia.removeAt(i) }) { Text("Quitar", color = k.rojo) }
                }
            }
        }
        TextButton(onClick = { nombres.add(""); montos.add(""); porDia.add(false) }) { Text("+ Agregar otro gasto", fontWeight = FontWeight.SemiBold) }

        Text("Imprevistos (por las dudas)", fontWeight = FontWeight.SemiBold, color = k.texto)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 5, 10, 15, 20).forEach { p -> FilterChip(selected = imp == p, onClick = { imp = p }, label = { Text("$p%") }) }
        }

        Tarjeta(color = k.verdeClaro) {
            Renglon("Total de las vacaciones", dinero(v.total))
            if (v.montoImprevistos > 0) Text("Incluye ${dinero(v.montoImprevistos)} de imprevistos", fontSize = 12.sp, color = k.suave)
        }

        Text("¿Con qué lo pagás?", fontWeight = FontWeight.SemiBold, color = k.texto)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = aparte, onClick = { aparte = true }, label = { Text("Aguinaldo / ahorro") })
            FilterChip(selected = !aparte, onClick = { aparte = false }, label = { Text("Del sueldo del mes") })
        }
        Text(if (aparte) "Entra como gasto importante junto con esa plata aparte: tu límite de cada día no cambia."
             else "Entra como gasto importante y se reparte en los días que quedan: tu límite de cada día baja ≈ ${dinero(v.total / diasQueQuedan(S))}.",
            fontSize = 13.sp, color = if (aparte) k.verde else k.naranja)

        if (yaCargada) Text("Ya hay unas vacaciones cargadas en este período. Si querés cambiarlas, editá el gasto en \"Pagar al cobrar\".",
            fontSize = 13.sp, color = k.naranja, fontWeight = FontWeight.SemiBold)
        Button(onClick = { alCargar(v) }, Modifier.fillMaxWidth().height(52.dp), enabled = v.total > 0 && !yaCargada) {
            Text("Cargar como gasto importante de este período")
        }
        Text("Cargalo cuando arranque el período de las vacaciones. Mientras tanto, guardá la simulación.", fontSize = 12.sp, color = k.suave)
        OutlinedButton(onClick = { alGuardar(v) }, Modifier.fillMaxWidth()) { Text("Guardar la simulación") }
    }
}

/** Días que quedan del período, contando hoy (para mostrar cuánto bajaría el día). */
private fun diasQueQuedan(S: Estado): Int {
    val hoy = com.miplata.kotlin.nucleo.Fechas.hoy()
    val fin = com.miplata.kotlin.nucleo.Fechas.dia(S.end)
    val desde = maxOf(com.miplata.kotlin.nucleo.Fechas.dia(hoy), com.miplata.kotlin.nucleo.Fechas.dia(S.start))
    return (fin - desde).toInt().coerceAtLeast(1)
}
