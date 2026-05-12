# 违停短信响铃 Android App

这个项目会在收到符合规则的短信后，立刻启动前台服务并循环播放系统闹铃。

## 当前匹配规则

短信正文同时包含以下 3 段文字时触发：

- `【上海交警】`
- `未按规定停放已被记录`
- `请立即驶离`

你给的示例短信会命中这套规则。

## 项目结构

- `app/src/main/java/com/example/parkingalert/SmsAlertReceiver.kt`
  负责接收短信广播并做文本匹配。
- `app/src/main/java/com/example/parkingalert/AlertService.kt`
  负责启动前台告警通知并循环播放闹铃。
- `app/src/main/java/com/example/parkingalert/MainActivity.kt`
  负责申请权限、手动测试、手动停止响铃。

## 在 Android Studio 里使用

1. 用 Android Studio 打开 `parking-alert-android`。
2. 等 Gradle 同步完成。
3. 连接安卓手机并安装到手机。
4. 首次打开 App，点“申请短信和通知权限”。
5. 在系统设置里关闭该 App 的省电限制，并允许自启动。
6. 点“测试响铃”确认手机能发出声音。

## 重要限制

- 某些国产安卓系统会限制短信广播和后台服务，必须手动放开自启动和电池优化。
- Android 13 及以上还需要通知权限，否则前台告警通知可能显示不完整。
- 我在当前机器上没法本地构建 APK，因为这里缺少 Java/Android SDK 环境；项目代码已经按标准 Android Gradle 结构落好。
