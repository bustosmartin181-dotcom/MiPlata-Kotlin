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

    /** Abre directo la pantalla "Transferir" de Mercado Pago, usando el mismo atajo que muestra el teléfono
        al mantener apretado su ícono. Si no lo encuentra, abre Mercado Pago normal. true = entró directo a Transferir. */
    fun abrirTransferir(ctx: Context): Boolean {
        /* 1) El link de "Transferir" de la web, entregado directo a la app de Mercado Pago (no al navegador) */
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.mercadopago.com.ar/withdraw")).setPackage(PAQUETE)
        if (ctx.packageManager.resolveActivity(web, 0) != null &&
            runCatching { ctx.startActivity(web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)) return true
        /* 2) El atajo "Transferir" del ícono, si Mercado Pago lo deja usar */
        val i = runCatching { intentDeAtajo(ctx, listOf("transf")) }.getOrNull()
        if (i != null && runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)) return true
        abrir(ctx)
        return false
    }

    /** Lee los atajos fijos que Mercado Pago le declara a Android (res/xml de su app) y arma el intent del que coincide. */
    private fun intentDeAtajo(ctx: Context, palabras: List<String>): Intent? {
        val pm = ctx.packageManager
        val comp = pm.getLaunchIntentForPackage(PAQUETE)?.component ?: return null
        val info = pm.getActivityInfo(comp, android.content.pm.PackageManager.GET_META_DATA)
        val idXml = info.metaData?.getInt("android.app.shortcuts") ?: 0
        if (idXml == 0) return null
        val res = pm.getResourcesForApplication(PAQUETE)
        val xml = res.getXml(idXml)
        val ns = "http://schemas.android.com/apk/res/android"
        fun texto(attr: String): String {
            val id = xml.getAttributeResourceValue(ns, attr, 0)
            return if (id != 0) runCatching { res.getString(id) }.getOrDefault("") else xml.getAttributeValue(ns, attr) ?: ""
        }
        var coincide = false
        var elegido: Intent? = null
        while (xml.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (xml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG) when (xml.name) {
                "shortcut" -> {
                    val nombre = (texto("shortcutId") + " " + texto("shortcutShortLabel") + " " + texto("shortcutLongLabel")).lowercase()
                    coincide = elegido == null && palabras.any { it in nombre }
                }
                "intent" -> if (coincide && elegido == null) {
                    val it = Intent(xml.getAttributeValue(ns, "action") ?: Intent.ACTION_VIEW)
                    xml.getAttributeValue(ns, "data")?.let { d -> it.data = Uri.parse(d) }
                    val clase = xml.getAttributeValue(ns, "targetClass")
                    if (clase != null) it.setClassName(xml.getAttributeValue(ns, "targetPackage") ?: PAQUETE, clase) else it.setPackage(PAQUETE)
                    elegido = it
                }
                "extra" -> if (coincide) elegido?.putExtra(xml.getAttributeValue(ns, "name") ?: "", xml.getAttributeValue(ns, "value") ?: "")
            } else if (xml.eventType == org.xmlpull.v1.XmlPullParser.END_TAG && xml.name == "shortcut") coincide = false
        }
        xml.close()
        return elegido
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

