package com.miplata.kotlin.ia

import android.content.Context
import com.miplata.kotlin.nucleo.*
import com.miplata.kotlin.ui.pesos
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL

/* Preguntale a tu plata: Gemini (Google) con la clave gratis de la persona.
   La clave queda solo en el teléfono. Se manda un resumen de los datos del período como contexto. */
object Gemini {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ia", Context.MODE_PRIVATE)
    fun clave(ctx: Context): String = prefs(ctx).getString("clave", "") ?: ""
    fun guardarClave(ctx: Context, c: String) = prefs(ctx).edit().putString("clave", c.trim()).apply()

    /* Modelos Flash del plan gratis; si uno no existe más, se prueba el siguiente y se recuerda el que anduvo. */
    private val MODELOS = listOf("gemini-flash-latest", "gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash")

    data class Mensaje(val deVos: Boolean, val texto: String)
    class ErrorIA(mensaje: String) : Exception(mensaje)

    /** Hace la pregunta. Llamar fuera del hilo principal. */
    fun preguntar(ctx: Context, contexto: String, historial: List<Mensaje>, pregunta: String): String {
        val clave = clave(ctx).ifEmpty { throw ErrorIA("Falta tu clave de Gemini: cargala en Ajustes → Inteligencia artificial.") }
        val sistema = """Sos el asistente de "Mi plata", una app argentina para administrar la plata día a día.
Hablás en español rioplatense (vos), claro, cálido y corto: 2 a 6 oraciones o una lista breve. Plata en pesos con punto de miles, por ejemplo $20.859.
Respondé SOLO con los datos de abajo; si algo no está, decilo. No inventes gastos ni números. Si hacés cuentas, mostralas simples.
No podés cambiar datos de la app: si te piden anotar algo, explicá que se hace con el botón +.
Cómo funciona la app: lo que queda después de los gastos importantes se reparte por día hasta el próximo cobro; lo que sobra un día pasa al siguiente (o al fondo de ahorro si está activo); si te pasás, se descuenta del día siguiente.

DATOS DE LA PERSONA (hoy ${Fechas.hoy()}):
$contexto"""
        val contenidos = (historial.takeLast(10) + Mensaje(true, pregunta)).map { m ->
            JsonObject(mapOf("role" to JsonPrimitive(if (m.deVos) "user" else "model"),
                "parts" to JsonArray(listOf(JsonObject(mapOf("text" to JsonPrimitive(m.texto)))))))
        }
        val cuerpo = JsonObject(mapOf(
            "systemInstruction" to JsonObject(mapOf("parts" to JsonArray(listOf(JsonObject(mapOf("text" to JsonPrimitive(sistema))))))),
            "contents" to JsonArray(contenidos),
            "generationConfig" to JsonObject(mapOf("temperature" to JsonPrimitive(0.4), "maxOutputTokens" to JsonPrimitive(1024))),
        )).toString()

        val recordado = prefs(ctx).getString("modelo", null)
        val orden = listOfNotNull(recordado) + MODELOS.filter { it != recordado }
        var ultimoError = "No pude conectarme con Gemini."
        for (modelo in orden) {
            val c = (URL("https://generativelanguage.googleapis.com/v1beta/models/$modelo:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 15_000; readTimeout = 60_000
                setRequestProperty("Content-Type", "application/json"); setRequestProperty("x-goog-api-key", clave)
            }
            try {
                c.outputStream.use { it.write(cuerpo.toByteArray()) }
                val codigo = c.responseCode
                val texto = (if (codigo in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                when {
                    codigo in 200..299 -> {
                        prefs(ctx).edit().putString("modelo", modelo).apply()
                        val partes = Json.parseToJsonElement(texto).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
                        val respuesta = partes?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.joinToString("")?.trim()
                        return respuesta?.takeIf { it.isNotEmpty() } ?: "No tengo una respuesta para eso. Probá preguntarlo de otra forma."
                    }
                    codigo == 404 || (codigo == 400 && texto.contains("not found", true)) -> { ultimoError = "Modelo no disponible"; continue }
                    codigo == 429 -> throw ErrorIA("Llegaste al límite gratis de Gemini por ahora. Probá de nuevo en un rato.")
                    codigo == 400 && texto.contains("API key", true) || codigo == 401 || codigo == 403 ->
                        throw ErrorIA("La clave de Gemini no es válida. Revisala en Ajustes → Inteligencia artificial.")
                    else -> { ultimoError = "Gemini respondió con un error ($codigo)."; continue }
                }
            } catch (e: ErrorIA) { throw e }
            catch (e: java.io.IOException) { throw ErrorIA("Sin conexión a internet. La IA necesita internet; el resto de la app anda igual.") }
            finally { c.disconnect() }
        }
        throw ErrorIA(ultimoError)
    }

    /** Resumen de tus datos para que la IA pueda responder. */
    fun contexto(S: Estado): String {
        val hoy = Fechas.hoy(); val c = Nucleo.calcular(S, hoy)
        val b = StringBuilder()
        b.appendLine("Nombre: ${S.nombre.ifBlank { "-" }}")
        b.appendLine("Período: del ${S.start} al ${S.end} (cobra de nuevo el ${S.end}). Días que faltan contando hoy: ${c.faltan}.")
        b.appendLine("Ingresos: " + S.ingresos.joinToString { "${it.cat} ${pesos(it.a)}" } + (if (S.previo != 0.0) "; saldo previo ${pesos(S.previo)}" else ""))
        b.appendLine("Gastos importantes (total ${pesos(c.total)}, falta pagar ${pesos(c.porPagar)}): " +
            S.items.filter { it.n.isNotBlank() || it.a > 0 }.joinToString { "${it.n} ${pesos(it.a)}${if (it.p) " (pagado)" else " (pendiente)"}" })
        b.appendLine("Hoy: presupuesto ${pesos(c.quedaHoy + c.gastoHoy)}, gastó ${pesos(c.gastoHoy)}, le queda ${pesos(c.quedaHoy)}. Mañana tendría ${c.manana?.let(::pesos) ?: "-"}.")
        b.appendLine("Base por día: ${pesos(c.baseHoy)}. Saldo del período: ${pesos(c.saldo)}.")
        if (c.fondo.activo) b.appendLine("Fondo de ahorro: ${pesos(c.fondo.totalConRend)} (este período ${pesos(c.fondo.ahorroPeriodo)}). Disponible para gastar: ${pesos(c.fondo.disponible)}.")
        if (S.objetivo > 0) b.appendLine("Objetivo por día: ${pesos(S.objetivo)}.")
        S.metas.filter { it.t > 0 }.forEach { b.appendLine("Meta ${it.n}: juntó ${pesos(Nucleo.juntadoEnMeta(S, it))} de ${pesos(it.t)}${if (it.f.isNotEmpty()) " para el ${it.f}" else ""}.") }
        val r = Nucleo.rachaEnVerde(S, hoy); b.appendLine("Racha de días sin pasarse: ${r.racha}.")
        b.appendLine("Día por día (fecha: tenía / gastó / sobró):")
        Nucleo.diasDelPeriodo(S, hoy, c).filter { it.i <= c.hoyIdx }.forEach { d ->
            b.appendLine("${d.iso} ${d.dia}: ${pesos(d.presupuesto)} / ${pesos(d.gasto)} / ${pesos(d.sobra)}") }
        b.appendLine("Movimientos del período (fecha, monto, categoría, detalle; negativo = entrada):")
        S.spent.sortedBy { it.date }.forEach { e ->
            b.appendLine("${e.date} ${pesos(e.amt)} ${e.cat ?: "-"} ${e.note}${when { e.esMeta -> " [aporte a meta]"; e.dev == 1 -> " [me lo deben]"; e.dev == 2 -> " [me lo devolvieron]"; else -> "" }}") }
        return b.toString()
    }
}
