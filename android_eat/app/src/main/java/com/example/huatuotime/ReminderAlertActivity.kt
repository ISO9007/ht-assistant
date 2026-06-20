package com.example.huatuotime

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.huatuotime.data.LocalReminderRepository
import com.example.huatuotime.reminder.ACTION_STOP_RING
import com.example.huatuotime.reminder.EXTRA_OCCURRENCE_ID
import com.example.huatuotime.reminder.NUDGE_DELAY_MILLIS
import com.example.huatuotime.reminder.ReminderNotifier
import com.example.huatuotime.reminder.ReminderRingService
import com.example.huatuotime.reminder.ReminderScheduler
import com.example.huatuotime.ui.theme.CareGreen
import com.example.huatuotime.ui.theme.CareRed
import com.example.huatuotime.ui.theme.HuaTuoTimeTheme

class ReminderAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID)
        setContent {
            HuaTuoTimeTheme {
                AlertContent(
                    title = titleForOccurrence(occurrenceId),
                    onAcknowledge = {
                        if (occurrenceId != null) acknowledge(occurrenceId)
                        finish()
                    },
                    onSnooze = {
                        if (occurrenceId != null) snooze(occurrenceId)
                        finish()
                    },
                    onOpenApp = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                    }
                )
            }
        }
    }

    private fun titleForOccurrence(occurrenceId: String?): String {
        val repository = LocalReminderRepository(this)
        val occurrence = occurrenceId?.let { repository.getOccurrence(it) }
        val reminder = occurrence?.let { repository.getReminder(it.reminderId) }
        return reminder?.title ?: "请按时吃药"
    }

    private fun acknowledge(occurrenceId: String) {
        val repository = LocalReminderRepository(this)
        repository.acknowledgeOccurrence(occurrenceId)
        ReminderScheduler(this).cancelNudge(occurrenceId)
        ReminderNotifier(this).cancelOccurrence(occurrenceId)
        stopService(Intent(this, ReminderRingService::class.java).setAction(ACTION_STOP_RING))
    }

    private fun snooze(occurrenceId: String) {
        val repository = LocalReminderRepository(this)
        val occurrence = repository.getOccurrence(occurrenceId) ?: return
        val next = System.currentTimeMillis() + NUDGE_DELAY_MILLIS
        val updated = occurrence.copy(nextNudgeAtMillis = next, snoozedUntilMillis = next)
        repository.saveOccurrence(updated)
        ReminderScheduler(this).scheduleNudge(updated, next)
        ReminderNotifier(this).cancelOccurrence(occurrenceId)
        stopService(Intent(this, ReminderRingService::class.java).setAction(ACTION_STOP_RING))
    }
}

@Composable
private fun AlertContent(title: String, onAcknowledge: () -> Unit, onSnooze: () -> Unit, onOpenApp: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("该吃药了", style = MaterialTheme.typography.displaySmall, color = CareRed, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "请现在服药。服药后点击“已吃”，否则 10 分钟后会继续提醒。",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onAcknowledge,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CareGreen)
        ) {
            Text("已吃", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSnooze,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("延后10分钟", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onOpenApp,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("打开华佗时间")
        }
    }
}
