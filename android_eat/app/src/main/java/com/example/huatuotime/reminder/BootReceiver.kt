package com.example.huatuotime.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.huatuotime.data.LocalReminderRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repository = LocalReminderRepository(context)
        val reminders = repository.getReminders()
        val pendingOccurrences = repository.getPendingOccurrences()
        val options = repository.getDeliveryOptions()
        val hasEnabledReminders = reminders.any { it.enabled }
        ReminderNotifier(context).ensureChannels()
        ReminderScheduler(context).rescheduleAll(reminders, pendingOccurrences)
        if (options.keepAliveService && hasEnabledReminders) {
            ReminderMaintenanceScheduler(context).schedule()
        } else {
            ReminderMaintenanceScheduler(context).cancel()
        }
        if (options.keepAliveService && hasEnabledReminders) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, ReminderKeepAliveService::class.java))
            } catch (_: IllegalStateException) {
            } catch (_: SecurityException) {
            }
        }
    }
}
