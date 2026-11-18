package com.healthcoach.ircoach.coach

import kotlin.math.roundToLong

/** Pull a body weight in kg out of a chat message. Bare numbers only count when we asked for weight. */
object WeightParser {
    private const val NUM = """(\d{2,3}(?:[.,]\d{1,2})?)"""
    private val withUnit = Regex("""$NUM\s*(kg|kgs|kilo|kilos|lb|lbs|pounds)\b""", RegexOption.IGNORE_CASE)
    private val withWord = Regex("""\bweigh(?:t|ing|s)?\b\D{0,20}$NUM""", RegexOption.IGNORE_CASE)
    private val bare = Regex("""^\D{0,15}$NUM\D{0,15}$""")

    fun parse(input: String, expecting: Boolean): Double? {
        val text = input.trim()
        var unit = "kg"
        var match = withUnit.find(text)?.also { unit = it.groupValues[2].lowercase() }
        if (match == null) match = withWord.find(text)
        if (match == null && expecting && text.length <= 40) match = bare.matchEntire(text)
        match ?: return null
        var value = match.groupValues[1].replace(',', '.').toDouble()
        if (unit.startsWith("lb") || unit.startsWith("pound")) value *= 0.453592
        return if (value in 25.0..300.0) (value * 100).roundToLong() / 100.0 else null
    }
}
