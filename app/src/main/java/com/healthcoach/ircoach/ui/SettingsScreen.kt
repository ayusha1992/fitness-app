package com.healthcoach.ircoach.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.healthcoach.ircoach.R
import com.healthcoach.ircoach.data.Settings
import java.time.LocalTime

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CoachColors.Green,
    unfocusedBorderColor = CoachColors.Outline,
    focusedLabelColor = CoachColors.Green,
    cursorColor = CoachColors.Green,
)

private val FieldShape = RoundedCornerShape(14.dp)

@Composable
private fun SectionCard(
    @DrawableRes icon: Int,
    title: String,
    helper: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CoachColors.Outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(CoachColors.Mint),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(icon), null, Modifier.size(20.dp), tint = CoachColors.Green)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = CoachColors.Ink)
                    Text(helper, style = MaterialTheme.typography.bodySmall, color = CoachColors.InkMuted)
                }
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val t = runCatching { LocalTime.parse(initial.trim()) }.getOrDefault(LocalTime.of(9, 0))
    val state = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title, color = CoachColors.Ink) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) }) {
                Text("OK", color = CoachColors.Green)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = CoachColors.InkMuted) }
        },
    )
}

@Composable
private fun TimeRow(@DrawableRes icon: Int, label: String, value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = CoachColors.Green)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = CoachColors.Ink)
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(12.dp),
            color = CoachColors.Mint,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "$label time $value, tap to change" },
        ) {
            Box(Modifier.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = CoachColors.GreenDark,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    if (open) {
        TimePickerDialog(label, value, onDismiss = { open = false }, onConfirm = { onChange(it); open = false })
    }
}

@Composable
private fun ActionRow(title: String, button: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CoachColors.MintSoft).padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = CoachColors.Ink)
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = CoachColors.Mint, contentColor = CoachColors.GreenDark),
        ) { Text(button) }
    }
}

