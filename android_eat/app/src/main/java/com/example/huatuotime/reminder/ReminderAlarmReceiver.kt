package com.example.huatuotime.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.huatuotime.data.LocalReminderRepository

/** AlarmManager entrypoint for both scheduled doses and repeat nudges. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repository = LocalReminderRepository(context)
        val dispatcher = ReminderDeliveryDispatcher(context)
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return

        when (intent.action) {
            ACTION_SCHEDULED_REMINDER -> {
                val reminder = repository.getReminder(reminderId) ?: return
                if (!reminder.enabled) return
                val scheduledAt = intent.getLongExtra(EXTRA_TRIGGER_AT, System.currentTimeMillis())
                dispatcher.fireScheduledReminder(reminder, scheduledAt)
            }

            ACTION_NUDGE_REMINDER -> {
                val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID) ?: return
                val occurrence = repository.getOccurrence(occurrenceId) ?: return
                val reminder = repository.getReminder(reminderId) ?: return
                if (!occurrence.isPending || !reminder.enabled) return
                dispatcher.fireNudge(occurrence, reminder)
            }
        }
    }
}
