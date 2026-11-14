package com.miplata.kotlin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Mercado Pago: siempre abre la APP (nunca la web). Si no está instalada, lleva a la tienda. */
object MercadoPago {
    const val PAQUETE = "com.mercadopago.wallet"
    val AZUL = Color(0xFF009EE3)
    const val ALIAS_CREADOR = "MartinchoLujanino"

    fun abrir(ctx: Context): Boolean {
        val intentos = listOfNotNull(
            ctx.packageManager.getLaunchIntentForPackage(PAQUETE),
            Intent(Intent.ACTION_VIEW, Uri.parse("mercadopago://home")),
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PAQUETE")),
        )
        return intentos.any { i -> runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false) }
    }

    fun copiar(ctx: Context, etiqueta: String, texto: String) {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(etiqueta, texto))
    }
}

/** Botón celeste de Mercado Pago. */
@Composable
fun BotonMP(texto: String, modifier: Modifier = Modifier, alTocar: () -> Unit) {
    Button(onClick = alTocar, modifier, colors = ButtonDefaults.buttonColors(containerColor = MercadoPago.AZUL, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        Text(texto, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** Ayudá al creador: alias para transferir. */
@Composable
fun TarjetaColabora(alCopiar: () -> Unit, alAbrirMP: () -> Unit) {
    val k = LocalColores.current
    Tarjeta {
        Titulo("💚 Ayudá al creador")
        ContenidoColabora(alCopiar, alAbrirMP)
    }
}

/** Lo de adentro del cartel "Ayudá al creador" (se usa en Ajustes y en la hoja que abre la burbuja). */
@Composable
fun ColumnScope.ContenidoColabora(alCopiar: () -> Unit, alAbrirMP: () -> Unit) {
    val k = LocalColores.current
    run {
        Subtitulo("Hola, soy Martín Enrique Bustos. Hice Mi plata para llegar a fin de mes sin papeles ni lapiceras sacando cuentas, y la comparto gratis y sin publicidad. " +
            "Si te sirve y querés darme una mano para seguir mejorándola, cualquier aporte suma. ¡Gracias de corazón!")
        Text("Alias", fontSize = 13.sp, color = k.suave)
        Text(MercadoPago.ALIAS_CREADOR, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = k.texto)
        Button(onClick = alCopiar, Modifier.fillMaxWidth()) { Text("Copiar alias") }
        BotonMP("Abrir Mercado Pago", Modifier.fillMaxWidth(), alAbrirMP)
        Text("Cómo: 1) Copiá el alias. 2) Abrí Mercado Pago. 3) Transferir → pegá el alias → elegí el monto que quieras.", fontSize = 12.sp, color = k.suave)
    }
}

