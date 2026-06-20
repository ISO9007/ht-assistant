package com.example.huatuotime.reminder

const val ACTION_SCHEDULED_REMINDER = "com.example.huatuotime.action.SCHEDULED_REMINDER"
const val ACTION_NUDGE_REMINDER = "com.example.huatuotime.action.NUDGE_REMINDER"
const val ACTION_STOP_RING = "com.example.huatuotime.action.STOP_RING"
const val ACTION_STOP_KEEP_ALIVE = "com.example.huatuotime.action.STOP_KEEP_ALIVE"
const val EXTRA_REMINDER_ID = "extra_reminder_id"
const val EXTRA_OCCURRENCE_ID = "extra_occurrence_id"
const val EXTRA_TRIGGER_AT = "extra_trigger_at"

const val REMINDER_CHANNEL_ID = "medicine_reminders"
const val RING_CHANNEL_ID = "medicine_ringing"
const val KEEP_ALIVE_CHANNEL_ID = "medicine_keep_alive"
const val NUDGE_DELAY_MILLIS = 10 * 60 * 1000L
