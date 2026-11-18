package com.healthcoach.ircoach.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.healthcoach.ircoach.MainActivity
import com.healthcoach.ircoach.R
import com.healthcoach.ircoach.coach.CoachEngine
import com.healthcoach.ircoach.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Reminders {
    const val CHANNEL = "coach_reminders"
    private const val EXTRA_KIND = "kind"

    /** (kind, "HH:mm") for every daily reminder. kind is "weight" or a meal name. */
    private fun all(s: Settings) = listOf("weight" to s.weightTime) + s.mealReminders()

    fun scheduleAll(ctx: Context) {
        val s = Settings(ctx)
        all(s).forEachIndexed { i, (kind, time) -> schedule(ctx, i, kind, time) }
    }

    private fun schedule(ctx: Context, requestCode: Int, kind: String, hhmm: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, requestCode,
            Intent(ctx, ReminderReceiver::class.java).putExtra(EXTRA_KIND, kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val t = runCatching { LocalTime.parse(hhmm) }.getOrDefault(LocalTime.of(9, 0))
        var next = LocalDateTime.of(LocalDate.now(), t)
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1)
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Daily check-ins", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Weigh-in and meal photo reminders"
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun notify(ctx: Context, id: Int, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val plain = text.replace("*", "")
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("IR Coach")
            .setContentText(plain)
            .setStyle(NotificationCompat.BigTextStyle().bigText(plain))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    class ReminderReceiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val kind = intent.getStringExtra(EXTRA_KIND) ?: return
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val engine = CoachEngine(ctx.applicationContext)
                    val text = if (kind == "weight") engine.weightReminder() else engine.mealReminder(kind)
                    if (text != null) notify(ctx, kind.hashCode(), text)
                    MainActivity.refresh.tryEmit(Unit)
                } finally {
                    scheduleAll(ctx) // queue tomorrow's alarm
                    pending.finish()
                }
            }
        }
    }

    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
                intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ||
                intent.action == Intent.ACTION_TIMEZONE_CHANGED
            ) scheduleAll(ctx)
        }
    }
}
