# HuaTuo Time

HuaTuo Time is an Android medicine reminder app designed for older adults. It uses Kotlin and Jetpack Compose, keeps reminder data on the device, and includes a local-only network boundary so server synchronization can be added later without rewriting the main app flow.

Chinese supplementary documentation is available in [README_zh-CN.md](README_zh-CN.md).

## Features

- Create medicine reminders with medicine name, dose, meal timing, optional note, repeat type, and reminder time.
- Supported schedules: one-time, daily, and weekly reminders.
- Global reminder settings for notification, loud ringtone, vibration, full-screen alert, wake screen, calendar reminder, and background guard.
- Required confirmation: when a dose becomes due, the user must tap "Taken" in the app.
- Repeat nudges: if a due dose is not confirmed, the app keeps reminding the user.
- Snooze: the user can delay a due reminder by 10 minutes.
- Friendly home screen status: shows today's taken, pending, snoozed, missed, and upcoming doses.
- Large typography and high-contrast controls for older users.

## Reminder Reliability

Android places strict limits on background execution. HuaTuo Time uses several layers to improve reminder reliability:

- `AlarmManager.setAlarmClock` is the primary path for due medicine reminders.
- A foreground keep-alive service helps the app remain active when background guard is enabled.
- `JobScheduler` periodically restores reminder scheduling and pending nudge alarms.
- `BootReceiver` restores reminders after device reboot or app update.
- Optional system calendar events provide a backup reminder path.

Important limitation:

- If the user force-stops the app from system settings, Android prevents the app from auto-starting. In that state, `AlarmManager`, `JobScheduler`, foreground services, and broadcasts cannot restart the app. The user must open HuaTuo Time again, or rely on system calendar reminders.

For Xiaomi, OPPO, vivo, Huawei, Honor, and similar Android distributions, users should also enable auto-start, background running, notification permission, alarms and reminders permission, and battery optimization exemption.

## Architecture

- UI: `MainActivity` and Compose components render the home screen, add reminder screen, settings screen, and pending reminder dialog.
- Data: `LocalReminderRepository` stores reminder rules, occurrence records, and global delivery settings locally.
- Scheduling: `ReminderScheduler` uses `AlarmManager` for due reminders and 10-minute nudge alarms.
- Delivery: `ReminderAlarmReceiver` receives alarm broadcasts, and `ReminderDeliveryDispatcher` creates occurrences, notifications, ringtone/vibration, and full-screen alerts.
- Calendar: `CalendarReminderWriter` mirrors enabled reminders into the system calendar when calendar access is granted.
- Background recovery: `ReminderKeepAliveService` provides a foreground guard notification, and `ReminderMaintenanceJobService` performs periodic reminder maintenance through `JobScheduler`.
- Sync boundary: `ReminderApi` is intentionally local-only today and can be replaced by a server-backed implementation later.

## Permissions

The app requests or guides the user to enable:

- `POST_NOTIFICATIONS` on Android 13+ for local notifications.
- Exact alarm permission on Android 12+ when required by the device.
- Calendar read/write permissions for optional system calendar reminders.
- Battery optimization exemption to reduce missed reminders while locked or backgrounded.
- Full-screen and wake-screen capabilities for strong due-dose alerts.

## Development

Requirements:

- Android Studio
- JDK 11+
- Android SDK configured through `local.properties`
- Project Gradle Wrapper

Project SDK targets:

- `minSdk`: 24
- `targetSdk`: 36

Common commands:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew testDebugUnitTest assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Git Ignore Policy

The repository should track source code, Gradle Wrapper files, resources, and stable project configuration. It should not track local SDK paths, build outputs, caches, generated APK/AAB files, signing keys, or machine-specific IDE state.
