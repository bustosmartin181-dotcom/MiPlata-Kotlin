package com.miplata.kotlin.aviso

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.miplata.kotlin.MainActivity
import com.miplata.kotlin.R
import com.miplata.kotlin.datos.Almacen
import com.miplata.kotlin.nucleo.Taller
import com.miplata.kotlin.nucleo.texto
import com.miplata.kotlin.ui.pesos
import kotlinx.coroutines.launch
import java.text.Normalizer

/* Plata que te entra por Mercado Pago.
   Mercado Pago te avisa con una notificación cuando te transfieren ("Fulano te transfirió $2.800").
   Mi plata la lee (solo las de Mercado Pago) y la anota SOLA, sin preguntar:
   - si es un compañero del taller que te debe → marca cobrado lo que te debía en "Me deben";
   - si es alguien de "Repartir siempre" (ej.: Mariela) → se reparte entre hoy y los días que quedan;
   - cualquier otro → entrada que suma a tu día.
   Después te deja un aviso chiquito de lo que anotó, con "Deshacer" por si se equivocó. */
class EscuchaMP : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName != PAQUETE_MP) return
        val ex = n.notification?.extras ?: return
        val titulo = ex.getCharSequence("android.title")?.toString() ?: ""
        val texto = (ex.getCharSequence("android.bigText") ?: ex.getCharSequence("android.text"))?.toString() ?: ""
        EntradasMP.recibida(applicationContext, titulo, texto, n.postTime)
    }

    companion object {
        const val PAQUETE_MP = "com.mercadopago.wallet"
    }
}

object EntradasMP {
    private const val CANAL = "entradas_mp"
    private const val PREFS = "entradas_mp"
    const val DESHACER = "com.miplata.kotlin.ENTRADA_MP_DESHACER"

    /** Nombres a los que siempre se les reparte la plata en los días que quedan (separados por coma). */
    fun repartirA(ctx: Context): String = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("repartir", "Mariela Villegas") ?: ""
    fun guardarRepartirA(ctx: Context, nombres: String) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("repartir", nombres.trim()).apply()
    /** Nombres que NUNCA se anotan solos (ej.: vos mismo pasando el sueldo del banco, o la empresa con el aguinaldo). */
    fun nuncaDe(ctx: Context): String = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("nunca", "Martin Enrique Bustos, El Trapiche") ?: ""
    fun guardarNuncaDe(ctx: Context, nombres: String) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("nunca", nombres.trim()).apply()
    /** Más que esto no se anota solo (sueldo, aguinaldo): solo avisa. */
    const val TOPE = 150_000.0

