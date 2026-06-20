package com.example.huatuotime.data

import java.util.Calendar
import java.util.TimeZone

/** Pure time calculation used by alarms, overdue recovery, and tests. */
object ReminderScheduleCalculator {
    fun nextTriggerAt(
        reminder: MedicineReminder,
        afterMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long? {
        if (!reminder.enabled) return null

        return when (reminder.repeatType) {
            RepeatType.ONCE -> reminder.onceAtMillis?.takeIf { it > afterMillis }
            RepeatType.DAILY -> nextDaily(reminder.hour, reminder.minute, afterMillis, timeZone)
            RepeatType.WEEKLY -> {
                val day = reminder.dayOfWeek ?: return null
                nextWeekly(day, reminder.hour, reminder.minute, afterMillis, timeZone)
            }
        }
    }

    fun nextDaily(hour: Int, minute: Int, afterMillis: Long, timeZone: TimeZone): Long {
        val candidate = Calendar.getInstance(timeZone).apply {
            timeInMillis = afterMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (candidate.timeInMillis <= afterMillis) {
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        return candidate.timeInMillis
    }

    fun nextWeekly(dayOfWeek: Int, hour: Int, minute: Int, afterMillis: Long, timeZone: TimeZone): Long {
        val candidate = Calendar.getInstance(timeZone).apply {
            timeInMillis = afterMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetCalendarDay = mondayBasedToCalendarDay(dayOfWeek)
        val currentCalendarDay = candidate.get(Calendar.DAY_OF_WEEK)
        val daysToAdd = (targetCalendarDay - currentCalendarDay + 7) % 7
        candidate.add(Calendar.DAY_OF_YEAR, daysToAdd)
        if (candidate.timeInMillis <= afterMillis) {
            candidate.add(Calendar.DAY_OF_YEAR, 7)
        }
        return candidate.timeInMillis
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
