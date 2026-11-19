package com.healthcoach.ircoach.coach

import android.content.Context
import com.healthcoach.ircoach.BuildConfig
import com.healthcoach.ircoach.data.Db
import com.healthcoach.ircoach.data.FoodItem
import com.healthcoach.ircoach.data.Settings
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The coaching flow. Weigh-ins and the weight-gain checklist run offline ([WeightCheck]);
 * meal photos and free-form questions go to Gemini.
 * All methods block (database + network); call them from Dispatchers.IO.
 */
class CoachEngine(private val ctx: Context) {
    private val db = Db.get(ctx)
    private val settings = Settings(ctx)
    private val lock = Any()

    private val programDocs: String by lazy {
        ctx.assets.open("program_guidelines.md").bufferedReader().use { it.readText() }
    }

    // ---------- inbound ----------
    fun onText(text: String) = synchronized(lock) {
        db.addMessage("user", text)
        val mode = db.getState(KEY_MODE, MODE_IDLE)
        if (mode == MODE_WEIGHT_FOLLOWUP && handleFollowup(text)) return@synchronized
        val kg = WeightParser.parse(text, expecting = mode == MODE_AWAITING_WEIGHT)
        if (kg != null) {
            handleWeight(kg)
            return@synchronized
        }
        if (settings.apiKey.isBlank() && BuildConfig.RELAY_URL.isBlank()) {
            db.addMessage("assistant", Prompts.OFFLINE_HELP)
            return@synchronized
        }
        val event = if (mode == MODE_AWAITING_WEIGHT)
            "You asked for today's weight and they haven't sent a number yet; gently remind them after replying." else null
        db.addMessage("assistant", chat(event))
    }

    /** [jpeg] must already be downscaled; [imagePath] is the saved copy shown in the chat. */
    fun onPhoto(jpeg: ByteArray, imagePath: String, caption: String) = synchronized(lock) {
        val b64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
        val prompt = Prompts.FOOD_INSTRUCTIONS + (if (caption.isNotBlank()) "\n\nTheir caption: \"$caption\"" else "")
        val turn = Gemini.Turn("user", listOf(Gemini.Part.Text(contextBlock(null) + "\n\n" + prompt), Gemini.Part.Image(b64)))
        val a = try {
            JSONObject(gemini().call(systemPrompt(), listOf(turn), Prompts.FOOD_SCHEMA))
        } catch (e: Exception) {
            db.addMessage("user", caption.ifBlank { "📷" }, "[sent a food photo] $caption", imagePath)
            if (e is CoachException) throw e
            throw CoachException("Couldn't read the photo analysis. Please try again.", e)
        }

        try {
            if (!a.getBoolean("is_food")) {
                db.addMessage("user", caption.ifBlank { "📷" }, "[sent a photo that isn't food] $caption", imagePath)
                db.addMessage("assistant", a.getString("message"))
                return@synchronized
            }
            val itemsArr = a.getJSONArray("items")
            val items = (0 until itemsArr.length()).map {
                val o = itemsArr.getJSONObject(it)
                FoodItem(o.getString("name"), o.getString("portion"), o.getInt("calories"))
            }
            val today = LocalDate.now()
            val mealType = a.getString("meal_type")
            val kcal = a.optInt("total_calories", items.sumOf { it.calories })
            db.addMeal(today, mealType, items, kcal, a.getString("health_rating"), a.toString())

            val total = db.mealsOn(today).sumOf { it.calories }
            val target = settings.calorieTarget
            var tally = "\n\n📊 Today so far: ~$total kcal"
            if (target != null) {
                val left = target - total
                tally += " of $target" + if (left > 0) " ($left left)" else " (target reached)"
            }
            val summary = items.joinToString { "${it.name} (${it.portion}, ~${it.calories} kcal)" }
            db.addMessage(
                "user", caption.ifBlank { "📷" },
                "[sent a photo of their $mealType: $summary; total ~$kcal kcal; rated ${a.getString("health_rating")}] $caption".trim(),
                imagePath,
            )
            db.addMessage("assistant", a.getString("message") + tally)
        } catch (e: JSONException) {
            db.addMessage("user", caption.ifBlank { "📷" }, "[sent a food photo] $caption", imagePath)
            throw CoachException("Couldn't read the photo analysis. Please try again.", e)
        }
    }

