# HuaTuo Time

[中文文档](README_zh-CN.md)

HuaTuo Time is a medicine reminder project for older adults. This repository contains two client implementations:

- `android_eat`: a native Android app built with Kotlin and Jetpack Compose.
- `wx_eat`: a native WeChat Mini Program built with WXML, WXSS, and JavaScript.

Both clients are local-first today. Medicine data, reminder settings, and dose records are stored on the device, while the code keeps a clear service/API boundary so server synchronization can be added later.

## Features

- Add medicine reminders with medicine name, dosage, meal timing, notes, repeat rules, and reminder times.
- Supported reminder schedules: one-time, daily, and weekly.
- Today view with taken, pending, missed, and upcoming dose states.
- Required confirmation: due doses remain active until the user marks them as taken.
- Snooze support for delaying a due reminder by 10 minutes.
- Large, high-contrast screens designed for older users.
- Optional stronger delivery paths such as sound, vibration, full-screen or in-app alerts, and system calendar reminders.

## Repository Layout

```text
.
├── android_eat/  # Native Android medicine reminder app
├── wx_eat/       # Native WeChat Mini Program medicine reminder app
├── LICENSE
└── README.md
```

## Android App

The Android app is the stronger local reminder implementation. It uses Android system scheduling APIs to improve reminder reliability even when the app is backgrounded.

Technology stack:

- Kotlin
- Jetpack Compose
- Material 3
- Android Gradle Plugin
- Local device storage

Key modules:

- UI: `MainActivity`, `ReminderAlertActivity`, and Compose screens.
- Data: `LocalReminderRepository`, reminder models, occurrence records, and settings.
- Scheduling: `ReminderScheduler` with `AlarmManager.setAlarmClock`.
- Delivery: `ReminderAlarmReceiver`, `ReminderDeliveryDispatcher`, notifications, ringtone, vibration, and full-screen alerts.
- Recovery: `BootReceiver`, `ReminderKeepAliveService`, and `ReminderMaintenanceJobService`.
- Calendar backup: `CalendarReminderWriter`.
- Future sync boundary: `ReminderApi`.

Development requirements:

- Android Studio
- JDK 11+
- Android SDK configured through `android_eat/local.properties`
- Gradle Wrapper included in `android_eat`

Run common commands from `android_eat`:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew testDebugUnitTest assembleDebug
```

Debug APK output:

```text
android_eat/app/build/outputs/apk/debug/app-debug.apk
```

Project SDK targets:

- `minSdk`: 24
- `targetSdk`: 36

More Android-specific details are available in [`android_eat/README.md`](android_eat/README.md).

## WeChat Mini Program

The WeChat Mini Program provides the same core medicine reminder workflow in a lightweight WeChat-native experience.

Technology stack:

- Native WeChat Mini Program
- WXML
- WXSS
- JavaScript
- WeChat local storage

Main pages:

- `pages/index`: today dashboard, next dose, reminder confirmation, and medicine list.
- `pages/medicine-form`: create and edit medicine reminders.
- `pages/reminder-settings`: reminder settings, subscribe-message entry, calendar test, and alert test.

Key services:

- `services/storage.js`: local medicines, settings, and dose records.
- `services/reminderScheduler.js`: repeat-rule expansion, due reminder detection, taken records, and snooze logic.
- `services/reminderAdapters.js`: vibration, reminder sound, subscribe-message placeholder, and phone calendar integration.
- `services/reminderApi.js`: local-only API facade that can be replaced by a server-backed implementation.

Run locally:

1. Open WeChat DevTools.
2. Import the `wx_eat` folder as a Mini Program project.
3. Use the AppID from `wx_eat/project.config.json`, or replace it with your own AppID.
4. Compile and preview in the simulator.
5. Test vibration, audio, and calendar features on a real device, because DevTools cannot fully emulate those mobile APIs.

## Reminder Reliability Notes

Medicine reminders are safety-sensitive, and mobile platforms place strict limits on background execution.

On Android, HuaTuo Time uses exact alarm scheduling, foreground service support, boot recovery, periodic maintenance, and optional calendar events. However, if the user force-stops the app in system settings, Android prevents the app from restarting itself. The user must open the app again, or rely on calendar reminders.

On WeChat Mini Program, in-app checks run when the Mini Program is active or brought back to the foreground. Sound, vibration, and calendar writes depend on WeChat and device API support. WeChat subscribe messages are prepared as an integration point, but template IDs and backend delivery still need to be configured.

For Xiaomi, OPPO, vivo, Huawei, Honor, and similar Android distributions, users should enable notification permission, alarms and reminders permission, auto-start, background running, and battery optimization exemption where applicable.

## Data and Sync Direction

The current implementation is intentionally local-first:

- Android stores reminder rules, settings, and occurrence records locally.
- WeChat Mini Program stores medicines, settings, and records through `wx` local storage.
- Both clients keep API/service facades so future work can add account login, backend persistence, cross-device sync, caregiver sharing, and cloud push notifications without rewriting the main reminder flow.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