    /** Apagado a mano desde Ajustes (aunque el permiso siga dado). */
    fun encendida(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("on", true)
    fun encender(ctx: Context, on: Boolean) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()

    data class Leida(val monto: Double, val nombre: String)

    /** ¿Mi plata tiene permiso para leer las notificaciones? */
    fun activa(ctx: Context) = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    fun abrirPermiso(ctx: Context) = runCatching {
        ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    fun reconectar(ctx: Context) = runCatching {
        NotificationListenerService.requestRebind(ComponentName(ctx, EscuchaMP::class.java))
    }

    /** Las últimas notificaciones de Mercado Pago que llegaron (para ver en Ajustes qué leyó la app). */
    fun ultimas(ctx: Context): List<String> =
        (ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("ultimas", "") ?: "").split("\n").filter { it.isNotBlank() }

    private fun sinTildes(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()

    /** "$ 2.800", "$2.800,50" → 2800.0 / 2800.5 */
    private fun monto(s: String): Double? {
        val m = Regex("\\$\\s?([0-9]{1,3}(?:\\.[0-9]{3})*|[0-9]+)(?:,([0-9]{1,2}))?").find(s) ?: return null
        val entero = m.groupValues[1].replace(".", "").toDoubleOrNull() ?: return null
        val dec = m.groupValues[2].takeIf { it.isNotEmpty() }?.padEnd(2, '0')?.toDouble()?.div(100) ?: 0.0
        return entero + dec
    }

    /** Lee una notificación de Mercado Pago. Solo plata que TE ENTRA; descarta pagos tuyos, rendimientos y publicidad. */
    fun leer(titulo: String, texto: String): Leida? {
        val todo = "$titulo. $texto"
        val t = sinTildes(todo)
        val entra = listOf("recibiste", "te transfirio", "te transfirieron", "te envio", "te enviaron", "te mando", "te pago", "te pagaron",
            "te deposito", "ingreso de dinero", "ingresaron", "te llego", "acreditamos", "cobraste").any { t.contains(it) }
        val noEs = listOf("rendimiento", "rindio", "pagaste", "enviaste", "transferiste", "vence", "vencimiento", "oferta", "descuento", "promo",
            "cuota", "prestamo", "credito", "invert", "reserva", "cashback", "reintegro", "devolucion", "sorteo", "ganaste", "beneficio", "tarjeta")
            .any { t.contains(it) }
        if (!entra || noEs) return null
        val m = monto(todo) ?: return null
        if (m <= 0) return null
        val nombre = listOf(
            Regex("^\\s*([^.$!¡]+?)\\s+te\\s+(?:transfiri[oó]|envi[oó]|mand[oó]|pag[oó]|deposit[oó])", RegexOption.IGNORE_CASE),
            /* "De Gustavo Martín Lemos desde su cuenta de Mercado Pago": el "De" puede venir con mayúscula.
               Antes solo buscaba "de" en minúscula y se quedaba con el "de Mercado Pago" del final. */
            Regex("\\b[Dd]e\\s+([A-ZÁÉÍÓÚÑ][\\p{L}]+(?:\\s+[A-ZÁÉÍÓÚÑ][\\p{L}]+){0,4})"),
        ).firstNotNullOfOrNull { r -> (r.findAll(texto) + r.findAll(titulo)).map { it.groupValues[1].trim().removeSuffix(".") }
                .firstOrNull { !esMercadoPago(it) } }
            ?.takeIf { it.length in 2..60 && !it.contains("$") } ?: ""
        return Leida(m, nombre)
    }

    fun recibida(ctx: Context, titulo: String, texto: String, cuando: Long) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        /* guarda las últimas 6 para poder revisarlas en Ajustes */
        val linea = (titulo + " — " + texto).replace("\n", " ").take(160)
        p.edit().putString("ultimas", (listOf(linea) + ultimas(ctx)).distinct().take(6).joinToString("\n")).apply()
        if (!encendida(ctx)) return
        val l = leer(titulo, texto) ?: return
        /* Mercado Pago a veces repite la misma notificación: no la cuenta dos veces */
        val clave = "${l.monto}|${sinTildes(l.nombre)}|${cuando / 120_000}"
        val vistas = (p.getString("vistas", "") ?: "").split(",").filter { it.isNotBlank() }
        if (clave in vistas) return
        p.edit().putString("vistas", (listOf(clave) + vistas).take(40).joinToString(",")).apply()
        Thread { runCatching { anotarSola(ctx, l) } }.start()
    }

    private fun esMercadoPago(n: String) = sinTildes(n).let { it.startsWith("mercado") || it == "mp" }

    /** Dos palabras se parecen si son iguales o, en palabras largas, difieren en una sola letra (Lemus / Lemos). */
    fun parecida(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 4 || b.length < 4 || Math.abs(a.length - b.length) > 1) return false
        /* distancia de edición, cortando apenas pasa de 1 */
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length)
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        return d[a.length][b.length] <= 1
    }

    private fun palabras(t: String) = sinTildes(t).split(Regex("[^a-z0-9ñ]+")).filter { it.length > 1 }

    /** ¿El que te mandó plata ("Gustavo Martín Lemos") es el que buscás ("Gustavo Lemus")?
        Cada palabra que escribiste tiene que estar en el nombre de Mercado Pago; el segundo nombre puede faltar.
        flexible: acepta una letra distinta (solo para deudas, donde ya hay alguien que te debe; "Repartir" y "Nunca" van exactos). */
    fun coincide(nombre: String, buscado: String, flexible: Boolean = false): Boolean {
        val quien = palabras(nombre)
        val partes = palabras(buscado)
        return partes.isNotEmpty() && partes.all { p -> quien.any { if (flexible) parecida(it, p) else it == p } }
    }

