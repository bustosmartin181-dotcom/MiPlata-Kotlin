package com.miplata.kotlin.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive

/* =====================================================================
   DEMO AUTOMÁTICA — igual que en Mi plata original: se escribe sola con
   los datos de ejemplo para grabar la pantalla. Cuenta regresiva de 5,
   frases arriba, círculo donde "toca", cursor que parpadea al escribir.
   Tocar 3 veces la pantalla la corta.
   ===================================================================== */
object Demo {
    class Registro(val accion: () -> Unit, val escribir: (String) -> Unit, val traer: suspend () -> Unit)
    class Toque(val id: Long, val centro: Offset)

    var corriendo by mutableStateOf(false); private set
    var bienvenida by mutableStateOf(false)          // muestra la bienvenida aunque haya datos
    var frase by mutableStateOf<String?>(null)
    var cuenta by mutableIntStateOf(0)
    var cursor by mutableStateOf<String?>(null)     // campo donde parpadea el cursor
    var parpadeo by mutableStateOf(true)
    var resaltado by mutableStateOf<String?>(null)
    var vitrina by mutableStateOf(false)             // pantalla final "Y además…"
    var colabora by mutableIntStateOf(0)             // burbuja "Ayudá al creador" (1 burbuja, 2 cartel)
    val toques = mutableStateListOf<Toque>()
    val registros = mutableMapOf<String, Registro>()
    val lugares = mutableMapOf<String, Rect>()
    var altoPx = 2000f
    var densidad = 3f
    @Volatile var cancelada = false

    class Corte : Exception()

    suspend fun pausa(ms: Long) {
        var falta = ms
        while (falta > 0) { if (cancelada) throw Corte(); val p = minOf(100L, falta); delay(p); falta -= p }
        if (cancelada) throw Corte()
    }

    /** Toca un botón: si está fuera de la vista se desplaza, muestra el círculo y lo aprieta. */
    suspend fun tocar(clave: String, mover: Boolean = true) {
        val r = registros[clave] ?: run { pausa(300); return }
        if (mover) {
            val l = lugares[clave]
            if (l == null || l.top < 90 * densidad || l.bottom > altoPx - 150 * densidad) { runCatching { r.traer() }; pausa(700) }
        }
        lugares[clave]?.let { toques += Toque(System.nanoTime(), it.center) }
        pausa(450); r.accion(); pausa(350)
    }

    /** Escribe letra por letra con el cursor parpadeando. */
    suspend fun escribir(clave: String, texto: String, ms: Long = 130, espera: Long = 600) {
        val r = registros[clave] ?: return
        r.escribir("")
        try {
            cursor = clave; pausa(espera)
            var t = ""
            for (letra in texto) { t += letra; r.escribir(t); pausa(ms) }
            pausa(400)
        } finally { cursor = null }
    }

    suspend fun traer(clave: String) { registros[clave]?.let { runCatching { it.traer() } }; pausa(900) }

    suspend fun resaltar(clave: String, ms: Long) {
        traer(clave); resaltado = clave
        try { pausa(ms) } finally { resaltado = null }
    }

    var conFrases by mutableStateOf(true)
    fun decir(t: String?) { frase = if (conFrases) t else null }

    /** Arranca la demo. [preparar] carga los datos de ejemplo de cero; [guion] es lo que pasa en pantalla. */
    suspend fun correr(preparar: () -> Unit, guion: suspend Demo.() -> Unit, alTerminar: () -> Unit) {
        if (corriendo) return
        cancelada = false; corriendo = true
        try {
            preparar()
            for (n in 5 downTo 1) { cuenta = n; pausa(1000) }
            cuenta = 0
            guion()
        } catch (_: Corte) {
        } catch (e: Exception) {          // si algo falla, la demo se corta pero la app no se cierra
            if (e is kotlinx.coroutines.CancellationException) throw e
        } finally {
            cuenta = 0; frase = null; cursor = null; resaltado = null; bienvenida = false; vitrina = false; colabora = 0; toques.clear()
            corriendo = false; cancelada = false
            alTerminar()
        }
    }
}

