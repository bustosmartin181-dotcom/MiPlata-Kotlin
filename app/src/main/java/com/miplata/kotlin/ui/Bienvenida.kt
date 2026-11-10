package com.miplata.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.Fechas

/* Bienvenida: empezar de cero en 4 pasos (como la introducción de Mi plata) o traer una copia. */
@Composable
fun Bienvenida(alImportar: () -> Unit, alEmpezar: (nombre: String, sueldo: Double, inicio: String, fin: String, gastos: List<Pair<String, Double>>) -> Unit) {
    val k = LocalColores.current
    var paso by remember { mutableIntStateOf(0) }
    var nombre by remember { mutableStateOf("") }
    var sueldo by remember { mutableStateOf("") }
    var inicio by remember { mutableStateOf(Fechas.hoy()) }
    var fin by remember { mutableStateOf(Fechas.mesSiguiente(Fechas.hoy())) }
    val gastos = remember { mutableStateListOf("Alquiler" to "", "Luz" to "", "Internet" to "") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).systemBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Mi plata", fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, color = k.verde)
        when (paso) {
            0 -> {
                Text("Llegá a fin de mes sabiendo, cada día, cuánto podés gastar.", fontSize = 18.sp, color = k.texto)
                Column(Modifier.fillMaxWidth().demo("bv.comofunciona").background(k.tarjeta, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Así funciona", fontWeight = FontWeight.Bold, color = k.texto)
                    Text("1. Cargás lo que cobrás y tus gastos importantes (alquiler, luz, cuotas…).", color = k.texto)
                    Text("2. Lo que queda se reparte entre los días hasta el próximo cobro.", color = k.texto)
                    Text("3. Anotás lo que gastás. Lo que te sobra hoy pasa a mañana; si te pasás, se descuenta mañana.", color = k.texto)
                    Text("Ejemplo: cobrás $600.000, tenés $300.000 de gastos importantes y faltan 30 días: podés gastar $10.000 por día.", fontSize = 13.sp, color = k.suave)
                    Text("Tus datos quedan solo en tu teléfono.", Modifier.demo("bv.datos"), fontSize = 13.sp, color = k.suave)
                }
                Button(onClick = { paso = 1 }, Modifier.fillMaxWidth().height(52.dp).demo("bv.cero", accion = { paso = 1 })) { Text("Empezar de cero", fontSize = 16.sp) }
                OutlinedButton(onClick = alImportar, Modifier.fillMaxWidth().height(52.dp)) { Text("Ya tengo una copia de seguridad") }
            }
            1 -> {
                Titulo("¿Cómo te llamás?")
                OutlinedTextField(nombre, { nombre = it }, Modifier.fillMaxWidth().demo("bv.nombre", escribir = { nombre = it }), label = { Text("Tu nombre (para el saludo)") }, singleLine = true,
                    visualTransformation = cursorDemo("bv.nombre"))
                Titulo("¿Cuánto cobrás?")
                CampoMonto(sueldo, { sueldo = it; error = null }, "Lo que cobrás en el período", demoClave = "bv.sueldo")
                CampoFecha("Día que cobrás", inicio, { inicio = it })
                CampoFecha("Próximo cobro", fin, { fin = it })
            }
            2 -> {
                Titulo("Tus gastos importantes")
                Text("Lo primero que se paga cuando cobrás. Después podés agregar o cambiar todo en Plan.", color = k.suave, fontSize = 14.sp)
                gastos.forEachIndexed { i, (n, a) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(n, { gastos[i] = it to gastos[i].second }, Modifier.weight(1.2f).demo("bv.gn.$i", escribir = { v -> gastos[i] = v to gastos[i].second }),
                            label = { Text("Qué") }, singleLine = true, visualTransformation = cursorDemo("bv.gn.$i"))
                        CampoMonto(a, { gastos[i] = gastos[i].first to it }, "Monto", Modifier.weight(1f), demoClave = "bv.ga.$i")
                        IconButton(onClick = { gastos.removeAt(i) }) { Icon(Icons.Outlined.Close, "Quitar") }
                    }
                }
                TextButton(onClick = { gastos += "" to "" }) { Text("+ Agregar otro") }
            }
            else -> {
                val s = aNumero(sueldo); val g = gastos.sumOf { aNumero(it.second) }
                val dias = maxOf(1L, Fechas.dia(fin) - Fechas.dia(inicio))
                Titulo("¡Listo${if (nombre.isNotBlank()) ", ${nombre.trim()}" else ""}!")
                Text("Podés gastar por día", color = k.suave)
                Text(pesos((s - g) / dias), fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, color = k.verde)
                Text("${pesos(s)} − ${pesos(g)} de gastos importantes, repartido en $dias días.", color = k.suave, textAlign = TextAlign.Start)
            }
        }
        error?.let { Text(it, color = k.rojo) }
        if (paso in 1..3) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { paso -= 1 }, Modifier.weight(1f).height(52.dp)) { Text("Atrás") }
            val avanzar: () -> Unit = {
                when {
                    paso == 1 && aNumero(sueldo) <= 0 -> error = "Cargá cuánto cobrás para seguir."
                    paso == 1 && Fechas.dia(fin) <= Fechas.dia(inicio) -> error = "El próximo cobro tiene que ser después."
                    paso < 3 -> { error = null; paso += 1 }
                    else -> alEmpezar(nombre.trim(), aNumero(sueldo), inicio, fin, gastos.map { it.first.trim() to aNumero(it.second) })
                }
            }
            Button(onClick = avanzar, Modifier.weight(1f).height(52.dp).demo("bv.siguiente", avanzar)) { Text(if (paso == 3) "Empezar" else "Siguiente") }
        }
    }
}
