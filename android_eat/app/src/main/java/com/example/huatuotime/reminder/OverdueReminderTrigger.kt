package com.example.huatuotime.reminder

import android.content.Context
import com.example.huatuotime.data.LocalReminderRepository
import com.example.huatuotime.data.MedicineReminder
import com.example.huatuotime.data.ReminderOccurrence
import com.example.huatuotime.data.RepeatType
import java.util.Calendar
import java.util.TimeZone

/** Fires reminders that became due while the process was dead or alarms were delayed. */
class OverdueReminderTrigger(private val context: Context) {
    private val repository = LocalReminderRepository(context)
    private val dispatcher = ReminderDeliveryDispatcher(context)

    fun triggerDueReminders(nowMillis: Long = System.currentTimeMillis()) {
        val reminders = repository.getReminders().filter { it.enabled }
        val occurrences = repository.getOccurrences()

        reminders.forEach { reminder ->
            val scheduledAt = latestDueScheduledAt(reminder, nowMillis) ?: return@forEach
            // Prevent a newly-created reminder from backfilling an occurrence before its creation time.
            if (scheduledAt < reminder.createdAtMillis) return@forEach
            if (hasOccurrenceFor(reminder.id, scheduledAt, occurrences)) return@forEach
            dispatcher.fireScheduledReminder(reminder, scheduledAt)
        }

        repository.getPendingOccurrences().forEach { occurrence ->
            val nextNudgeAt = occurrence.nextNudgeAtMillis ?: return@forEach
            if (nextNudgeAt > nowMillis) return@forEach
            val reminder = repository.getReminder(occurrence.reminderId) ?: return@forEach
            if (!reminder.enabled) return@forEach
            dispatcher.fireNudge(occurrence, reminder)
        }
    }

    private fun latestDueScheduledAt(reminder: MedicineReminder, nowMillis: Long): Long? {
        return when (reminder.repeatType) {
            RepeatType.ONCE -> reminder.onceAtMillis?.takeIf { it <= nowMillis }
            RepeatType.DAILY -> latestDailyScheduledAt(reminder.hour, reminder.minute, nowMillis)
            RepeatType.WEEKLY -> {
                val day = reminder.dayOfWeek ?: return null
                latestWeeklyScheduledAt(day, reminder.hour, reminder.minute, nowMillis)
            }
        }
    }

    private fun hasOccurrenceFor(reminderId: String, scheduledAt: Long, occurrences: List<ReminderOccurrence>): Boolean {
        return occurrences.any { it.reminderId == reminderId && it.scheduledAtMillis == scheduledAt }
    }

    private fun latestDailyScheduledAt(hour: Int, minute: Int, nowMillis: Long): Long {
        return Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis > nowMillis) {
                add(Calendar.DAY_OF_YEAR, -1)
            }
        }.timeInMillis
    }

    private fun latestWeeklyScheduledAt(dayOfWeek: Int, hour: Int, minute: Int, nowMillis: Long): Long {
        return Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val targetCalendarDay = mondayBasedToCalendarDay(dayOfWeek)
            val currentCalendarDay = get(Calendar.DAY_OF_WEEK)
            val daysSinceTarget = (currentCalendarDay - targetCalendarDay + 7) % 7
            add(Calendar.DAY_OF_YEAR, -daysSinceTarget)
            if (timeInMillis > nowMillis) {
                add(Calendar.DAY_OF_YEAR, -7)
            }
        }.timeInMillis
    }

    private fun mondayBasedToCalendarDay(dayOfWeek: Int): Int {
        return when (dayOfWeek) {
            1 -> Calendar.MONDAY
            2 -> Calendar.TUESDAY
            3 -> Calendar.WEDNESDAY
            4 -> Calendar.THURSDAY
            5 -> Calendar.FRIDAY
            6 -> Calendar.SATURDAY
            7 -> Calendar.SUNDAY
            else -> Calendar.MONDAY
        }
    }
}
