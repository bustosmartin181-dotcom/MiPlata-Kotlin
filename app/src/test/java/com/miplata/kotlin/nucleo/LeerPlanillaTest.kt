package com.miplata.kotlin.nucleo

import com.miplata.kotlin.nucleo.LeerPlanilla.Pieza
import org.junit.Assert.*
import org.junit.Test

/** Imita lo que el teléfono lee de la foto de la planilla de noviembre (un poco torcida, con la columna TOTAL). */
class LeerPlanillaTest {
    private val turnos = mapOf(
        1 to "08:00", 2 to "07:00", 3 to "07:00", 4 to "07:00", 5 to "07:00", 6 to "07:00", 7 to "08:00", 8 to "", 9 to "",
        10 to "13:00", 11 to "13:00", 12 to "13:00", 13 to "13:00", 14 to "08:00", 15 to "08:00", 16 to "07:00", 17 to "07:00",
        18 to "07:00", 19 to "07:00", 20 to "07:00", 21 to "08:00", 22 to "", 23 to "08:00", 24 to "07:00", 25 to "07:00",
        26 to "07:00", 27 to "07:00", 28 to "08:00", 29 to "08:00", 30 to "13:00")
    private fun foto(inclinacion: Float = 0.045f, conDias: Boolean = true, curva: Float = 0f): List<Pieza> {
        val l = mutableListOf(Pieza("MES", 600f, 590f), Pieza("NOVIEMBRE", 680f, 592f),
            Pieza("MAÑANA", 278f, 620f), Pieza("TARDE", 462f, 626f), Pieza("NOCHE", 655f, 632f), Pieza("TOTAL", 818f, 645f),
            Pieza("Entra", 584f, 657f), Pieza("Sale", 702f, 660f))
        fun y(fila: Int, x: Float) = 682f + fila * 30.2f + inclinacion * (x - 95f) + curva * ((x - 500f) / 500f) * ((x - 500f) / 500f)
        turnos.forEach { (d, entra) ->
            val f = d - 1
            if (conDias) l += Pieza("%02d-nov".format(d), 95f, y(f, 95f))
            when {
                entra.isEmpty() -> {}
                entra == "13:00" -> { l += Pieza("13:00", 435f, y(f, 435f)); l += Pieza("21:00", 525f, y(f, 525f)) }
                else -> { l += Pieza(entra, 254f, y(f, 254f)); l += Pieza(if (entra == "08:00") "12:00" else "15:00", 344f, y(f, 344f)) }
            }
            l += Pieza(if (entra.isEmpty()) "00:00" else "08:00", 865f, y(f, 865f))      // columna TOTAL: se ignora
            l += Pieza("00:00", 1015f, y(f, 1015f))
        }
        l += Pieza("184:00:00", 850f, y(30, 850f)); l += Pieza("16:00:00", 1010f, y(30, 1010f))
        l += Pieza("168:00:00", 540f, y(33, 540f))
        return l.shuffled(java.util.Random(7))
    }

    @Test fun leeNoviembre() {
        val r = LeerPlanilla.leer(foto(), 30)
        assertEquals(11, r.mes)
        assertEquals(30, r.entradas.size)
        turnos.forEach { (d, e) -> assertEquals("día $d", e, r.entradas[d]) }
        val t = r.entradas.mapValues { Taller.turnoPorEntrada(it.value) }
        assertEquals(listOf(10, 11, 12, 13, 30), t.filterValues { it == "T" }.keys.sorted())
        assertEquals(listOf(8, 9, 22), t.filterValues { it == "F" }.keys.sorted())
    }

    @Test fun erroresTipicosDelLector() {
        val l = foto().map { if (it.texto == "07:00") it.copy(texto = "O7:00") else if (it.texto == "01-nov") it.copy(texto = "O1-nov") else it }
        val r = LeerPlanilla.leer(l, 30)
        assertEquals("08:00", r.entradas[1]); assertEquals("07:00", r.entradas[2])
    }

    @Test fun sinPlanillaNoInventa() {
        assertTrue(LeerPlanilla.leer(listOf(Pieza("hola", 1f, 1f))).entradas.isEmpty())
    }

    private fun comprobar(r: LeerPlanilla.Resultado) = turnos.forEach { (d, e) -> assertEquals("día $d", e, r.entradas[d]) }

    @Test fun sinLeerLosDias() = comprobar(LeerPlanilla.leer(foto(conDias = false), 30))
    @Test fun muyTorcidaYCurva() = comprobar(LeerPlanilla.leer(foto(inclinacion = -0.09f, curva = 6f), 30))
    @Test fun algunosDiasMalLeidos() {
        val l = foto().filter { !(it.texto.endsWith("-nov") && it.texto.take(2).toInt() % 3 != 0) }   // solo se leyó 1 de cada 3 días
        comprobar(LeerPlanilla.leer(l, 30))
    }
}
