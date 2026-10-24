# 无尽冬日活动正中靶心 · Android 1.0.3

本文件夹是独立 Android 工程。安装包在上一级：`../正中靶心-1.0.3.apk`。Windows 源码单独放在 `exe/源码/`。

## 使用

Android 11 及以上可用。用户在系统辅助功能中开启“靶心助手”，上传首页和完整 12 靶子页的两张全屏原图，无需在助手内选择倍率，直接开始执行；倍率在游戏中设置。截图及识别在手机本地完成，不需要电脑或 ADB。

每轮回到首页后读取实际余券，不按估计倍率扣减。剩 1～4 张时，结合右侧按钮颜色和开始按钮下方的红 / 白费用字检查状态；5 倍不足时自动切到 1 倍，确认画面后再开始。宝箱打开后自动识别底部退出提示，返回首页并开始下一轮，不等待所有靶子打开。悬浮条可暂停、继续和停止；首页连续两次读到 0 时停止。读数不清、按钮被遮挡或切换失败时暂停，保留进度。

主界面和悬浮条显示 **靶心助手 1.0.3 · 自动余券**，安装版本为 1.0.3，versionCode 为 5（高于前一份 1.0.3，可覆盖更新）。

## 构建

环境：JDK 17、Android SDK `platforms;android-35-ext15` 和 `build-tools;35.0.0`。Gradle Wrapper 使用 Gradle 8.9。

在当前目录创建不提交到 Git 的 `local.properties`，填写本地 SDK 路径：

```properties
sdk.dir=C:/Android/Sdk
```

在当前“源码”文件夹执行：

```powershell
.\gradlew.bat :app:assembleRelease :app:lintRelease
python tools/verify_android_package.py app/build/outputs/apk/release/app-release.apk --version 1.0.3
Copy-Item app/build/outputs/apk/release/app-release.apk ../正中靶心-1.0.3.apk
```

签名验证可使用 Android SDK build-tools 中的 `apksigner.bat verify`。当前测试包采用本机 Android debug 签名；覆盖更新需要保留同一签名密钥，密钥不在仓库中。

主要实现位于 `app/src/main/java/cn/local/bullseye/`。`MainActivity.java` 是界面，`Vision.java` 是识别，`GameService.java` 是屏幕截图、点击及运行控制。

## 验证范围

发包时检查编译、静态检查、APK 签名，以及 APK 内部不存在旧倍率识别提示。实际手机游戏整轮运行仍需要实机验证，不能用桌面回归测试代替。

## 本地流程检查

以下检查直接运行实际使用的券数决策和颜色检测代码，无需连接手机。

```powershell
javac -encoding UTF-8 -d .build/check app/src/main/java/cn/local/bullseye/TicketPolicy.java app/src/main/java/cn/local/bullseye/LowBalanceControl.java app/src/main/java/cn/local/bullseye/TicketDigits.java tools/CoreCheck.java
java -cp .build/check CoreCheck
```

可在最后一条命令追加本地两张样本路径（1 倍白字、5 倍红字），验证控件颜色、遮挡和单数字识别；样本为当前 1080 × 2400 游戏布局，不上传仓库。其他尺寸和位置由运行时截图中的 12 靶子及开始按钮自动定位。运行条数字代表最近一次首页读数，回首页后更新。
