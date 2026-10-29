# Android 源码

JDK 17、Gradle 8.9、Android SDK 35 extension 15、Build Tools 35.0.0。设置 JAVA_HOME 与 ANDROID_HOME，或在不提交的 local.properties 中配置 sdk.dir。

构建：`gradlew.bat :app:testEngine :app:assembleRelease :app:lintRelease`。

将 app/build/outputs/apk/release/app-release.apk 复制到上一级，命名为 无尽冬日助手-2.0.2.apk。

检查成品：`python tools/verify_android_package.py ../无尽冬日助手-2.0.2.apk --version 2.0.2`。

一个 launcher：ModeActivity；一个无障碍服务：BearService。cn.local.bullseye.GameService 是该服务内的模块，不是第二个 Android Service。ModeGate、运行代次和输入前检查共同限制旧任务回调。返回活动选择会停止两个模块。

打熊实际流程在 MarchFlow.java，与 Windows bear_flow.py 对照测试。BearFlow.java 只保留页面和动作数据类型。正中靶心保留实际券数、低券切换、结算退出；正中靶心悬浮条保持显示；仅在识别用的截图副本里遮罩悬浮条。固定尺寸避免状态更新时跳动，拖动时停发点击，落点自动避开券数、开始按钮、靶子、倍率及结算区域；旧位置采集的画面不再用于点击。

Release 开启 R8 与资源裁剪，仅打包 ARM64 和 ARMv7，共用两套流程的 OpenCV / ML Kit。应用合并清单移除联网权限。

本地输出使用当前开发机调试签名，便于覆盖同机此前构建的打熊助手；密钥不提交。发布到应用商店前应自行配置正式签名，不要把密码或 keystore 写进 Git。

纯 Java 的正中靶心补充检查：用 javac 编译 TicketPolicy.java、TicketDigits.java、LowBalanceControl.java 与 tools/CoreCheck.java，运行 CoreCheck。可传入本地一倍和五倍截图以启用图片检查。

录屏验证：`python tools/replay_recording.py <录屏.mp4> [非巨熊截图.jpg]`。这验证图像线索与误识别控制，不能替代手机上的 ML Kit、权限、触控和服务器响应测试。
