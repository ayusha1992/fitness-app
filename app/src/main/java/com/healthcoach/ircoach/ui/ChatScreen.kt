package com.healthcoach.ircoach.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.compose.ui.res.painterResource
import com.healthcoach.ircoach.R
import com.healthcoach.ircoach.data.ChatMessage
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(vm: ChatViewModel, modifier: Modifier = Modifier) {
    val messages by vm.messages.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val options by vm.options.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val ctx = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) { vm.sendPhoto(uri, input); input = "" }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { vm.sendPhoto(uri, input); input = "" }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    Column(modifier.fillMaxSize().imePadding()) {
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Say hi, send today's weight (e.g. \"72.4 kg\"), or snap a meal photo with 📷.\n\n" +
                        "I'll ask for your weight every morning and remind you about meals.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) { items(messages, key = { it.id }) { Bubble(it) } }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (options.isNotEmpty() && !busy) {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { opt -> SuggestionChip(onClick = { vm.send(opt) }, label = { Text(opt) }) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = !busy, onClick = {
                val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(dir, "capture.jpg"))
                cameraUri = uri
                takePicture.launch(uri)
            }) { Icon(painterResource(R.drawable.ic_camera), "Take meal photo") }
            IconButton(enabled = !busy, onClick = {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Icon(painterResource(R.drawable.ic_gallery), "Pick meal photo") }
            OutlinedTextField(
                input, { input = it }, Modifier.weight(1f),
                placeholder = { Text("Message or photo caption") }, maxLines = 4,
            )
            IconButton(enabled = !busy && input.isNotBlank(), onClick = { vm.send(input); input = "" }) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send")
            }
        }
    }

    error?.let {
        AlertDialog(
            onDismissRequest = vm::clearError,
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
            title = { Text("Couldn't get a reply") },
            text = { Text(it) },
        )
    }
}

@Composable
private fun Bubble(m: ChatMessage) {
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp),
        ) {
            m.imagePath?.let { path ->
                val bmp = remember(path) { ChatViewModel.thumbnail(path) }
                if (bmp != null) Image(
                    bmp.asImageBitmap(), "Meal photo",
                    Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop,
                )
            }
            if (!(m.imagePath != null && m.text == "📷")) Text(boldMarkup(m.text))
        }
    }
}

/** Renders *bold* (single asterisks), the only markup the coach uses. */
private fun boldMarkup(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split('*')
    parts.forEachIndexed { i, s ->
        // Odd segments are inside asterisks, but only when a closing asterisk exists.
        if (i % 2 == 1 && i < parts.lastIndex) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s) }
        else { if (i % 2 == 1) append('*'); append(s) }
    }
}
