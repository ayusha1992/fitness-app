package com.healthcoach.ircoach.coach

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class CoachException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Minimal client for Google's Gemini Interactions API (REST, no SDK).
 * Blocking; call from Dispatchers.IO.
 */
class Gemini(private val apiKey: String, private val model: String) {
    sealed interface Part {
        data class Text(val text: String) : Part
        data class Image(val jpegBase64: String) : Part
    }

    /** role is "user" or "assistant". */
    data class Turn(val role: String, val parts: List<Part>)

    /** Returns the model's text. With [schema], that text is JSON matching the schema. */
    fun call(system: String, turns: List<Turn>, schema: Map<String, Any>? = null): String =
        try {
            post(model, system, turns, schema)
        } catch (e: Busy) {
            // Free models are sometimes overloaded; try the other free model before giving up.
            if (model == FALLBACK_MODEL) throw CoachException(BUSY_MSG)
            try { post(FALLBACK_MODEL, system, turns, schema) } catch (_: Busy) { throw CoachException(BUSY_MSG) }
        }

    private class Busy : Exception()

    private fun post(model: String, system: String, turns: List<Turn>, schema: Map<String, Any>?): String {
        val body = buildBody(model, system, turns, schema).toString().toByteArray()
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 120_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("Api-Revision", API_REVISION)
        }
        val code: Int
        val text: String
        try {
            conn.outputStream.use { it.write(body) }
            code = conn.responseCode
            text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: IOException) {
            throw CoachException("Couldn't reach Gemini. Check your internet connection.", e)
        } finally {
            conn.disconnect()
        }

        if (code == 503 || code == 500) throw Busy()
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(text).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
            throw CoachException(
                when {
                    code == 429 -> "You've hit the free Gemini limit for now (too many requests per minute or per day). Try again later."
                    code == 401 || code == 403 || msg.contains("API key", ignoreCase = true) ->
                        "Your Gemini API key was rejected. Check it in Settings."
                    code == 404 -> "Gemini model \"$model\" wasn't found. Check the model name in Settings."
                    else -> "Gemini error ($code)${if (msg.isNotBlank()) ": $msg" else ""}"
                }
            )
        }
        return extractText(JSONObject(text))
            ?: throw CoachException("Gemini didn't return an answer. Please try again.")
    }

    companion object {
        const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
        const val API_REVISION = "2026-05-20"
        const val FALLBACK_MODEL = "gemini-3.5-flash-lite"
        private const val BUSY_MSG = "Gemini's free servers are busy right now. Please try again in a few minutes."

        fun buildBody(model: String, system: String, turns: List<Turn>, schema: Map<String, Any>?): JSONObject {
            val input = JSONArray()
            turns.forEach { turn ->
                val content = JSONArray()
                turn.parts.forEach { p ->
                    content.put(
                        when (p) {
                            is Part.Text -> JSONObject().put("type", "text").put("text", p.text)
                            is Part.Image -> JSONObject().put("type", "image").put("data", p.jpegBase64).put("mime_type", "image/jpeg")
                        }
                    )
                }
                input.put(
                    JSONObject()
                        .put("type", if (turn.role == "assistant") "model_output" else "user_input")
                        .put("content", content)
                )
            }
            val body = JSONObject()
                .put("model", model)
                .put("system_instruction", system)
                .put("input", input)
                .put("store", false) // stateless: we keep the history on the phone
                .put("generation_config", JSONObject().put("thinking_level", "low").put("max_output_tokens", 8192))
            if (schema != null) {
                body.put(
                    "response_format",
                    JSONObject().put("type", "text").put("mime_type", "application/json").put("schema", JSONObject(schema)),
                )
            }
            return body
        }

        /** Text of the last model output step (falls back to a top-level output_text). */
        fun extractText(resp: JSONObject): String? {
            resp.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
            val steps = resp.optJSONArray("steps") ?: return null
            for (i in steps.length() - 1 downTo 0) {
                val step = steps.optJSONObject(i) ?: continue
                if (step.optString("type") != "model_output") continue
                val content = step.optJSONArray("content") ?: continue
                val text = buildString {
                    for (j in 0 until content.length()) {
                        val c = content.optJSONObject(j) ?: continue
                        if (c.optString("type") == "text") append(c.optString("text"))
                    }
                }
                if (text.isNotBlank()) return text
            }
            return null
        }
    }
}
