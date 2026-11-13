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
import androidx.compose.ui.draw.blur
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
import kotlinx.coroutines.launch

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
    var hojaHoy by remember { mutableStateOf("") }
    var colaboraAbierta by remember { mutableStateOf(false) }          // "fondo", "cuentas", "pase", "pin"
    var errorPin by remember { mutableStateOf<String?>(null) }
    var irAPeriodoNuevo by remember { mutableStateOf(false) }
    var pendientePase by remember { mutableDoubleStateOf(0.0) }
    fun aviso(t: String) = Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()

    /* Cada vez que cambian los datos se reprograma el aviso de la noche */
    LaunchedEffect(vuelta) {
        runCatching { AvisoReserva.programar(ctx) }
        runCatching { AvisoReserva.programarRecordatorio(ctx) }
        com.miplata.kotlin.widget.Widget.actualizar(ctx)
        if (vuelta > 0) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.miplata.kotlin.datos.CopiaAutomatica.guardar(ctx) }
    }
    /* Al abrir: cierre nocturno de los días que quedaron sin cerrar (si la app estaba cerrada a medianoche) */
    LaunchedEffect(Unit) { if (almacen.cambiarSiHace { com.miplata.kotlin.nucleo.Periodo.congelarDias(it, Fechas.hoy()) }) vuelta++ }
    LaunchedEffect(Unit) {
        val hayIpc = estado?.items?.any { Ipc.de(it) != null } == true
        val cuando = ctx.getSharedPreferences("ipc", android.content.Context.MODE_PRIVATE).getLong("cuando", 0)
        if (hayIpc && System.currentTimeMillis() - cuando > 2 * 86_400_000L) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Ipc.bajar(ctx) }; vuelta++ }
    }
    LaunchedEffect(estado?.ocultar) { Privacidad.ocultar = estado?.ocultar == true; Privacidad.mostrando = false }
    val prefsCopia = remember { ctx.getSharedPreferences("aviso_copia", android.content.Context.MODE_PRIVATE) }
    var avisoCopiaVersion by remember { mutableIntStateOf(0) }
    fun copiaHecha() { prefsCopia.edit().putLong("ultima", System.currentTimeMillis()).apply(); avisoCopiaVersion++ }
    val exportarExcel = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null && estado != null) runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { com.miplata.kotlin.exportar.Exportar.xlsx(com.miplata.kotlin.exportar.Exportar.datos(estado, Fechas.hoy()), it) } }
            .onSuccess { aviso("Excel guardado") }.onFailure { aviso("No pude guardar el Excel") }
    }
    val exportarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && estado != null) runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { com.miplata.kotlin.exportar.Exportar.pdf(com.miplata.kotlin.exportar.Exportar.datos(estado, Fechas.hoy()), "Mi plata v$version", it) } }
            .onSuccess { aviso("PDF guardado") }.onFailure { aviso("No pude guardar el PDF") }
    }
    var pidiendoPinOjo by remember { mutableStateOf(false) }
    fun compartirTexto(t: String, paquete: String? = null) {
        val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, t)
        if (paquete != null) i.setPackage(paquete)
        runCatching { ctx.startActivity(if (paquete == null) android.content.Intent.createChooser(i, "Compartir resumen") else i) }
            .onFailure { if (paquete != null) compartirTexto(t) }
    }
    LaunchedEffect(estado?.color, estado?.tema) { estado?.let { alCambiarTema(it.color to it.tema) } }
    val elegirCopia = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) { com.miplata.kotlin.datos.CopiaAutomatica.elegir(ctx, uri); vuelta++
            aviso(if (com.miplata.kotlin.datos.CopiaAutomatica.error(ctx) == null) "Copia automática activada" else "No pude escribir ahí: elegí otro lugar") }
    }
    val pedirNotis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) aviso("Sin permiso de notificaciones no te puedo avisar. Activalo en Ajustes del teléfono → Apps → Mi plata.")
        vuelta++
    }
    fun asegurarNotis() { if (!AvisoReserva.hayPermiso(ctx) && android.os.Build.VERSION.SDK_INT >= 33) pedirNotis.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    /* Al abrir: si el aviso de la noche está activo y falta el permiso, pedirlo (si no, el aviso queda mudo) */
    LaunchedEffect(Unit) { if (estado?.modoFondo == true && estado.resAviso) asegurarNotis() }
    fun campo(clave: String, valor: kotlinx.serialization.json.JsonElement) { almacen.cambiarCampo(clave, valor); vuelta++ }
    fun abrirMP() { if (!MercadoPago.abrir(ctx)) aviso("No encontré la app de Mercado Pago") }

    /* Pagar un gasto importante en Mercado Pago: copia el alias o código, abre MP,
       y al volver pregunta "¿Ya lo pagaste?" (como la app original). */
    var pagando by remember { mutableStateOf<Triple<Int, String, Long>?>(null) }
    var preguntarPago by remember { mutableStateOf<Pair<Int, String>?>(null) }
    fun pagarMP(i: Int) {
        val it = almacen.estado?.items?.getOrNull(i) ?: return
        val dato = it.extra["mp"].texto() ?: it.extra["cod"].texto()
        if (dato != null) { MercadoPago.copiar(ctx, "Mercado Pago", dato); aviso((if (it.extra["mp"] != null) "Alias" else "Código") + " copiado: $dato · pegalo en Mercado Pago") }
        pagando = Triple(i, it.n, System.currentTimeMillis())
        abrirMP()
    }
    val dueño = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(dueño) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) pagando?.let { (i, n, desde) ->
                if (System.currentTimeMillis() - desde > 2500) { pagando = null
                    val item = almacen.estado?.items?.getOrNull(i)
                    if (item != null && !item.p && item.n == n) preguntarPago = i to n }
            }
        }
        dueño.lifecycle.addObserver(obs); onDispose { dueño.lifecycle.removeObserver(obs) }
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
        }.onSuccess { aviso("Copia guardada"); ctx.getSharedPreferences("aviso_copia", android.content.Context.MODE_PRIVATE).edit().putLong("ultima", System.currentTimeMillis()).apply() }
            .onFailure { aviso("No pude guardar la copia") }
    }
    /* Demo automática: arranca siempre de los mismos datos de ejemplo y al terminar los deja limpios */
    val alcance = rememberCoroutineScope()
    fun cargarDemo() { almacen.entrarDemo(estadoDemo(almacen.crudo)); hojaAbierta = false; editando = null; hojaHoy = ""; vuelta++ }
    fun reproducirDemo() { alcance.launch {
        val colorAntes = almacen.crudo?.get("color")
        Demo.correr(preparar = { cargarDemo(); pestana = Pestana.HOY },
            guion = { guionMiPlata(AccionesDemo(
                calculo = { almacen.estado?.let { Nucleo.calcular(it, Fechas.hoy()) } },
                ultimoGasto = { almacen.estado?.spent?.maxByOrNull { it.id }?.id },
                borrar = { almacen.borrarMovimiento(it); vuelta++ },
                ajustar = { m -> m.forEach { (k, v) -> almacen.cambiarCampo(k, v) }; vuelta++ },
                hoja = { hojaHoy = it },
                estado = { almacen.estado })) },
            alTerminar = { cargarDemo(); colorAntes?.let { almacen.cambiarCampo("color", it); vuelta++ } })   // datos de ejemplo de nuevo y tu color
    } }
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
    /* Con el cartel "Ayudá al creador" abierto, la app queda borrosa detrás (Android 12 o más nuevo) */
    val borroso by androidx.compose.animation.core.animateDpAsState(if (colaboraAbierta) 14.dp else 0.dp, label = "borroso")
    Scaffold(modifier = Modifier.blur(borroso), containerColor = if (k.argentina) androidx.compose.ui.graphics.Color.Transparent else k.fondo, bottomBar = {
        if (estado != null && !Demo.bienvenida) BarraAbajo(pestana, { pestana = it }) { editando = null; hojaAbierta = true }
    }) { relleno ->
        Box(Modifier.padding(relleno).fillMaxSize()) {
            if (estado == null || Demo.bienvenida) Bienvenida(abrirImportar) { n, sueldo, ini, fin, gastos ->
                if (Demo.corriendo) { almacen.entrarDemo(estadoDemo(almacen.crudo)); Demo.bienvenida = false; pestana = Pestana.HOY; vuelta++ }
                else { almacen.empezarDeCero(n, sueldo, ini, fin, gastos); vuelta++; aviso("¡Bienvenido a Mi plata!") } }
            else {
                val tocar: (Movimiento) -> Unit = { editando = it; hojaAbierta = true }
                when (pestana) {
                    Pestana.HOY -> PantallaHoy(estado, nombre.ifEmpty { "Martín" }, tocar,
                        alAbrirFondo = { hojaHoy = "fondo" }, alAbrirCuentas = { hojaHoy = "cuentas" },
                        alYaPase = { pendientePase = it; hojaHoy = "pase" }, alAbrirMP = ::abrirMP,
                        alCobrar = { d -> almacen.cobrado(d.id, d.previo); vuelta++; aviso("¡Cobrado!") },
                        alNoPagaron = { d -> almacen.noPagaron(d.id, d.previo); vuelta++; aviso("Pasó a ser un gasto tuyo") },
                        alPeriodoNuevo = { irAPeriodoNuevo = true; pestana = Pestana.PLAN },
                        alIrAPlan = { pestana = Pestana.PLAN },
                        alOjo = { if (Privacidad.mostrando || estado.pin.isEmpty()) Privacidad.mostrando = !Privacidad.mostrando else pidiendoPinOjo = true },
                        alCompartir = { hojaHoy = "resumen" },
                        alIA = { hojaHoy = "ia" },
                        alSalirDemo = { almacen.salirDemo(); vuelta++; aviso("Volviste a tus datos") },
                        avisoCopia = run {
                            val auto = com.miplata.kotlin.datos.CopiaAutomatica.uri(ctx) != null
                            val ultima = maxOf(prefsCopia.getLong("ultima", 0), com.miplata.kotlin.datos.CopiaAutomatica.ultima(ctx))
                            val dias = if (ultima > 0) ((System.currentTimeMillis() - ultima) / 86_400_000L).toInt() else null
                            val pospuesto = System.currentTimeMillis() < prefsCopia.getLong("posponer", 0)
                            avisoCopiaVersion.let { }
                            if (!auto && !pospuesto && !estado.demo && (if (dias == null) estado.spent.size >= 3 else dias >= 7)) ({
                                TarjetaAvisoCopia(dias, alGuardar = { abrirExportar() }, alAutomatica = { elegirCopia.launch("mi-plata-copia-automatica.json") },
                                    alMasTarde = { prefsCopia.edit().putLong("posponer", System.currentTimeMillis() + 3 * 86_400_000L).apply(); avisoCopiaVersion++ })
                            }) else null
                        })
                    Pestana.PLAN -> PantallaPlan(estado, almacen, { vuelta++ }, ::aviso, ::pagarMP, irAPeriodoNuevo) { irAPeriodoNuevo = false }
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
                        pasado = { almacen.fijarPasado(it); vuelta++; aviso("Montos guardados") },
                        rendDisp = { almacen.activarRendDisp(it); vuelta++ },
                        rendFondo = { almacen.activarRendFondo(it); vuelta++ },
                        tasa = { campo("rendTasa", num(it)); aviso("Tasa guardada") },
                        rendEnMP = { campo("rendEnMP", JsonPrimitive(it)) },
                        abrirPin = { hojaHoy = "pin" },
                        copiarAlias = { MercadoPago.copiar(ctx, "Alias", MercadoPago.ALIAS_CREADOR); aviso("Alias copiado: ${MercadoPago.ALIAS_CREADOR} 🙌") },
                        abrirMP = ::abrirMP,
                        abrirWebIA = { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://aistudio.google.com/apikey"))) } },
                        avisar = ::aviso,
                        ocultar = { campo("ocultar", JsonPrimitive(it)) },
                        recordatorio = { on -> campo("recOn", JsonPrimitive(on)); if (on) { asegurarNotis(); aviso("Te recuerdo todos los días a las ${estado.recHora}") } },
                        horaRecordatorio = { campo("recHora", JsonPrimitive(it)); aviso("Te recuerdo a las $it") },
                        excel = { exportarExcel.launch("mi-plata-${Fechas.hoy()}.xlsx") },
                        pdf = { exportarPdf.launch("mi-plata-${Fechas.hoy()}.pdf") },
                        copias = (estado.crudo["copias"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.get("iso").texto() } ?: emptyList(),
                        restaurarCierre = { i -> if (almacen.cambiarSiHace { com.miplata.kotlin.nucleo.Periodo.restaurarCierre(it, i) }) { vuelta++; aviso("Restaurado") } },
                        reproducirDemo = ::reproducirDemo,
                        demo = { on -> if (on) { almacen.entrarDemo(estadoDemo(almacen.crudo)); pestana = Pestana.HOY; aviso("Modo demo: tus datos quedaron guardados aparte") }
                                       else { almacen.salirDemo(); aviso("Volviste a tus datos") }; vuelta++ },
                        borrarTodo = { pin ->
                            if (estado.pin.isNotEmpty() && !almacen.pinCorrecto(pin)) "PIN incorrecto."
                            else { almacen.borrarTodo(); vuelta++; pestana = Pestana.HOY; aviso("Listo: la app quedó vacía"); null }
                        },
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
                alQuitarUso = { almacen.quitarUsoFondo(it); vuelta++ }, alAbrirMP = ::abrirMP) else LaunchedEffect(Unit) { hojaHoy = "" }
            "cuentas" -> HojaCuentas(estado, c, { hojaHoy = "" },
                alGuardar = { almacen.guardarCuentas(it); vuelta++; aviso("Saldos guardados") },
                alAjustar = { almacen.agregarRecomposicion(it); vuelta++; hojaHoy = ""; aviso("Recomposición anotada") },
                alAnotarDiferencia = { d -> almacen.anotar(Math.abs(d), if (d < 0) "Gasto sin anotar" else "Entrada sin anotar", null, d > 0, false, moverCuenta = false)
                    vuelta++; hojaHoy = ""; aviso(if (d < 0) "Anotado como gasto" else "Anotado como entrada") },
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
            "ia" -> PantallaIA(estado, { hojaHoy = "" }) { hojaHoy = ""; pestana = Pestana.AJUSTES }
            "resumen" -> HojaResumen(com.miplata.kotlin.exportar.Exportar.resumenTexto(estado, Fechas.hoy()), { hojaHoy = "" },
                alCompartir = { compartirTexto(it) }, alWhatsApp = { compartirTexto(it, "com.whatsapp") },
                alCopiar = { MercadoPago.copiar(ctx, "Resumen", it); aviso("Copiado") })
            "pase" -> DialogoYaPase(pendientePase, { hojaHoy = "" }) { almacen.sumarPasado(it); vuelta++; hojaHoy = ""; aviso("Anotado: pasaste ${pesos(it)} a tu reserva") }
        }
    }

    if (pidiendoPinOjo) {
        var pin by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { pidiendoPinOjo = false }, title = { Text("Ingresá tu PIN") },
            text = { Column { CampoPin(pin, { pin = it }, "PIN"); err?.let { Text(it, color = k.rojo) } } },
            confirmButton = { TextButton(onClick = { if (almacen.pinCorrecto(pin)) { Privacidad.mostrando = true; pidiendoPinOjo = false } else err = "PIN incorrecto." }) { Text("Mostrar") } },
            dismissButton = { TextButton(onClick = { pidiendoPinOjo = false }) { Text("Cancelar") } })
    }

    preguntarPago?.let { (i, n) ->
        val item = estado?.items?.getOrNull(i)
        AlertDialog(onDismissRequest = { preguntarPago = null }, title = { Text("¿Ya lo pagaste?") },
            text = { Text("${n.ifBlank { "Gasto" }} · ${pesos(item?.a ?: 0.0)}") },
            confirmButton = { TextButton(onClick = { preguntarPago = null; almacen.marcarPagado(i, true); vuelta++; aviso("Marcado como pagado ✅") }) { Text("Sí, marcar pagado") } },
            dismissButton = { TextButton(onClick = { preguntarPago = null }) { Text("Todavía no") } })
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

    /* "Ayudá al creador" flotante: burbuja que se convierte en el cartel grande */
    ColaboraFlotante(puede = estado != null && pestana == Pestana.HOY && !estado.demo && !Demo.corriendo &&
        estado.spent.size >= 5 && hojaHoy.isEmpty() && !hojaAbierta,
        alCopiar = { MercadoPago.copiar(ctx, "Alias", MercadoPago.ALIAS_CREADOR); aviso("Alias copiado: ${MercadoPago.ALIAS_CREADOR} 🙌") },
        alAbrirMP = ::abrirMP,
        alYaColabore = { Colabora.yaColaboro(ctx); aviso("¡Gracias de corazón! 💚") },
        alCambiarAbierto = { colaboraAbierta = it })

    CapaDemo()
}

@Composable
fun BarraAbajo(actual: Pestana, alElegir: (Pestana) -> Unit, alSumar: () -> Unit) {
    val k = LocalColores.current
    Row(Modifier.fillMaxWidth().background(k.tarjeta).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        @Composable fun Boton(p: Pestana) {
            val elegido = p == actual
            Column(Modifier.weight(1f).demo("nav.${p.name}", accion = { alElegir(p) }).clickable { alElegir(p) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.background(if (elegido) k.verdeClaro else k.tarjeta, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Icon(p.icono, null, tint = if (elegido) k.verde else k.suave)
                }
                Text(p.titulo, fontSize = 12.sp, color = if (elegido) k.verde else k.suave, fontWeight = if (elegido) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        Boton(Pestana.HOY); Boton(Pestana.PLAN)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            FloatingActionButton(onClick = alSumar, modifier = Modifier.demo("fab", alSumar), shape = CircleShape, containerColor = k.verde, contentColor = k.sobreVerde,
                elevation = FloatingActionButtonDefaults.elevation(2.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = "Anotar gasto")
            }
        }
        Boton(Pestana.DIAS); Boton(Pestana.AJUSTES)
    }
}

