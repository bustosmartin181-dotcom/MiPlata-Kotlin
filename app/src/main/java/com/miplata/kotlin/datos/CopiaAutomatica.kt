package com.miplata.kotlin.datos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/* Copia automática: elegís UN archivo una sola vez (en el teléfono o en Drive) y la app lo
   vuelve a escribir sola cada vez que cambian tus datos. Siempre es el mismo archivo, con todo. */
object CopiaAutomatica {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("copia", Context.MODE_PRIVATE)

    fun uri(ctx: Context): Uri? = prefs(ctx).getString("uri", null)?.let(Uri::parse)
    fun ultima(ctx: Context): Long = prefs(ctx).getLong("ultima", 0)
    fun error(ctx: Context): String? = prefs(ctx).getString("error", null)

    fun nombre(ctx: Context): String? = uri(ctx)?.let { u ->
        runCatching { ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null } }.getOrNull() ?: u.lastPathSegment }

    fun elegir(ctx: Context, u: Uri) {
        runCatching { ctx.contentResolver.takePersistableUriPermission(u,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        prefs(ctx).edit().putString("uri", u.toString()).remove("error").apply()
        guardar(ctx)
    }

    fun desactivar(ctx: Context) {
        uri(ctx)?.let { u -> runCatching { ctx.contentResolver.releasePersistableUriPermission(u,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        prefs(ctx).edit().clear().apply()
    }

    /** Escribe la copia (si está activada). Llamar fuera del hilo principal. */
    fun guardar(ctx: Context): Boolean {
        val u = uri(ctx) ?: return false
        val texto = Almacen(ctx).crudo?.toString() ?: return false
        val ok = listOf("wt", "rwt").any { modo ->
            runCatching { ctx.contentResolver.openOutputStream(u, modo)!!.bufferedWriter().use { it.write(texto) }; true }.getOrDefault(false)
        }
        prefs(ctx).edit().apply {
            if (ok) putLong("ultima", System.currentTimeMillis()).remove("error")
            else putString("error", "No pude escribir el archivo. Elegí otro (por ejemplo, en el teléfono).")
        }.apply()
        return ok
    }
}
