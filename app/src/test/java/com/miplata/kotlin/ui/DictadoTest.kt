package com.miplata.kotlin.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Lo que entiende el micrófono del widget. */
class DictadoTest {
    @Test fun frasesComunes() {
        assertEquals("2800" to "Negocio Telma", interpretarDictado("2800 negocio Telma"))
        assertEquals("2800" to "Negocio de la Telma", interpretarDictado("2800 en el negocio de la Telma"))
        assertEquals("2800" to "Negocio de la Telma", interpretarDictado("2.800 pesos en el negocio de la Telma"))
        assertEquals("2800" to "Negocio Telma", interpretarDictado("$2800 negocio Telma"))
        assertEquals("2800" to "Kiosco", interpretarDictado("2 mil 800 kiosco"))
        assertEquals("2800" to "Kiosco", interpretarDictado("2 mil 800 en el kiosco"))
        assertEquals("15000" to "Nafta", interpretarDictado("15.000 de nafta"))
        assertEquals("3000" to "Verdulería", interpretarDictado("3 mil verdulería"))
        assertEquals("1500" to "Pan", interpretarDictado("pan 1500"))
        assertEquals("" to "Negocio Telma", interpretarDictado("negocio Telma"))
    }
    @Test fun loQueYaAndaba() {
        assertEquals("2800" to "Tortas", interpretarDictado("2800 tortas"))
        assertEquals("2500" to "Nafta", interpretarDictado("2500 pesos en nafta"))
        assertEquals("12500" to "Súper", interpretarDictado("12.500 súper"))
    }
}
