package com.healthcoach.ircoach.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.healthcoach.ircoach.R
import com.healthcoach.ircoach.data.ChatMessage
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val CoachShape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
private val UserShape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp)

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
    val focusRequester = remember { FocusRequester() }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) { vm.sendPhoto(uri, input); input = "" }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { vm.sendPhoto(uri, input); input = "" }
    }

    val launchCamera = {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(dir, "capture.jpg"))
        cameraUri = uri
        takePicture.launch(uri)
    }

    val listState = rememberLazyListState()
    val itemCount = messages.size + if (busy) 1 else 0
    LaunchedEffect(itemCount) { if (itemCount > 0) listState.animateScrollToItem(itemCount - 1) }

    Column(modifier.fillMaxSize().background(Color.White).imePadding()) {
        ChatHeader(busy)
        HorizontalDivider(color = CoachColors.Outline, thickness = 1.dp)

        if (messages.isEmpty() && !busy) {
            EmptyState(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onWeight = { input = "My weight today is "; focusRequester.requestFocus() },
                onMeal = { launchCamera() },
                onQuestion = { input = "What's a good IR-friendly breakfast?"; focusRequester.requestFocus() },
            )
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().background(CoachColors.MintSoft.copy(alpha = 0.5f)),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) {
                itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
                    val prev = messages.getOrNull(i - 1)
                    val day = dayOf(m)
                    if (prev == null || dayOf(prev) != day) DaySeparator(day)
                    val firstOfRun = prev == null || prev.role != m.role || dayOf(prev) != day
                    Bubble(m, showAvatar = firstOfRun, topGap = if (firstOfRun && prev != null) 8.dp else 0.dp)
                }
                if (busy) item(key = "typing") { TypingBubble() }
            }
        }

        if (options.isNotEmpty() && !busy) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.forEach { opt -> QuickReply(opt) { vm.send(opt) } }
            }
        }

        HorizontalDivider(color = CoachColors.Outline, thickness = 1.dp)
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RoundAction(R.drawable.ic_camera, "Take meal photo", enabled = !busy) { launchCamera() }
            RoundAction(R.drawable.ic_gallery, "Pick meal photo from gallery", enabled = !busy) {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text("Message or photo caption", color = CoachColors.InkMuted) },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CoachColors.Green,
                    unfocusedBorderColor = CoachColors.Outline,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = CoachColors.MintSoft,
                    focusedTextColor = CoachColors.Ink,
                    unfocusedTextColor = CoachColors.Ink,
                    cursorColor = CoachColors.Green,
                ),
            )
            FilledIconButton(
                onClick = { vm.send(input); input = "" },
                enabled = !busy && input.isNotBlank(),
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = CoachColors.Green,
                    contentColor = Color.White,
                    disabledContainerColor = CoachColors.Outline,
                    disabledContentColor = Color.White,
                ),
            ) { Icon(painterResource(R.drawable.ic_send), contentDescription = "Send message") }
        }
    }

    error?.let {
        AlertDialog(
            onDismissRequest = vm::clearError,
            containerColor = Color.White,
            icon = { Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = CoachColors.Green) },
            confirmButton = {
                TextButton(onClick = vm::clearError) { Text("OK", color = CoachColors.Green, fontWeight = FontWeight.SemiBold) }
            },
            title = { Text("Hmm, that didn't go through", color = CoachColors.Ink) },
            text = { Text("$it\n\nNo worries, please try again in a moment.", color = CoachColors.InkMuted) },
        )
    }
}

@Composable
private fun ChatHeader(busy: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ill_coach_avatar), contentDescription = null, modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Coach", style = MaterialTheme.typography.titleMedium, color = CoachColors.GreenDark)
            Text(
                if (busy) "Typing…" else "Your IR health coach",
                style = MaterialTheme.typography.bodySmall,
                color = if (busy) CoachColors.Green else CoachColors.InkMuted,
            )
        }
    }
}

@Composable
private fun RoundAction(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = CoachColors.Mint,
            contentColor = CoachColors.Green,
            disabledContainerColor = CoachColors.MintSoft,
            disabledContentColor = CoachColors.InkMuted.copy(alpha = 0.5f),
        ),
    ) { Icon(painterResource(icon), contentDescription = description) }
}

