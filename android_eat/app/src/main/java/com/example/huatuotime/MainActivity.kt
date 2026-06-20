package com.example.huatuotime

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.NumberPicker
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.huatuotime.data.LocalReminderRepository
import com.example.huatuotime.data.MedicationTiming
import com.example.huatuotime.data.MedicineReminder
import com.example.huatuotime.data.ReminderDeliveryOptions
import com.example.huatuotime.data.ReminderOccurrence
import com.example.huatuotime.data.RepeatType
import com.example.huatuotime.data.dayOfWeekLabel
import com.example.huatuotime.data.medicationTimingLabel
import com.example.huatuotime.reminder.ACTION_STOP_KEEP_ALIVE
import com.example.huatuotime.reminder.ACTION_STOP_RING
import com.example.huatuotime.reminder.EXTRA_OCCURRENCE_ID
import com.example.huatuotime.reminder.NUDGE_DELAY_MILLIS
import com.example.huatuotime.reminder.ReminderNotifier
import com.example.huatuotime.reminder.ReminderKeepAliveService
import com.example.huatuotime.reminder.ReminderMaintenanceScheduler
import com.example.huatuotime.reminder.ReminderRingService
import com.example.huatuotime.reminder.ReminderScheduler
import com.example.huatuotime.reminder.CalendarReminderWriter
import com.example.huatuotime.reminder.OverdueReminderTrigger
import com.example.huatuotime.ui.theme.CareGreen
import com.example.huatuotime.ui.theme.CareMint
import com.example.huatuotime.ui.theme.CareRed
import com.example.huatuotime.ui.theme.HuaTuoTimeTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HuaTuoTimeTheme {
                HuaTuoTimeApp(initialOccurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID))
            }
        }
    }
}

