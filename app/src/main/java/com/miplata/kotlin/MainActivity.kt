package com.miplata.kotlin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplata.kotlin.aviso.AvisoReserva
import com.miplata.kotlin.datos.Almacen
import com.miplata.kotlin.nucleo.Nucleo
import com.miplata.kotlin.nucleo.num
import kotlinx.serialization.json.JsonPrimitive
import com.miplata.kotlin.nucleo.Fechas
import com.miplata.kotlin.nucleo.Movimiento
import com.miplata.kotlin.nucleo.texto
import com.miplata.kotlin.ui.*

class MainActivity : ComponentActivity() {
    private lateinit var almacen: Almacen
    private var enSegundoPlanDesde = 0L
    /** Bloqueada con PIN: al abrir, y si estuvo más de un minuto en segundo plano. */
    val bloqueada = mutableStateOf(false)
    /** color y modo (claro/oscuro/auto) del tema elegido */
    val apariencia = mutableStateOf("verde" to "auto")

    private fun pidePin() = almacen.estado?.let { it.pin.isNotEmpty() && it.pinApp } ?: false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        almacen = Almacen(applicationContext)
        bloqueada.value = pidePin()
        almacen.estado?.let { apariencia.value = it.color to it.tema }
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        setContent { TemaMiPlata(apariencia.value.first, apariencia.value.second) { App(almacen, version, bloqueada) { apariencia.value = it } } }
    }
    override fun onStop() { super.onStop(); enSegundoPlanDesde = System.currentTimeMillis() }
    override fun onStart() {
        super.onStart()
        if (enSegundoPlanDesde > 0 && System.currentTimeMillis() - enSegundoPlanDesde > 60_000 && pidePin()) bloqueada.value = true
    }
}

enum class Pestana(val titulo: String, val icono: ImageVector) {
    HOY("Hoy", Icons.Outlined.Home), PLAN("Plan", Icons.Outlined.ListAlt),
    DIAS("Días", Icons.Outlined.CalendarMonth), AJUSTES("Ajustes", Icons.Outlined.Settings)
}

