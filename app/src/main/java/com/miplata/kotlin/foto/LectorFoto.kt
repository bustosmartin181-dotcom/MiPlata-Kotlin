package com.miplata.kotlin.foto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.miplata.kotlin.nucleo.LeerPlanilla
import java.util.concurrent.TimeUnit

/* Lee el texto de una foto EN EL TELÉFONO, sin internet y sin claves (lector de texto de Google, viene dentro de la app).
   Llamar fuera del hilo principal. Si una forma de abrir la foto falla, prueba otra. */
object LectorFoto {
    class ErrorLectura(mensaje: String, causa: Throwable?) : Exception(mensaje, causa)

    fun piezas(ctx: Context, uri: Uri): List<LeerPlanilla.Pieza> {
        val errores = mutableListOf<String>()
        /* 1) Como viene (respeta si la foto está girada) */
        runCatching { return leer(InputImage.fromFilePath(ctx, uri)) }.onFailure { errores += "archivo: " + detalle(it) }
        /* 2) Abriéndola como imagen común, achicada, con el giro de la foto */
        runCatching {
            val (bmp, giro) = abrir(ctx, uri)
            return leer(InputImage.fromBitmap(bmp, giro))
        }.onFailure { errores += "imagen: " + detalle(it) }
        throw ErrorLectura(errores.joinToString(" | "), null)
    }

    private fun detalle(e: Throwable): String = (e.cause ?: e).let { c -> c.javaClass.simpleName + (c.message?.let { ": " + it.take(160) } ?: "") }

    private fun leer(imagen: InputImage): List<LeerPlanilla.Pieza> {
        val lector = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val texto = Tasks.await(lector.process(imagen), 60, TimeUnit.SECONDS)
            return texto.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { e ->
                e.boundingBox?.let { LeerPlanilla.Pieza(e.text, it.exactCenterX(), it.exactCenterY()) }
            }
        } finally { lector.close() }
    }

    private fun abrir(ctx: Context, uri: Uri): Pair<Bitmap, Int> {
        val cr = ctx.contentResolver
        val tam = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, tam) }
        var escala = 1
        while (maxOf(tam.outWidth, tam.outHeight) / escala > 3000) escala *= 2
        val bmp = cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = escala }) }
            ?: throw IllegalStateException("no es una imagen")
        val giro = runCatching { cr.openInputStream(uri)!!.use { androidx.exifinterface.media.ExifInterface(it).rotationDegrees } }.getOrDefault(0)
        return bmp to giro
    }
}
