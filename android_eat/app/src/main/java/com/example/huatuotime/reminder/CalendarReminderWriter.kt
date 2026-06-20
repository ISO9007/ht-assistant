package com.example.huatuotime.reminder

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.huatuotime.data.MedicineReminder
import com.example.huatuotime.data.ReminderDeliveryOptions
import com.example.huatuotime.data.ReminderScheduleCalculator
import com.example.huatuotime.data.RepeatType
import java.util.TimeZone

/** Mirrors enabled reminders into the system calendar when the user grants calendar access. */
class CalendarReminderWriter(private val context: Context) {
    fun syncReminder(reminder: MedicineReminder, options: ReminderDeliveryOptions): MedicineReminder {
        if (!options.calendarEvent || !reminder.enabled || !hasCalendarPermission()) {
            reminder.calendarEventId?.let { deleteEvent(it) }
            return reminder.copy(calendarEventId = null)
        }

        val startMillis = ReminderScheduleCalculator.nextTriggerAt(reminder) ?: return reminder
        val calendar = findCalendar() ?: return reminder
        reminder.calendarEventId?.let { deleteEvent(it) }

        val eventId = insertEvent(reminder, calendar.id, startMillis) ?: return reminder.copy(calendarEventId = null)
        insertEventReminders(eventId, calendar)
        return reminder.copy(calendarEventId = eventId)
    }

    fun deleteEvent(eventId: Long) {
        runCatching {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun insertEvent(reminder: MedicineReminder, calendarId: Long, startMillis: Long): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.TITLE, "吃药：${reminder.title}")
            put(CalendarContract.Events.DESCRIPTION, buildDescription(reminder))
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
            val rule = recurrenceRule(reminder)
            if (rule == null) {
                put(CalendarContract.Events.DTEND, startMillis + 10 * 60 * 1000L)
            } else {
                put(CalendarContract.Events.RRULE, rule)
                put(CalendarContract.Events.DURATION, "P600S")
            }
        }
        val eventUri = runCatching {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        }.getOrNull() ?: return null
        return eventUri.lastPathSegment?.toLongOrNull()
    }

    private fun insertEventReminders(eventId: Long, calendar: CalendarTarget) {
        val method = calendar.bestReminderMethod()
        // Some calendars support only one reminder; use an at-time alarm first, then a 10-minute backup.
        val reminderMinutes = if (calendar.maxReminders == 1) listOf(0) else listOf(0, 10)
        reminderMinutes.take(calendar.maxReminders.coerceAtLeast(1)).forEach { minutes ->
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, method)
            }
            runCatching { context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues) }
        }
    }

    private fun buildDescription(reminder: MedicineReminder): String {
        return buildString {
            append("请打开华佗时间并点击“已吃”。")
            if (reminder.note.isNotBlank()) {
                append("\n备注：")
                append(reminder.note)
            }
        }
    }

    private fun recurrenceRule(reminder: MedicineReminder): String? {
        return when (reminder.repeatType) {
            RepeatType.ONCE -> null
            RepeatType.DAILY -> "FREQ=DAILY"
            RepeatType.WEEKLY -> "FREQ=WEEKLY;BYDAY=${calendarByDay(reminder.dayOfWeek ?: 1)}"
        }
    }

    private fun calendarByDay(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "MO"
            2 -> "TU"
            3 -> "WE"
            4 -> "TH"
            5 -> "FR"
            6 -> "SA"
            7 -> "SU"
            else -> "MO"
        }
    }

    private fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    private fun findCalendar(): CalendarTarget? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ALLOWED_REMINDERS,
            CalendarContract.Calendars.MAX_REMINDERS
        )
        return runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    CalendarTarget(
                        id = cursor.getLong(0),
                        allowedReminderMethods = cursor.getString(1).orEmpty(),
                        maxReminders = cursor.getInt(2).takeIf { it > 0 } ?: 1
                    )
                } else {
                    null
                }
            }
        }.getOrNull()
    }
}

private data class CalendarTarget(
    val id: Long,
    val allowedReminderMethods: String,
    val maxReminders: Int
) {
    fun bestReminderMethod(): Int {
        val methods = allowedReminderMethods
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()

        return when {
            methods.isEmpty() -> CalendarContract.Reminders.METHOD_ALARM
            CalendarContract.Reminders.METHOD_ALARM in methods -> CalendarContract.Reminders.METHOD_ALARM
            CalendarContract.Reminders.METHOD_ALERT in methods -> CalendarContract.Reminders.METHOD_ALERT
            CalendarContract.Reminders.METHOD_DEFAULT in methods -> CalendarContract.Reminders.METHOD_DEFAULT
            else -> methods.first()
        }
    }
}
