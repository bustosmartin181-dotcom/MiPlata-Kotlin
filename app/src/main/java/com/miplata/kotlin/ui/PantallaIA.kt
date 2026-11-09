package com.miplata.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.miplata.kotlin.ia.Gemini
import com.miplata.kotlin.nucleo.Estado
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SUGERENCIAS = listOf("¿En qué gasté más esta semana?", "¿Llego a fin de mes si sigo así?",
    "¿Cuánto gasté en comida este período?", "¿Cuánto llevo ahorrado en el fondo?", "Dame un consejo para este mes")

/** "Preguntale a tu plata": chat con Gemini sobre tus datos. */
@Composable
fun PantallaIA(S: Estado, alCerrar: () -> Unit, alIrAAjustes: () -> Unit) {
    val k = LocalColores.current
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    val mensajes = remember { mutableStateListOf<Gemini.Mensaje>() }
    var texto by remember { mutableStateOf("") }
    var pensando by remember { mutableStateOf(false) }
    val lista = rememberLazyListState()
    val hayClave = Gemini.clave(ctx).isNotEmpty()

    fun enviar(p: String) {
        if (p.isBlank() || pensando) return
        val historial = mensajes.toList()
        mensajes += Gemini.Mensaje(true, p.trim()); texto = ""; pensando = true
        alcance.launch {
            val r = runCatching { withContext(Dispatchers.IO) { Gemini.preguntar(ctx, Gemini.contexto(S), historial, p.trim()) } }
            mensajes += Gemini.Mensaje(false, r.getOrElse { "⚠️ " + (it.message ?: "Algo salió mal.") })
            pensando = false
            lista.animateScrollToItem(maxOf(0, mensajes.size))
        }
    }

    androidx.activity.compose.BackHandler(onBack = alCerrar)
    /* Pantalla completa sobre la app (no una ventana aparte), así respeta la barra de abajo y el teclado */
    Box(Modifier.fillMaxSize().background(k.fondo)
        .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("✨ Preguntale a tu plata", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = k.texto)
                    Text("Con inteligencia artificial (Gemini)", fontSize = 12.sp, color = k.suave)
                }
                IconButton(onClick = alCerrar) { Icon(Icons.Outlined.Close, "Cerrar") }
            }
            if (!hayClave) {
                Tarjeta(Modifier.padding(16.dp)) {
                    Titulo("Falta tu clave de Gemini")
                    Subtitulo("Es gratis: la sacás con tu cuenta de Google y la pegás en Ajustes → Inteligencia artificial. Te lleva 2 minutos.")
                    Button(onClick = alIrAAjustes, Modifier.fillMaxWidth()) { Text("Ir a Ajustes") }
                }
                return@Column
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = lista, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mensajes.isEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Preguntame lo que quieras sobre tu plata de este período. Por ejemplo:", color = k.suave)
                        SUGERENCIAS.forEach { s -> SuggestionChip(onClick = { enviar(s) }, label = { Text(s) }) }
                    }
                }
                items(mensajes) { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.deVos) Arrangement.End else Arrangement.Start) {
                        Text(m.texto, Modifier.widthIn(max = 320.dp)
                            .background(if (m.deVos) k.verde else k.tarjeta, RoundedCornerShape(18.dp)).padding(14.dp),
                            color = if (m.deVos) k.sobreVerde else k.texto, fontSize = 15.sp)
                    }
                }
                if (pensando) item { Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("  Pensando…", color = k.suave) } }
            }
            Text("Tus datos del período se mandan a Google para responder.", fontSize = 11.sp, color = k.suave, modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(texto, { texto = it }, Modifier.weight(1f), placeholder = { Text("Escribí tu pregunta…") }, maxLines = 4)
                IconButton(onClick = { enviar(texto) }, enabled = texto.isNotBlank() && !pensando) { Icon(Icons.AutoMirrored.Outlined.Send, "Enviar", tint = k.verde) }
            }
        }
    }
}

/** Tarjeta de Ajustes para cargar la clave de Gemini. */
@Composable
fun TarjetaIA(alAbrirWeb: () -> Unit, aviso: (String) -> Unit) {
    val k = LocalColores.current
    val ctx = LocalContext.current
    var clave by remember { mutableStateOf(Gemini.clave(ctx)) }
    var ver by remember { mutableStateOf(false) }
    Tarjeta {
        Titulo("✨ Inteligencia artificial")
        Subtitulo("Para \"Preguntale a tu plata\" (el ✨ de Hoy). Usa Gemini de Google con tu propia clave gratis. Para responder, se mandan tus datos del período a Google; con el plan gratis Google puede usarlos para mejorar sus productos.")
        Text("Cómo sacar la clave: 1) Tocá \"Conseguir clave gratis\" y entrá con tu cuenta de Google. 2) Tocá \"Create API key\" (Crear clave). 3) Copiala y pegala acá abajo.", fontSize = 13.sp, color = k.texto)
        OutlinedButton(onClick = alAbrirWeb, Modifier.fillMaxWidth()) { Text("Conseguir clave gratis") }
        OutlinedTextField(clave, { clave = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Clave de Gemini") }, singleLine = true,
            visualTransformation = if (ver) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { ver = !ver }) { Text(if (ver) "Ocultar" else "Ver") } })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { Gemini.guardarClave(ctx, clave); aviso(if (clave.isEmpty()) "Clave borrada" else "Clave guardada. Probala con el ✨ de Hoy") }, Modifier.weight(1f)) { Text("Guardar") }
            if (clave.isNotEmpty()) OutlinedButton(onClick = { clave = ""; Gemini.guardarClave(ctx, ""); aviso("Clave borrada") }, Modifier.weight(1f)) { Text("Borrar") }
        }
    }
}
