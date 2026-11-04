# Android 源码

JDK 17、Gradle 8.9、Android SDK 35 extension 15、Build Tools 35.0.0。设置 JAVA_HOME 与 ANDROID_HOME，或在不提交的 local.properties 中配置 sdk.dir。

构建：`gradlew.bat :app:testEngine :app:assembleRelease :app:lintRelease`。

将 app/build/outputs/apk/release/app-release.apk 复制到上一级，命名为 无尽冬日助手-2.1.0.apk。

检查成品：`python tools/verify_android_package.py ../无尽冬日助手-2.1.0.apk --version 2.1.0`。

一个 launcher：ModeActivity；一个无障碍服务：BearService。cn.local.bullseye.GameService 和 HuntController 是该服务内的模块，不是其他 Android Service。ModeGate、运行代次和输入前检查共同限制旧任务回调。返回活动选择会停止所有模块。

打熊实际流程在 MarchFlow.java。2.0.4 仅更新 Android，电脑版保持 2.0.3。BearFlow.java 只保留页面和动作数据类型。正中靶心保留实际券数、低券切换、结算退出；正中靶心悬浮条保持显示；仅在识别用的截图副本里遮罩悬浮条。固定尺寸避免状态更新时跳动，拖动时停发点击，落点自动避开券数、开始按钮、靶子、倍率及结算区域；旧位置采集的画面不再用于点击。

Release 开启 R8 与资源裁剪，仅打包 ARM64 和 ARMv7，四套流程共用 OpenCV / ML Kit。应用合并清单移除联网权限。

2.1.0：HuntFlow 管理巨兽集结、野兽攻击、1～6 行军上限、预设顺序及库存罐头额度；HuntVision 定位游戏控件并读取等级、行军和库存；HuntController 在同一无障碍服务中运行。HuntActivity 的设置仅保留在本次任务内。OverlayFold 为四个活动提供手动收起、点击展开，状态更新不会重新挂载窗口。

HuntOverlayGuard 使用当前窗口系统栏/刘海边距，在固定识别区域之外选择浮窗位置。BearService / GameService 放不下展开浮窗时尝试收起后重新避让；收起操作会使旧截图失效。新增宽竖屏检查仅验证窗口几何，未扩宽 ReferenceLayout 的游戏识别比例白名单。

PermissionActivity 将系统前置限制与无障碍启用分开，优先打开本服务详情，不存在时回退到公开无障碍列表；跳转未离开本页面会明确提示。查询服务列表仅用于排查，不将“服务未列出”武断解释为用户拒绝。它不写系统权限、不绕过受限设置、不申请额外权限，不保证卓易通支持系统截图与点击。

新增截图回归：`run_feedback_regression.py` 可传入 `--hunt-fixtures <11张本地样本目录>`；数字输入在这部分回放中使用标注。真实本地 OCR 与界面检查使用 `python tools/run_hunt_device_check.py --adb <adb路径> --serial emulator-5580 --fixtures <本地样本目录> --output <验证目录>`，需要模拟器系统完整启动。检查包含退出后新任务设置复位、收起组件的持续状态更新和实际 OCR；不启用无障碍，也不操作游戏。

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

## 2.0.4 打熊修复

“士兵超过出征上限”使用具体错误文字和下方确定按钮共同识别，不再要求弹窗标题无遮挡。关闭后必须退回集结列表，重新识别绿色＋，再使用当前未成功发送的预设；重复弹窗不会重复退还空位或记作成功。

打熊运行时使用常驻悬浮条（2.0.5 起在空间不足时按可用高度缩小），自动避开行军、六个旗帜、报错文字、确定按钮、绿色＋及滑动路径。只在识别截图副本中遮罩自身区域；运行截图和点击不会隐藏悬浮窗。拖动或布局变化后废弃旧画面；手动框选模板时仍会暂时隐藏一次。

本次还可在专用 x86_64 Android 模拟器上直接执行 APK 中的 Android Bitmap / OpenCV 识别，不依赖应用安装服务：

```powershell
gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PemulatorTest
python tools/run_feedback_regression.py --adb <adb路径> --serial emulator-5580 --fixtures <私人样本目录> --output <本地验证目录>
```

样本目录须包含 `reported-overflow.png`、`huawei-map.png`（1152×2376）、`xiaomi-list.png`、`countdown-list.png`、`too-late.png`（均为1080×2400）和前文列出的 13 个 `video-秒数.png`（1080×2400）。测试包括报错图、常驻条遮罩、仅文字／仅按钮负例及历史录屏帧，共 17 个图像用例；最后用实际识别结果驱动关闭→返回→重新加入的状态机。日志为 `android-feedback-runtime.txt`。不会点击游戏，也不能代替用户手机上的实际触控与完整活动测试。


## 2.0.5 屏幕兼容

悬浮条同时约束宽度与可用高度，能够容纳原有大小时保持不变。系统字体较大时文字在固定控件中缩小，截图和点击不隐藏悬浮条；仍检查实测边界、系统栏、遮挡和旧画面。

内置布局保持图标等比缩放，顶部旗帜按屏幕宽度定位，底部出征和导航按底部距离定位。1080×2400 的坐标保持原值，旧录屏匹配尺寸保持原值。自动连接必须通过两张实时地图确认，不能仅凭参考图绑定。旧参考配置可适配新尺寸并保留预设开关和顺序；手动取样不会被自动替换。

`CompatibilityRuntimeCheck` 运行实际 Config、Matcher、Android Bitmap 和 OpenCV，增加华为地图、小米非熊列表、13 张旧录屏、3 个明确标记为合成的宽屏样本，并验证旧配置迁移。数字 OCR 在该入口中由样本标注代替，同时检查行军裁剪框完整包含实际数字；这不属于 ML Kit OCR 或手机触控实测。


## 2.0.6 集结倒计时与取消派兵

每条绿色＋上方的 HH:MM:SS 单独读取，不使用队长或编队名称。倒计时使用内置数字字形离线识别，必须同时匹配 6 个明确数字和 2 个冒号；无效、模糊或被遮住的读数保持未知并跳过。无需重新取样。

进入前至少保留 10 秒，并扣除截图年龄；选择预设期间继续按单调时钟计算剩余时间，不足 10 秒便返回列表，避免进入后继续过期出征。“没时间了！您的部队必须加速…”文字与左侧取消按钮联合确认后，仅取消派兵，返回列表并短暂排除刚才的行，保留本次未发送的预设、只退还一次空位。不点击蓝色确定、不使用加速道具、不修改“今日不再提示”或游戏原生自动加入。

`CountdownRuntimeCheck` 直接调用实际 CountdownVision / FeedbackVision：回放新截图中的 33、57、61 秒及旧录屏计时，验证 0/1/8/9/10/11 秒合成边界、读数遮挡、提示文字或取消按钮缺失、悬浮条避让，以及取消 → 返回 → 其他绿色＋。华为 1152×2376、1344×2772 使用明确标记为合成的比例样本；本轮倒计时识别没有使用标注值代替。它仍不能替代真机系统触控或整场活动测试。
