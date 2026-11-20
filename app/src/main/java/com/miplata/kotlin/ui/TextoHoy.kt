package com.miplata.kotlin.ui

/* Cómo se nombra lo que queda del día: si te pasaste, se dice "te pasaste $99" en vez de "te quedan -$99". */

/** "Te quedan $X hoy" o "Te pasaste $X hoy" (sin el signo menos). Con mayus = false empieza en minúscula. */
fun textoQuedaHoy(queda: Double, mayus: Boolean = true): String {
    val t = if (queda < 0) "te pasaste ${pesos(-queda)} hoy" else "te quedan ${pesos(queda)} hoy"
    return if (mayus) t.replaceFirstChar { it.uppercase() } else t
}

/** Si te pasaste poquito (hasta 10% del presupuesto del día) se muestra en naranja; el rojo queda para cuando te pasás bastante. */
fun sePasoPoco(queda: Double, gastoHoy: Double): Boolean {
    val presupuesto = queda + gastoHoy
    return queda < 0 && presupuesto > 0 && -queda <= presupuesto * 0.10
}
