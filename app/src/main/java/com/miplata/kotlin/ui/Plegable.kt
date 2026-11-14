package com.miplata.kotlin.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext

/* Secciones que se pliegan y despliegan tocando el título. La app recuerda cómo las dejaste.
   Durante la demo automática quedan siempre abiertas. */
class Plegado(val abierto: Boolean, val alternar: () -> Unit)

@Composable
fun rememberPlegado(clave: String, abiertoAlPrincipio: Boolean = true): Plegado {
    val prefs = LocalContext.current.getSharedPreferences("plegables", android.content.Context.MODE_PRIVATE)
    var abierto by remember { mutableStateOf(prefs.getBoolean(clave, abiertoAlPrincipio)) }
    return Plegado(abierto || Demo.corriendo) { abierto = !abierto; prefs.edit().putBoolean(clave, abierto).apply() }
}

/** Fila del título: tocándola se pliega o despliega. La flechita gira. */
@Composable
fun CabeceraPlegable(p: Plegado, modifier: Modifier = Modifier, contenido: @Composable RowScope.() -> Unit) {
    val giro by animateFloatAsState(if (p.abierto) 180f else 0f, label = "flecha")
    Row(modifier.clickable(onClick = p.alternar), verticalAlignment = Alignment.CenterVertically) {
        contenido()
        Icon(Icons.Outlined.ExpandMore, if (p.abierto) "Plegar" else "Desplegar", Modifier.rotate(giro), tint = LocalColores.current.suave)
    }
}
