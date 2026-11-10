package com.miplata.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.nucleo.*

/* ---------- Me deben ---------- */
data class Deuda(val id: Long, val fecha: String, val monto: Double, val nota: String, val previo: Boolean)

fun deudas(S: Estado): List<Deuda> =
    S.spent.filter { it.dev == 1 }.map { Deuda(it.id, it.date, it.amt, it.note, false) } +
    S.meDeben.map { Deuda(it.id, it.date, it.amt, it.note, true) }

@Composable
fun TarjetaMeDeben(lista: List<Deuda>, alCobrar: (Deuda) -> Unit, alNoPagaron: (Deuda) -> Unit) {
    val k = LocalColores.current
    var confirmar by remember { mutableStateOf<Deuda?>(null) }
    Tarjeta {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Titulo("Me deben"); Spacer(Modifier.weight(1f))
            Text(dinero(lista.sumOf { it.monto }), fontWeight = FontWeight.Bold, color = k.naranja)
        }
        lista.forEach { d ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.nota.ifBlank { "Compra para otros" }, color = k.texto)
                        Text(Fechas.diaYMes(d.fecha) + if (d.previo) " · del período anterior" else "", fontSize = 12.sp, color = k.suave)
                    }
                    Text(dinero(d.monto), fontWeight = FontWeight.SemiBold, color = k.texto)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { alCobrar(d) }) { Text("Me lo pagaron") }
                    TextButton(onClick = { confirmar = d }) { Text("No me lo pagan") }
                }
            }
        }
    }
    confirmar?.let { d -> AlertDialog(onDismissRequest = { confirmar = null }, title = { Text("¿No te lo van a pagar?") },
        text = { Text("Pasa a ser un gasto tuyo.") },
        confirmButton = { TextButton(onClick = { confirmar = null; alNoPagaron(d) }) { Text("Sí, es mío") } },
        dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } }) }
}

/* ---------- Objetivo por día y recalibrar (en Días) ---------- */
@Composable
fun TarjetaObjetivo(S: Estado, alPoner: (Double) -> Unit, alRecalibrar: () -> Unit, alQuitarRecal: () -> Unit) {
    val k = LocalColores.current
    val hoy = Fechas.hoy()
    val o = Nucleo.objetivoDiario(S, hoy)
    var valor by remember(S.objetivo) { mutableStateOf(textoMonto(S.objetivo)) }
    var preguntarRecal by remember { mutableStateOf(false) }
    Tarjeta {
        Titulo("Mi objetivo por día")
        Subtitulo("Cuánto querés gastar por día. Pasa a ser tu límite desde hoy, y lo que no gastes se suma al día siguiente.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CampoMonto(valor, { valor = it }, "Objetivo por día", Modifier.weight(1f), demoClave = "obj.campo")
            Button(onClick = { alPoner(aNumero(valor)) }, Modifier.demo("obj.usar", accion = { alPoner(aNumero(valor)) })) { Text("Usar") }
        }
        if (o != null) {
            Renglon("Podrías gastar parejo", dinero(o.promedio) + " por día", negrita = false)
            Renglon("Al final te sobraría", dinero(o.proyectado), colorDer = if (o.proyectado >= 0) k.verde else k.rojo)
            if (o.diasPrevios > 0) Renglon("Ahorrado vs. objetivo", dinero(o.ahorradoAntes), colorDer = if (o.ahorradoAntes >= 0) k.verde else k.rojo)
            Text("Si gastaras por día…", fontSize = 13.sp, color = k.suave)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                o.escenarios.forEach { e ->
                    Column(Modifier.weight(1f).background(if (e.actual) k.verdeClaro else k.fondo, RoundedCornerShape(12.dp)).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dinero(e.o), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = k.texto)
                        Text("sobra " + dinero(e.sobra), fontSize = 11.sp, color = if (e.sobra >= 0) k.verde else k.rojo, textAlign = TextAlign.Center)
                    }
                }
            }
            TextButton(onClick = { alPoner(0.0) }) { Text("Quitar objetivo") }
        } else {
            HorizontalDivider(color = k.linea)
            if (S.recal != null && !S.recal.obj) {
                Text("Recalibrado: cada día vale ${dinero(S.recal.base)}.", color = k.texto, fontSize = 14.sp)
                TextButton(onClick = alQuitarRecal) { Text("Quitar recalibración") }
            } else {
                Text("¿Te desordenaste? Recalibrar reparte lo que te queda en partes iguales entre los días que faltan.", fontSize = 13.sp, color = k.suave)
                OutlinedButton(onClick = { preguntarRecal = true }) { Text("Recalibrar desde hoy") }
            }
        }
    }
    if (preguntarRecal) AlertDialog(onDismissRequest = { preguntarRecal = false }, title = { Text("¿Recalibrar?") },
        text = { Text("Desde hoy cada día vale lo mismo: lo que te queda dividido los días que faltan. Lo que sobró o faltó antes deja de arrastrarse.") },
        confirmButton = { TextButton(onClick = { preguntarRecal = false; alRecalibrar() }) { Text("Recalibrar") } },
        dismissButton = { TextButton(onClick = { preguntarRecal = false }) { Text("Cancelar") } })
}

