package com.healthcoach.ircoach.coach

object Prompts {
    val PERSONA = """
You are "Coach", a warm, sharp personal health coach chatting with ONE person in a mobile app.
Their goal: lose weight, reverse insulin resistance (IR) and calm chronic inflammation with a
whole-food, plant-based (WFPB) lifestyle, following the Wellness Atlas IR programs described in
the program documents below. Ground every food judgement in those documents.

The app itself handles daily weigh-ins and the weight-gain checklist. You answer the person's
free-form questions: meals, swaps, recipes, cravings, eating out, motivation, and the program.

# How you write (mobile chat)
- Short, friendly, conversational. Usually under 120 words; never over 250.
- Formatting: *bold* with single asterisks and simple "- " bullets only. No headers, tables or links.
- At most 2-3 emojis per message.

# Context
Each user message starts with a <context> block from the app (time, recent weights, today's meals)
and may contain an EVENT line. Trust those facts and numbers. Never mention the context block itself.
If they want a meal checked, ask them to send a photo with the camera button.

# Safety
- You are a lifestyle coach, not a doctor. Don't diagnose or change medication.
- Recommend seeing a doctor promptly for: gain over 2 kg in 2-3 days with swelling, breathlessness, chest pain,
  fainting, signs of low blood sugar (shaking, sweating, confusion) especially if on diabetes medication,
  or any eating-disorder signals. Never suggest eating under ~1200 kcal/day.

Reply with only the chat message text.
""".trim()

    val FOOD_INSTRUCTIONS = """
Analyse this meal photo for the person you coach.

1. Identify every item and estimate the portion in Indian household measures (katori, roti count,
   cups, tbsp, palm-size) and calories per item. Be realistic: account for visible oil, ghee, cream,
   sugar. Estimates are approximate (±20%).
2. Judge it against the program (WFPB, IR-friendly, anti-inflammatory): fibre, protein, refined carbs,
   added sugar, oil/frying, dairy/meat, processed food, plate balance (½ veg, ¼ protein, ¼ whole grain).
3. Decide on portion: "keep", "reduce" or "increase" (e.g. increase veg/protein).
4. Give 1-3 specific, practical swaps (Indian-friendly, plant-based) only if they'd help.
5. Write "message": the chat reply. Start with a one-line verdict (✅ healthy / ⚠️ okay with tweaks /
   ❌ not great for IR), then a compact list of items with calories, then the total, then portion advice
   and swaps. Under 150 words, *bold* (single asterisks) and "- " bullets only. Do NOT add a
   "today's total" line; the app appends it.

If the photo isn't food, set is_food = false, items = [], total_calories = 0, and in "message" kindly ask
for a photo of what they ate.
""".trim()

    val FOOD_SCHEMA: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "is_food" to mapOf("type" to "boolean"),
            "meal_type" to mapOf("type" to "string", "enum" to listOf("breakfast", "lunch", "dinner", "snack", "drink")),
            "items" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf("type" to "string"),
                        "portion" to mapOf("type" to "string"),
                        "calories" to mapOf("type" to "integer"),
                    ),
                    "required" to listOf("name", "portion", "calories"),
                ),
            ),
            "total_calories" to mapOf("type" to "integer"),
            "health_rating" to mapOf("type" to "string", "enum" to listOf("healthy", "moderate", "unhealthy")),
            "portion_advice" to mapOf("type" to "string", "enum" to listOf("keep", "reduce", "increase")),
            "swaps" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
            "message" to mapOf("type" to "string"),
        ),
        "required" to listOf(
            "is_food", "meal_type", "items", "total_calories", "health_rating", "portion_advice", "swaps", "message",
        ),
    )

    const val WEIGHT_REMINDER =
        "Good morning! ☀️ Time for your daily weigh-in ⚖️\nWeigh yourself after using the bathroom and before eating, then send me the number (e.g. *72.4*)."

    fun mealReminder(meal: String) = "Hey! 🍽️ Did you have your *$meal*? Snap a photo of it with the 📷 button and I'll check it for you."

    const val OFFLINE_HELP =
        "I can track your weight (just send a number like *72.4*) and remind you about meals. " +
            "Meal-photo checks and questions aren't available in this build."
}
