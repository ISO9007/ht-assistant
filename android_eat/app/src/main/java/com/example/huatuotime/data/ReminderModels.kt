package com.example.huatuotime.data

enum class RepeatType {
    ONCE,
    DAILY,
    WEEKLY
}

enum class MedicationTiming {
    BEFORE_MEAL,
    AFTER_MEAL,
    WITH_MEAL,
    BEFORE_SLEEP,
    ANYTIME
}

/** Global delivery switches shared by every medicine reminder. */
data class ReminderDeliveryOptions(
    val notification: Boolean = true,
    val loudRingtone: Boolean = true,
    val vibration: Boolean = true,
    val fullScreenAlert: Boolean = true,
    val wakeScreen: Boolean = true,
    val calendarEvent: Boolean = true,
    val keepAliveService: Boolean = true
)

/** A user-created reminder rule. Rules are immutable after creation and can only be deleted. */
data class MedicineReminder(
    val id: String,
    val medicineName: String,
    val dose: String,
    val medicationTiming: MedicationTiming = MedicationTiming.ANYTIME,
    val note: String = "",
    val repeatType: RepeatType,
    val onceAtMillis: Long? = null,
    val hour: Int,
    val minute: Int,
    val dayOfWeek: Int? = null,
    val enabled: Boolean = true,
    val createdAtMillis: Long = 0L,
    val calendarEventId: Long? = null
) {
    val title: String
        get() = if (dose.isBlank()) medicineName else "$medicineName $dose"
}

fun medicationTimingLabel(timing: MedicationTiming): String {
    return when (timing) {
        MedicationTiming.BEFORE_MEAL -> "饭前"
        MedicationTiming.AFTER_MEAL -> "饭后"
        MedicationTiming.WITH_MEAL -> "随餐"
        MedicationTiming.BEFORE_SLEEP -> "睡前"
        MedicationTiming.ANYTIME -> "不限"
    }
}

/** One concrete due dose generated from a reminder rule; it stays pending until "已吃". */
data class ReminderOccurrence(
    val id: String,
    val reminderId: String,
    val scheduledAtMillis: Long,
    val nextNudgeAtMillis: Long? = null,
    val snoozedUntilMillis: Long? = null,
    val acknowledgedAtMillis: Long? = null
) {
    val isPending: Boolean
        get() = acknowledgedAtMillis == null
}

fun dayOfWeekLabel(day: Int): String {
    return when (day) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> "周一"
    }
}
