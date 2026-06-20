package com.example.huatuotime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalReminderRepository(
    context: Context,
    private val api: ReminderApi = LocalOnlyReminderApi()
) : ReminderRepository {
    private val prefs = context.applicationContext.getSharedPreferences("hua_tuo_time", Context.MODE_PRIVATE)

    override fun getReminders(): List<MedicineReminder> {
        return prefs.getString(KEY_REMINDERS, "[]").orEmpty().asJsonArray().mapNotNull { it.toReminderOrNull() }
    }

    override fun getReminder(reminderId: String): MedicineReminder? {
        return getReminders().firstOrNull { it.id == reminderId }
    }

    override fun saveReminder(reminder: MedicineReminder) {
        val next = getReminders().filterNot { it.id == reminder.id } + reminder
        prefs.edit().putString(KEY_REMINDERS, JSONArray(next.map { it.toJson() }).toString()).apply()
        api.syncReminder(reminder)
    }

    override fun deleteReminder(reminderId: String) {
        val reminders = getReminders().filterNot { it.id == reminderId }
        val occurrences = getOccurrences().filterNot { it.reminderId == reminderId }
        prefs.edit()
            .putString(KEY_REMINDERS, JSONArray(reminders.map { it.toJson() }).toString())
            .putString(KEY_OCCURRENCES, JSONArray(occurrences.map { it.toJson() }).toString())
            .apply()
        api.deleteReminder(reminderId)
    }

    override fun getOccurrences(): List<ReminderOccurrence> {
        return prefs.getString(KEY_OCCURRENCES, "[]").orEmpty().asJsonArray().mapNotNull { it.toOccurrenceOrNull() }
    }

    override fun getOccurrence(occurrenceId: String): ReminderOccurrence? {
        return getOccurrences().firstOrNull { it.id == occurrenceId }
    }

    override fun getPendingOccurrences(): List<ReminderOccurrence> {
        return getOccurrences().filter { it.isPending }
    }

    override fun saveOccurrence(occurrence: ReminderOccurrence) {
        val next = getOccurrences().filterNot { it.id == occurrence.id } + occurrence
        prefs.edit().putString(KEY_OCCURRENCES, JSONArray(next.map { it.toJson() }).toString()).apply()
    }

    override fun acknowledgeOccurrence(occurrenceId: String, acknowledgedAtMillis: Long) {
        val occurrence = getOccurrence(occurrenceId) ?: return
        val acknowledged = occurrence.copy(acknowledgedAtMillis = acknowledgedAtMillis, nextNudgeAtMillis = null)
        saveOccurrence(acknowledged)
        api.acknowledgeOccurrence(acknowledged)
    }

    override fun getDeliveryOptions(): ReminderDeliveryOptions {
        val json = prefs.getString(KEY_DELIVERY_OPTIONS, null) ?: return ReminderDeliveryOptions()
        return runCatching { JSONObject(json).toDeliveryOptions() }.getOrDefault(ReminderDeliveryOptions())
    }

    override fun saveDeliveryOptions(options: ReminderDeliveryOptions) {
        prefs.edit().putString(KEY_DELIVERY_OPTIONS, options.toJson().toString()).apply()
    }

    private fun String.asJsonArray(): List<JSONObject> {
        return runCatching {
            val array = JSONArray(this)
            List(array.length()) { index -> array.getJSONObject(index) }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_REMINDERS = "reminders"
        private const val KEY_OCCURRENCES = "occurrences"
        private const val KEY_DELIVERY_OPTIONS = "delivery_options"
    }
}

private fun MedicineReminder.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("medicineName", medicineName)
        .put("dose", dose)
        .put("medicationTiming", medicationTiming.name)
        .put("note", note)
        .put("repeatType", repeatType.name)
        .put("onceAtMillis", onceAtMillis ?: JSONObject.NULL)
        .put("hour", hour)
        .put("minute", minute)
        .put("dayOfWeek", dayOfWeek ?: JSONObject.NULL)
        .put("enabled", enabled)
        .put("createdAtMillis", createdAtMillis)
        .put("calendarEventId", calendarEventId ?: JSONObject.NULL)
}

private fun ReminderDeliveryOptions.toJson(): JSONObject {
    return JSONObject()
        .put("notification", notification)
        .put("loudRingtone", loudRingtone)
        .put("vibration", vibration)
        .put("fullScreenAlert", fullScreenAlert)
        .put("wakeScreen", wakeScreen)
        .put("calendarEvent", calendarEvent)
        .put("keepAliveService", keepAliveService)
}

private fun JSONObject.toReminderOrNull(): MedicineReminder? = runCatching {
    MedicineReminder(
        id = getString("id"),
        medicineName = getString("medicineName"),
        dose = optString("dose"),
        medicationTiming = optMedicationTiming("medicationTiming"),
        note = optString("note"),
        repeatType = RepeatType.valueOf(getString("repeatType")),
        onceAtMillis = optNullableLong("onceAtMillis"),
        hour = getInt("hour"),
        minute = getInt("minute"),
        dayOfWeek = optNullableInt("dayOfWeek"),
        enabled = optBoolean("enabled", true),
        createdAtMillis = optLong("createdAtMillis", 0L),
        calendarEventId = optNullableLong("calendarEventId")
    )
}.getOrNull()

private fun JSONObject.toDeliveryOptions(): ReminderDeliveryOptions {
    return ReminderDeliveryOptions(
        notification = optBoolean("notification", true),
        loudRingtone = optBoolean("loudRingtone", true),
        vibration = optBoolean("vibration", true),
        fullScreenAlert = optBoolean("fullScreenAlert", true),
        wakeScreen = optBoolean("wakeScreen", true),
        calendarEvent = optBoolean("calendarEvent", true),
        keepAliveService = optBoolean("keepAliveService", true)
    )
}

private fun ReminderOccurrence.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("reminderId", reminderId)
        .put("scheduledAtMillis", scheduledAtMillis)
        .put("nextNudgeAtMillis", nextNudgeAtMillis ?: JSONObject.NULL)
        .put("snoozedUntilMillis", snoozedUntilMillis ?: JSONObject.NULL)
        .put("acknowledgedAtMillis", acknowledgedAtMillis ?: JSONObject.NULL)
}

private fun JSONObject.toOccurrenceOrNull(): ReminderOccurrence? = runCatching {
    ReminderOccurrence(
        id = getString("id"),
        reminderId = getString("reminderId"),
        scheduledAtMillis = getLong("scheduledAtMillis"),
        nextNudgeAtMillis = optNullableLong("nextNudgeAtMillis"),
        snoozedUntilMillis = optNullableLong("snoozedUntilMillis"),
        acknowledgedAtMillis = optNullableLong("acknowledgedAtMillis")
    )
}.getOrNull()

private fun JSONObject.optNullableLong(name: String): Long? {
    return if (isNull(name) || !has(name)) null else optLong(name)
}

private fun JSONObject.optNullableInt(name: String): Int? {
    return if (isNull(name) || !has(name)) null else optInt(name)
}

private fun JSONObject.optMedicationTiming(name: String): MedicationTiming {
    return runCatching { MedicationTiming.valueOf(optString(name, MedicationTiming.ANYTIME.name)) }
        .getOrDefault(MedicationTiming.ANYTIME)
}