    /** Quick-reply buttons to show under the chat, if a checklist question is pending. */
    fun pendingOptions(): List<String> = synchronized(lock) {
        if (db.getState(KEY_MODE, MODE_IDLE) != MODE_WEIGHT_FOLLOWUP) return emptyList()
        val idx = db.getState(KEY_Q, "0").toIntOrNull() ?: 0
        val q = WeightCheck.QUESTIONS.getOrNull(idx) ?: return emptyList()
        q.options + "Skip"
    }

    // ---------- reminders (called from the alarm receiver) ----------
    /** Returns the reminder text if one should be shown, or null if not needed. */
    fun weightReminder(): String? = synchronized(lock) {
        if (db.weightOn(LocalDate.now()) != null) return null
        db.setState(KEY_MODE, MODE_AWAITING_WEIGHT)
        db.addMessage("assistant", Prompts.WEIGHT_REMINDER)
        Prompts.WEIGHT_REMINDER
    }

    fun mealReminder(meal: String): String? = synchronized(lock) {
        if (db.mealsOn(LocalDate.now()).any { it.mealType == meal }) return null
        val body = Prompts.mealReminder(meal)
        db.addMessage("assistant", body)
        body
    }

    // ---------- weight (offline) ----------
    private fun handleWeight(kg: Double) {
        val today = LocalDate.now()
        val prev = db.previousWeight(today)
        val already = db.weightOn(today)
        db.upsertWeight(today, kg)
        val start = db.recentWeights(100_000).firstOrNull()?.second
        val yday = db.mealsOn(today.minusDays(1))
        val r = WeightCheck.weighIn(
            kg = kg,
            prevKg = prev?.second,
            gapDays = prev?.let { ChronoUnit.DAYS.between(it.first, today) } ?: 0,
            startKg = start,
            yesterdayOnPlan = if (yday.isEmpty()) null else yday.none { it.healthRating == "unhealthy" },
            corrected = already != null,
            dayIndex = today.dayOfYear,
        )
        db.addMessage("assistant", r.message)
        if (r.trend == WeightCheck.Trend.INCREASED) {
            db.setState(KEY_MODE, MODE_WEIGHT_FOLLOWUP)
            db.setState(KEY_Q, "0")
            db.setState(KEY_ANSWERS, "{}")
            db.setState(KEY_DELTA, r.delta.toString())
            db.addMessage("assistant", WeightCheck.questionText(0))
        } else {
            db.setState(KEY_MODE, MODE_IDLE)
        }
    }

    /** Returns true if [text] was consumed as a checklist answer (or a re-prompt was sent). */
    private fun handleFollowup(text: String): Boolean {
        val idx = db.getState(KEY_Q, "0").toIntOrNull() ?: 0
        val q = WeightCheck.QUESTIONS.getOrNull(idx) ?: run { finishFollowup(); return true }
        val answers = runCatching { JSONObject(db.getState(KEY_ANSWERS, "{}")) }.getOrDefault(JSONObject())
        if (text.trim().equals("skip", ignoreCase = true)) {
            finishFollowup(answers)
            return true
        }
        val ans = WeightCheck.matchAnswer(q, text)
        if (ans == null) {
            if (WeightParser.parse(text, expecting = false) != null) return false // a new weight; handle normally
            db.addMessage("assistant", "Tap one of the options below 👇 (or tap *Skip* to stop the questions).")
            return true
        }
        answers.put(q.id, ans)
        db.setState(KEY_ANSWERS, answers.toString())
        if (idx + 1 < WeightCheck.QUESTIONS.size) {
            db.setState(KEY_Q, (idx + 1).toString())
            db.addMessage("assistant", WeightCheck.questionText(idx + 1))
        } else {
            finishFollowup(answers)
        }
        return true
    }

