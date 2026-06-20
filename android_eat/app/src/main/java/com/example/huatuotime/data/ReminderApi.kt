package com.example.huatuotime.data

/** Future server-sync boundary. The current app intentionally runs local-only. */
interface ReminderApi {
    fun syncReminder(reminder: MedicineReminder)
    fun deleteReminder(reminderId: String)
    fun acknowledgeOccurrence(occurrence: ReminderOccurrence)
}

class LocalOnlyReminderApi : ReminderApi {
    override fun syncReminder(reminder: MedicineReminder) = Unit

    override fun deleteReminder(reminderId: String) = Unit

    override fun acknowledgeOccurrence(occurrence: ReminderOccurrence) = Unit
}