@Composable
fun HuaTuoTimeApp(initialOccurrenceId: String? = null) {
    val context = LocalContext.current
    val repository = remember { LocalReminderRepository(context) }
    val scheduler = remember { ReminderScheduler(context) }
    val maintenanceScheduler = remember { ReminderMaintenanceScheduler(context) }
    val notifier = remember { ReminderNotifier(context) }
    val calendarWriter = remember { CalendarReminderWriter(context) }
    val overdueTrigger = remember { OverdueReminderTrigger(context) }
    var reminders by remember { mutableStateOf(repository.getReminders()) }
    var occurrences by remember { mutableStateOf(repository.getOccurrences()) }
    var deliveryOptions by remember { mutableStateOf(repository.getDeliveryOptions()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var highlightedOccurrenceId by remember { mutableStateOf(initialOccurrenceId) }
    val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        reminders = repository.getReminders()
        occurrences = repository.getOccurrences()
        deliveryOptions = repository.getDeliveryOptions()
    }
    var exactAlarmSettingsOpened by remember { mutableStateOf(false) }

    fun syncKeepAliveService(options: ReminderDeliveryOptions = repository.getDeliveryOptions()) {
        val hasEnabledReminders = repository.getReminders().any { it.enabled }
        val intent = Intent(context, ReminderKeepAliveService::class.java)
        if (options.keepAliveService && hasEnabledReminders) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.stopService(intent.setAction(ACTION_STOP_KEEP_ALIVE))
        }
    }

    fun syncMaintenanceJob(options: ReminderDeliveryOptions = repository.getDeliveryOptions()) {
        val hasEnabledReminders = repository.getReminders().any { it.enabled }
        if (options.keepAliveService && hasEnabledReminders) {
            maintenanceScheduler.schedule()
        } else {
            maintenanceScheduler.cancel()
        }
    }

    fun requestAllReminderPermissions(openExactAlarmSettings: Boolean) {
        val runtimePermissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.READ_CALENDAR)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.WRITE_CALENDAR)
            }
        }.toTypedArray()
        if (runtimePermissions.isNotEmpty()) {
            permissionsLauncher.launch(runtimePermissions)
        }
        if (openExactAlarmSettings && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !scheduler.canScheduleExactAlarms()) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}")))
            return
        }
        if (openExactAlarmSettings && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations(context)) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        }
    }

    DisposableEffect(Unit) {
        notifier.ensureChannels()
        syncMaintenanceJob()
        syncKeepAliveService()
        requestAllReminderPermissions(openExactAlarmSettings = !exactAlarmSettingsOpened)
        exactAlarmSettingsOpened = true
        onDispose {}
    }

    fun refresh() {
        overdueTrigger.triggerDueReminders()
        reminders = repository.getReminders()
        occurrences = repository.getOccurrences()
        deliveryOptions = repository.getDeliveryOptions()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun acknowledge(occurrence: ReminderOccurrence) {
        repository.acknowledgeOccurrence(occurrence.id)
        scheduler.cancelNudge(occurrence.id)
        notifier.cancelOccurrence(occurrence.id)
        context.stopService(Intent(context, ReminderRingService::class.java).setAction(ACTION_STOP_RING))
        highlightedOccurrenceId = null
        refresh()
    }

    fun snooze(occurrence: ReminderOccurrence) {
        val next = System.currentTimeMillis() + NUDGE_DELAY_MILLIS
        val updated = occurrence.copy(nextNudgeAtMillis = next, snoozedUntilMillis = next)
        repository.saveOccurrence(updated)
        scheduler.scheduleNudge(updated, next)
        notifier.cancelOccurrence(occurrence.id)
        context.stopService(Intent(context, ReminderRingService::class.java).setAction(ACTION_STOP_RING))
        highlightedOccurrenceId = null
        refresh()
    }

    fun syncReminderCalendar(reminder: MedicineReminder): MedicineReminder {
        val synced = calendarWriter.syncReminder(reminder, repository.getDeliveryOptions())
        repository.saveReminder(synced)
        return synced
    }

    fun syncAllCalendars(options: ReminderDeliveryOptions = repository.getDeliveryOptions()) {
        repository.getReminders().forEach { reminder ->
            val synced = calendarWriter.syncReminder(reminder, options)
            if (synced != reminder) repository.saveReminder(synced)
        }
        refresh()
    }

    BackHandler(enabled = screen != Screen.Home) {
        scheduler.rescheduleAll(repository.getReminders(), repository.getPendingOccurrences())
        refresh()
        screen = Screen.Home
    }

    val pendingForDialog = occurrences
        .filter { it.isPending }
        .filter { !it.isSnoozedNow() }
        .sortedBy { it.scheduledAtMillis }
        .let { pending ->
            pending.firstOrNull { it.id == highlightedOccurrenceId } ?: pending.firstOrNull()
        }
    val pendingReminderForDialog = pendingForDialog?.let { occurrence ->
        reminders.firstOrNull { it.id == occurrence.reminderId }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when (val current = screen) {
            Screen.Home -> HomeScreen(
                modifier = Modifier.padding(innerPadding),
                reminders = reminders,
                occurrences = occurrences,
                highlightedOccurrenceId = highlightedOccurrenceId,
                onAdd = {
                    requestAllReminderPermissions(openExactAlarmSettings = true)
                    screen = Screen.Add
                },
                onDelete = {
                    it.calendarEventId?.let { eventId -> calendarWriter.deleteEvent(eventId) }
                    repository.deleteReminder(it.id)
                    scheduler.cancelReminder(it.id)
                    syncMaintenanceJob()
                    syncKeepAliveService()
                    refresh()
                },
                onAcknowledge = ::acknowledge,
                onSnooze = ::snooze,
                onOpenSettings = { screen = Screen.Settings }
            )

            Screen.Add -> ReminderEditScreen(
                modifier = Modifier.padding(innerPadding),
                onCancel = { screen = Screen.Home },
                onSave = { reminder ->
                    val synced = syncReminderCalendar(reminder)
                    scheduler.cancelReminder(synced.id)
                    scheduler.scheduleReminder(synced)
                    overdueTrigger.triggerDueReminders()
                    syncMaintenanceJob()
                    syncKeepAliveService()
                    refresh()
                    screen = Screen.Home
                }
            )

            Screen.Settings -> ReminderSettingsScreen(
                modifier = Modifier.padding(innerPadding),
                scheduler = scheduler,
                options = deliveryOptions,
                onOptionsChange = {
                    repository.saveDeliveryOptions(it)
                    deliveryOptions = it
                    syncAllCalendars(it)
                    syncMaintenanceJob(it)
                    syncKeepAliveService(it)
                },
                onPermissionsChanged = { syncAllCalendars() },
                onBack = {
                    syncAllCalendars()
                    scheduler.rescheduleAll(repository.getReminders(), repository.getPendingOccurrences())
                    syncMaintenanceJob()
                    syncKeepAliveService()
                    screen = Screen.Home
                }
            )
        }
    }

    if (pendingForDialog != null) {
        PendingMedicineDialog(
            occurrence = pendingForDialog,
            reminder = pendingReminderForDialog,
            onAcknowledge = { acknowledge(pendingForDialog) },
            onSnooze = { snooze(pendingForDialog) }
        )
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Add : Screen
    data object Settings : Screen
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    reminders: List<MedicineReminder>,
    occurrences: List<ReminderOccurrence>,
    highlightedOccurrenceId: String?,
    onAdd: () -> Unit,
    onDelete: (MedicineReminder) -> Unit,
    onAcknowledge: (ReminderOccurrence) -> Unit,
    onSnooze: (ReminderOccurrence) -> Unit,
    onOpenSettings: () -> Unit
) {
    val nowMillis = System.currentTimeMillis()
    val pending = occurrences
        .filter { it.isPending && !it.isSnoozedNow(nowMillis) }
        .sortedBy { it.scheduledAtMillis }
    val todayDoses = buildTodayDoseStatuses(reminders, occurrences)
    val remainingCount = todayDoses.count { it.status != TodayDoseState.TAKEN }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("华佗时间", style = MaterialTheme.typography.displaySmall)
                    Text("按时吃药，一次都不落下", style = MaterialTheme.typography.bodyLarge)
                }
                Button(onClick = onAdd, shape = RoundedCornerShape(8.dp)) {
                    Text("新增")
                }
            }
        }

        item {
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Text("提醒设置")
            }
        }

        item {
            TodaySummaryCard(
                doses = todayDoses,
                remainingCount = remainingCount
            )
        }

        if (pending.isNotEmpty()) {
            item {
                Text("待确认", style = MaterialTheme.typography.headlineMedium, color = CareRed)
            }
            items(pending, key = { it.id }) { occurrence ->
                val reminder = reminders.firstOrNull { it.id == occurrence.reminderId }
                PendingOccurrenceCard(
                    occurrence = occurrence,
                    reminder = reminder,
                    highlighted = occurrence.id == highlightedOccurrenceId,
                    onAcknowledge = { onAcknowledge(occurrence) },
                    onSnooze = { onSnooze(occurrence) }
                )
            }
        }

        item {
            Text("提醒列表", style = MaterialTheme.typography.headlineMedium)
        }

        if (reminders.isEmpty()) {
            item {
                EmptyState(onAdd = onAdd)
            }
        } else {
            items(reminders, key = { it.id }) { reminder ->
                ReminderCard(
                    reminder = reminder,
                    todayStatus = todayDoses.firstOrNull { it.reminder.id == reminder.id },
                    onDelete = { onDelete(reminder) }
                )
            }
        }

        item {
            Text(
                text = "提示：普通关闭 app 后提醒仍会继续；如果在系统设置里强行停止应用，Android 会取消后续后台提醒，需要重新打开华佗时间。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF48554D)
            )
        }
    }
}

