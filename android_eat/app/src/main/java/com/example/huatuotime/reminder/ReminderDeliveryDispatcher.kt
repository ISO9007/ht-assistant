package com.example.huatuotime.reminder

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.huatuotime.ReminderAlertActivity
import com.example.huatuotime.data.LocalReminderRepository
import com.example.huatuotime.data.MedicineReminder
import com.example.huatuotime.data.ReminderOccurrence
import java.util.UUID

/** Converts due alarms into persistent occurrences and user-visible reminder channels. */
class ReminderDeliveryDispatcher(private val context: Context) {
    private val repository = LocalReminderRepository(context)
    private val scheduler = ReminderScheduler(context)
    private val notifier = ReminderNotifier(context)

    fun fireScheduledReminder(reminder: MedicineReminder, scheduledAtMillis: Long): ReminderOccurrence {
        val occurrence = ReminderOccurrence(
            id = UUID.randomUUID().toString(),
            reminderId = reminder.id,
            scheduledAtMillis = scheduledAtMillis,
            nextNudgeAtMillis = System.currentTimeMillis() + NUDGE_DELAY_MILLIS
        )
        repository.saveOccurrence(occurrence)
        deliver(reminder, occurrence, isNudge = false)
        scheduler.scheduleNudge(occurrence, occurrence.nextNudgeAtMillis!!)
        // Repeating rules schedule their next future alarm only after the current dose is materialized.
        scheduler.scheduleReminder(reminder, scheduledAtMillis + 60_000L)
        return occurrence
    }

    fun fireNudge(occurrence: ReminderOccurrence, reminder: MedicineReminder): ReminderOccurrence {
        val next = System.currentTimeMillis() + NUDGE_DELAY_MILLIS
        val updated = occurrence.copy(nextNudgeAtMillis = next, snoozedUntilMillis = null)
        repository.saveOccurrence(updated)
        deliver(reminder, updated, isNudge = true)
        scheduler.scheduleNudge(updated, next)
        return updated
    }

    private fun deliver(reminder: MedicineReminder, occurrence: ReminderOccurrence, isNudge: Boolean) {
        val deliveryOptions = repository.getDeliveryOptions()
        if (deliveryOptions.notification) {
            notifier.showReminder(
                reminder = reminder,
                occurrence = occurrence,
                isNudge = isNudge,
                fullScreenAlert = deliveryOptions.fullScreenAlert
            )
        }
        startRingIfNeeded(
            playSound = deliveryOptions.loudRingtone,
            vibrate = deliveryOptions.vibration,
            occurrenceId = occurrence.id,
            title = reminder.title
        )
        openFullScreenIfNeeded(
            enabled = deliveryOptions.fullScreenAlert || deliveryOptions.wakeScreen,
            occurrenceId = occurrence.id
        )
    }

    private fun startRingIfNeeded(playSound: Boolean, vibrate: Boolean, occurrenceId: String, title: String) {
        if (!playSound && !vibrate) return
        val serviceIntent = Intent(context, ReminderRingService::class.java)
            .putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            .putExtra("title", title)
            .putExtra("playSound", playSound)
            .putExtra("vibrate", vibrate)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private fun openFullScreenIfNeeded(enabled: Boolean, occurrenceId: String) {
        if (!enabled) return
        val alertIntent = Intent(context, ReminderAlertActivity::class.java)
            .putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(alertIntent)
    }
}