@Composable
fun App(almacen: Almacen, version: String, bloqueada: MutableState<Boolean>, alCambiarTema: (Pair<String, String>) -> Unit = {}) {
    val ctx = LocalContext.current
    val k = LocalColores.current
    var vuelta by remember { mutableIntStateOf(0) }          // sube cada vez que cambian los datos
    val estado = remember(vuelta) { almacen.estado }
    val nombre = remember(vuelta) { almacen.crudo?.get("nombre").texto()?.takeIf { it.isNotBlank() } ?: "" }
    var pestana by rememberSaveable { mutableStateOf(Pestana.HOY) }
    var hojaAbierta by remember { mutableStateOf(false) }
    var editando by remember { mutableStateOf<Movimiento?>(null) }
    var hojaHoy by remember { mutableStateOf("") }          // "fondo", "cuentas", "pase", "pin"
    var errorPin by remember { mutableStateOf<String?>(null) }
    var pendientePase by remember { mutableDoubleStateOf(0.0) }
    fun aviso(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()

    /* Cada vez que cambian los datos se reprograma el aviso de la noche */
    LaunchedEffect(vuelta) {
        runCatching { AvisoReserva.programar(ctx) }
        com.miplata.kotlin.widget.Widget.actualizar(ctx)
        if (vuelta > 0) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.miplata.kotlin.datos.CopiaAutomatica.guardar(ctx) }
    }
    LaunchedEffect(estado?.color, estado?.tema) { estado?.let { alCambiarTema(it.color to it.tema) } }
    val elegirCopia = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) { com.miplata.kotlin.datos.CopiaAutomatica.elegir(ctx, uri); vuelta++
            aviso(if (com.miplata.kotlin.datos.CopiaAutomatica.error(ctx) == null) "Copia automática activada" else "No pude escribir ahí: elegí otro lugar") }
    }
    val pedirNotis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) aviso("Sin permiso de notificaciones no te puedo avisar. Activalo en Ajustes del teléfono → Apps → Mi plata K.")
        vuelta++
    }
    fun asegurarNotis() { if (!AvisoReserva.hayPermiso(ctx) && android.os.Build.VERSION.SDK_INT >= 33) pedirNotis.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    /* Al abrir: si el aviso de la noche está activo y falta el permiso, pedirlo (si no, el aviso queda mudo) */
    LaunchedEffect(Unit) { if (estado?.modoFondo == true && estado.resAviso) asegurarNotis() }
    fun campo(clave: String, valor: kotlinx.serialization.json.JsonElement) { almacen.cambiarCampo(clave, valor); vuelta++ }
    fun abrirMP() {
        val i = ctx.packageManager.getLaunchIntentForPackage("com.mercadopago.wallet")
        if (i != null) ctx.startActivity(i) else aviso("No encontré la app de Mercado Pago")
    }

    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val texto = runCatching { ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText() }.getOrNull()
            val error = if (texto == null) "No pude abrir el archivo." else almacen.importar(texto)
            if (error == null) { vuelta++; aviso("Datos recuperados") } else aviso(error)
        }
    }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(almacen.exportar()) }
        }.onSuccess { aviso("Copia guardada") }.onFailure { aviso("No pude guardar la copia") }
    }
    val abrirImportar = { importar.launch(arrayOf("application/json", "text/plain", "*/*")) }
    val abrirExportar = { exportar.launch("mi-plata-copia-${Fechas.hoy()}.json") }

    if (bloqueada.value && estado != null) {
        PantallaBloqueo(errorPin) { pin ->
            val espera = com.miplata.kotlin.datos.Seguridad.segundosDeEspera(ctx)
            when {
                espera > 0 -> errorPin = "Esperá $espera segundos."
                almacen.pinCorrecto(pin) -> { com.miplata.kotlin.datos.Seguridad.limpiarFallos(ctx); errorPin = null; bloqueada.value = false }
                else -> { com.miplata.kotlin.datos.Seguridad.registrarFallo(ctx); errorPin = "PIN incorrecto." }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(k.fondo)) { FondoArgentina() }
    Scaffold(containerColor = if (k.argentina) androidx.compose.ui.graphics.Color.Transparent else k.fondo, bottomBar = {
        if (estado != null) BarraAbajo(pestana, { pestana = it }) { editando = null; hojaAbierta = true }
    }) { relleno ->
        Box(Modifier.padding(relleno).fillMaxSize()) {
            if (estado == null) Bienvenida(abrirImportar)
            else {
                val tocar: (Movimiento) -> Unit = { editando = it; hojaAbierta = true }
                when (pestana) {
                    Pestana.HOY -> PantallaHoy(estado, nombre.ifEmpty { "Martín" }, tocar,
                        alAbrirFondo = { hojaHoy = "fondo" }, alAbrirCuentas = { hojaHoy = "cuentas" },
                        alYaPase = { pendientePase = it; hojaHoy = "pase" }, alAbrirMP = ::abrirMP,
                        alCobrar = { d -> almacen.cobrado(d.id, d.previo); vuelta++; aviso("¡Cobrado!") },
                        alNoPagaron = { d -> almacen.noPagaron(d.id, d.previo); vuelta++; aviso("Pasó a ser un gasto tuyo") })
                    Pestana.PLAN -> PantallaPlan(estado, almacen, { vuelta++ }, ::aviso)
                    Pestana.DIAS -> PantallaDias(estado, tocar,
                        alObjetivo = { n -> almacen.aplicarObjetivo(n); vuelta++; aviso(if (n > 0) "Listo: tu límite de hoy ahora es ${pesos(n)}" else "Volviste a tu límite normal") },
                        alRecalibrar = { almacen.recalibrar(); vuelta++; aviso("Recalibrado desde hoy") },
                        alQuitarRecal = { almacen.quitarRecalibracion(); vuelta++ })
                    Pestana.AJUSTES -> PantallaAjustes(estado, version, AccionesAjustes(
                        importar = abrirImportar, exportar = abrirExportar,
                        nombre = { campo("nombre", JsonPrimitive(it)); aviso("Guardado") },
                        aviso = { campo("aviso", num(it)) },
                        fondo = { on -> almacen.activarFondo(on); vuelta++; if (on) asegurarNotis() },
                        excesoAparte = { campo("fondoExceso", JsonPrimitive(if (it) "aparte" else "siguiente")) },
                        fondoPrev = { campo("fondoPrev", num(it)) },
                        avisoReserva = { on -> campo("resAviso", JsonPrimitive(on)); if (on) asegurarNotis() },
                        horaReserva = { campo("resHora", JsonPrimitive(it)); aviso("Te aviso todas las noches a las $it") },
                        probarAviso = { if (!AvisoReserva.hayPermiso(ctx)) asegurarNotis() else { AvisoReserva.probar(ctx) } },
                        pasado = { campo("resPasado", num(it)); aviso("Montos guardados") },
                        rendDisp = { almacen.activarRendDisp(it); vuelta++ },
                        rendFondo = { almacen.activarRendFondo(it); vuelta++ },
                        tasa = { campo("rendTasa", num(it)); aviso("Tasa guardada") },
                        rendEnMP = { campo("rendEnMP", JsonPrimitive(it)) },
                        abrirPin = { hojaHoy = "pin" },
                        tema = { campo("tema", JsonPrimitive(it)) },
                        color = { campo("color", JsonPrimitive(it)) },
                        copiaElegir = { elegirCopia.launch("mi-plata-copia-automatica.json") },
                        copiaDesactivar = { com.miplata.kotlin.datos.CopiaAutomatica.desactivar(ctx); vuelta++; aviso("Copia automática desactivada") },
                        copiaEstado = {
                            val c = com.miplata.kotlin.datos.CopiaAutomatica
                            c.error(ctx) ?: c.nombre(ctx)?.let { n -> "Se guarda sola en \"$n\"" + (c.ultima(ctx).takeIf { it > 0 }?.let { t ->
                                " · última: " + java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("es", "AR")).format(java.util.Date(t)) } ?: "") }
                        },
                        pinAlAbrir = { campo("pinApp", JsonPrimitive(it)) },
                        estadoAviso = {
                            (if (AvisoReserva.hayPermiso(ctx)) "Permiso de notificaciones: concedido." else "Falta el permiso de notificaciones: tocá \"Probar aviso\" para darlo.") +
                            (AvisoReserva.proximo(ctx).takeIf { it > 0 }?.let { t -> " Próximo aviso: " +
                                java.text.SimpleDateFormat("EEEE d 'a las' HH:mm", java.util.Locale("es", "AR")).format(java.util.Date(t)) + "." } ?: " No hay aviso programado.") +
                            (if (!AvisoReserva.alarmasExactas(ctx)) " Puede llegar tarde (Android ahorra batería)." else "")
                        },
                    ))
                }
            }
        }
    }

    if (estado != null) {
        val c = remember(vuelta) { Nucleo.calcular(estado, Fechas.hoy()) }
        when (hojaHoy) {
            "fondo" -> if (c.fondo.activo) HojaFondo(estado, c, { hojaHoy = "" },
                alSacar = { m, n, p -> almacen.usarFondo(m, n, p); vuelta++; hojaHoy = ""; aviso("Listo: sacaste ${pesos(m)} del fondo") },
                alQuitarUso = { almacen.quitarUsoFondo(it); vuelta++ }) else LaunchedEffect(Unit) { hojaHoy = "" }
            "cuentas" -> HojaCuentas(estado, c, { hojaHoy = "" },
                alGuardar = { almacen.guardarCuentas(it); vuelta++; aviso("Saldos guardados") },
                alAjustar = { almacen.agregarRecomposicion(it); vuelta++; hojaHoy = ""; aviso("Recomposición anotada") },
                alQuitarAjuste = { almacen.quitarRecomposicion(it); vuelta++ })
            "pin" -> HojaPin(estado.pin.isNotEmpty(), { hojaHoy = "" },
                alGuardar = { actual, nuevo ->
                    if (estado.pin.isNotEmpty() && !almacen.pinCorrecto(actual)) "El PIN actual no es correcto."
                    else { almacen.ponerPin(nuevo); vuelta++; hojaHoy = ""; aviso("PIN guardado"); null }
                },
                alQuitar = { actual ->
                    if (!almacen.pinCorrecto(actual)) "Escribí el PIN actual para quitarlo."
                    else { almacen.quitarPin(); vuelta++; hojaHoy = ""; aviso("PIN quitado"); null }
                })
            "pase" -> DialogoYaPase(pendientePase, { hojaHoy = "" }) { almacen.sumarPasado(it); vuelta++; hojaHoy = ""; aviso("Anotado: pasaste ${pesos(it)} a tu reserva") }
        }
    }

    if (hojaAbierta) HojaAnotar(
        editando = editando,
        alCerrar = { hojaAbierta = false },
        alGuardar = { m ->
            almacen.anotar(m.monto, m.nota, m.categoria, m.entrada, m.meLoDevuelven, m.fecha, m.editarId)
            hojaAbierta = false; vuelta++
            aviso(when { m.entrada -> "Entrada anotada: +${pesos(m.monto)}"; m.meLoDevuelven -> "Anotado en \"Me deben\""; else -> "Gasto anotado" })
        },
        alBorrar = { id -> almacen.borrarMovimiento(id); hojaAbierta = false; vuelta++; aviso("Borrado") },
        alMarcarDevuelto = { id -> almacen.marcarDevuelto(id); hojaAbierta = false; vuelta++; aviso("Marcado como devuelto") },
    )
}

