package com.miplata.kotlin.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.datos.Almacen
import com.miplata.kotlin.nucleo.*

/** Mi fondo de ahorro: cuánto hay, de dónde salió, y sacar plata. */
@Composable
fun HojaFondo(S: Estado, c: Calculo, alCerrar: () -> Unit, alSacar: (Double, String, String) -> Unit, alQuitarUso: (Int) -> Unit, alAbrirMP: () -> Unit = {}) {
    val k = LocalColores.current
    val f = c.fondo
    var sacando by remember { mutableStateOf(false) }
    var monto by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }
    var para by remember { mutableStateOf("hoy") }
    var quitar by remember { mutableStateOf<Int?>(null) }
    Hoja(alCerrar, "Mi fondo de ahorro") {
        Text(dinero(f.totalConRend), fontSize = 38.sp, fontFamily = Serif, fontWeight = FontWeight.Bold, color = k.verde)
        if (S.rendOn && S.rendTasa > 0) Text("↑ Rinde ${formatoTasa(S.rendTasa)}%", color = k.verde, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Renglon("Este período", dinero(f.ahorroPeriodo), negrita = false)
        if (f.fondoPrev != 0.0) Renglon("De antes", dinero(f.fondoPrev), negrita = false)
        val retirado = f.acumulado - f.total
        if (retirado > 0) Renglon("Retiraste", dinero(retirado), negrita = false)
        if (f.rend > 0.005) Renglon("Rendimiento estimado", dinero(f.rend), negrita = false)
        if (f.hoySuma >= 1) Text("Esta noche se suman ${dinero(f.hoySuma)} de hoy (si no gastás más).", fontSize = 13.sp, color = k.suave)
        BotonMP("Abrir Mercado Pago (Reservas)", Modifier.fillMaxWidth(), alAbrirMP)
        if (S.fondoUsos.isNotEmpty()) {
            HorizontalDivider(color = k.linea)
            Text("Retiros de este período", fontWeight = FontWeight.SemiBold, color = k.texto)
            S.fondoUsos.forEachIndexed { i, u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${Fechas.diaYMes(u.date)} ${u.note.ifBlank { "Uso del fondo" }}", color = k.texto)
                        Text(if (u.para == "cubre") "Cubrió gastos ya hechos" else "Para gastar ese día", fontSize = 12.sp, color = k.suave)
                    }
                    Text("-" + dinero(u.amt), fontWeight = FontWeight.SemiBold, color = k.texto)
                    IconButton(onClick = { quitar = i }) { Icon(Icons.Outlined.Close, "Quitar retiro") }
                }
            }
        }
        if (!sacando) OutlinedButton(onClick = { sacando = true }, Modifier.fillMaxWidth()) { Text("Sacar del fondo") }
        else {
            HorizontalDivider(color = k.linea)
            CampoMonto(monto, { monto = it }, "¿Cuánto sacás?")
            OutlinedTextField(nota, { nota = it }, Modifier.fillMaxWidth(), label = { Text("¿Para qué? (opcional)") }, singleLine = true)
            Text("¿Qué hacés con esa plata?", color = k.texto)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(para == "hoy", { para = "hoy" }); Text("La gasto hoy (suma a mi límite de hoy)", color = k.texto, fontSize = 14.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(para == "cubre", { para = "cubre" }); Text("Cubre gastos que ya hice o cuadrar con la cuenta", color = k.texto, fontSize = 14.sp)
            }
            Button(onClick = { val n = aNumero(monto); if (n > 0) alSacar(n, nota.trim(), para) }, Modifier.fillMaxWidth().height(52.dp)) { Text("Sacar") }
        }
    }
    quitar?.let { i -> AlertDialog(onDismissRequest = { quitar = null }, title = { Text("¿Quitar este retiro?") },
        text = { Text("El monto vuelve al fondo.") },
        confirmButton = { TextButton(onClick = { quitar = null; alQuitarUso(i) }) { Text("Quitar") } },
        dismissButton = { TextButton(onClick = { quitar = null }) { Text("Cancelar") } }) }
}

