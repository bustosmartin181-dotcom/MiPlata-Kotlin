package com.miplata.kotlin.foto

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.miplata.kotlin.nucleo.LeerPlanilla

/* Lee el texto de una foto EN EL TELÉFONO, sin internet y sin claves (lector de texto de Google, viene dentro de la app).
   Llamar fuera del hilo principal. */
object LectorFoto {
    fun piezas(ctx: Context, uri: Uri): List<LeerPlanilla.Pieza> {
        val imagen = InputImage.fromFilePath(ctx, uri)          // respeta si la foto está girada
        val lector = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val texto = Tasks.await(lector.process(imagen), 60, java.util.concurrent.TimeUnit.SECONDS)
            return texto.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { e ->
                e.boundingBox?.let { LeerPlanilla.Pieza(e.text, it.exactCenterX(), it.exactCenterY()) }
            }
        } finally { lector.close() }
    }
}