@Composable
private fun PendingOccurrenceCard(
    occurrence: ReminderOccurrence,
    reminder: MedicineReminder?,
    highlighted: Boolean,
    onAcknowledge: () -> Unit,
    onSnooze: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (highlighted) Color(0xFFFFE7E7) else Color(0xFFFFF4D6)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(reminder?.title ?: "未知药品", style = MaterialTheme.typography.headlineMedium, color = CareRed)
            Text("提醒时间：${formatDateTime(occurrence.scheduledAtMillis)}", style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = onAcknowledge,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CareGreen)
            ) {
                Text("已吃", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("延后10分钟", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun PendingMedicineDialog(
    occurrence: ReminderOccurrence,
    reminder: MedicineReminder?,
    onAcknowledge: () -> Unit,
    onSnooze: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        shape = RoundedCornerShape(8.dp),
        title = {
            Text("该吃药了", style = MaterialTheme.typography.headlineMedium, color = CareRed)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(reminder?.title ?: "未知药品", style = MaterialTheme.typography.headlineMedium)
                Text("提醒时间：${formatDateTime(occurrence.scheduledAtMillis)}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "服药后请点击“已吃”，否则 10 分钟后会继续提醒。",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAcknowledge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CareGreen)
                ) {
                    Text("已吃", style = MaterialTheme.typography.titleLarge)
                }
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("延后10分钟", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    )
}

@Composable
private fun TodaySummaryCard(doses: List<TodayDoseStatus>, remainingCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("今日吃药情况", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (doses.isEmpty()) "今天没有安排吃药" else "还有 $remainingCount 项需要关注",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            if (doses.isEmpty()) {
                Text("添加提醒后，这里会显示今天哪些药已吃、未吃或还未到时间。", style = MaterialTheme.typography.bodyMedium)
            } else {
                doses.forEach { dose ->
                    TodayDoseRow(dose)
                }
            }
        }
    }
}

@Composable
private fun TodayDoseRow(dose: TodayDoseStatus) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = statusBackground(dose.status)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatTime(dose.scheduledAtMillis),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.width(78.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(dose.reminder.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(medicationTimingLabel(dose.reminder.medicationTiming), style = MaterialTheme.typography.bodyMedium)
                }
                StatusBadge(dose.status)
            }
            if (dose.reminder.note.isNotBlank()) {
                Text("备注：${dose.reminder.note}", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: TodayDoseState, prefix: String? = null) {
    val text = listOfNotNull(prefix, todayDoseStateLabel(status)).joinToString("：")
    Text(
        text = text,
        color = statusColor(status),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun statusColor(status: TodayDoseState): Color {
    return when (status) {
        TodayDoseState.TAKEN -> CareGreen
        TodayDoseState.SNOOZED -> Color(0xFFB7791F)
        TodayDoseState.PENDING_CONFIRMATION -> CareRed
        TodayDoseState.MISSED -> CareRed
        TodayDoseState.UPCOMING -> Color(0xFF48554D)
    }
}

@Composable
private fun statusBackground(status: TodayDoseState): Color {
    return when (status) {
        TodayDoseState.TAKEN -> Color(0xFFE3F6E8)
        TodayDoseState.SNOOZED -> Color(0xFFFFF4D6)
        TodayDoseState.PENDING_CONFIRMATION -> Color(0xFFFFE7E7)
        TodayDoseState.MISSED -> Color(0xFFFFE7E7)
        TodayDoseState.UPCOMING -> Color(0xFFFFF4D6)
    }
}

private fun todayDoseStateLabel(status: TodayDoseState): String {
    return when (status) {
        TodayDoseState.TAKEN -> "已吃"
        TodayDoseState.SNOOZED -> "已延后"
        TodayDoseState.PENDING_CONFIRMATION -> "待确认"
        TodayDoseState.MISSED -> "未吃"
        TodayDoseState.UPCOMING -> "未到"
    }
}

@Composable
private fun ReminderCard(
    reminder: MedicineReminder,
    todayStatus: TodayDoseStatus?,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CareMint),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(reminder.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(scheduleLabel(reminder), style = MaterialTheme.typography.bodyLarge)
            }
            if (todayStatus != null) {
                StatusBadge(todayStatus.status, prefix = "今天")
            } else {
                Text("今天不用吃", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF48554D))
            }
            Text("服药时机：${medicationTimingLabel(reminder.medicationTiming)}", style = MaterialTheme.typography.bodyMedium)
            if (reminder.note.isNotBlank()) {
                Text("备注：${reminder.note}", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { confirmDelete = true }, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("删除")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除提醒？") },
            text = { Text("删除后不会再提醒 ${reminder.title}。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("还没有吃药提醒", style = MaterialTheme.typography.titleLarge)
            Text("先添加药品和时间，华佗时间会到点提醒。", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onAdd, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text("添加第一个提醒")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReminderEditScreen(
    modifier: Modifier,
    onCancel: () -> Unit,
    onSave: (MedicineReminder) -> Unit
) {
    var medicineName by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var medicationTiming by remember { mutableStateOf(MedicationTiming.ANYTIME) }
    var note by remember { mutableStateOf("") }
    var repeatType by remember { mutableStateOf(RepeatType.DAILY) }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var hour by remember { mutableStateOf(currentHour()) }
    var minute by remember { mutableStateOf(currentMinute()) }
    var dayOfWeek by remember { mutableStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader(
                title = "新增吃药提醒",
                onBack = onCancel
            )
        }
        item {
            OutlinedTextField(
                value = medicineName,
                onValueChange = { medicineName = it },
                label = { Text("药品名称") },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = dose,
                onValueChange = { dose = it },
                label = { Text("剂量，例如 1片") },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text("服药时机", style = MaterialTheme.typography.titleLarge)
            MedicationTiming.entries.forEach { timing ->
                FrequencyOption(medicationTimingLabel(timing), medicationTiming == timing) {
                    medicationTiming = timing
                }
            }
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("备注（选填）") },
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text("提醒频率", style = MaterialTheme.typography.titleLarge)
            FrequencyOption("一次", repeatType == RepeatType.ONCE) { repeatType = RepeatType.ONCE }
            FrequencyOption("每天", repeatType == RepeatType.DAILY) { repeatType = RepeatType.DAILY }
            FrequencyOption("每周", repeatType == RepeatType.WEEKLY) { repeatType = RepeatType.WEEKLY }
        }
        if (repeatType == RepeatType.ONCE) {
            item {
                SelectionButton(
                    title = "提醒日期",
                    value = formatDateInput(dateMillis),
                    onClick = { showDatePicker = true }
                )
            }
        }
        if (repeatType == RepeatType.WEEKLY) {
            item {
                Text("每周哪一天", style = MaterialTheme.typography.titleLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { day ->
                        FrequencyOption(dayOfWeekLabel(day), dayOfWeek == day) { dayOfWeek = day }
                    }
                }
            }
        }
        item {
            SelectionButton(
                title = "提醒时间",
                value = "%02d:%02d".format(hour, minute),
                onClick = { showTimePicker = true }
            )
        }
        error?.let { message ->
            item {
                Text(message, color = CareRed, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        val parsed = buildReminder(
                            existing = null,
                            medicineName = medicineName,
                            dose = dose,
                            medicationTiming = medicationTiming,
                            note = note,
                            repeatType = repeatType,
                            dateMillis = dateMillis,
                            hour = hour,
                            minute = minute,
                            dayOfWeek = dayOfWeek,
                            enabled = true
                        )
                        if (parsed == null) {
                            error = "请填写药品名称，并使用正确日期和时间。"
                        } else {
                            onSave(parsed)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("保存")
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateMillis = datePickerState.selectedDateMillis ?: dateMillis
                    showDatePicker = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        WheelTimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { selectedHour, selectedMinute ->
                hour = selectedHour
                minute = selectedMinute
                showTimePicker = false
            }
        )
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onBack,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(56.dp)
        ) {
            Text("返回")
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WheelTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var selectedHour by remember { mutableStateOf(initialHour) }
    var selectedMinute by remember { mutableStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择提醒时间") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "%02d:%02d".format(selectedHour, selectedMinute),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WheelNumberPicker(
                        value = selectedHour,
                        range = 0..23,
                        label = "时",
                        onValueChange = { selectedHour = it }
                    )
                    Text(":", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(horizontal = 8.dp))
                    WheelNumberPicker(
                        value = selectedMinute,
                        range = 0..59,
                        label = "分",
                        onValueChange = { selectedMinute = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedHour, selectedMinute) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun WheelNumberPicker(
    value: Int,
    range: IntRange,
    label: String,
    onValueChange: (Int) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(
            modifier = Modifier
                .width(108.dp)
                .height(190.dp),
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = range.first
                    maxValue = range.last
                    wrapSelectorWheel = true
                    setFormatter { "%02d".format(it) }
                    descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                    setOnValueChangedListener { _, _, newValue -> onValueChange(newValue) }
                }
            },
            update = { picker ->
                if (picker.minValue != range.first) picker.minValue = range.first
                if (picker.maxValue != range.last) picker.maxValue = range.last
                if (picker.value != value) picker.value = value
            }
        )
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SelectionButton(title: String, value: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun FrequencyOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DeliveryOptionsEditor(options: ReminderDeliveryOptions, onChange: (ReminderDeliveryOptions) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("提醒方式（默认全选）", style = MaterialTheme.typography.titleLarge)
        DeliveryCheckbox("手机本地推送", options.notification) { onChange(options.copy(notification = it)) }
        DeliveryCheckbox("铃声最大", options.loudRingtone) { onChange(options.copy(loudRingtone = it)) }
        DeliveryCheckbox("震动提醒", options.vibration) { onChange(options.copy(vibration = it)) }
        DeliveryCheckbox("全屏提醒", options.fullScreenAlert) { onChange(options.copy(fullScreenAlert = it)) }
        DeliveryCheckbox("屏幕关闭也亮起", options.wakeScreen) { onChange(options.copy(wakeScreen = it)) }
        DeliveryCheckbox("手机日历提醒", options.calendarEvent) { onChange(options.copy(calendarEvent = it)) }
        DeliveryCheckbox("后台守护提醒", options.keepAliveService) { onChange(options.copy(keepAliveService = it)) }
    }
}

@Composable
private fun DeliveryCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReminderSettingsScreen(
    modifier: Modifier,
    scheduler: ReminderScheduler,
    options: ReminderDeliveryOptions,
    onOptionsChange: (ReminderDeliveryOptions) -> Unit,
    onPermissionsChanged: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var refreshToken by remember { mutableStateOf(0) }
    val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshToken++
        onPermissionsChanged()
    }
    val notificationGranted = refreshToken.let {
        hasPermission(context, Manifest.permission.POST_NOTIFICATIONS) || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }
    val calendarGranted = refreshToken.let {
        hasPermission(context, Manifest.permission.READ_CALENDAR) && hasPermission(context, Manifest.permission.WRITE_CALENDAR)
    }
    val exactGranted = refreshToken.let { scheduler.canScheduleExactAlarms() }
    val batteryGranted = refreshToken.let { Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isIgnoringBatteryOptimizations(context) }
    Box(Modifier.background(MaterialTheme.colorScheme.background))
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PageHeader(title = "提醒设置", onBack = onBack)
            Text("这里是全局统一提醒方式，所有吃药提醒都会使用这套设置。", style = MaterialTheme.typography.bodyLarge)
        }
        item {
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeliveryOptionsEditor(options = options, onChange = onOptionsChange)
                }
            }
        }
        item {
            Text("系统权限", style = MaterialTheme.typography.headlineMedium)
            Text(
                "为了准时提醒，请尽量打开下面这些能力。开启后台守护后，系统会显示一条常驻通知。",
                style = MaterialTheme.typography.bodyLarge
            )
        }
        item {
            PermissionCard("本地推送", notificationGranted, "Android 13 及以上需要授权通知。") {
                val permissions = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray()
                if (permissions.isNotEmpty()) permissionsLauncher.launch(permissions)
            }
        }
        item {
            PermissionCard("精确闹钟", exactGranted, "用于到点准时提醒，系统页面名通常是“闹钟和提醒”。") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}")))
                }
            }
        }
        item {
            PermissionCard("日历提醒", calendarGranted, "勾选日历提醒后，会尝试写入手机日历。") {
                permissionsLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            }
        }
        item {
            PermissionCard("省电保护", batteryGranted, "允许华佗时间忽略电池优化，降低锁屏和后台时漏提醒的概率。") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:${context.packageName}"))
                    )
                }
            }
        }
        item {
            PermissionCard("全屏和亮屏", true, "已在应用中配置。部分手机还需要在系统通知设置里允许全屏通知。") {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}")))
            }
        }
        item {
            AlwaysActionCard("自启动和后台运行", "小米、OPPO、vivo、华为等手机通常还需要允许自启动、后台运行、锁屏清理保护。") {
                openAutoStartSettings(context)
            }
        }
        item {
            HorizontalDivider()
            Text(
                "可以从最近任务划掉 app；不要在系统设置里强行停止/结束运行。强行停止后 Android 会取消本应用后续提醒，普通 app 不能自动重启自己。",
                style = MaterialTheme.typography.bodyLarge,
                color = CareRed,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Text("返回首页")
            }
        }
    }
}

@Composable
private fun AlwaysActionCard(title: String, message: String, onAction: () -> Unit) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Text("去设置")
            }
        }
    }
}