    /** ¿Una deuda anotada a mano ("Tortas Gustavo Lemus") es de esta persona? Tienen que aparecer al menos
        dos palabras de su nombre (ej.: nombre y apellido), así un "Gustavo" solo no se confunde con otro. */
    fun notaEsDe(nota: String, nombre: String): Boolean {
        val enNota = palabras(nota)
        val suyas = palabras(nombre).filter { it.length >= 3 }
        return suyas.count { w -> enNota.any { parecida(it, w) } } >= minOf(2, suyas.size)
    }

    /** Con lo que te pagó se cancelan sus deudas de la más vieja a la más nueva. Si no alcanza para la siguiente,
        lo que queda va "a cuenta" de esa (pago parcial). Devuelve (deudas pagadas enteras, pago parcial (id, monto), sobrante). */
    fun repartirPago(monto: Double, deudas: List<Pair<Long, Double>>): Triple<List<Long>, Pair<Long, Double>?, Double> {
        var resto = monto; val enteras = mutableListOf<Long>(); var parcial: Pair<Long, Double>? = null
        for ((id, amt) in deudas) {
            if (resto < 1) break
            if (amt <= resto + 0.5) { enteras += id; resto -= amt }
            else { parcial = id to resto; resto = 0.0; break }
        }
        return Triple(enteras, parcial, Math.max(0.0, resto))
    }

    /** Anota sola la plata que entró y avisa qué hizo. */
    private fun anotarSola(ctx: Context, l: Leida) {
        val a = Almacen(ctx)
        val S = a.estado ?: return
        val de = if (l.nombre.isNotBlank()) " de ${l.nombre}" else ""
        /* Sueldo, aguinaldo o plata tuya que pasás del banco: NO se anota sola (no es plata para gastar hoy) */
        val esNunca = l.nombre.isNotBlank() && nuncaDe(ctx).split(",").any { it.isNotBlank() && coincide(l.nombre, it) }
        if (esNunca || l.monto > TOPE) {
            avisar(ctx, "💰 Te entraron ${pesos(l.monto)}$de", "No lo anoté solo " +
                (if (esNunca) "(está en \"No anotar nunca\")" else "porque es mucha plata (¿sueldo o aguinaldo?)") +
                ". Si querés, cargalo vos en la app como corresponda.", 0L, 0L, emptyList())
            return
        }
        val nota = if (l.nombre.isNotBlank()) "Entrada MP · ${l.nombre}" else "Entrada MP"
        var entrada = 0L; var repartida = 0L; var cobradas = emptyList<Long>()
        var parcialNuevo = 0L; var parcialDe = 0L; var parcialMonto = 0.0
        val texto: String
        /* 1) ¿Alguien que te debe? Un compañero del taller (por su nombre en Compras del taller) o una deuda anotada
              a mano con su nombre en la nota (ej.: "Tortas Gustavo Lemus"). Se cobra de la más vieja a la más nueva;
              si no alcanza, queda a cuenta (pago parcial) y en "Me deben" sigue lo que falta. */
        val mu = if (l.nombre.isBlank()) null else Taller.de(a.crudo).muchachos.firstOrNull { coincide(l.nombre, it.nombre, flexible = true) }
        val pendientes = S.spent.filter { it.dev == 1 && it.amt > 0 }
        val deudas = when {
            l.nombre.isBlank() -> emptyList()
            mu != null && pendientes.any { it.extra["tp"].texto() == mu.id } -> pendientes.filter { it.extra["tp"].texto() == mu.id }
            else -> pendientes.filter { it.extra["tp"] == null && notaEsDe(it.note, l.nombre) }
        }.sortedBy { it.date + it.id }
        if (deudas.isNotEmpty()) {
            val (enteras, parcial, resto) = repartirPago(l.monto, deudas.map { it.id to it.amt })
            if (enteras.isNotEmpty()) { a.cobrarPorMP(enteras); cobradas = enteras }
            if (parcial != null) { parcialNuevo = a.cobrarParcialMP(parcial.first, parcial.second); parcialDe = parcial.first; parcialMonto = parcial.second }
            if (resto >= 1) entrada = a.anotarEntradaMP(resto, nota + " (lo que sobró)")
            val quien = when { mu != null && mu.apodo.isNotBlank() -> mu.apodo; mu != null -> mu.nombre; else -> l.nombre }
            val falta = deudas.sumOf { it.amt } - (l.monto - resto)
            texto = buildString {
                append(if (parcial != null && enteras.isEmpty()) "Lo tomé a cuenta de lo que te debe $quien" else "Lo tomé como pago de lo que te debía $quien")
                append(if (falta >= 1) ". Todavía te debe ${pesos(falta)}." else ". ¡Ya no te debe nada!")
                if (resto >= 1) append(" Sobraron ${pesos(resto)}: los sumé a tu día.")
            }
        } else if (l.nombre.isNotBlank() && repartirA(ctx).split(",").any { it.isNotBlank() && coincide(l.nombre, it) }) {
            /* 2) Alguien de "Repartir siempre" (ej.: Mariela): se reparte en los días que quedan */
            repartida = a.entradaRepartida(l.monto, nota)
            val faltan = runCatching { com.miplata.kotlin.nucleo.Nucleo.calcular(a.estado!!, com.miplata.kotlin.nucleo.Fechas.hoy()).faltan }.getOrDefault(0)
            texto = "La repartí en los días que quedan" + (if (faltan > 0) ": ${pesos(l.monto / faltan)} más por día." else ".")
        } else {
            /* 3) Cualquier otro: suma a tu día */
            entrada = a.anotarEntradaMP(l.monto, nota)
            texto = "La sumé a lo que tenés para hoy."
        }
        runCatching { kotlinx.coroutines.runBlocking { com.miplata.kotlin.widget.Widget.actualizar(ctx) } }
        avisar(ctx, "✅ Anoté ${pesos(l.monto)}$de", texto, entrada, repartida, cobradas, Triple(parcialNuevo, parcialDe, parcialMonto))
    }

