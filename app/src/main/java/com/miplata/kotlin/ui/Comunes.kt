package com.miplata.kotlin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Tarjeta(modifier: Modifier = Modifier, color: Color = LocalColores.current.tarjeta, contenido: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(color, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = contenido)
}

@Composable
fun Renglon(izq: String, der: String, colorDer: Color = LocalColores.current.texto, negrita: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(izq, color = LocalColores.current.texto, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(der, color = colorDer, fontSize = 15.sp, fontWeight = if (negrita) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun Titulo(t: String) = Text(t, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LocalColores.current.texto)

@Composable
fun Subtitulo(t: String) = Text(t, fontSize = 13.sp, color = LocalColores.current.suave)
