package com.miplata.kotlin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PantallaAjustes(version: String, alImportar: () -> Unit, alExportar: () -> Unit) {
    val k = LocalColores.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp, 12.dp, 16.dp, 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Ajustes", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = k.texto)
        Tarjeta {
            Titulo("Copias de seguridad")
            Subtitulo("Tus datos se guardan solo en este teléfono. Hacé una copia cada tanto.")
            Button(onClick = alExportar, Modifier.fillMaxWidth()) { Text("Guardar copia (.json)") }
            OutlinedButton(onClick = alImportar, Modifier.fillMaxWidth()) { Text("Recuperar desde una copia") }
            Text("Las copias son compatibles con tu Mi plata de siempre: podés pasar datos de una a otra.", fontSize = 12.sp, color = k.suave)
        }
        Tarjeta {
            Titulo("Acerca de")
            Renglon("Versión", version, negrita = false)
            Renglon("Motor de cálculo", "igual a Mi plata v9.9.36", negrita = false)
        }
    }
}