    private fun avisar(ctx: Context, titulo: String, cuerpo: String, entrada: Long, repartida: Long, cobradas: List<Long>,
                       parcial: Triple<Long, Long, Double> = Triple(0L, 0L, 0.0)) {
        if (!AvisoReserva.hayPermiso(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Plata que te entra (Mercado Pago)", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Te cuenta lo que Mi plata anotó solo cuando te entró plata a Mercado Pago" })
        val id = (System.currentTimeMillis() % 100_000).toInt() + 8000
        val deshacer = PendingIntent.getBroadcast(ctx, id, Intent(ctx, ReceptorEntradaMP::class.java).setAction(DESHACER)
            .putExtra("id", id).putExtra("entrada", entrada).putExtra("repartida", repartida).putExtra("cobradas", cobradas.toLongArray())
            .putExtra("parcialNuevo", parcial.first).putExtra("parcialDe", parcial.second).putExtra("parcialMonto", parcial.third),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val abrir = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(id, NotificationCompat.Builder(ctx, CANAL).setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(titulo).setContentText(cuerpo).setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setContentIntent(abrir).setAutoCancel(true)
            .apply { if (entrada != 0L || repartida != 0L || cobradas.isNotEmpty() || parcial.first != 0L) addAction(0, "Deshacer", deshacer) }.build())
    }
}

/** Botón "Deshacer" del aviso. */
class ReceptorEntradaMP : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != EntradasMP.DESHACER) return
        ctx.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra("id", 0))
        val fin = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                Almacen(ctx).deshacerEntradaMP(intent.getLongExtra("entrada", 0), intent.getLongExtra("repartida", 0),
                    intent.getLongArrayExtra("cobradas")?.toList() ?: emptyList(),
                    intent.getLongExtra("parcialNuevo", 0), intent.getLongExtra("parcialDe", 0), intent.getDoubleExtra("parcialMonto", 0.0))
            }
            runCatching { com.miplata.kotlin.widget.Widget.actualizar(ctx) }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(ctx, "Listo, lo deshice", android.widget.Toast.LENGTH_SHORT).show()
            }
            fin.finish()
        }
    }
}
