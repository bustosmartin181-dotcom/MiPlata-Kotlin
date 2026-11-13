package com.miplata.kotlin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

/* Bienvenida en 6 pantallas, como la introducción de Mi plata original:
   0 Bienvenido · 1 Así funciona · 2 Tu plata, tu decisión · 3 ¿De dónde viene tu plata?
   4 Tus gastos importantes · 5 ¡Listo! — cada pantalla entra deslizando y se va llenando de a poco. */
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
    val enDemo = Demo.corriendo

    val avanzar: () -> Unit = {
        when {
            paso == 3 && aNumero(sueldo) <= 0 -> error = "Cargá cuánto cobrás para seguir."
            paso == 3 && Fechas.dia(fin) <= Fechas.dia(inicio) -> error = "El próximo cobro tiene que ser después."
            paso < 5 -> { error = null; paso += 1 }
            else -> alEmpezar(nombre.trim(), aNumero(sueldo), inicio, fin, gastos.map { it.first.trim() to aNumero(it.second) })
        }
    }

    AnimatedContent(paso, Modifier.fillMaxSize(), label = "bienvenida", transitionSpec = {
        val adelante = targetState > initialState
        (slideInHorizontally(tween(420)) { if (adelante) it / 3 else -it / 3 } + fadeIn(tween(420))) togetherWith
            (slideOutHorizontally(tween(320)) { if (adelante) -it / 4 else it / 4 } + fadeOut(tween(260)))
    }) { p ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).systemBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height((if (p == 0 || p == 5) 40.dp else 16.dp) + (if (enDemo && Demo.conFrases) 64.dp else 0.dp)))   // que la frase de la demo no tape el título
            when (p) {
                0 -> {
                    Aparece(0, Modifier.align(Alignment.CenterHorizontally)) { Text("💰", fontSize = 64.sp) }
                    Aparece(1, Modifier.fillMaxWidth()) {
                        Text("Bienvenido" + (if (nombre.isNotBlank()) ",\n${nombre.trim()}" else ""), Modifier.fillMaxWidth(),
                            fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Serif, color = k.texto, textAlign = TextAlign.Center, lineHeight = 38.sp)
                    }
                    Aparece(2, Modifier.fillMaxWidth()) {
                        Text("a Mi plata", Modifier.fillMaxWidth(), fontSize = 26.sp, fontFamily = Serif, color = k.verde, textAlign = TextAlign.Center)
                    }
                    Aparece(3, Modifier.fillMaxWidth()) {
                        Text("Ordená tu plata, un día a la vez.", Modifier.fillMaxWidth(), fontSize = 17.sp, color = k.suave, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(8.dp))
                    Aparece(4, Modifier.fillMaxWidth()) {
                        OutlinedTextField(nombre, { nombre = it }, Modifier.fillMaxWidth().demo("bv.nombre", escribir = { nombre = it }),
                            label = { Text("¿Cómo te llamás? (opcional)") }, singleLine = true, visualTransformation = cursorDemo("bv.nombre"))
                    }
                    Aparece(5, Modifier.fillMaxWidth()) {
                        Button(onClick = avanzar, Modifier.fillMaxWidth().height(52.dp).demo("bv.siguiente", avanzar)) {
                            Text(if (enDemo) "Continuar →" else "Comenzar →", fontSize = 16.sp) }
                    }
                    if (!enDemo) Aparece(6, Modifier.fillMaxWidth()) {
                        Tarjeta {
                            Text("📥  ¿Ya usabas Mi plata?", fontWeight = FontWeight.Bold, color = k.texto)
                            Text("Traé tus datos desde una copia de seguridad.", fontSize = 13.sp, color = k.suave)
                            OutlinedButton(onClick = alImportar, Modifier.fillMaxWidth()) { Text("Importar copia de seguridad") }
                        }
                    }
                }
                1 -> {
                    Encabezado("Así funciona", "Tres pasos y listo.")
                    Aparece(1, Modifier.fillMaxWidth()) {
                        Tarjeta {
                            listOf("Cargás lo que cobrás.", "Anotás lo que pagás sí o sí: alquiler, servicios, tarjetas.", "Lo que sobra se divide entre los días del mes.")
                                .forEachIndexed { i, t -> Aparece(2 + i) { PasoNumerado(i + 1, t) } }
                        }
                    }
                    Aparece(5, Modifier.fillMaxWidth()) {
                        Tarjeta {
                            Text("Un ejemplo", fontSize = 13.sp, color = k.suave)
                            FilaDoble("Cobrás", pesos(1_000_000.0))
                            FilaDoble("Pagás sí o sí", "− " + pesos(400_000.0))
                            FilaDoble("Te quedan para 30 días", pesos(600_000.0))
                            Aparece(7) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(pesos(numeroAnimado(20_000.0, 1400, desdeCero = true)), fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, color = k.verde)
                                    Text("  por día", Modifier.padding(bottom = 6.dp), color = k.suave)
                                }
                            }
                        }
                    }
                    Aparece(8, Modifier.fillMaxWidth()) {
                        Text("Si hoy gastás menos, lo que sobra pasa a mañana. Si te pasás, mañana tenés un poco menos.", color = k.suave)
                    }
                }
                2 -> {
                    Encabezado("Tu plata, tu decisión", "Lo que tenés que saber antes de empezar.")
                    Aparece(1, Modifier.fillMaxWidth()) {
                        Tarjeta {
                            listOf("Sin cuenta ni registro.", "Sin anuncios.", "Tus números se guardan en tu teléfono y solo salen si vos guardás una copia.",
                                "No pide acceso a tus contactos, ubicación ni cámara. El micrófono solo se usa si tocás Dictar.",
                                "Podés ponerle PIN para que nadie vea tus números.", "Las copias las guardás vos, en tu Drive.")
                                .forEachIndexed { i, t -> Aparece(2 + i) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("✓", color = k.verde, fontWeight = FontWeight.Bold); Text(t, color = k.texto) } } }
                        }
                    }
                }
                3 -> {
                    Encabezado("¿De dónde viene tu plata?", "Escribí cuánto cobrás y cuándo.")
                    Aparece(1, Modifier.fillMaxWidth()) {
                        Tarjeta {
                            Text("💵 Sueldo", fontWeight = FontWeight.Bold, color = k.texto)
                            CampoMonto(sueldo, { sueldo = it; error = null }, "Lo que cobrás en el período", demoClave = "bv.sueldo")
                        }
                    }
                    Aparece(2, Modifier.fillMaxWidth()) {
                        Tarjeta {
                            Text("📅 ¿Cuándo cobrás?", fontWeight = FontWeight.Bold, color = k.texto)
                            CampoFecha("Día que cobrás", inicio, { inicio = it })
                            CampoFecha("Próximo cobro", fin, { fin = it })
                        }
                    }
                }
                4 -> {
                    Encabezado("Tus gastos importantes", "Lo primero que pagás al cobrar: alquiler, servicios, tarjetas, cuotas.")
                    Aparece(1, Modifier.fillMaxWidth()) {
                        Tarjeta {
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
                    }
                    Aparece(2, Modifier.fillMaxWidth()) {
                        val s = aNumero(sueldo); val g = gastos.sumOf { aNumero(it.second) }
                        Tarjeta(Modifier.demo("bv.asiqueda")) {
                            Text("Así queda tu mes", fontWeight = FontWeight.Bold, color = k.texto)
                            FilaDoble("Ingresos", pesos(s))
                            FilaDoble("Gastos importantes", pesos(numeroAnimado(g, 500)))
                            FilaDoble("Te queda para el día a día", pesos(numeroAnimado(s - g, 500)), fuerte = true)
                        }
                    }
                }
                else -> {
                    val s = aNumero(sueldo); val g = gastos.sumOf { aNumero(it.second) }
                    val dias = maxOf(1L, Fechas.dia(fin) - Fechas.dia(inicio))
                    Aparece(0, Modifier.align(Alignment.CenterHorizontally)) { Text("🎉", fontSize = 64.sp) }
                    Aparece(1, Modifier.fillMaxWidth()) {
                        Text("Listo${if (nombre.isNotBlank()) ", ${nombre.trim()}" else ""}.\nTu plan está armado.", Modifier.fillMaxWidth(),
                            fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Serif, color = k.texto, textAlign = TextAlign.Center, lineHeight = 36.sp)
                    }
                    Aparece(3, Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Podés gastar unos", color = k.suave)
                            Text(pesos(numeroAnimado((s - g) / dias, 1600, desdeCero = true)), fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, color = k.verde)
                            Text("por día durante $dias días", color = k.suave)
                        }
                    }
                    Aparece(5, Modifier.fillMaxWidth()) {
                        Text("Lo que no gastes hoy, pasa a mañana.", Modifier.fillMaxWidth().padding(top = 8.dp), color = k.texto, textAlign = TextAlign.Center)
                    }
                }
            }
            error?.let { Text(it, color = k.rojo) }
            if (p in 1..5) Aparece(9, Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { error = null; paso -= 1 }, Modifier.weight(1f).height(52.dp)) { Text("← Atrás") }
                    Button(onClick = avanzar, Modifier.weight(1f).height(52.dp).demo("bv.siguiente", avanzar)) { Text(if (p == 5) "Empezar" else "Siguiente →") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Encabezado(grande: String, chico: String) {
    val k = LocalColores.current
    Aparece(0) {
        Column(Modifier.padding(bottom = 4.dp)) {
            Text(grande, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Serif, color = k.texto, lineHeight = 32.sp)
            Text(chico, Modifier.padding(top = 6.dp), color = k.suave)
        }
    }
}

@Composable
private fun PasoNumerado(n: Int, texto: String) {
    val k = LocalColores.current
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).background(k.verde, CircleShape), contentAlignment = Alignment.Center) {
            Text("$n", color = k.sobreVerde, fontWeight = FontWeight.Bold) }
        Text(texto, Modifier.padding(top = 3.dp), color = k.texto)
    }
}

@Composable
private fun FilaDoble(izq: String, der: String, fuerte: Boolean = false) {
    val k = LocalColores.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(izq, color = if (fuerte) k.texto else k.suave, fontWeight = if (fuerte) FontWeight.SemiBold else FontWeight.Normal)
        Text(der, color = k.texto, fontWeight = if (fuerte) FontWeight.Bold else FontWeight.SemiBold)
    }
}
