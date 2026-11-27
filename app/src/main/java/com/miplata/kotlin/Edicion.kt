package com.miplata.kotlin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Dos versiones de Mi plata desde el mismo código:
 *  - personal: la de Martín, con todo (taller, Mercado Pago, rendimientos, IA, demo, estilos).
 *  - publica: la de la gente. Lo básico a la vista; fondo, "me deben" y planes se prenden en Ajustes.
 * Se elige al compilar (variante "personal" o "publica" en app/build.gradle.kts).
 */
object Edicion {
    val publica: Boolean = BuildConfig.PUBLICA
    val personal: Boolean get() = !publica
}

/** "Herramientas extra" de la versión pública: fondo de ahorro, "Me deben" / me lo devuelven y Mis planes.
 *  En la personal siempre están prendidas. */
object Extras {
    var on by mutableStateOf(true)
        private set
    fun cargar(ctx: android.content.Context) {
        on = Edicion.personal || ctx.getSharedPreferences("edicion", 0).getBoolean("extras", false)
    }
    fun guardar(ctx: android.content.Context, v: Boolean) {
        on = Edicion.personal || v
        ctx.getSharedPreferences("edicion", 0).edit().putBoolean("extras", v).apply()
    }
}