    private fun finishFollowup(answers: JSONObject = JSONObject()) {
        val map = answers.keys().asSequence().associateWith { answers.getString(it) }
        val delta = db.getState(KEY_DELTA, "0").toDoubleOrNull() ?: 0.0
        db.addMessage("assistant", WeightCheck.conclusion(map, delta))
        db.setState(KEY_MODE, MODE_IDLE)
    }

    // ---------- Gemini ----------
    private fun chat(event: String?): String {
        val history = db.recentMessages(30).dropWhile { it.role != "user" }
        val turns = mutableListOf<Gemini.Turn>()
        history.forEachIndexed { i, m ->
            val text = if (i == history.lastIndex) contextBlock(event) + "\n\n" + m.modelText else m.modelText
            val last = turns.lastOrNull()
            // Merge consecutive same-role messages (e.g. a reminder followed by a reply) into one turn.
            if (last != null && last.role == m.role) turns[turns.lastIndex] = last.copy(parts = last.parts + Gemini.Part.Text(text))
            else turns += Gemini.Turn(m.role, listOf(Gemini.Part.Text(text)))
        }
        if (turns.isEmpty() || turns.last().role != "user") {
            turns += Gemini.Turn("user", listOf(Gemini.Part.Text(contextBlock(event))))
        }
        return gemini().call(systemPrompt(), turns).trim()
    }

    private fun gemini(): Gemini {
        return Gemini(settings.apiKey, settings.model, BuildConfig.RELAY_URL, settings.deviceId)
    }

    private fun systemPrompt(): String = buildString {
        append(Prompts.PERSONA)
        append("\n\n# Program knowledge\n<document name=\"program_guidelines\">\n").append(programDocs).append("\n</document>")
        if (settings.courseNotes.isNotBlank()) {
            append("\n<document name=\"my_course_material\">\n").append(settings.courseNotes)
            append("\n</document>\n(The course material overrides the general guidelines.)")
        }
        append("\n\n# About the person\n").append(settings.profile)
    }

    private fun contextBlock(event: String?): String = buildString {
        val now = LocalDateTime.now()
        append("<context>\nCurrent local time: ").append(now.format(DateTimeFormatter.ofPattern("EEEE dd MMM yyyy, HH:mm", Locale.US)))
        val weights = db.recentWeights(14)
        append("\n").append(
            if (weights.isEmpty()) "No weights logged yet."
            else "Recent weights (kg): " + weights.joinToString { "${it.first}: ${it.second}" }
        )
        val meals = db.mealsOn(now.toLocalDate())
        if (meals.isEmpty()) append("\nNo meals logged today yet.")
        else {
            append("\nMeals logged today:")
            meals.forEach { m -> append("\n- ${m.time} ${m.mealType}: ${m.items.joinToString { it.name }} (~${m.calories} kcal, ${m.healthRating})") }
            append("\nTotal today: ~${meals.sumOf { it.calories }} kcal")
        }
        val yday = db.mealsOn(now.toLocalDate().minusDays(1))
        if (yday.isNotEmpty()) {
            append("\nYesterday's meals: ")
            append(yday.joinToString("; ") { m -> "${m.time} ${m.mealType}: ${m.items.joinToString { it.name }}" })
        }
        settings.calorieTarget?.let { append("\nDaily calorie target: $it kcal.") }
        if (event != null) append("\nEVENT: ").append(event)
        append("\n</context>")
    }

    companion object {
        const val KEY_MODE = "mode"
        private const val KEY_Q = "followup_q"
        private const val KEY_ANSWERS = "followup_answers"
        private const val KEY_DELTA = "followup_delta"
        const val MODE_IDLE = "idle"
        const val MODE_AWAITING_WEIGHT = "awaiting_weight"
        const val MODE_WEIGHT_FOLLOWUP = "weight_followup"
    }
}
