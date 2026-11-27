package com.miplata.kotlin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.ParteAgui

/* 💰 Mi aguinaldo: aparece en diciembre, enero, junio y julio (o mientras te quede plata sin usar). */

@Composable
fun TarjetaAguinaldo(S: Estado, alAbrir: () -> Unit) {
    val k = LocalColores.current
    val a = Aguinaldo.desde(S.crudo)
    Tarjeta(Modifier.clickable(onClick = alAbrir)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Titulo("💰 Mi aguinaldo"); Spacer(Modifier.weight(1f))
            Text("Abrir", color = k.verde, fontWeight = FontWeight.SemiBold)
        }
        if (a.cobrado <= 0) Text("¿Ya cobraste? Cargalo y repartilo: queda aparte, no se suma a tu día.", fontSize = 14.sp, color = k.suave)
        else {
            Renglon("Te queda del aguinaldo", dinero(a.queda), colorDer = if (a.queda < 0) k.rojo else k.verde)
            Text("Cobraste ${dinero(a.cobrado)} · usaste ${dinero(a.usado)}", fontSize = 13.sp, color = k.suave)
        }
    }
}

@Composable
fun HojaAguinaldo(S: Estado, alCerrar: () -> Unit, alGuardar: (Double, List<ParteAgui>) -> Unit,
                  alUsar: (parte: String, nombre: String, monto: Double, como: String) -> Unit, alDeshacer: (Long) -> Unit) {
    val k = LocalColores.current
    val a = Aguinaldo.desde(S.crudo)
    var cobrado by remember { mutableStateOf(textoMonto(a.cobrado)) }
    val nombres = remember { mutableStateListOf(*a.partes.map { it.n }.toTypedArray()) }
    val montos = remember { mutableStateListOf(*a.partes.map { textoMonto(it.m) }.toTypedArray()) }
    fun partes() = nombres.indices.map { ParteAgui(nombres[it].trim(), aNumero(montos[it])) }.filter { it.n.isNotBlank() }
    val total = aNumero(cobrado)
    val sinRepartir = total - partes().sumOf { it.m }

    var parteUso by remember { mutableStateOf(a.partes.firstOrNull()?.n ?: "") }
    var nombreUso by remember { mutableStateOf("") }
    var montoUso by remember { mutableStateOf("") }
    var como by remember { mutableStateOf("gasto") }

    Hoja(alCerrar, "💰 Mi aguinaldo") {
        Subtitulo("Esta plata queda aparte: no se suma a tu límite de cada día hasta que vos decidas usarla.")
        CampoMonto(cobrado, { cobrado = it }, "¿Cuánto cobraste de aguinaldo?")

        Text("¿Cómo lo repartís?", fontWeight = FontWeight.SemiBold, color = k.texto)
        nombres.indices.forEach { i ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(nombres[i], { nombres[i] = it }, Modifier.weight(1.2f), label = { Text("Parte") }, singleLine = true)
                    CampoMonto(montos[i], { montos[i] = it }, "Monto", Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val usado = a.usadoDe(nombres[i].trim())
                    val queda = aNumero(montos[i]) - usado
                    Text(if (usado > 0) "Usaste ${dinero(usado)} · queda ${dinero(queda)}" else if (total > 0 && aNumero(montos[i]) > 0)
                        "${Math.round(aNumero(montos[i]) * 100 / total)}% del aguinaldo" else "",
                        Modifier.weight(1f), fontSize = 12.sp, color = if (queda < 0) k.rojo else k.suave)
                    TextButton(onClick = { nombres.removeAt(i); montos.removeAt(i) }) { Text("Quitar", color = k.rojo) }
                }
            }
        }
        TextButton(onClick = { nombres.add(""); montos.add("") }) { Text("+ Agregar parte", fontWeight = FontWeight.SemiBold) }
        if (total > 0) Text(when {
            sinRepartir >= 1 -> "Sin repartir: ${dinero(sinRepartir)}"
            sinRepartir <= -1 -> "Te pasaste: repartiste ${dinero(-sinRepartir)} más de lo que cobraste"
            else -> "Repartiste todo ✅"
        }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (sinRepartir <= -1) k.rojo else if (sinRepartir >= 1) k.naranja else k.verde)
        Button(onClick = { alGuardar(total, partes()) }, Modifier.fillMaxWidth()) { Text("Guardar reparto") }

        if (a.cobrado > 0 && a.partes.isNotEmpty()) {
            HorizontalDivider(color = k.linea)
            Text("Usar una parte", fontWeight = FontWeight.Bold, color = k.texto, fontSize = 16.sp)
            Row(Modifier.horizontalScrollSeguro(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                a.partes.forEach { p -> FilterChip(selected = parteUso == p.n, onClick = { parteUso = p.n }, label = { Text(p.n) }) }
            }
            val queda = a.quedaDe(parteUso)
            Text("En \"$parteUso\" te quedan ${dinero(queda)}", fontSize = 13.sp, color = if (queda < 0) k.rojo else k.suave)
            OutlinedTextField(nombreUso, { nombreUso = it }, Modifier.fillMaxWidth(), label = { Text("¿Para qué? (ej.: regalos de Navidad)") }, singleLine = true)
            CampoMonto(montoUso, { montoUso = it }, "¿Cuánto?")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = como == "gasto", onClick = { como = "gasto" }, label = { Text("Gasto del mes") })
                FilterChip(selected = como == "dias", onClick = { como = "dias" }, label = { Text("Sumar a mis días") })
            }
            Text(if (como == "gasto") "Entra como gasto importante del mes junto con esa plata: tu límite de cada día no cambia."
                 else "Se suma a tu plata y se reparte entre hoy y los días que quedan: tu límite de cada día sube.",
                fontSize = 13.sp, color = k.suave)
            val m = aNumero(montoUso)
            if (m > queda && m > 0) Text("Ojo: es más de lo que te queda en esta parte.", fontSize = 13.sp, color = k.naranja, fontWeight = FontWeight.SemiBold)
            Button(onClick = { alUsar(parteUso, nombreUso.trim(), m, como); nombreUso = ""; montoUso = "" },
                Modifier.fillMaxWidth(), enabled = m > 0 && parteUso.isNotBlank()) { Text("Usar ${if (m > 0) dinero(m) else ""}") }
        }

        if (a.usos.isNotEmpty()) {
            HorizontalDivider(color = k.linea)
            Text("Lo que ya usaste", fontWeight = FontWeight.Bold, color = k.texto)
            a.usos.forEach { u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(u.n, color = k.texto, fontWeight = FontWeight.SemiBold)
                        Text("${u.parte} · ${if (u.date.isNotEmpty()) Fechas.diaYMes(u.date) else ""} · ${if (u.como == "dias") "sumado a tus días" else "gasto del mes"}",
                            fontSize = 12.sp, color = k.suave)
                    }
                    Text(dinero(u.amt), fontWeight = FontWeight.Bold, color = k.texto)
                    TextButton(onClick = { alDeshacer(u.id) }) { Text("Deshacer", color = k.rojo) }
                }
            }
        }
        Text("Si tenés el aguinaldo en una cuenta de \"Mis cuentas\" (ej.: reserva Aguinaldo), dejala fuera del total para que no te descuadre.",
            fontSize = 12.sp, color = k.suave)
    }
}

@Composable
private fun Modifier.horizontalScrollSeguro() = this.horizontalScroll(androidx.compose.foundation.rememberScrollState())
