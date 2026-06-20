# 华佗时间

“华佗时间”是一个面向老年人的本地吃药提醒 Android app。应用使用 Kotlin + Jetpack Compose 开发，提醒数据保存在手机本地，不依赖服务器；代码中保留了网络层接口，方便后续接入服务端同步。

## 主要功能

- 新增吃药提醒：药品名、剂量、服药时机、备注、提醒频率和时间。
- 提醒频率：一次、每天、每周某一天。
- 全局提醒方式：本地通知、响铃、震动、全屏提醒、锁屏亮屏、日历提醒、后台守护提醒。
- 到点确认：提醒触发后必须点击“已吃”；未确认会继续补提醒。
- 延后提醒：用户暂时不方便时可延后 10 分钟。
- 今日状态：首页展示今天已吃、待确认、已延后、未到等状态。
- 大字体和高对比度控件，方便老年用户查看和操作。

## 后台提醒说明

Android 对后台启动和保活有系统限制：

- 普通从最近任务划掉 app，提醒会尽量通过闹钟、通知、前台服务和日历继续工作。
- 如果用户在系统设置中“强行停止/结束运行”应用，Android 会禁止应用自动启动，`AlarmManager`、`JobScheduler` 和前台服务都无法绕过。
- 小米、OPPO、vivo、华为、荣耀等手机建议在系统里开启自启动、后台运行、忽略电池优化和通知权限。

## 技术结构

- UI：`MainActivity` 和 Compose 组件负责首页、新增提醒、设置页和待确认弹窗。
- 数据：`LocalReminderRepository` 使用本地存储保存提醒规则、触发记录和全局提醒设置。
- 调度：`ReminderScheduler` 使用 `AlarmManager.setAlarmClock` 安排主提醒和 10 分钟补提醒。
- 触发：`ReminderAlarmReceiver` 接收闹钟广播，`ReminderDeliveryDispatcher` 统一发送通知、响铃和全屏提醒。
- 日历：`CalendarReminderWriter` 写入系统日历事件和日历闹钟。
- 后台辅助：`ReminderKeepAliveService` 提供前台守护通知，`ReminderMaintenanceJobService` 定期巡检并恢复提醒链路。

## 开发环境

- Android Studio
- JDK 11+
- Android SDK 通过 `local.properties` 配置
- minSdk 24，targetSdk 36

## 常用命令

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew testDebugUnitTest assembleDebug
```

Debug APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 权限

应用会请求或引导用户开启：

- 通知权限：Android 13+ 需要 `POST_NOTIFICATIONS`。
- 精确闹钟：Android 12+ 可能需要“闹钟和提醒”特殊授权。
- 日历权限：用于写入系统日历提醒和日历闹钟。
- 忽略电池优化：降低锁屏和后台漏提醒概率。
- 全屏/亮屏提醒：到点时尽量唤起提醒界面。

## Git 忽略策略

仓库应提交源码、Gradle Wrapper、资源文件和稳定工程配置；不提交本机 SDK 配置、构建产物、缓存、生成的 APK/AAB、签名密钥和 IDE 临时状态。