@Composable
private fun StatusRow(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(painterResource(R.drawable.ic_check_circle), null, Modifier.size(20.dp), tint = CoachColors.Green)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = CoachColors.Ink)
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val s = remember { Settings(ctx) }
    val hasKey = remember { s.apiKey.isNotBlank() }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(s.model) }
    var weightTime by remember { mutableStateOf(s.weightTime) }
    var breakfast by remember { mutableStateOf(s.breakfastTime) }
    var lunch by remember { mutableStateOf(s.lunchTime) }
    var dinner by remember { mutableStateOf(s.dinnerTime) }
    var target by remember { mutableStateOf(s.calorieTarget?.toString() ?: "") }
    var profile by remember { mutableStateOf(s.profile) }
    var notes by remember { mutableStateOf(s.courseNotes) }
    var problem by remember { mutableStateOf<String?>(null) }
    var advancedOpen by remember { mutableStateOf(false) }

    fun notifMissing() = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    var needsNotif by remember { mutableStateOf(notifMissing()) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { needsNotif = notifMissing() }
    val needsExact = Build.VERSION.SDK_INT >= 31 && !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    Column(modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Banner
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
                Image(
                    painterResource(R.drawable.ill_settings_header), null,
                    Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth,
                )
                Column(
                    Modifier.align(Alignment.CenterStart).fillMaxWidth(0.52f).padding(start = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall, color = CoachColors.Ink)
                    Text("Make the coach yours", style = MaterialTheme.typography.bodyMedium, color = CoachColors.InkMuted)
                }
            }

            SectionCard(R.drawable.ic_person, "About you", "Helps the coach tailor its advice") {
                OutlinedTextField(
                    profile, { profile = it }, Modifier.fillMaxWidth(), minLines = 6,
                    label = { Text("Profile") }, shape = FieldShape, colors = fieldColors(),
                )
                OutlinedTextField(
                    target, { target = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("Daily calorie target (optional)") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_flame), null, Modifier.size(24.dp), tint = CoachColors.Green) },
                    suffix = { Text("kcal") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = FieldShape, colors = fieldColors(),
                )
            }

            SectionCard(R.drawable.ic_bell, "Reminders", "Gentle nudges at the times you choose") {
                Column {
                    TimeRow(R.drawable.ic_scale, "Weigh-in", weightTime) { weightTime = it }
                    TimeRow(R.drawable.ic_breakfast, "Breakfast", breakfast) { breakfast = it }
                    TimeRow(R.drawable.ic_lunch, "Lunch", lunch) { lunch = it }
                    TimeRow(R.drawable.ic_dinner, "Dinner", dinner) { dinner = it }
                }
                if (needsNotif) ActionRow("Allow reminder notifications", "Allow") {
                    notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (needsExact) ActionRow("Allow on-time reminders", "Allow") {
                    ctx.startActivity(
                        Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                    )
                }
                if (!needsNotif && !needsExact) StatusRow("Reminders are on")
            }

            SectionCard(R.drawable.ic_book, "Course material", "Optional") {
                OutlinedTextField(
                    notes, { notes = it }, Modifier.fillMaxWidth(), minLines = 4,
                    label = { Text("Notes") },
                    supportingText = {
                        Text("Paste meal plans or notes from your Wellness Atlas course. The coach follows these over its general guidelines.")
                    },
                    shape = FieldShape, colors = fieldColors(),
                )
            }

            SectionCard(R.drawable.ic_sparkle, "AI & privacy", "How the coach thinks") {
                StatusRow("Meal photos & questions use the built-in coach service — nothing to set up.")
                Text(
                    "Privacy: meal photos and questions are processed by Google Gemini on its free tier, so Google may use " +
                        "them to improve its products. Weights and checklist answers never leave this phone.",
                    style = MaterialTheme.typography.bodySmall, color = CoachColors.InkMuted,
                )
                val rotation by animateFloatAsState(if (advancedOpen) 180f else 0f, label = "chevron")
                TextButton(
                    onClick = { advancedOpen = !advancedOpen },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = CoachColors.Green),
                ) {
                    Text("Advanced")
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (advancedOpen) "Collapse advanced settings" else "Expand advanced settings",
                        modifier = Modifier.rotate(rotation),
                    )
                }
                AnimatedVisibility(advancedOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            apiKey, { apiKey = it }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("Own Gemini key (optional)") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_key), null, Modifier.size(24.dp), tint = CoachColors.Green) },
                            supportingText = {
                                Text(
                                    if (hasKey) "A key is saved (encrypted on this phone) and used instead of the shared service. Paste a new one to replace it."
                                    else "No key saved. Only add one (from aistudio.google.com/apikey) if you often hit the daily limit."
                                )
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            shape = FieldShape, colors = fieldColors(),
                        )
                        OutlinedTextField(
                            model, { model = it }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text("Model") }, shape = FieldShape, colors = fieldColors(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Surface(color = Color.White, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = CoachColors.Outline)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    problem?.let { msg ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(CoachColors.Danger.copy(alpha = 0.10f)).padding(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(painterResource(R.drawable.ic_info), null, Modifier.size(20.dp), tint = CoachColors.Danger)
                            Spacer(Modifier.width(8.dp))
                            Text(msg, style = MaterialTheme.typography.bodyMedium, color = CoachColors.Danger)
                        }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoachColors.Green, contentColor = Color.White),
                        onClick = {
                            val times = listOf(weightTime, breakfast, lunch, dinner).map { it.trim() }
                            val bad = times.firstOrNull { t -> runCatching { LocalTime.parse(t) }.isFailure }
                            when {
                                bad != null -> problem = "\"$bad\" isn't a valid time. Use HH:mm, e.g. 09:00."
                                else -> {
                                    if (apiKey.isNotBlank()) s.apiKey = apiKey
                                    s.model = model
                                    s.weightTime = times[0]; s.breakfastTime = times[1]; s.lunchTime = times[2]; s.dinnerTime = times[3]
                                    s.calorieTarget = target.toIntOrNull()
                                    s.profile = profile
                                    s.courseNotes = notes
                                    s.setupDone = true
                                    problem = null
                                    if (needsNotif) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    onSaved()
                                }
                            }
                        },
                    ) { Text("Save changes", style = MaterialTheme.typography.titleMedium, color = Color.White) }
                }
            }
        }
    }
}
