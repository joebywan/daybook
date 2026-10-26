package com.joebywan.daybook.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.joebywan.daybook.MainActivity
import com.joebywan.daybook.R
import com.joebywan.daybook.data.DataStoreKeyValueStore
import com.joebywan.daybook.data.KeyValueStore
import com.joebywan.daybook.data.ProgressStore
import com.joebywan.daybook.data.preferencesFile
import com.joebywan.daybook.ui.home.LaunchPreferences
import com.joebywan.daybook.ui.home.reminderText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZonedDateTime

// The Android half of the daily reminder (`ui/home/Reminder.kt`). One inexact alarm, set for the next time the
// chosen hour comes round and set again each time it goes off, after a reboot, after an update and whenever the
// app opens. Platform AlarmManager, no dependency, so the F-Droid build stays as it was.

/** Android can post a notification, so the reminder is offered. */
const val OFFERS_REMINDER: Boolean = true

/**
 * Asks for leave to notify, then answers: at once if notifications are already allowed, through the system's
 * permission prompt on Android 13 and later, and no below 13 when the player has turned them off for the app.
 */
@Composable
fun rememberAskToNotify(): ((Boolean) -> Unit) -> Unit {
    val context = LocalContext.current
    val waiting = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        waiting[0]?.invoke(granted)
        waiting[0] = null
    }
    return remember(launcher) {
        { done ->
            when {
                NotificationManagerCompat.from(context).areNotificationsEnabled() -> done(true)
                Build.VERSION.SDK_INT >= 33 -> {
                    waiting[0] = done
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> done(false)
            }
        }
    }
}

/** Sets the alarm for [hour] (`on`) or takes it away. */
@Composable
fun rememberReminderSchedule(): (Boolean, Int) -> Unit {
    val context = LocalContext.current.applicationContext
    return remember(context) { { on, hour -> DailyReminder.schedule(context, on, hour) } }
}

internal object DailyReminder {
    private const val CHANNEL = "daily_reminder"
    private const val NOTIFICATION_ID = 1

    private fun launchPrefs(context: Context) = LaunchPreferences(DataStoreKeyValueStore(context.preferencesFile(KeyValueStore.LAUNCH)))

    private fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(context: Context, on: Boolean, hour: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        if (!on) {
            alarms.cancel(alarm(context))
            return
        }
        // Inexact (no exact-alarm permission), and allowed through Doze so it is not held until morning.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt(hour), alarm(context))
    }

    /** The next moment the clock reads [hour]:00 locally, today if it is still to come. */
    private fun nextAt(hour: Int): Long {
        val now = ZonedDateTime.now()
        var at = now.with(LocalTime.of(hour, 0))
        if (!at.isAfter(now)) at = at.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    /** Sets the alarm again from what is stored: after a reboot, an update, or when the app opens. */
    fun reschedule(context: Context) = background {
        val prefs = launchPrefs(context)
        schedule(context, prefs.reminder.first(), prefs.reminderHour.first())
    }

    /** The alarm went off: one note, unless the reminder is off, a daily is solved today, or it is the small hours. */
    fun fire(context: Context, finished: () -> Unit) = background {
        try {
            val prefs = launchPrefs(context)
            if (prefs.reminder.first()) {
                val today = currentDate()
                val solvedToday = ProgressStore(DataStoreKeyValueStore(context.preferencesFile(KeyValueStore.PROGRESS)))
                    .completions.first().any { it.day == today }
                val now = LocalTime.now()
                if (!solvedToday && now.hour >= 5) notify(context, reminderText(now.hour, now.minute))
                schedule(context, true, prefs.reminderHour.first())
            }
        } finally {
            finished()
        }
    }

    private fun notify(context: Context, text: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val note = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Daybook")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, note)
        } catch (_: SecurityException) {
            // Permission taken away since the check: nothing to show.
        }
    }

    private fun background(block: suspend () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch { block() }
    }
}

/** The alarm itself. Not exported: only our own PendingIntent reaches it. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        DailyReminder.fire(context.applicationContext) { pending.finish() }
    }
}

/** A reboot or an update clears alarms; this sets the reminder's again. It never notifies. */
class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            DailyReminder.reschedule(context.applicationContext)
        }
    }
}
