package com.miplata.kotlin.datos

import android.content.Context
import com.miplata.kotlin.nucleo.Estado
import com.miplata.kotlin.nucleo.Fechas
import kotlinx.serialization.json.*
import java.io.File

/* Guarda los datos en un archivo JSON con el MISMO formato que las copias de Mi plata.
   Los cambios se hacen sobre el JSON completo, así nunca se pierde ningún dato que
   esta versión todavía no use (cuotas, IPC, alias de MP, copias, cierres…). */
class Almacen(carpeta: File) {
    constructor(context: Context) : this(context.filesDir)
    private val archivo = File(carpeta, "estado.json")
    private val respaldo = File(carpeta, "estado.anterior.json")

    var crudo: JsonObject? = leer()
        private set

    val estado: Estado? get() = crudo?.let { runCatching { Estado.desdeJson(it) }.getOrNull() }

    private fun leer(): JsonObject? =
        if (archivo.exists()) runCatching { Json.parseToJsonElement(archivo.readText()).jsonObject }.getOrNull() else null

    private fun guardar(nuevo: JsonObject) {
        if (archivo.exists()) archivo.copyTo(respaldo, overwrite = true)
        val tmp = File(archivo.parent, "estado.tmp")
        tmp.writeText(nuevo.toString())
        tmp.renameTo(archivo)
        crudo = nuevo
    }

    /** Importa una copia .json. Devuelve un mensaje de error o null si salió bien. */
    fun importar(texto: String): String? {
        val o = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull()
            ?: return "Ese archivo no es una copia válida de Mi plata."
        if (o["items"] !is JsonArray || o["spent"] !is JsonArray) return "Ese archivo no es una copia válida de Mi plata."
        runCatching { Estado.desdeJson(o) }.onFailure { return "No pude leer la copia: ${it.message}" }
        guardar(o)
        return null
    }

    fun exportar(): String = crudo?.toString() ?: "{}"

    private fun cambiar(f: MutableMap<String, JsonElement>.() -> Unit) {
        val m = (crudo ?: return).toMutableMap()
        m.f()
        m["upd"] = JsonPrimitive(System.currentTimeMillis())
        guardar(JsonObject(m))
    }

    private fun MutableMap<String, JsonElement>.spent(): MutableList<JsonElement> =
        ((this["spent"] as? JsonArray)?.toMutableList() ?: mutableListOf())

    /** Anota un gasto (o una entrada si `entrada`). Igual que la app original. */
    fun anotar(monto: Double, nota: String, categoria: String?, entrada: Boolean, meLoDevuelven: Boolean,
               fecha: String = Fechas.hoy(), editarId: Long? = null) {
        cambiar {
            val lista = spent()
            val datos = buildMap<String, JsonElement> {
                put("date", JsonPrimitive(fecha))
                put("amt", JsonPrimitive(if (entrada) -Math.abs(monto) else Math.abs(monto)))
                put("note", JsonPrimitive(nota))
                if (!entrada && !categoria.isNullOrEmpty()) put("cat", JsonPrimitive(categoria))
                if (!entrada && meLoDevuelven) put("dev", JsonPrimitive(1))
            }
            val i = if (editarId != null) lista.indexOfFirst { (it as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull == editarId } else -1
            if (i >= 0) {
                val viejo = lista[i].jsonObject.toMutableMap()
                val yaCobrado = viejo["dev"]?.jsonPrimitive?.intOrNull == 2
                viejo.remove("cat"); viejo.remove("dev")
                viejo.putAll(datos)
                if (datos.containsKey("dev") && yaCobrado) viejo["dev"] = JsonPrimitive(2)
                lista[i] = JsonObject(viejo)
            } else {
                lista += JsonObject(mapOf("id" to JsonPrimitive(System.currentTimeMillis())) + datos)
            }
            this["spent"] = JsonArray(lista)
        }
    }

    fun borrarMovimiento(id: Long) = cambiar {
        this["spent"] = JsonArray(spent().filter { (it as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull != id })
    }

    /** "Me lo devolvieron": el gasto deja de contar en el saldo (dev = 2). */
    fun marcarDevuelto(id: Long) = cambiar {
        this["spent"] = JsonArray(spent().map { e ->
            val o = e as? JsonObject ?: return@map e
            if (o["id"]?.jsonPrimitive?.longOrNull == id) JsonObject(o + mapOf("dev" to JsonPrimitive(2), "cob" to JsonPrimitive(Fechas.hoy()))) else o
        })
    }

    /** Marca o desmarca un gasto importante como pagado. */
    fun marcarPagado(indice: Int, pagado: Boolean) = cambiar {
        val items = (this["items"] as? JsonArray)?.toMutableList() ?: return@cambiar
        val o = items.getOrNull(indice) as? JsonObject ?: return@cambiar
        items[indice] = JsonObject(o + ("p" to JsonPrimitive(pagado)))
        this["items"] = JsonArray(items)
    }
}
