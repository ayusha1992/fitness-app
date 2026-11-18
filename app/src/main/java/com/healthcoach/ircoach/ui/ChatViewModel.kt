package com.healthcoach.ircoach.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthcoach.ircoach.MainActivity
import com.healthcoach.ircoach.coach.CoachEngine
import com.healthcoach.ircoach.coach.CoachException
import com.healthcoach.ircoach.data.ChatMessage
import com.healthcoach.ircoach.data.Db
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Db.get(app)
    private val engine = CoachEngine(app)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages
    private val _weights = MutableStateFlow<List<Pair<LocalDate, Double>>>(emptyList())
    val weights: StateFlow<List<Pair<LocalDate, Double>>> = _weights
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _options = MutableStateFlow<List<String>>(emptyList())
    /** Quick-reply buttons for the current checklist question (empty when none). */
    val options: StateFlow<List<String>> = _options
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        reload()
        viewModelScope.launch { MainActivity.refresh.collect { reload() } }
    }

    fun reload() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _messages.value = db.allMessages()
                _weights.value = db.recentWeights(90)
                _options.value = engine.pendingOptions()
            }
        }
    }

    fun clearError() { _error.value = null }

    fun send(text: String) = run { engine.onText(text.trim()) }

    fun sendPhoto(uri: Uri, caption: String) = run {
        val ctx = getApplication<Application>()
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (w, h) = info.size.width to info.size.height
            val scale = MAX_SIDE.toFloat() / maxOf(w, h)
            if (scale < 1f) d.setTargetSize((w * scale).toInt(), (h * scale).toInt())
        }
        val jpeg = ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it); it.toByteArray() }
        val dir = File(ctx.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "meal_${System.currentTimeMillis()}.jpg").apply { writeBytes(jpeg) }
        engine.onPhoto(jpeg, file.absolutePath, caption.trim())
    }

    private fun run(block: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: CoachException) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = "Something went wrong: ${e.message}"
            } finally {
                _busy.value = false
                reload()
            }
        }
    }

    fun clearChat() = viewModelScope.launch {
        withContext(Dispatchers.IO) { db.clearChat() }
        reload()
    }

    companion object {
        private const val MAX_SIDE = 1600
        fun thumbnail(path: String): Bitmap? = runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(path, opts)
        }.getOrNull()
    }
}