/** Marca un botón o campo para que la demo lo pueda encontrar, tocar o escribir. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.demo(clave: String, accion: (() -> Unit)? = null, escribir: ((String) -> Unit)? = null): Modifier {
    val pedido = remember { BringIntoViewRequester() }
    val a by rememberUpdatedState(accion)
    val e by rememberUpdatedState(escribir)
    DisposableEffect(clave) {
        val r = Demo.Registro({ a?.invoke() }, { t -> e?.invoke(t) }, { pedido.bringIntoView() })
        Demo.registros[clave] = r
        onDispose { if (Demo.registros[clave] === r) { Demo.registros.remove(clave); Demo.lugares.remove(clave) } }
    }
    return this.bringIntoViewRequester(pedido).onGloballyPositioned { Demo.lugares[clave] = it.boundsInWindow() }
}

/** Cursor dibujado al final del texto mientras la demo escribe (el teclado no se abre). */
@Composable
fun cursorDemo(clave: String): VisualTransformation {
    val k = LocalColores.current
    if (Demo.cursor != clave) return VisualTransformation.None
    val visible = Demo.parpadeo
    val color = if (visible) k.verde else Color.Transparent
    return VisualTransformation { texto ->
        val t = buildAnnotatedString { append(texto); withStyle(SpanStyle(color = color, fontWeight = FontWeight.Light)) { append("|") } }
        TransformedText(t, object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = offset
            override fun transformedToOriginal(offset: Int) = offset.coerceAtMost(texto.length)
        })
    }
}

/** Capa encima de todo: cuenta regresiva, frase, círculos de los toques. Corta con 3 toques. */
@Composable
fun CapaDemo() {
    if (!Demo.corriendo) return
    val dens = LocalDensity.current
    LaunchedEffect(Unit) { while (true) { Demo.parpadeo = !Demo.parpadeo; delay(500) } }
    Box(Modifier.fillMaxSize()
        .onGloballyPositioned { Demo.altoPx = it.size.height.toFloat(); Demo.densidad = dens.density }
        .pointerInput(Unit) {
            val toques = ArrayDeque<Long>()
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent()
                    ev.changes.forEach { c ->
                        if (c.pressed && !c.previousPressed) {
                            val t = System.currentTimeMillis(); toques.addLast(t)
                            while (toques.isNotEmpty() && t - toques.first() > 1200) toques.removeFirst()
                            if (toques.size >= 3) Demo.cancelada = true
                        }
                        c.consume()
                    }
                }
            }
        }) {
        if (Demo.vitrina) Vitrina()
        Demo.toques.toList().forEach { t -> key(t.id) { CirculoToque(t) } }

        val f = Demo.frase
        val opacidad = remember { Animatable(0f) }
        var ultima by remember { mutableStateOf("") }
        LaunchedEffect(f) { if (f != null) { ultima = f; opacidad.animateTo(1f, tween(350)) } else opacidad.animateTo(0f, tween(350)) }
        if (ultima.isNotEmpty()) Text(ultima, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp).fillMaxWidth()
            .alpha(opacidad.value).background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

        if (Demo.cuenta > 0) Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Empezá a grabar la pantalla", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(Demo.cuenta.toString(), color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.ExtraBold)
            Text("Para cortar: tocá 3 veces la pantalla", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CirculoToque(t: Demo.Toque) {
    val dens = LocalDensity.current
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(700)); Demo.toques.remove(t) }
    val lado = with(dens) { 56.dp.roundToPx() }
    Box(Modifier.offset { IntOffset(t.centro.x.toInt() - lado / 2, t.centro.y.toInt() - lado / 2) }.size(56.dp)
        .scale(0.4f + 0.9f * anim.value).alpha(1f - anim.value)
        .border(3.dp, Color.White, CircleShape).background(Color.White.copy(alpha = 0.35f), CircleShape))
}

/** Lo que la demo hace en pantalla, en el mismo orden que la de Mi plata original. */
class AccionesDemo(
    val calculo: () -> com.miplata.kotlin.nucleo.Calculo?,
    val ultimoGasto: () -> Long?,
    val borrar: (Long) -> Unit,
    val ajustar: (Map<String, kotlinx.serialization.json.JsonElement>) -> Unit,   // cambia campos de los datos de ejemplo
    val hoja: (String) -> Unit,                                                   // abre ("cuentas", "fondo"…) o cierra ("") una hoja de Hoy
    val estado: () -> com.miplata.kotlin.nucleo.Estado?,
)

