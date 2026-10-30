# Android 源码

JDK 17、Gradle 8.9、Android SDK 35 extension 15、Build Tools 35.0.0。设置 JAVA_HOME 与 ANDROID_HOME，或在不提交的 local.properties 中配置 sdk.dir。

构建：`gradlew.bat :app:testEngine :app:assembleRelease :app:lintRelease`。

将 app/build/outputs/apk/release/app-release.apk 复制到上一级，命名为 无尽冬日助手-2.0.3.apk。

检查成品：`python tools/verify_android_package.py ../无尽冬日助手-2.0.3.apk --version 2.0.3`。

一个 launcher：ModeActivity；一个无障碍服务：BearService。cn.local.bullseye.GameService 是该服务内的模块，不是第二个 Android Service。ModeGate、运行代次和输入前检查共同限制旧任务回调。返回活动选择会停止两个模块。

打熊实际流程在 MarchFlow.java，与 Windows bear_flow.py 对照测试。BearFlow.java 只保留页面和动作数据类型。正中靶心保留实际券数、低券切换、结算退出；正中靶心悬浮条保持显示；仅在识别用的截图副本里遮罩悬浮条。固定尺寸避免状态更新时跳动，拖动时停发点击，落点自动避开券数、开始按钮、靶子、倍率及结算区域；旧位置采集的画面不再用于点击。

Release 开启 R8 与资源裁剪，仅打包 ARM64 和 ARMv7，共用两套流程的 OpenCV / ML Kit。应用合并清单移除联网权限。

本地输出使用当前开发机调试签名，便于覆盖同机此前构建的打熊助手；密钥不提交。发布到应用商店前应自行配置正式签名，不要把密码或 keystore 写进 Git。

纯 Java 的正中靶心补充检查：用 javac 编译 TicketPolicy.java、TicketDigits.java、LowBalanceControl.java 与 tools/CoreCheck.java，运行 CoreCheck。可传入本地一倍和五倍截图以启用图片检查。

录屏验证：`python tools/replay_recording.py <录屏.mp4> [非巨熊截图.jpg]`。这验证图像线索与误识别控制，不能替代手机上的 ML Kit、权限、触控和服务器响应测试。

## Android 运行时回归

桌面 JDK 单测不能替代 Android ART。2.0.3 修复了兵力解析中的 `UNICODE_CHARACTER_CLASS`：桌面支持这个参数，Android 会抛出 `IllegalArgumentException`。数字表达式不需要此参数。

在专用 Android 11 或以上 x86_64 模拟器上构建并运行：

- `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PemulatorTest`
- `python tools/run_android_regression.py --adb <adb路径> --serial emulator-5580 --screenshot <本次179810出征页.jpg> --recording <本地录屏.mp4> --output <本地验证目录>`

测试入口 `RuntimeRegression` 先用旧参数重现异常，再在 ART 上运行流程单测，最后将本次截图和 13 个录屏关键帧交给实际的 OpenCV / ML Kit 识别。需要 Python 的 Pillow、OpenCV。截图仅写入指定本地验证目录及模拟器应用私有目录，不提交仓库。测试不点击游戏。

首次提取的 14 张 PNG 保存在 `<本地验证目录>/runtime-fixtures`。后续可复用该目录，省去重新读取录屏：

`python tools/run_android_regression.py --adb <adb路径> --serial emulator-5580 --fixtures <已有runtime-fixtures目录> --output <本次验证目录>`

`--fixtures` 模式不需要 `--screenshot` 和 `--recording`，仍需 Pillow、OpenCV。目录必须包含 `reported-formation.png`，以及 `video-170.png`、`video-250.png`、`video-270.png`、`video-315.png`、`video-358.png`、`video-411.png`、`video-691.png`、`video-718.png`、`video-751.png`、`video-771.png`、`video-780.png`、`video-816.png`、`video-820.png`，每张均为 1080 × 2400。脚本会检查这些文件、安装 Debug 和测试 APK、上传样本并运行识别；日志写入本次输出目录的 `android-runtime.txt`。

只验证 ART 上的正则修复与流程单测，可用 `RuntimeCoreCheck`。先按上述命令构建两个 Debug APK，然后在 `apk/源码` 目录执行以下命令（示例假定 `adb` 已在 PATH，模拟器序列号按实际替换）：

```powershell
adb -s emulator-5580 push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/wujindongri-runtime-app.apk
adb -s emulator-5580 push app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk /data/local/tmp/wujindongri-runtime-tests.apk
adb -s emulator-5580 shell mkdir -p /data/local/tmp/wujindongri-runtime-reports
adb -s emulator-5580 shell "CLASSPATH=/data/local/tmp/wujindongri-runtime-app.apk:/data/local/tmp/wujindongri-runtime-tests.apk app_process /system/bin cn.local.winterbear.RuntimeCoreCheck /data/local/tmp/wujindongri-runtime-reports"
```

这个入口直接通过 `app_process` 执行 APK 内的 DEX，不依赖安装或启动应用。最后一个参数是必填的模拟器报告目录。成功时输出旧异常复现、`FIX VERIFIED ON ART` 和 `ART JUNIT` 的测试数量及 `0 failures`；失败会抛出异常。它不执行 OpenCV / ML Kit 图片识别，也不验证触控、权限或游戏服务器响应；图片识别回归仍需运行 `RuntimeRegression`。

`-PemulatorTest` 仅供模拟器测试，使用 x86_64；交付时必须去掉此参数重新构建 Release，成品检查会拒绝包含 x86 的安装包。
