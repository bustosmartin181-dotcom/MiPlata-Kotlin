package com.miplata.kotlin.datos

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/* PIN igual que Mi plata: "miplata:" + pin, PBKDF2-SHA256 con 150.000 vueltas y la sal guardada.
   Un PIN puesto en la app vieja sirve acá (y al revés), porque viaja en la copia de seguridad. */
object Seguridad {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    fun hashPin(pin: String, sal: String): String {
        val texto = "miplata:$pin"
        if (sal.isEmpty()) return hex(MessageDigest.getInstance("SHA-256").digest(texto.toByteArray()))   // PINs de versiones viejas
        val spec = PBEKeySpec(texto.toCharArray(), sal.toByteArray(), 150_000, 256)
        return hex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
    }

    fun nuevaSal(): String = hex(ByteArray(12).also { SecureRandom().nextBytes(it) })

    /* Intentos fallidos: después de 5, hay que esperar (30 s, 60 s, ... hasta 15 min). */
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("seguridad", Context.MODE_PRIVATE)
    fun segundosDeEspera(ctx: Context): Long {
        val hasta = prefs(ctx).getLong("u", 0)
        return if (System.currentTimeMillis() < hasta) (hasta - System.currentTimeMillis() + 999) / 1000 else 0
    }
    fun registrarFallo(ctx: Context) {
        val p = prefs(ctx); val f = p.getInt("f", 0) + 1
        val e = p.edit().putInt("f", f)
        if (f >= 5) e.putLong("u", System.currentTimeMillis() + minOf(900L, 30L * (1L shl minOf(f - 5, 10))) * 1000)
        e.apply()
    }
    fun limpiarFallos(ctx: Context) = prefs(ctx).edit().clear().apply()
}
