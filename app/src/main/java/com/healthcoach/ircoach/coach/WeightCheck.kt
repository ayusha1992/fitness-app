package com.healthcoach.ircoach.coach

import java.util.Locale

/**
 * Offline weigh-in coaching: compares with the last weight, praises drops, and when the weight
 * goes up runs a short tap-to-answer checklist and explains the likely causes. No AI needed.
 */
object WeightCheck {
    enum class Trend { FIRST, DECREASED, SAME, INCREASED }

    data class Result(val trend: Trend, val delta: Double, val message: String)

    data class Question(val id: String, val text: String, val options: List<String>)

    private const val SAME_KG = 0.1

    val QUESTIONS = listOf(
        Question("dinner", "When did you finish dinner yesterday?", listOf("Before 8 pm", "8–10 pm", "After 10 pm")),
        Question("salt", "Did you eat salty, packaged, pickled or restaurant food yesterday?", listOf("Yes", "No")),
        Question("refined", "Any sweets, maida, fried food, or a big portion of white rice yesterday?", listOf("Yes", "No")),
        Question("water", "How much water did you drink yesterday?", listOf("Under 1.5 L", "1.5–2.5 L", "Over 2.5 L")),
        Question("sleep", "How long did you sleep last night?", listOf("Under 6 h", "6–7 h", "7 h or more")),
        Question("bowel", "Have you had a bowel movement today?", listOf("Yes", "Not yet")),
        Question(
            "other", "Does any of these apply?",
            listOf("Period / PMS", "New or hard workout", "Stressed", "Weighed at a different time", "None of these"),
        ),
    )

    /** (question id, answer) -> (likely cause, fix for today). */
    private val CAUSES = mapOf(
        ("dinner" to "After 10 pm") to ("Late dinner: food was still in your system this morning" to
            "Finish dinner by 7:30–8 pm tonight and keep it light (dal + sabzi; go easy on rice/roti at night)."),
        ("dinner" to "8–10 pm") to ("Fairly late dinner" to "Try to finish dinner a bit earlier tonight, ideally by 8 pm."),
        ("salt" to "Yes") to ("Extra salt makes the body hold water (often 0.5–1 kg)" to
            "Home-cooked, low-salt meals today; skip pickle, papad and namkeen; add potassium-rich veg like palak and lauki."),
        ("refined" to "Yes") to ("Refined carbs / sugar spike insulin and store extra water with glycogen" to
            "Fill half your plate with veg, keep grains small and whole (millets, brown rice), and no sweets today."),
        ("water" to "Under 1.5 L") to ("Too little water: the body holds on to fluid" to
            "Aim for 2.5–3 L today; drink a glass of water before each meal."),
        ("sleep" to "Under 6 h") to ("Short sleep raises cortisol and hunger hormones" to
            "Get to bed 30–60 minutes earlier tonight, with no screens in the last half hour."),
        ("bowel" to "Not yet") to ("Food and stool still in the gut add weight" to
            "Warm water on waking, high-fibre foods (veg, flaxseed, papaya) and a 10-minute walk after meals."),
        ("other" to "Period / PMS") to ("Hormonal water retention: normal, and it passes in a few days" to
            "Keep salt low and carry on; judge progress by the weekly trend, not today."),
        ("other" to "New or hard workout") to ("Muscles hold water while they repair after a workout" to
            "Totally normal: stretch, hydrate well and keep going."),
        ("other" to "Stressed") to ("Stress raises cortisol, which causes water retention and cravings" to
            "Take 5 minutes of slow breathing and a short walk outside today."),
        ("other" to "Weighed at a different time") to ("Weighing at a different time can swing 0.5–1 kg" to
            "Weigh every day right after waking and using the bathroom, before eating or drinking."),
    )

    private val PLATEAU_TIPS = listOf(
        "Add a 10–15 minute walk after lunch and dinner; it really helps blood sugar.",
        "Make half of every plate vegetables before adding anything else.",
        "Finish dinner by 8 pm to give your body a longer overnight fast.",
        "Swap one refined grain today for a millet (jowar, bajra, ragi).",
        "Add a protein source to breakfast: sprouts, besan chilla or tofu bhurji.",
        "Drink a glass of water before each meal.",
    )

    fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    /**
     * @param prevKg the most recent earlier weight, [gapDays] days ago
     * @param startKg the very first logged weight (for total progress)
     * @param yesterdayOnPlan true if yesterday's logged meals were all healthy/moderate, null if none logged
     */
    fun weighIn(
        kg: Double, prevKg: Double?, gapDays: Long, startKg: Double?, yesterdayOnPlan: Boolean?,
        corrected: Boolean, dayIndex: Int,
    ): Result {
        val prefix = if (corrected) "Updated today's weight. " else ""
        if (prevKg == null) {
            return Result(
                Trend.FIRST, 0.0,
                "$prefix✅ *${fmt(kg)} kg* logged as your starting point. I'll compare every morning.\n\n" +
                    "📸 When you eat breakfast, send me a photo with the camera button.",
            )
        }
        val delta = Math.round((kg - prevKg) * 100) / 100.0
        val since = if (gapDays == 1L) "yesterday" else "your last weigh-in $gapDays days ago"
        val total = startKg?.let { Math.round((kg - it) * 100) / 100.0 }
        return when {
            delta < -SAME_KG -> {
                var m = "$prefix🎉 Great job! *${fmt(kg)} kg*, down *${fmt(-delta)} kg* from $since."
                if (total != null && total < -SAME_KG) m += " That's *${fmt(-total)} kg* down since you started."
                m += if (yesterdayOnPlan == true) "\nYour meals yesterday were on plan, and it shows. Keep it up! 💪"
                else "\nWhatever you did yesterday is working, so keep it up! 💪"
                Result(Trend.DECREASED, delta, "$m\n\n📸 Send me your breakfast photo when you eat.")
            }
            delta > SAME_KG -> Result(
                Trend.INCREASED, delta,
                "$prefix⚖️ *${fmt(kg)} kg*, up *${fmt(delta)} kg* from $since.\n" +
                    "No stress: day-to-day swings of 0.2–1 kg are mostly water, not fat. " +
                    "Let's find out why with a few quick taps 👇" +
                    if (delta >= 2) "\n\n⚠️ If you also have swelling, breathlessness or chest pain, please see a doctor." else "",
            )
            else -> Result(
                Trend.SAME, delta,
                "$prefix⚖️ *${fmt(kg)} kg*, about the same as $since. Plateaus are normal; the weekly trend is what matters.\n" +
                    "💡 Tip for today: ${PLATEAU_TIPS[Math.floorMod(dayIndex, PLATEAU_TIPS.size)]}\n\n" +
                    "📸 Send me your breakfast photo when you eat.",
            )
        }
    }

    fun questionText(index: Int): String = "*${index + 1}/${QUESTIONS.size}* ${QUESTIONS[index].text}"

    /** Map a typed or tapped reply to one of the question's options (also accepts "1", "2", ...). */
    fun matchAnswer(q: Question, input: String): String? {
        val t = input.trim().lowercase(Locale.US).removeSuffix(".")
        q.options.firstOrNull { it.lowercase(Locale.US) == t }?.let { return it }
        t.toIntOrNull()?.let { n -> return q.options.getOrNull(n - 1) }
        if (q.options == listOf("Yes", "No") || q.options == listOf("Yes", "Not yet")) {
            if (t in setOf("y", "yes", "yeah", "yep", "haan", "ha", "han")) return q.options[0]
            if (t in setOf("n", "no", "nope", "nahi", "not yet", "na")) return q.options[1]
        }
        return q.options.firstOrNull { t.length >= 3 && it.lowercase(Locale.US).startsWith(t) }
    }

    fun conclusion(answers: Map<String, String>, delta: Double): String {
        val found = answers.mapNotNull { (id, a) -> CAUSES[id to a] }
        val sb = StringBuilder()
        if (found.isEmpty()) {
            sb.append("Thanks! Nothing obvious stands out, so this +${fmt(delta)} kg is most likely normal water fluctuation. ")
            sb.append("Stick to the plan today and look at the weekly trend in the Progress tab.")
        } else {
            sb.append("Thanks! Here's what most likely caused the +${fmt(delta)} kg:\n")
            found.forEach { sb.append("- ").append(it.first).append('\n') }
            sb.append("\n*Fixes for today:*\n")
            found.take(3).forEach { sb.append("- ").append(it.second).append('\n') }
            sb.append("\nMost of this should come off in 1–2 days. 💪")
        }
        sb.append("\n\n📸 Send me your breakfast photo when you eat.")
        return sb.toString()
    }
}
