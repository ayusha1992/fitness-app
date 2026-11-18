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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.healthcoach.ircoach.data.Settings
import java.time.LocalTime

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

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val needsNotif = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    val needsExact = Build.VERSION.SDK_INT >= 31 && !ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Text("Gemini API key (optional, free)", style = MaterialTheme.typography.titleMedium)
        Text(
            if (hasKey) "A key is saved (encrypted on this phone). Paste a new one to replace it."
            else "Weigh-ins, follow-up questions and reminders work without it. For meal-photo checks and questions, " +
                "get a free key at aistudio.google.com/apikey (sign in with Google → Create API key). " +
                "It's stored encrypted on this phone only.\n\nNote: on Google's free tier, photos and messages may be " +
                "used by Google to improve its products, and there's a daily request limit.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            apiKey, { apiKey = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(if (hasKey) "Replace key (optional)" else "AIza...") },
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Model") })

        Text("Reminder times (24h, HH:mm)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(weightTime, { weightTime = it }, Modifier.weight(1f), singleLine = true, label = { Text("Weigh-in") })
            OutlinedTextField(breakfast, { breakfast = it }, Modifier.weight(1f), singleLine = true, label = { Text("Breakfast") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(lunch, { lunch = it }, Modifier.weight(1f), singleLine = true, label = { Text("Lunch") })
            OutlinedTextField(dinner, { dinner = it }, Modifier.weight(1f), singleLine = true, label = { Text("Dinner") })
        }
        OutlinedTextField(
            target, { target = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Daily calorie target (optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        Text("About you", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(profile, { profile = it }, Modifier.fillMaxWidth(), minLines = 6)

        Text("Course material (optional)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Paste meal plans, food lists or notes from your Wellness Atlas course. The coach follows these over its general guidelines.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), minLines = 4)

        if (needsNotif) OutlinedButton(onClick = { notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
            Text("Allow reminder notifications")
        }
        if (needsExact) OutlinedButton(onClick = {
            ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
        }) { Text("Allow on-time reminders (exact alarms)") }

        problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
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
        }) { Text("Save") }
    }
}