/** Mis cuentas: cargás lo que tenés en Mercado Pago (u otras) y la app te dice la diferencia. */
@Composable
fun HojaCuentas(S: Estado, c: Calculo, alCerrar: () -> Unit, alGuardar: (List<Almacen.CuentaEditable>) -> Unit,
                alAjustar: (Double) -> Unit, alQuitarAjuste: (Int) -> Unit, alAnotarDiferencia: (Double) -> Unit = {}) {
    val k = LocalColores.current
    val filas = remember { mutableStateListOf(*S.cuentas.map { Triple(it.n, textoMonto(it.m), !it.excluida) }
        .ifEmpty { listOf(Triple("Mercado Pago", "", true)) + (if (c.fondo.activo) listOf(Triple("Reserva (fondo de ahorro)", "", true)) else emptyList()) }
        .toTypedArray()) }
    var confirmar by remember { mutableStateOf<Double?>(null) }
    val prueba = S.copy(cuentas = filas.map { Cuenta(it.first, aNumero(it.second), !it.third) })
    val cc = Nucleo.conciliar(prueba, c)
    Hoja(alCerrar, "Mis cuentas") {
        Text("Cargá lo que tenés hoy en tus cuentas. Las tildadas cuentan." +
            (if (c.fondo.activo) " Sumá también la reserva de Mercado Pago donde guardás el fondo de ahorro." else " Destildá la plata que no es del día a día."),
            color = k.suave, fontSize = 14.sp)
        Text("Mercado Pago baja solo con cada gasto que anotás, y lo que pasás a la reserva pasa solo de una cuenta a la otra. Cargá el saldo real de vez en cuando para controlar.",
            color = k.suave, fontSize = 13.sp)
        filas.forEachIndexed { i, (n, m, cuenta) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Checkbox(cuenta, { filas[i] = Triple(n, m, it) })
                OutlinedTextField(n, { filas[i] = Triple(it, m, cuenta) }, Modifier.weight(1f), label = { Text("Cuenta") }, singleLine = true)
                CampoMonto(m, { filas[i] = Triple(n, it, cuenta) }, "Saldo", Modifier.weight(1f))
                IconButton(onClick = { filas.removeAt(i) }) { Icon(Icons.Outlined.Close, "Quitar") }
            }
        }
        TextButton(onClick = { filas += Triple("", "", true) }) { Text("+ Agregar cuenta") }
        HorizontalDivider(color = k.linea)
        if (c.fondo.activo) {
            Renglon("Disponible para gastar", dinero(c.fondo.disponible), negrita = false)
            Renglon("Fondo de ahorro", dinero(c.fondo.total + if (c.fondo.rendEnMP) c.fondo.rend else 0.0), negrita = false)
        } else Renglon("Saldo según la app", dinero(c.saldo), negrita = false)
        if (cc.hayCuentas) {
            Renglon("Total en cuentas", dinero(cc.total))
            val ok = Math.abs(cc.diferencia) < 1
            Renglon("Diferencia con la app", if (ok) "Coincide" else dinero(cc.diferencia), colorDer = if (ok) k.verde else k.naranja)
            val faltaReserva = c.fondo.activo && Math.abs(cc.diferencia + c.fondo.total + (if (c.fondo.rendEnMP) c.fondo.rend else 0.0)) < 1
            if (faltaReserva) Text("La diferencia es justo tu fondo de ahorro: agregá la cuenta de la reserva con ${dinero(c.fondo.total)} y va a coincidir. No toques \"Ajustar\".",
                fontSize = 13.sp, color = k.naranja)
            else if (!ok) Text(if (cc.diferencia < 0) "Tenés menos de lo que dice la app: seguro hay gastos sin anotar." else "Tenés más de lo que dice la app: puede faltar anotar una entrada.",
                fontSize = 13.sp, color = k.suave)
        }
        Button(onClick = { alGuardar(filas.map { Almacen.CuentaEditable(it.first.trim(), aNumero(it.second), it.third) }) },
            Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar saldos") }
        if (cc.hayCuentas && Math.abs(cc.diferencia) >= 1 && !(c.fondo.activo && Math.abs(cc.diferencia + c.fondo.total + (if (c.fondo.rendEnMP) c.fondo.rend else 0.0)) < 1)) {
            val d = cc.diferencia
            OutlinedButton(onClick = { alGuardar(filas.map { Almacen.CuentaEditable(it.first.trim(), aNumero(it.second), it.third) }); alAnotarDiferencia(d) },
                Modifier.fillMaxWidth()) { Text(if (d < 0) "Anotar ${dinero(-d)} como gasto que me olvidé" else "Anotar ${dinero(d)} como entrada que faltaba") }
            OutlinedButton(onClick = { confirmar = d }, Modifier.fillMaxWidth()) { Text("Ajustar la app con las cuentas") }
        }
        if (S.ajustes.isNotEmpty()) {
            HorizontalDivider(color = k.linea)
            Text("Recomposiciones (se reparten desde el día siguiente)", fontWeight = FontWeight.SemiBold, color = k.texto)
            S.ajustes.forEachIndexed { i, a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${a.date?.let { Fechas.diaYMes(it) } ?: ""} ${a.note}", Modifier.weight(1f), color = k.texto)
                    Text(dinero(a.amt), fontWeight = FontWeight.SemiBold, color = k.texto)
                    IconButton(onClick = { alQuitarAjuste(i) }) { Icon(Icons.Outlined.Close, "Quitar") }
                }
            }
        }
    }
    confirmar?.let { d -> AlertDialog(onDismissRequest = { confirmar = null },
        title = { Text("¿Ajustar ${dinero(d)}?") },
        text = { Text("La diferencia se reparte desde mañana entre los días que faltan. Lo mejor es primero anotar los gastos que te olvidaste; usá esto si no los recordás.") },
        confirmButton = { TextButton(onClick = { confirmar = null; alGuardar(filas.map { Almacen.CuentaEditable(it.first.trim(), aNumero(it.second), it.third) }); alAjustar(d) }) { Text("Ajustar") } },
        dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } }) }
}

/** "¿Cuánto pasaste a la reserva?" */
@Composable
fun DialogoYaPase(pendiente: Double, alCerrar: () -> Unit, alListo: (Double) -> Unit) {
    var monto by remember { mutableStateOf(textoMonto(Math.round(pendiente).toDouble())) }
    AlertDialog(onDismissRequest = alCerrar, title = { Text("¿Cuánto pasaste a la reserva?") },
        text = { CampoMonto(monto, { monto = it }, "Monto") },
        confirmButton = { TextButton(onClick = { val n = aNumero(monto); if (n > 0) alListo(n) else alCerrar() }) { Text("Listo") } },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } })
}