@Composable
private fun QuickReply(label: String, onClick: () -> Unit) {
    val skip = label.equals("Skip", ignoreCase = true)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (skip) Color.White else CoachColors.Mint,
        border = BorderStroke(1.dp, CoachColors.Outline),
        modifier = Modifier.heightIn(min = 40.dp),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (skip) CoachColors.InkMuted else CoachColors.GreenDark,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onWeight: () -> Unit, onMeal: () -> Unit, onQuestion: () -> Unit) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.ill_chat_empty), contentDescription = null, modifier = Modifier.size(width = 240.dp, height = 180.dp))
        Spacer(Modifier.height(12.dp))
        Text("Hi! I'm your IR coach 🌿", style = MaterialTheme.typography.titleLarge, color = CoachColors.GreenDark, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            "Log your weight, snap a meal, or ask me anything. I'll check in every morning and nudge you about meals.",
            style = MaterialTheme.typography.bodyMedium, color = CoachColors.InkMuted, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        SuggestionCard(R.drawable.ic_scale, "Log today's weight", onWeight)
        Spacer(Modifier.height(8.dp))
        SuggestionCard(R.drawable.ic_camera, "Snap a meal", onMeal)
        Spacer(Modifier.height(8.dp))
        SuggestionCard(R.drawable.ic_sparkle, "Ask a question", onQuestion)
    }
}

@Composable
private fun SuggestionCard(icon: Int, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CoachColors.Outline),
        modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(min = 52.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(CoachColors.Mint), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = CoachColors.Green)
            }
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = CoachColors.Ink)
        }
    }
}

@Composable
private fun DaySeparator(day: LocalDate?) {
    if (day == null) return
    val today = LocalDate.now()
    val label = when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = CoachColors.GreenDark,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(CoachColors.Mint).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TypingBubble() {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Image(painterResource(R.drawable.ill_coach_avatar), contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        val transition = rememberInfiniteTransition(label = "typing")
        Row(
            Modifier.clip(CoachShape).background(CoachColors.MintSoft)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { i ->
                val a by transition.animateFloat(
                    initialValue = 0.3f, targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 900
                            0.3f at 0
                            1f at 300
                            0.3f at 600
                        },
                        repeatMode = RepeatMode.Restart,
                        initialStartOffset = StartOffset(i * 150),
                    ),
                    label = "dot$i",
                )
                Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(CoachColors.Green))
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, showAvatar: Boolean, topGap: Dp) {
    val mine = m.role == "user"
    val time = remember(m.ts) { timeOf(m) }
    Row(
        Modifier.fillMaxWidth().padding(top = topGap),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!mine) {
            if (showAvatar) Image(painterResource(R.drawable.ill_coach_avatar), contentDescription = null, modifier = Modifier.size(28.dp))
            else Spacer(Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
        }
        val bg = if (mine) CoachColors.Green else CoachColors.MintSoft
        val shape = if (mine) UserShape else CoachShape
        Column(
            Modifier.widthIn(max = 300.dp)
                .clip(shape)
                .background(bg)
                .then(if (mine) Modifier else Modifier.border(1.dp, CoachColors.Outline, shape))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            m.imagePath?.let { path ->
                val bmp = remember(path) { ChatViewModel.thumbnail(path) }
                if (bmp != null) Image(
                    bmp.asImageBitmap(), "Meal photo",
                    Modifier.size(220.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop,
                )
            }
            val hideText = m.imagePath != null && m.text == "📷"
            if (!hideText) {
                Text(
                    boldMarkup(m.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (mine) Color.White else CoachColors.Ink,
                    modifier = Modifier.padding(top = if (m.imagePath != null) 6.dp else 0.dp),
                )
            }
            if (time.isNotEmpty()) {
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mine) Color.White.copy(alpha = 0.7f) else CoachColors.InkMuted,
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                )
            }
        }
    }
}

private fun parseTs(m: ChatMessage): LocalDateTime? = runCatching { LocalDateTime.parse(m.ts) }.getOrNull()
private fun dayOf(m: ChatMessage): LocalDate? = parseTs(m)?.toLocalDate()
private fun timeOf(m: ChatMessage): String = parseTs(m)?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: ""

/** Renders *bold* (single asterisks), the only markup the coach uses. */
private fun boldMarkup(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split('*')
    parts.forEachIndexed { i, s ->
        // Odd segments are inside asterisks, but only when a closing asterisk exists.
        if (i % 2 == 1 && i < parts.lastIndex) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s) }
        else { if (i % 2 == 1) append('*'); append(s) }
    }
}