/* ---------- Metas (en Plan) ---------- */
@Composable
fun TarjetaMetas(S: Estado, alAbrir: (Meta?) -> Unit) {
    val k = LocalColores.current
    val metas = S.metas.filter { it.n.isNotBlank() || it.t > 0 }
    Tarjeta {
        Titulo("Metas")
        if (metas.isEmpty()) Subtitulo("Juntá para algo: vacaciones, un arreglo del auto… Lo que apartás sale de tu plata disponible.")
        metas.forEach { m ->
            val juntado = Nucleo.juntadoEnMeta(S, m)
            Column(Modifier.fillMaxWidth().clickable { alAbrir(m) }.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { Text(m.n.ifBlank { "Meta" }, Modifier.weight(1f), color = k.texto, fontWeight = FontWeight.SemiBold)
                    Text("${dinero(juntado)} de ${dinero(m.t)}", color = k.texto) }
                if (m.t > 0) LinearProgressIndicator(progress = { (juntado / m.t).coerceIn(0.0, 1.0).toFloat() }, Modifier.fillMaxWidth().height(6.dp),
                    color = k.verde, trackColor = k.linea, gapSize = 0.dp, drawStopIndicator = {})
                val sug = Nucleo.aporteMensualSugerido(S, m, Fechas.hoy())
                if (sug > 0) Text("Para llegar al ${Fechas.diaYMes(m.f)}: ${dinero(sug)} por mes", fontSize = 12.sp, color = k.suave)
            }
        }
        OutlinedButton(onClick = { alAbrir(null) }, Modifier.fillMaxWidth()) { Text("+ Nueva meta") }
    }
}

@Composable
fun HojaMeta(S: Estado, meta: Meta?, alCerrar: () -> Unit, alGuardar: (String, Double, Double, String) -> Unit,
             alAportar: (Double) -> Unit, alQuitar: () -> Unit) {
    val k = LocalColores.current
    var nombre by remember { mutableStateOf(meta?.n ?: "") }
    var objetivo by remember { mutableStateOf(textoMonto(meta?.t ?: 0.0)) }
    var previo by remember { mutableStateOf(textoMonto(meta?.g0 ?: 0.0)) }
    var fecha by remember { mutableStateOf(meta?.f ?: "") }
    var aporte by remember { mutableStateOf("") }
    var borrar by remember { mutableStateOf(false) }
    Hoja(alCerrar, if (meta == null) "Meta nueva" else "Meta") {
        if (meta != null) {
            Text("Juntaste ${dinero(Nucleo.juntadoEnMeta(S, meta))}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = k.verde)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoMonto(aporte, { aporte = it }, "Apartar hoy", Modifier.weight(1f))
                Button(onClick = { val n = aNumero(aporte); if (n > 0) alAportar(n) }) { Text("Apartar") }
            }
            HorizontalDivider(color = k.linea)
        }
        OutlinedTextField(nombre, { nombre = it }, Modifier.fillMaxWidth(), label = { Text("¿Para qué juntás?") }, singleLine = true)
        CampoMonto(objetivo, { objetivo = it }, "¿Cuánto necesitás?")
        CampoMonto(previo, { previo = it }, "Lo que ya tenías juntado")
        CampoFecha("¿Para cuándo? (opcional)", fecha, { fecha = it })
        Button(onClick = { alGuardar(nombre.trim(), aNumero(objetivo), aNumero(previo), fecha) }, Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar") }
        if (meta != null) TextButton(onClick = { borrar = true }, Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = k.rojo)) { Text("Borrar meta") }
    }
    if (borrar) AlertDialog(onDismissRequest = { borrar = false }, title = { Text("¿Borrar la meta?") },
        text = { Text("Los aportes ya hechos quedan descontados de tu plata.") },
        confirmButton = { TextButton(onClick = { borrar = false; alQuitar() }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { borrar = false }) { Text("Cancelar") } })
}

/* ---------- PIN ---------- */
@Composable
fun CampoPin(valor: String, alCambiar: (String) -> Unit, etiqueta: String) {
    OutlinedTextField(valor, { alCambiar(it.filter(Char::isDigit).take(8)) }, Modifier.fillMaxWidth(), label = { Text(etiqueta) },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, textAlign = TextAlign.Center, letterSpacing = 6.sp))
}

@Composable
fun PantallaBloqueo(error: String?, alProbar: (String) -> Unit) {
    val k = LocalColores.current
    var pin by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(k.fondo).padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Mi plata", fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, color = k.verde)
        Spacer(Modifier.height(20.dp))
        CampoPin(pin, { pin = it }, "Ingresá tu PIN")
        error?.let { Text(it, color = k.rojo, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { alProbar(pin); pin = "" }, Modifier.fillMaxWidth().height(52.dp)) { Text("Entrar") }
    }
}

@Composable
fun HojaPin(hayPin: Boolean, alCerrar: () -> Unit, alGuardar: (actual: String, nuevo: String) -> String?, alQuitar: (actual: String) -> String?) {
    val k = LocalColores.current
    var actual by remember { mutableStateOf("") }
    var nuevo by remember { mutableStateOf("") }
    var repetir by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Hoja(alCerrar, if (hayPin) "Cambiar PIN" else "Poner PIN") {
        if (hayPin) CampoPin(actual, { actual = it }, "PIN actual")
        CampoPin(nuevo, { nuevo = it }, "PIN nuevo (4 a 8 números)")
        CampoPin(repetir, { repetir = it }, "Repetilo")
        error?.let { Text(it, color = k.rojo, fontSize = 14.sp) }
        Button(onClick = {
            error = when { nuevo.length < 4 -> "El PIN tiene que tener al menos 4 números."; nuevo != repetir -> "Los PIN no coinciden."
                else -> alGuardar(actual, nuevo) }
        }, Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar PIN") }
        if (hayPin) TextButton(onClick = { error = alQuitar(actual) }, Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = k.rojo)) { Text("Quitar PIN (escribí el actual arriba)") }
        Text("Si te olvidás el PIN, la única forma de entrar es desinstalar y recuperar una copia. Guardá copias seguido.", fontSize = 12.sp, color = k.suave)
    }
}
