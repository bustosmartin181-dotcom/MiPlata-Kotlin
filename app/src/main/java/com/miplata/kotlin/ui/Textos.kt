package com.miplata.kotlin.ui

import com.miplata.kotlin.nucleo.Calculo
import com.miplata.kotlin.nucleo.Fechas

/* Textos del día, iguales a los de Mi plata. */
val FRASES = listOf("Cada peso anotado es un peso que controlás.","Gastar con intención rinde más que gastar con culpa.","Hoy un pequeño ahorro, mañana una gran tranquilidad.","Lo que no se anota se escapa.","Pagá primero lo importante y vivé con lo que sobra.","Un día en verde es un buen día.","No es cuánto ganás, es cuánto te queda.","Ahorrar no es privarse, es elegir.","Pasito a pasito se junta la meta.","Revisar tus números es cuidarte.","La constancia le gana a la urgencia.","Una meta clara se junta más rápido.","Antes de comprar, esperá un día.","Tu yo del mes que viene te lo va a agradecer.","Lo barato sale caro si no lo necesitás.","Un imprevisto no arruina el mes, se reparte.","Anotar toma diez segundos, ordenarse rinde todo el mes.","Cuidá los pesos chicos, son los que más se escapan.","Cada ahorro suma, aunque sea chico.","Planificar es tener tranquilidad.","Gastá en lo que te acerca a tus metas.","El orden es la mitad del ahorro.","Hoy es un buen día para anotar todo.","La plata ordenada alcanza para más.","Poco por día, mucho al final.","Pensá en tu meta cada vez que dudes.","Lo que juntás hoy es libertad mañana.","Un presupuesto es una guía, no una cárcel.","Respirá antes de comprar.","Seguí así, vas mejor de lo que creés.")

fun fraseDelDia(hoy: String) = FRASES[(Fechas.dia(hoy) % FRASES.size).toInt()]
fun saludoSegunHora(h: Int) = if (h < 12) "Buen día" else if (h < 20) "Buenas tardes" else "Buenas noches"
fun fraseDeEstado(c: Calculo, clase: String) = when {
    c.quedaHoy < 0 -> "Mañana lo acomodamos con lo que queda."
    clase == "warn" -> "Cuidado con lo que queda del día."
    c.gastoHoy == 0.0 -> "Todavía no gastaste nada hoy."
    else -> "Venís bien, seguí así."
}

val CATEGORIAS = listOf("Comida", "Transporte", "Súper", "Salidas", "Salud", "Otro")
