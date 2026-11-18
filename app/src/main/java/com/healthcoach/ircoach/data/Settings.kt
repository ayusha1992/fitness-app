package com.healthcoach.ircoach.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App settings. The API key is encrypted with a hardware-backed Android Keystore key. */
class Settings(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("api_key", null)?.let { runCatching { decrypt(it) }.getOrNull() } ?: ""
        set(value) = prefs.edit().putString("api_key", if (value.isBlank()) null else encrypt(value.trim())).apply()

    var model: String
        // Ignore a model saved by the old Claude version of the app.
        get() = prefs.getString("model", null)?.takeUnless { it.startsWith("claude") } ?: DEFAULT_MODEL
        set(v) = prefs.edit().putString("model", v.trim().ifBlank { DEFAULT_MODEL }).apply()

    var weightTime: String
        get() = prefs.getString("weight_time", "09:00")!!
        set(v) = prefs.edit().putString("weight_time", v).apply()

    var breakfastTime: String
        get() = prefs.getString("breakfast_time", "09:45")!!
        set(v) = prefs.edit().putString("breakfast_time", v).apply()

    var lunchTime: String
        get() = prefs.getString("lunch_time", "14:00")!!
        set(v) = prefs.edit().putString("lunch_time", v).apply()

    var dinnerTime: String
        get() = prefs.getString("dinner_time", "21:00")!!
        set(v) = prefs.edit().putString("dinner_time", v).apply()

    var calorieTarget: Int?
        get() = prefs.getInt("calorie_target", 0).takeIf { it > 0 }
        set(v) = prefs.edit().putInt("calorie_target", v ?: 0).apply()

    var profile: String
        get() = prefs.getString("profile", DEFAULT_PROFILE)!!
        set(v) = prefs.edit().putString("profile", v).apply()

    /** Extra program material (meal plans, recipes from the course) pasted by the user. */
    var courseNotes: String
        get() = prefs.getString("course_notes", "")!!
        set(v) = prefs.edit().putString("course_notes", v).apply()

    /** True once Settings has been saved at least once (the AI key is optional). */
    var setupDone: Boolean
        get() = prefs.getBoolean("setup_done", false)
        set(v) = prefs.edit().putBoolean("setup_done", v).apply()

    fun mealReminders(): List<Pair<String, String>> =
        listOf("breakfast" to breakfastTime, "lunch" to lunchTime, "dinner" to dinnerTime)

    // ---- keystore ----
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(enc: String): String {
        val bytes = Base64.decode(enc, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-4-hydrogen"
        private const val KEY_ALIAS = "coach_api_key"
        const val DEFAULT_PROFILE = """Name:
Age / sex:
Height (cm):
Starting weight (kg) and date:
Goal weight (kg):
Conditions (e.g. insulin resistance, PCOD):
Medications affecting blood sugar:
Food dislikes / allergies:
Usual schedule (wake, meals, sleep):
Exercise routine:"""
    }
}
