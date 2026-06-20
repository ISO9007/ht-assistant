# 华佗时间

[English README](README.md)

华佗时间是一个面向老年人的吃药提醒项目。本仓库包含两个客户端工程：

- `android_eat`：Kotlin + Jetpack Compose 编写的原生 Android 应用。
- `wx_eat`：WXML、WXSS、JavaScript 编写的原生微信小程序。

当前两个客户端都采用本地优先设计：药品、提醒设置和服药记录保存在设备本地；代码中保留了清晰的服务/API 边界，方便后续接入服务端同步。

## 主要功能

- 新增药品提醒：药品名、剂量、服药时机、备注、重复规则和提醒时间。
- 支持一次、每天、每周提醒。
- 首页展示今日已吃、待确认、已错过和未到提醒。
- 到点必须确认：提醒触发后，需要用户点击“已吃”才会结束该次提醒。
- 支持延后 10 分钟再提醒。
- 大字体、高对比度界面，便于老年用户查看和操作。
- 可选增强提醒方式：响铃、震动、全屏或小程序内弹窗、系统日历提醒等。

## 目录结构

```text
.
├── android_eat/  # 原生 Android 吃药提醒应用
├── wx_eat/       # 原生微信小程序吃药提醒应用
├── LICENSE
└── README.md
```

## Android 应用

Android 端是更完整的本地强提醒实现，会通过 Android 系统调度能力尽量提升后台和锁屏状态下的提醒可靠性。

技术栈：

- Kotlin
- Jetpack Compose
- Material 3
- Android Gradle Plugin
- 本地设备存储

关键模块：

- UI：`MainActivity`、`ReminderAlertActivity` 和 Compose 页面。
- 数据：`LocalReminderRepository`、提醒模型、触发记录和全局设置。
- 调度：`ReminderScheduler` 使用 `AlarmManager.setAlarmClock` 安排提醒。
- 触发：`ReminderAlarmReceiver`、`ReminderDeliveryDispatcher`、通知、响铃、震动和全屏提醒。
- 恢复：`BootReceiver`、`ReminderKeepAliveService`、`ReminderMaintenanceJobService`。
- 日历备份：`CalendarReminderWriter`。
- 未来同步边界：`ReminderApi`。

开发环境：

- Android Studio
- JDK 11+
- 通过 `android_eat/local.properties` 配置 Android SDK
- `android_eat` 内已包含 Gradle Wrapper

在 `android_eat` 目录执行常用命令：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew testDebugUnitTest assembleDebug
```

Debug APK 输出位置：

```text
android_eat/app/build/outputs/apk/debug/app-debug.apk
```

项目 SDK：

- `minSdk`: 24
- `targetSdk`: 36

Android 端更详细说明见 [`android_eat/README_zh-CN.md`](android_eat/README_zh-CN.md)。

## 微信小程序

微信小程序端提供相同的核心吃药提醒流程，适合在微信生态内轻量使用。

技术栈：

- 原生微信小程序
- WXML
- WXSS
- JavaScript
- 微信本地存储

主要页面：

- `pages/index`：今日提醒、下一顿药、提醒确认和药品列表。
- `pages/medicine-form`：新增和编辑药品提醒。
- `pages/reminder-settings`：提醒设置、订阅消息入口、日历测试和提醒测试。

关键服务：

- `services/storage.js`：本地保存药品、设置和服药记录。
- `services/reminderScheduler.js`：展开重复规则、检查到点提醒、记录已吃和延后提醒。
- `services/reminderAdapters.js`：震动、提醒铃声、订阅消息预留入口和手机日历写入。
- `services/reminderApi.js`：本地 API 门面，后续可替换为服务端实现。

本地运行：

1. 打开微信开发者工具。
2. 导入 `wx_eat` 目录作为小程序项目。
3. 使用 `wx_eat/project.config.json` 中的 AppID，或替换为你自己的 AppID。
4. 编译并在模拟器中预览。
5. 震动、音频、手机日历等能力需要真机测试，微信开发者工具无法完整模拟。

## 提醒可靠性说明

吃药提醒对可靠性要求较高，但移动系统会限制后台运行。

Android 端使用精确闹钟、前台服务、开机恢复、定期巡检和可选日历提醒来提升可靠性。不过，如果用户在系统设置里强行停止应用，Android 会阻止应用自动重启；此时需要用户重新打开应用，或依赖系统日历提醒。

微信小程序端主要在小程序处于前台或回到前台时检查提醒。响铃、震动和日历写入依赖微信与设备 API 支持。订阅消息入口已预留，但还需要配置模板 ID 和后端投递逻辑。

在小米、OPPO、vivo、华为、荣耀等 Android 系统上，建议用户开启通知权限、闹钟和提醒权限、自启动、后台运行、忽略电池优化等设置。

## 数据与后续同步方向

当前实现刻意保持本地优先：

- Android 端本地保存提醒规则、设置和触发记录。
- 微信小程序端通过 `wx` 本地存储保存药品、设置和记录。
- 两端都保留 API/服务门面，后续可以加入账号登录、服务端持久化、跨设备同步、家属共享和云端推送，而不需要重写主要提醒流程。

## 许可证

本项目采用 MIT License，详见 [LICENSE](LICENSE)。