suspend fun Demo.guionMiPlata(a: AccionesDemo) {
    val calculo = a.calculo; val ultimoGasto = a.ultimoGasto; val borrar = a.borrar
    val siguiente: suspend () -> Unit = { tocar("bv.siguiente"); pausa(900) }

    bienvenida = true; pausa(300)
    decir("Dejé el lápiz y papel: las cuentas de la casa, ahora en el celu 📱"); pausa(1600)        // 0 · Bienvenido
    tocar("bv.nombre"); escribir("bv.nombre", "Martín", 110, 350)
    pausa(900); siguiente()

    decir("Así funciona: tres pasos y listo"); pausa(5500); siguiente()                 // 1 · Así funciona

    decir("Sin cuenta ni anuncios: tus datos quedan en tu celu"); pausa(5000); siguiente() // 2 · Tu plata, tu decisión

    decir("Cargo lo que cobro"); pausa(1200)                                             // 3 · ¿De dónde viene tu plata?
    tocar("bv.sueldo"); escribir("bv.sueldo", "620000", 130)
    pausa(2200); siguiente()

    decir("Anoto lo que pago sí o sí"); pausa(1000)                                      // 4 · Gastos importantes
    listOf("Alquiler" to "180000", "Luz" to "25000", "Internet" to "20000").forEachIndexed { i, (n, m) ->
        tocar("bv.gn.$i"); escribir("bv.gn.$i", n, 100, 350)
        tocar("bv.ga.$i"); escribir("bv.ga.$i", m, 120, 350)
        pausa(400)
    }
    traer("bv.asiqueda"); pausa(3000); siguiente()

    decir("Y la app me dice cuánto puedo gastar por día"); pausa(5000)                  // 5 · ¡Listo!
    tocar("bv.siguiente"); pausa(1200)                       // Empezar → carga los datos de ejemplo

    decir("Anoto cada gasto en 5 segundos")
    tocar("fab", mover = false); pausa(600)
    tocar("an.monto"); escribir("an.monto", "6500", 150)
    tocar("an.nota"); escribir("an.nota", "Almuerzo", 110)
    tocar("an.cat.Comida"); pausa(500)
    tocar("an.guardar"); pausa(800)

    decir("Sé cuánta plata me queda para hoy")
    traer("hoy.arriba"); pausa(3200)

    decir("Lo que no gasto hoy, pasa a mañana 👉")
    resaltar("hoy.manana", 5500)

    decir("¿Y si me paso hoy? Se descuenta de mañana")
    val c = calculo()
    val exceso = if (c == null) 15000.0     // deja a mañana con poco, como la original
        else maxOf(1000.0, Math.round((c.quedaHoy + (c.baseManana ?: 0.0) * 0.75) / 1000.0) * 1000.0)
    tocar("fab", mover = false); pausa(600)
    tocar("an.monto"); escribir("an.monto", exceso.toLong().toString(), 150)
    tocar("an.nota"); escribir("an.nota", "Cena", 110)
    tocar("an.cat.Salidas"); pausa(500)
    tocar("an.guardar"); pausa(800)
    val idExceso = ultimoGasto()
    resaltar("hoy.manana", 6000)

    decir("Elijo cuánto gastar por día y veo cuánto ahorro")
    tocar("nav.DIAS", mover = false)
    idExceso?.let(borrar)                                    // vuelve a los números de antes
    pausa(800)
    tocar("obj.campo"); escribir("obj.campo", "12000", 160)
    pausa(300); tocar("obj.usar"); pausa(5000)

    decir("¿Alquilás? Te aviso cuándo toca el aumento 🏠")
    tocar("nav.HOY", mover = false); pausa(600)
    resaltar("hoy.ipc", 4500)
    tocar("hoy.ipc.ver"); pausa(900)
    decir("Y te calculo el alquiler nuevo con la inflación del INDEC 📈")
    tocar("plan.ipc.0"); pausa(4500)
    traer("ipc.guardar"); pausa(3500)
    decir("Lo confirmás y queda listo para el mes que viene")
    tocar("ipc.guardar"); pausa(3500)

    /* ---------- Las buenas funciones ---------- */
    tocar("nav.HOY", mover = false); pausa(700)

    decir("Comparo con mi saldo de Mercado Pago 💙")
    resaltar("hoy.cuentas", 2500)
    tocar("hoy.cuentas"); pausa(4500)
    a.hoja(""); pausa(600)

    decir("¿Compré algo para otros? Anoto quién me debe 🤝")
    tocar("fab", mover = false); pausa(600)
    tocar("an.monto"); escribir("an.monto", "8000", 140)
    tocar("an.nota"); escribir("an.nota", "Facturas para la oficina", 80)
    tocar("an.devuelven"); pausa(500)
    tocar("an.guardar"); pausa(900)
    resaltar("hoy.medeben", 4000)

    decir("Activo el fondo de ahorro: lo que no gasto se guarda aparte 🐷")
    tocar("nav.AJUSTES", mover = false); pausa(800)
    traer("aj.fondo"); tocar("aj.fondo.activar", mover = false)
    val inicio = a.estado()?.start ?: com.miplata.kotlin.nucleo.Fechas.hoy()
    a.ajustar(mapOf("modoFondo" to JsonPrimitive(true), "fondoDesde" to JsonPrimitive(inicio), "fondoPrev" to JsonPrimitive(45000),
        "rendOn" to JsonPrimitive(true), "rendDesde" to JsonPrimitive(inicio), "rendPrev" to JsonPrimitive(0), "rendTasa" to JsonPrimitive(19),
        "resAviso" to JsonPrimitive(false), "resPasado" to JsonPrimitive(45000)))      // los $45.000 de antes ya están en la reserva
    /* Mis cuentas: Mercado Pago + Reserva, que coincidan con la app */
    a.estado()?.let { S ->
        val mp = S.cuentas.firstOrNull()?.m ?: 0.0
        fun cuentas(m: Double) = kotlinx.serialization.json.JsonArray(listOf(
            kotlinx.serialization.json.JsonObject(mapOf("n" to JsonPrimitive("Mercado Pago"), "m" to JsonPrimitive(Math.round(m * 100) / 100.0))),
            kotlinx.serialization.json.JsonObject(mapOf("n" to JsonPrimitive("Reserva"), "m" to JsonPrimitive(45000)))))
        a.ajustar(mapOf("cuentas" to cuentas(mp)))
        val S2 = a.estado(); val c2 = calculo()
        if (S2 != null && c2 != null) {
            val d = com.miplata.kotlin.nucleo.Nucleo.conciliar(S2, c2).diferencia
            if (Math.abs(d) >= 1) a.ajustar(mapOf("cuentas" to cuentas(mp - d)))
        }
    }
    resaltar("aj.fondo", 2800)
    tocar("nav.HOY", mover = false); pausa(800)
    resaltar("hoy.fondo", 4500)

    decir("Y rinde como en Mercado Pago, día a día 📈")
    resaltar("hoy.minis", 4000)

    decir("Me dice cuánto pasar a mi reserva 🏦")
    resaltar("hoy.reserva", 4500)

    decir("Pago lo fijo directo con Mercado Pago 💳")
    tocar("nav.PLAN", mover = false); pausa(800)
    resaltar("plan.pagar", 5000)

    decir("Y la pongo con mis colores 🇦🇷")
    val colorAntes = a.estado()?.color ?: "verde"
    tocar("nav.HOY", mover = false); pausa(600)
    a.ajustar(mapOf("color" to JsonPrimitive("argentina"))); pausa(4500)
    a.ajustar(mapOf("color" to JsonPrimitive(colorAntes))); pausa(500)

    decir("Y además…")
    vitrina = true; pausa(9000)
    vitrina = false; pausa(300)

    decir("Si te sirve, podés darle una mano al creador 💚")
    colabora = 1; pausa(2500)
    tocar("colabora.burbuja", mover = false); pausa(5500)
    colabora = 0; pausa(700)

    decir("Gratis 👉 link en mi bio")
    vitrina = true; pausa(4000)
    vitrina = false
}

