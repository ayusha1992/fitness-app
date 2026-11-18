package com.healthcoach.ircoach.coach

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GeminiTest {
    @Test fun buildsInteractionsBody() {
        val turns = listOf(
            Gemini.Turn("user", listOf(Gemini.Part.Text("hi"), Gemini.Part.Image("QUJD"))),
            Gemini.Turn("assistant", listOf(Gemini.Part.Text("hello"))),
            Gemini.Turn("user", listOf(Gemini.Part.Text("again"))),
        )
        val b = Gemini.buildBody("gemini-4-hydrogen", "sys", turns, Prompts.FOOD_SCHEMA)
        assertEquals("gemini-4-hydrogen", b.getString("model"))
        assertEquals("sys", b.getString("system_instruction"))
        assertFalse(b.getBoolean("store"))
        val input = b.getJSONArray("input")
        assertEquals("user_input", input.getJSONObject(0).getString("type"))
        assertEquals("model_output", input.getJSONObject(1).getString("type"))
        val img = input.getJSONObject(0).getJSONArray("content").getJSONObject(1)
        assertEquals("image", img.getString("type"))
        assertEquals("image/jpeg", img.getString("mime_type"))
        val rf = b.getJSONObject("response_format")
        assertEquals("application/json", rf.getString("mime_type"))
        assertEquals("object", rf.getJSONObject("schema").getString("type"))
        assertEquals("is_food", rf.getJSONObject("schema").getJSONArray("required").getString(0))
    }

    @Test fun extractsLastModelText() {
        val resp = JSONObject(
            """{"steps":[{"type":"user_input","content":[{"type":"text","text":"q"}]},
               {"type":"thought","summary":[]},
               {"type":"model_output","content":[{"type":"text","text":"{\"a\":"},{"type":"text","text":"1}"}]}]}"""
        )
        assertEquals("{\"a\":1}", Gemini.extractText(resp))
        assertNull(Gemini.extractText(JSONObject("{}")))
    }
}
