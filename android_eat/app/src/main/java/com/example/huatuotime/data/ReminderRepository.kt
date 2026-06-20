package com.example.huatuotime.data

interface ReminderRepository {
    fun getReminders(): List<MedicineReminder>
    fun getReminder(reminderId: String): MedicineReminder?
    fun saveReminder(reminder: MedicineReminder)
    fun deleteReminder(reminderId: String)
    fun getOccurrences(): List<ReminderOccurrence>
    fun getOccurrence(occurrenceId: String): ReminderOccurrence?
    fun getPendingOccurrences(): List<ReminderOccurrence>
    fun saveOccurrence(occurrence: ReminderOccurrence)
    fun acknowledgeOccurrence(occurrenceId: String, acknowledgedAtMillis: Long = System.currentTimeMillis())
    fun getDeliveryOptions(): ReminderDeliveryOptions
    fun saveDeliveryOptions(options: ReminderDeliveryOptions)
}
