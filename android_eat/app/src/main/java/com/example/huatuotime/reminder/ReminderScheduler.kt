package com.example.huatuotime.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.huatuotime.data.MedicineReminder
import com.example.huatuotime.data.ReminderOccurrence
import com.example.huatuotime.data.ReminderScheduleCalculator

/** Primary reminder scheduler. JobScheduler is only a recovery helper; due reminders use AlarmManager. */
class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleReminder(reminder: MedicineReminder, afterMillis: Long = System.currentTimeMillis()) {
        val triggerAt = ReminderScheduleCalculator.nextTriggerAt(reminder, afterMillis) ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ACTION_SCHEDULED_REMINDER)
            .putExtra(EXTRA_REMINDER_ID, reminder.id)
            .putExtra(EXTRA_TRIGGER_AT, triggerAt)
        schedule(triggerAt, scheduledRequestCode(reminder.id), intent)
    }

    fun cancelReminder(reminderId: String) {
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                scheduledRequestCode(reminderId),
                Intent(context, ReminderAlarmReceiver::class.java).setAction(ACTION_SCHEDULED_REMINDER),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
        )
    }

    fun scheduleNudge(occurrence: ReminderOccurrence, triggerAt: Long) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ACTION_NUDGE_REMINDER)
            .putExtra(EXTRA_OCCURRENCE_ID, occurrence.id)
            .putExtra(EXTRA_REMINDER_ID, occurrence.reminderId)
            .putExtra(EXTRA_TRIGGER_AT, triggerAt)
        schedule(triggerAt, nudgeRequestCode(occurrence.id), intent)
    }

    fun cancelNudge(occurrenceId: String) {
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                context,
                nudgeRequestCode(occurrenceId),
                Intent(context, ReminderAlarmReceiver::class.java).setAction(ACTION_NUDGE_REMINDER),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return
        )
    }

    fun rescheduleAll(reminders: List<MedicineReminder>, pendingOccurrences: List<ReminderOccurrence>) {
        reminders.filter { it.enabled }.forEach { scheduleReminder(it) }
        pendingOccurrences.forEach { occurrence ->
            val next = occurrence.nextNudgeAtMillis ?: (System.currentTimeMillis() + NUDGE_DELAY_MILLIS)
            scheduleNudge(occurrence, next)
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    private fun schedule(triggerAt: Long, requestCode: Int, intent: Intent) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = PendingIntent.getActivity(
            context,
            requestCode xor 0x2424,
            Intent(context, com.example.huatuotime.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // AlarmClock alarms are visible to the system and are treated as user-significant reminders.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun scheduledRequestCode(reminderId: String): Int = reminderId.hashCode()

    private fun nudgeRequestCode(occurrenceId: String): Int = occurrenceId.hashCode() xor 0x5151
}
