package com.example.huatuotime

import com.example.huatuotime.data.MedicineReminder
import com.example.huatuotime.data.ReminderScheduleCalculator
import com.example.huatuotime.data.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReminderScheduleCalculatorTest {
    private val zone = TimeZone.getTimeZone("Asia/Shanghai")

    @Test
    fun onceReturnsFutureTimeOnly() {
        val reminder = baseReminder(
            repeatType = RepeatType.ONCE,
            onceAtMillis = millis(2026, 6, 20, 8, 30)
        )

        assertEquals(
            millis(2026, 6, 20, 8, 30),
            ReminderScheduleCalculator.nextTriggerAt(reminder, millis(2026, 6, 19, 8, 30), zone)
        )
        assertNull(ReminderScheduleCalculator.nextTriggerAt(reminder, millis(2026, 6, 21, 8, 30), zone))
    }

    @Test
    fun dailyMovesToTomorrowWhenTimePassed() {
        val reminder = baseReminder(repeatType = RepeatType.DAILY, hour = 8, minute = 30)

        assertEquals(
            millis(2026, 6, 20, 8, 30),
            ReminderScheduleCalculator.nextTriggerAt(reminder, millis(2026, 6, 19, 9, 0), zone)
        )
    }

    @Test
    fun weeklyUsesNextMatchingWeekday() {
        val reminder = baseReminder(repeatType = RepeatType.WEEKLY, hour = 8, minute = 30, dayOfWeek = 1)

        assertEquals(
            millis(2026, 6, 22, 8, 30),
            ReminderScheduleCalculator.nextTriggerAt(reminder, millis(2026, 6, 19, 9, 0), zone)
        )
    }

    @Test
    fun disabledReminderHasNoNextTrigger() {
        val reminder = baseReminder(repeatType = RepeatType.DAILY, enabled = false)

        assertNull(ReminderScheduleCalculator.nextTriggerAt(reminder, millis(2026, 6, 19, 9, 0), zone))
    }

    private fun baseReminder(
        repeatType: RepeatType,
        onceAtMillis: Long? = null,
        hour: Int = 8,
        minute: Int = 30,
        dayOfWeek: Int? = null,
        enabled: Boolean = true
    ) = MedicineReminder(
        id = "test",
        medicineName = "降压药",
        dose = "1片",
        repeatType = repeatType,
        onceAtMillis = onceAtMillis,
        hour = hour,
        minute = minute,
        dayOfWeek = dayOfWeek,
        enabled = enabled
    )

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(zone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
