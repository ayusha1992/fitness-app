package com.healthcoach.ircoach.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalDateTime

data class ChatMessage(
    val id: Long,
    val role: String,          // "user" | "assistant"
    val text: String,          // what is shown in the chat bubble
    val modelText: String,     // what is sent to the AI as history
    val imagePath: String?,
    val ts: String,
)

data class FoodItem(val name: String, val portion: String, val calories: Int)

data class Meal(
    val time: String,
    val mealType: String,
    val items: List<FoodItem>,
    val calories: Int,
    val healthRating: String,
)

/** Single on-device SQLite database. All calls are synchronous; call from Dispatchers.IO. */
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "coach.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE weights (day TEXT PRIMARY KEY, kg REAL NOT NULL, logged_at TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE meals (id INTEGER PRIMARY KEY AUTOINCREMENT, day TEXT NOT NULL, logged_at TEXT NOT NULL, " +
                "meal_type TEXT NOT NULL, items TEXT NOT NULL, calories INTEGER NOT NULL, health_rating TEXT NOT NULL, " +
                "analysis TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, ts TEXT NOT NULL, role TEXT NOT NULL, " +
                "text TEXT NOT NULL, model_text TEXT NOT NULL, image_path TEXT)"
        )
        db.execSQL("CREATE TABLE state (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ---- state ----
    fun getState(key: String, default: String = ""): String =
        readableDatabase.rawQuery("SELECT value FROM state WHERE key = ?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else default
        }

    fun setState(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "state", null,
            ContentValues().apply { put("key", key); put("value", value) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    // ---- chat ----
    fun addMessage(role: String, text: String, modelText: String = text, imagePath: String? = null) {
        writableDatabase.insert("messages", null, ContentValues().apply {
            put("ts", LocalDateTime.now().withNano(0).toString())
            put("role", role)
            put("text", text)
            put("model_text", modelText)
            put("image_path", imagePath)
        })
    }

    fun allMessages(): List<ChatMessage> = queryMessages("SELECT * FROM messages ORDER BY id")

    fun recentMessages(limit: Int = 30): List<ChatMessage> =
        queryMessages("SELECT * FROM (SELECT * FROM messages ORDER BY id DESC LIMIT $limit) ORDER BY id")

    private fun queryMessages(sql: String): List<ChatMessage> =
        readableDatabase.rawQuery(sql, null).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    ChatMessage(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        role = c.getString(c.getColumnIndexOrThrow("role")),
                        text = c.getString(c.getColumnIndexOrThrow("text")),
                        modelText = c.getString(c.getColumnIndexOrThrow("model_text")),
                        imagePath = c.getString(c.getColumnIndexOrThrow("image_path")),
                        ts = c.getString(c.getColumnIndexOrThrow("ts")),
                    )
                )
            }
        }

    fun clearChat() = writableDatabase.delete("messages", null, null)

    // ---- weights ----
    fun upsertWeight(day: LocalDate, kg: Double) {
        writableDatabase.insertWithOnConflict("weights", null, ContentValues().apply {
            put("day", day.toString()); put("kg", kg); put("logged_at", LocalDateTime.now().withNano(0).toString())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun weightOn(day: LocalDate): Double? =
        readableDatabase.rawQuery("SELECT kg FROM weights WHERE day = ?", arrayOf(day.toString())).use {
            if (it.moveToFirst()) it.getDouble(0) else null
        }

    fun previousWeight(before: LocalDate): Pair<LocalDate, Double>? =
        readableDatabase.rawQuery(
            "SELECT day, kg FROM weights WHERE day < ? ORDER BY day DESC LIMIT 1", arrayOf(before.toString())
        ).use { if (it.moveToFirst()) LocalDate.parse(it.getString(0)) to it.getDouble(1) else null }

    fun recentWeights(n: Int = 14): List<Pair<LocalDate, Double>> =
        readableDatabase.rawQuery("SELECT day, kg FROM weights ORDER BY day DESC LIMIT $n", null).use { c ->
            buildList { while (c.moveToNext()) add(LocalDate.parse(c.getString(0)) to c.getDouble(1)) }.reversed()
        }

    // ---- meals ----
    fun addMeal(day: LocalDate, mealType: String, items: List<FoodItem>, calories: Int, rating: String, analysisJson: String) {
        val arr = JSONArray()
        items.forEach { arr.put(org.json.JSONObject().put("name", it.name).put("portion", it.portion).put("calories", it.calories)) }
        writableDatabase.insert("meals", null, ContentValues().apply {
            put("day", day.toString())
            put("logged_at", LocalDateTime.now().withNano(0).toString())
            put("meal_type", mealType)
            put("items", arr.toString())
            put("calories", calories)
            put("health_rating", rating)
            put("analysis", analysisJson)
        })
    }

    fun mealsOn(day: LocalDate): List<Meal> =
        readableDatabase.rawQuery(
            "SELECT logged_at, meal_type, items, calories, health_rating FROM meals WHERE day = ? ORDER BY id",
            arrayOf(day.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val arr = JSONArray(c.getString(2))
                    val items = (0 until arr.length()).map {
                        val o = arr.getJSONObject(it)
                        FoodItem(o.getString("name"), o.optString("portion"), o.optInt("calories"))
                    }
                    add(Meal(c.getString(0).substring(11, 16), c.getString(1), items, c.getInt(3), c.getString(4)))
                }
            }
        }

    companion object {
        @Volatile private var instance: Db? = null
        fun get(ctx: Context): Db = instance ?: synchronized(this) {
            instance ?: Db(ctx.applicationContext).also { instance = it }
        }
    }
}
