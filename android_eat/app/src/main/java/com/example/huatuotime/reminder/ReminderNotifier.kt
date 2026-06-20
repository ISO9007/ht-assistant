package com.example.huatuotime.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.huatuotime.MainActivity
import com.example.huatuotime.R
import com.example.huatuotime.ReminderAlertActivity
import com.example.huatuotime.data.MedicineReminder
import com.example.huatuotime.data.ReminderOccurrence

class ReminderNotifier(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val alarmSound = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val reminderChannel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "吃药提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "到点吃药时显示通知"
            setSound(alarmSound, audioAttributes)
            enableVibration(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        val ringChannel = NotificationChannel(
            RING_CHANNEL_ID,
            "持续响铃",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "未点击已吃前保持响铃服务"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        val keepAliveChannel = NotificationChannel(
            KEEP_ALIVE_CHANNEL_ID,
            "后台守护",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持吃药提醒后台守护运行"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        notificationManager.createNotificationChannel(reminderChannel)
        notificationManager.createNotificationChannel(ringChannel)
        notificationManager.createNotificationChannel(keepAliveChannel)
    }

    fun showReminder(
        reminder: MedicineReminder,
        occurrence: ReminderOccurrence,
        isNudge: Boolean,
        fullScreenAlert: Boolean
    ) {
        ensureChannels()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openIntent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OCCURRENCE_ID, occurrence.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            occurrence.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(context, ReminderAlertActivity::class.java)
            .putExtra(EXTRA_OCCURRENCE_ID, occurrence.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            occurrence.id.hashCode() xor 0x7171,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (isNudge) "还没有确认吃药，请打开华佗时间点击“已吃”" else "现在该吃药了，请确认后点击“已吃”"
        val builder = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appIconBitmap())
            .setContentTitle("该吃药了：${reminder.title}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)

        if (fullScreenAlert) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        NotificationManagerCompat.from(context).notify(notificationId(occurrence.id), builder.build())
    }

    fun cancelOccurrence(occurrenceId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(occurrenceId))
    }

    fun ringingNotification(occurrenceId: String, title: String) =
        NotificationCompat.Builder(context, RING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appIconBitmap())
            .setContentTitle("正在提醒吃药")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()

    fun keepAliveNotification() =
        NotificationCompat.Builder(context, KEEP_ALIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appIconBitmap())
            .setContentTitle("华佗时间正在守护提醒")
            .setContentText("为了按时提醒吃药，请保持这条通知运行")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    9002,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun appIconBitmap() = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

    private fun notificationId(occurrenceId: String): Int = occurrenceId.hashCode()
}
