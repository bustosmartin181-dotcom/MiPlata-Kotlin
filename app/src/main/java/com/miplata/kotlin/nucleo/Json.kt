package com.miplata.kotlin.nucleo

import kotlinx.serialization.json.*

/* Ayudas para leer el JSON de las copias de Mi plata igual que lo hacía JavaScript:
   un monto vacío o con texto raro vale 0, "1500" vale 1500, etc. */

/** Como `+x || 0` en JavaScript. */
fun JsonElement?.num(): Double {
    if (this == null || this is JsonNull) return 0.0
    if (this !is JsonPrimitive) return 0.0
    if (this.isString) {
        val t = content.trim()
        if (t.isEmpty()) return 0.0
        val v = t.toDoubleOrNull() ?: return 0.0
        return if (v.isNaN()) 0.0 else v
    }
    booleanOrNull?.let { return if (it) 1.0 else 0.0 }
    val v = doubleOrNull ?: return 0.0
    return if (v.isNaN()) 0.0 else v
}

/** Como `!!x` en JavaScript. */
fun JsonElement?.verdadero(): Boolean {
    if (this == null || this is JsonNull) return false
    if (this !is JsonPrimitive) return true
    if (isString) return content.isNotEmpty()
    booleanOrNull?.let { return it }
    val v = doubleOrNull ?: return false
    return v != 0.0 && !v.isNaN()
}

fun JsonElement?.texto(): String? =
    if (this is JsonPrimitive && this !is JsonNull) content else null

fun JsonObject.lista(clave: String): List<JsonObject> =
    (this[clave] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