@Composable
fun BarraAbajo(actual: Pestana, alElegir: (Pestana) -> Unit, alSumar: () -> Unit) {
    val k = LocalColores.current
    Row(Modifier.fillMaxWidth().background(k.tarjeta).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        @Composable fun Boton(p: Pestana) {
            val elegido = p == actual
            Column(Modifier.weight(1f).clickable { alElegir(p) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.background(if (elegido) k.verdeClaro else k.tarjeta, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Icon(p.icono, null, tint = if (elegido) k.verde else k.suave)
                }
                Text(p.titulo, fontSize = 12.sp, color = if (elegido) k.verde else k.suave, fontWeight = if (elegido) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        Boton(Pestana.HOY); Boton(Pestana.PLAN)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            FloatingActionButton(onClick = alSumar, shape = CircleShape, containerColor = k.verde, contentColor = k.sobreVerde,
                elevation = FloatingActionButtonDefaults.elevation(2.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = "Anotar gasto")
            }
        }
        Boton(Pestana.DIAS); Boton(Pestana.AJUSTES)
    }
}

@Composable
fun Bienvenida(alImportar: () -> Unit) {
    val k = LocalColores.current
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Mi plata K", fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Serif, color = k.verde)
        Spacer(Modifier.height(10.dp))
        Text("Para empezar, traé tus datos desde una copia de seguridad (.json) de tu Mi plata de siempre.",
            textAlign = TextAlign.Center, color = k.suave)
        Spacer(Modifier.height(24.dp))
        Button(onClick = alImportar, Modifier.fillMaxWidth().height(52.dp)) { Text("Importar copia de seguridad", fontSize = 16.sp) }
    }
}