@Composable
private fun PermissionCard(title: String, granted: Boolean, message: String, onAction: () -> Unit) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(if (granted) "已开启" else "未开启", color = if (granted) CareGreen else CareRed, style = MaterialTheme.typography.bodyLarge)
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (!granted) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text("去开启")
                }
            } else if (title == "全屏和亮屏") {
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text("打开系统设置")
                }
            }
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openAutoStartSettings(context: Context) {
    val packageName = context.packageName
    val intents = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")),
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        Intent().setComponent(ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName")),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
}

private fun buildReminder(
    existing: MedicineReminder?,
    medicineName: String,
    dose: String,
    medicationTiming: MedicationTiming,
    note: String,
    repeatType: RepeatType,
    dateMillis: Long,
    hour: Int,
    minute: Int,
    dayOfWeek: Int,
    enabled: Boolean
): MedicineReminder? {
    val name = medicineName.trim()
    if (name.isBlank()) return null
    val onceAt = if (repeatType == RepeatType.ONCE) combineDateAndTime(dateMillis, hour, minute) else null
    val reminder = MedicineReminder(
        id = existing?.id ?: UUID.randomUUID().toString(),
        medicineName = name,
        dose = dose.trim(),
        medicationTiming = medicationTiming,
        note = note.trim(),
        repeatType = repeatType,
        onceAtMillis = onceAt,
        hour = hour,
        minute = minute,
        dayOfWeek = if (repeatType == RepeatType.WEEKLY) dayOfWeek else null,
        enabled = enabled,
        createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
        calendarEventId = existing?.calendarEventId
    )
    return reminder
}

private fun combineDateAndTime(dateMillis: Long, hour: Int, minute: Int): Long {
    val date = Calendar.getInstance().apply { timeInMillis = dateMillis }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, date.get(Calendar.YEAR))
        set(Calendar.MONTH, date.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, date.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun currentHour(): Int {
    return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}

private fun currentMinute(): Int {
    return Calendar.getInstance().get(Calendar.MINUTE)
}

private fun scheduleLabel(reminder: MedicineReminder): String {
    val time = "%02d:%02d".format(reminder.hour, reminder.minute)
    return when (reminder.repeatType) {
        RepeatType.ONCE -> "一次：${reminder.onceAtMillis?.let(::formatDateTime) ?: time}"
        RepeatType.DAILY -> "每天 $time"
        RepeatType.WEEKLY -> "每周${dayOfWeekLabel(reminder.dayOfWeek ?: 1)} $time"
    }
}

private data class TodayDoseStatus(
    val reminder: MedicineReminder,
    val scheduledAtMillis: Long,
    val occurrence: ReminderOccurrence?,
    val status: TodayDoseState
)

private enum class TodayDoseState {
    UPCOMING,
    SNOOZED,
    PENDING_CONFIRMATION,
    TAKEN,
    MISSED
}

private fun buildTodayDoseStatuses(
    reminders: List<MedicineReminder>,
    occurrences: List<ReminderOccurrence>,
    nowMillis: Long = System.currentTimeMillis()
): List<TodayDoseStatus> {
    return reminders
        .filter { it.enabled }
        .mapNotNull { reminder ->
            val scheduledAt = todayScheduledAt(reminder, nowMillis) ?: return@mapNotNull null
            val occurrence = occurrences
                .filter { it.reminderId == reminder.id && isSameDay(it.scheduledAtMillis, scheduledAt) }
                .maxByOrNull { it.scheduledAtMillis }
            val status = when {
                occurrence?.acknowledgedAtMillis != null -> TodayDoseState.TAKEN
                occurrence?.isSnoozedNow(nowMillis) == true -> TodayDoseState.SNOOZED
                occurrence?.isPending == true -> TodayDoseState.PENDING_CONFIRMATION
                nowMillis < scheduledAt -> TodayDoseState.UPCOMING
                else -> TodayDoseState.MISSED
            }
            TodayDoseStatus(reminder, scheduledAt, occurrence, status)
        }
        .sortedBy { it.scheduledAtMillis }
}

private fun ReminderOccurrence.isSnoozedNow(nowMillis: Long = System.currentTimeMillis()): Boolean {
    return snoozedUntilMillis?.let { it > nowMillis } == true
}

private fun todayScheduledAt(reminder: MedicineReminder, nowMillis: Long): Long? {
    return when (reminder.repeatType) {
        RepeatType.ONCE -> reminder.onceAtMillis?.takeIf { isSameDay(it, nowMillis) }
        RepeatType.DAILY -> combineDateAndTime(nowMillis, reminder.hour, reminder.minute)
        RepeatType.WEEKLY -> {
            val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
            if (calendarDayToMondayBased(today.get(Calendar.DAY_OF_WEEK)) == reminder.dayOfWeek) {
                combineDateAndTime(nowMillis, reminder.hour, reminder.minute)
            } else {
                null
            }
        }
    }
}

private fun calendarDayToMondayBased(calendarDay: Int): Int {
    return when (calendarDay) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }
}

private fun isSameDay(firstMillis: Long, secondMillis: Long): Boolean {
    val first = Calendar.getInstance().apply { timeInMillis = firstMillis }
    val second = Calendar.getInstance().apply { timeInMillis = secondMillis }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

private fun formatDateTime(millis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(millis)
}

private fun formatTime(millis: Long): String {
    return SimpleDateFormat("HH:mm", Locale.CHINA).format(millis)
}

private fun formatDateInput(millis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(millis)
}
