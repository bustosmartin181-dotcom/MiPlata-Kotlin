package com.miplata.kotlin.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/* Ocultar los montos (el ojito): si está activo, la plata se ve como "$ ••••" hasta que tocás el ojo. */
object Privacidad {
    var ocultar by mutableStateOf(false)      // la opción guardada (S.ocultar)
    var mostrando by mutableStateOf(false)    // tocaste el ojo para verlos
    val oculto get() = ocultar && !mostrando
}

/** Como pesos(), pero respeta el ojito. Usar para mostrar plata en pantalla. */
fun dinero(n: Double): String = if (Privacidad.oculto) "$ ••••" else pesos(n)
fun dineroCentavos(n: Double): String = if (Privacidad.oculto) "$ ••••" else pesosCentavos(n)
