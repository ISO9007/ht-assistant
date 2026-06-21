package com.example.huatuotime.reminder

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.huatuotime.data.LocalReminderRepository

/** Periodic recovery job; it improves resilience but never replaces exact AlarmManager reminders. */
class ReminderMaintenanceJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val repository = LocalReminderRepository(this)

        ReminderNotifier(this).ensureChannels()
        OverdueReminderTrigger(this).triggerDueReminders()

        val reminders = repository.getReminders()
        val pendingOccurrences = repository.getPendingOccurrences()
        ReminderScheduler(this).rescheduleAll(reminders, pendingOccurrences)

        if (repository.getDeliveryOptions().keepAliveService && reminders.any { it.enabled }) {
            try {
                ContextCompat.startForegroundService(this, Intent(this, ReminderKeepAliveService::class.java))
            } catch (_: IllegalStateException) {
            } catch (_: SecurityException) {
            }
        }

        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true
}
