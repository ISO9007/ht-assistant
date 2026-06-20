package com.example.huatuotime.reminder

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.huatuotime.data.LocalReminderRepository

class ReminderKeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_KEEP_ALIVE) {
            stopSelf()
            return START_NOT_STICKY
        }

        val repository = LocalReminderRepository(this)
        if (!repository.getDeliveryOptions().keepAliveService) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notifier = ReminderNotifier(this)
        notifier.ensureChannels()
        startForeground(9002, notifier.keepAliveNotification())
        ReminderScheduler(this).rescheduleAll(repository.getReminders(), repository.getPendingOccurrences())
        return START_STICKY
    }
}
