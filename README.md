# 挪车提醒

一个用于 Android 的短信告警应用。

当收到疑似违停挪车短信时，应用会立即触发强提醒：前台告警通知、循环闹铃、震动、亮屏、全屏闪烁，以及闪光灯爆闪。

## 功能

- 监听短信广播并匹配告警关键词
- 命中后启动前台告警服务
- 循环播放系统闹铃
- 震动提醒
- 亮屏并弹出全屏告警页
- 屏幕快速闪烁
- 闪光灯循环爆闪
- 支持手动测试和手动停止告警

## 当前匹配规则

短信正文满足以下条件时触发：

- 包含 `交警`
- 同时包含 `请立即驶离` 或 `未按规定停放`

核心逻辑位置：

- [SmsAlertReceiver.kt](app/src/main/java/com/example/parkingalert/SmsAlertReceiver.kt)

## 权限说明

应用会根据系统版本请求这些权限：

- `RECEIVE_SMS`：接收短信广播
- `POST_NOTIFICATIONS`：显示前台告警通知
- `CAMERA`：控制闪光灯爆闪
- `WAKE_LOCK`：亮屏和保持告警界面
- `VIBRATE`：震动提醒

## 运行要求

- Android `8.0+` (`minSdk 26`)
- `compileSdk 34`
- Java `17`
- Android Studio / Gradle `8.7` 可正常构建

## 本地构建

```bash
./gradlew assembleDebug
```

构建输出：

- `app/build/outputs/apk/debug/app-debug.apk`

## 在 Android Studio 中使用

1. 用 Android Studio 打开项目。
2. 等待 Gradle Sync 完成。
3. 连接 Android 真机并安装应用。
4. 首次打开后申请短信、通知和相机权限。
5. 在系统设置中关闭省电限制，并允许自启动。
6. 点“测试响铃”验证整套告警链路。

## 项目结构

- `app/src/main/java/com/example/parkingalert/SmsAlertReceiver.kt`
  负责接收短信广播并做文本匹配。
- `app/src/main/java/com/example/parkingalert/AlertService.kt`
  负责前台告警、闹铃、震动和闪光灯爆闪。
- `app/src/main/java/com/example/parkingalert/AlertActivity.kt`
  负责亮屏、全屏展示和屏幕闪烁。
- `app/src/main/java/com/example/parkingalert/MainActivity.kt`
  负责权限申请、手动测试和停止告警。

## 注意事项

- 部分国产 Android 系统会限制短信广播、自启动和后台前台服务，需手动放开。
- 闪光灯爆闪依赖相机权限，且部分机型可能被系统限制。
- 强闪和高频提醒可能刺激眼睛，不建议长时间测试。

## 许可证

本项目使用 [MIT License](LICENSE)。
