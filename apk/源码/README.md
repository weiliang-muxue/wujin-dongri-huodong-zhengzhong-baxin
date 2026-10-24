# 无尽冬日活动正中靶心 · Android 1.0.3

本文件夹是独立 Android 工程。安装包在上一级：`../正中靶心-1.0.3.apk`。Windows 源码单独放在 `exe/源码/`。

## 使用

Android 11 及以上可用。用户在系统辅助功能中开启“靶心助手”，上传首页和完整 12 靶子页的两张全屏原图，手动选择与游戏内一致的倍率，再开始执行。截图及识别在手机本地完成，不需要电脑或 ADB。

只读取总券数，不识别、不点击倍率按钮。宝箱打开后自动识别底部退出提示，返回首页并开始下一轮，不等待所有靶子打开。悬浮条可暂停、继续和停止；券数不足所选倍率时停止并提示手动调整。

主界面和悬浮条显示 **靶心助手 1.0.3 · 手动倍率**，安装版本为 1.0.3，versionCode 为 4。

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
