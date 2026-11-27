package com.miplata.kotlin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.Aguinaldo
import com.miplata.kotlin.nucleo.Estado
import com.miplata.kotlin.nucleo.Rubro
import com.miplata.kotlin.nucleo.Vacaciones
import com.miplata.kotlin.nucleo.Viaje

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
    val viajes = remember { mutableStateListOf(*guardada.rubros.map { it.viaje }.toTypedArray()) }
    var calculando by remember { mutableStateOf<Int?>(null) }      // la calculadora de nafta abierta (índice del rubro)
    val yaCargada = vacacionesCargadas(S)

    fun armar() = Vacaciones(dias, nombres.indices.map { Rubro(nombres[it].trim(), aNumero(montos[it]), porDia[it], viajes[it]) }
        .filter { it.n.isNotBlank() || it.monto > 0 }, imp, aparte)
    val v = armar()

    calculando?.let { i ->
        val vj = viajes.getOrNull(i)
        if (vj == null) LaunchedEffect(i) { calculando = null }
        else CalculadoraNafta(vj, alCerrar = { calculando = null }) { nuevo -> viajes[i] = nuevo; calculando = null }
    }

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
            val vj = viajes[i]
            val r = Rubro(nombres[i], aNumero(montos[i]), porDia[i], vj)
            fun quitar() { nombres.removeAt(i); montos.removeAt(i); porDia.removeAt(i); viajes.removeAt(i) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (vj == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(nombres[i], { nombres[i] = it }, Modifier.weight(1.3f), label = { Text("Gasto") }, singleLine = true)
                        CampoMonto(montos[i], { montos[i] = it }, "Monto", Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !porDia[i], onClick = { porDia[i] = false }, label = { Text("Una vez") })
                        FilterChip(selected = porDia[i], onClick = { porDia[i] = true }, label = { Text("Por día") })
                        Spacer(Modifier.weight(1f))
                        Text(dinero(v.totalRubro(r)), fontWeight = FontWeight.Bold, color = k.texto)
                        TextButton(onClick = { quitar() }) { Text("Quitar", color = k.rojo) }
                    }
                } else {
                    /* El viaje: ¿en auto o en avión? */
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Viaje", fontWeight = FontWeight.Bold, color = k.texto, fontSize = 16.sp)
                        Spacer(Modifier.width(4.dp))
                        FilterChip(selected = vj.medio == Viaje.AUTO, onClick = { viajes[i] = vj.copy(medio = Viaje.AUTO) }, label = { Text("🚗 Auto") })
                        FilterChip(selected = vj.medio == Viaje.AVION, onClick = { viajes[i] = vj.copy(medio = Viaje.AVION) }, label = { Text("✈️ Avión") })
                    }
                    if (vj.medio == Viaje.AVION) {
                        CampoMonto(montos[i], { montos[i] = it }, "Pasajes de avión (todos, ida y vuelta)")
                    } else {
                        OutlinedButton(onClick = { calculando = i }, Modifier.fillMaxWidth()) { Text(if (vj.listoAuto) "⛽ Cambiar la cuenta de nafta" else "⛽ Calcular la nafta") }
                        if (vj.listoAuto) Text("${km(vj.km)} km · ${litrosTxt(vj.litros)} litros" + (if (vj.peajes > 0) " · peajes ${dinero(vj.peajes)}" else ""),
                            fontSize = 13.sp, color = k.suave)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        Text(dinero(v.totalRubro(r)), fontWeight = FontWeight.Bold, color = k.texto)
                        TextButton(onClick = { quitar() }) { Text("Quitar", color = k.rojo) }
                    }
                }
            }
        }
        if (viajes.none { it != null }) TextButton(onClick = { nombres.add(0, "Viaje"); montos.add(0, ""); porDia.add(0, false); viajes.add(0, Viaje()) }) {
            Text("+ Agregar el viaje (auto o avión)", fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = { nombres.add(""); montos.add(""); porDia.add(false); viajes.add(null) }) { Text("+ Agregar otro gasto", fontWeight = FontWeight.SemiBold) }

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
        val agui = Aguinaldo.desde(S.crudo); val parteV = agui.parteVacaciones
        if (aparte && agui.cobrado > 0 && parteV != null) {
            val queda = agui.quedaDe(parteV)
            Text("En tu aguinaldo, la parte \"$parteV\" tiene ${dinero(queda)}" + if (v.total > queda) " · te faltan ${dinero(v.total - queda)}" else " · te sobran ${dinero(queda - v.total)}",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (v.total > queda) k.rojo else k.verde)
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

/** ⛽ La mini ventana: cuánto sale ir en auto (nafta + peajes). */
@Composable
private fun CalculadoraNafta(inicial: Viaje, alCerrar: () -> Unit, alUsar: (Viaje) -> Unit) {
    val k = LocalColores.current
    var kmIda by remember { mutableStateOf(textoMonto(inicial.kmIda)) }
    var idaYVuelta by remember { mutableStateOf(inicial.idaYVuelta) }
    var kml by remember { mutableStateOf(textoMonto(inicial.kmPorLitro)) }
    var precio by remember { mutableStateOf(textoMonto(inicial.precioLitro)) }
    var peajes by remember { mutableStateOf(textoMonto(inicial.peajes)) }
    val vj = Viaje(Viaje.AUTO, aNumero(kmIda), aDecimal(kml), aNumero(precio), idaYVuelta, aNumero(peajes))
    AlertDialog(onDismissRequest = alCerrar, containerColor = k.tarjeta,
        title = { Text("⛽ Calculadora de nafta", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoNumero(kmIda, { kmIda = it }, "¿Cuántos km hay hasta el destino?", "km")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Contar ida y vuelta", Modifier.weight(1f), color = k.texto)
                    Switch(idaYVuelta, { idaYVuelta = it })
                }
                CampoNumero(kml, { kml = it }, "¿Cuántos km hace tu auto con 1 litro?", "km/l")
                CampoMonto(precio, { precio = it }, "Precio del litro de nafta")
                CampoMonto(peajes, { peajes = it }, "Peajes (si hay, todos juntos)")
                if (vj.listoAuto) Tarjeta(color = k.verdeClaro) {
                    Text("${km(vj.km)} km ÷ ${textoMonto(vj.kmPorLitro)} km/l = ${litrosTxt(vj.litros)} litros", fontSize = 13.sp, color = k.texto)
                    Renglon("El viaje te sale", dinero(vj.costoAuto))
                } else Text("Completá los km, cuánto rinde y el precio del litro.", fontSize = 13.sp, color = k.suave)
            }
        },
        confirmButton = { Button(onClick = { alUsar(vj) }, enabled = vj.listoAuto) { Text(if (vj.listoAuto) "Usar ${dinero(vj.costoAuto)}" else "Usar") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } })
}

@Composable
private fun CampoNumero(valor: String, alCambiar: (String) -> Unit, etiqueta: String, sufijo: String) =
    OutlinedTextField(valor, { alCambiar(it.filter { c -> c.isDigit() || c == ',' || c == '.' }) }, Modifier.fillMaxWidth(),
        label = { Text(etiqueta) }, suffix = { Text(sufijo) }, singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))

private fun km(n: Double) = if (n % 1.0 == 0.0) "%,d".format(n.toLong()).replace(',', '.') else "%.1f".format(n)
private fun litrosTxt(n: Double) = "%.1f".format(n).replace('.', ',')

/** Para "km por litro": acepta 12,5 o 12.5 como decimal (no como miles). */
fun aDecimal(t: String): Double {
    val c = t.replace(',', '.').filter { it.isDigit() || it == '.' }
    val i = c.lastIndexOf('.')
    val limpio = if (i < 0) c else c.substring(0, i).replace(".", "") + "." + c.substring(i + 1)
    return limpio.toDoubleOrNull() ?: 0.0
}