/** Pantalla final de la demo: lo que no se puede mostrar en vivo, en una grilla que se va llenando. */
@Composable
private fun Vitrina() {
    val k = LocalColores.current
    val cosas = listOf(
        "🎤" to "Dictás el gasto con la voz", "📲" to "Widget para anotar sin abrir la app",
        "✨" to "Le preguntás a tu plata con IA", "🔒" to "PIN y montos ocultos",
        "☁️" to "Copia automática en tu Drive", "📊" to "Exportás a Excel y PDF",
        "🎯" to "Metas de ahorro", "🔔" to "Recordatorio para anotar",
    )
    Column(Modifier.fillMaxSize().background(k.fondo).statusBarsPadding().padding(horizontal = 20.dp).padding(top = 96.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Aparece(0) { Text("Y además, Mi plata…", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontFamily = Serif, color = k.texto) }
        cosas.chunked(2).forEachIndexed { fila, par ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                par.forEachIndexed { col, (icono, texto) ->
                    Aparece(2 + fila * 2 + col, Modifier.weight(1f).fillMaxHeight()) {
                        Column(Modifier.fillMaxSize().background(k.tarjeta, RoundedCornerShape(20.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(icono, fontSize = 30.sp)
                            Text(texto, color = k.texto, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
        Aparece(12, Modifier.fillMaxWidth()) {
            Text("Sin cuenta, sin anuncios y gratis.", Modifier.fillMaxWidth().padding(top = 8.dp), color = k.verde,
                fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Borde naranja cuando la demo "señala" esta parte de la pantalla. */
@Composable
fun Modifier.luz(clave: String): Modifier =
    if (Demo.resaltado == clave) this.border(3.dp, LocalColores.current.naranja, RoundedCornerShape(20.dp)) else this

/** En la demo las hojas van dentro de la misma pantalla (no en una ventana aparte),
    así los círculos, el cursor y las frases de la demo se ven encima. */
@Composable
fun HojaEnLinea(contenido: @Composable () -> Unit) {
    val k = LocalColores.current
    val sube = remember { Animatable(1f) }
    LaunchedEffect(Unit) { sube.animateTo(0f, tween(300)) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f * (1f - sube.value))).statusBarsPadding().padding(top = 40.dp),
        contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().graphicsLayer { translationY = size.height * sube.value }
            .background(k.tarjeta, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .navigationBarsPadding().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(32.dp, 4.dp).background(k.suave.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(16.dp))
            contenido()
        }
    }
}
