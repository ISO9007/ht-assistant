package com.example.huatuotime.reminder

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle

private const val REMINDER_MAINTENANCE_JOB_ID = 24001
private const val MAINTENANCE_INTERVAL_MILLIS = 15 * 60 * 1000L

/** Registers the lightweight JobScheduler heartbeat used to restore reminder plumbing. */
class ReminderMaintenanceScheduler(private val context: Context) {
    private val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    fun schedule() {
        val component = ComponentName(context, ReminderMaintenanceJobService::class.java)
        val info = JobInfo.Builder(REMINDER_MAINTENANCE_JOB_ID, component)
            .setPersisted(true)
            .setPeriodic(MAINTENANCE_INTERVAL_MILLIS)
            .setExtras(PersistableBundle().apply { putString("source", "reminder_maintenance") })
            .build()
        jobScheduler.schedule(info)
    }

    fun cancel() {
        jobScheduler.cancel(REMINDER_MAINTENANCE_JOB_ID)
    }
}
