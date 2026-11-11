# Android 源码

JDK 17、Gradle 8.9、Android SDK 35 extension 15、Build Tools 35.0.0。设置 JAVA_HOME 与 ANDROID_HOME，或在不提交的 local.properties 中配置 sdk.dir。

构建：`gradlew.bat :app:testEngine :app:assembleRelease :app:lintRelease`。

将 app/build/outputs/apk/release/app-release.apk 复制到上一级，命名为 无尽冬日助手-2.1.10.apk。

检查成品使用 `python tools/verify_android_package.py ../无尽冬日助手-2.1.10.apk --version 2.1.10`。

2.1.9 的 `VisualLocator.findAdaptive` 先按图像金字塔生成候选，在原图中按像素尺寸细化，再以调用方的置信度接受结果。比例间隔随模板短边／长边之比调整，长条文字使用更密的比例候选，避免细化前就漏掉正确尺寸。候选范围包含降采样取整误差。同一位置的尺寸候选会合并，不同位置继续接受唯一性检查。按钮颜色按匹配出的实际尺寸核对。打熊、巨兽、野兽、靶心共用该算法，靶心的目标比对区域也随布局比例变化。靶心页面的多个原比例标记已满足几何一致性时直接复用定位结果，只有不一致或不足时才搜索缩放比例，避免普通帧反复扫描所有比例。

`HuntController` 在点击前校验截图宽高、屏幕旋转、截图时效和浮窗遮挡。结束任务时清除拖动、窗口及任务所有权状态，旧浮窗回调校验所属流程。`OverlayFold.updateStatus` 在收起后显示活动名和当前状态，固定浮标尺寸，点按展开。

靶盘红色连通区域若被截图边缘裁切，不使用其包围框中心，避免放大时靶心偏移。完整内圈或已定位布局提供坐标。`--bullseye-zoom-only` 可在保留普通布局通过记录后，单独接续缩放、部分靶盘与负例回归。

Release 构建自动依赖 `testEngine`。图像回放使用 `run_feedback_regression.py`，其中 `--suite scale` 验证缩放、重复目标和颜色，`--suite restart --restart-fixtures <目录>` 回放结束重开录屏，`--suite overlay` 检查浮窗状态与文字边界。图像组需要本地测试素材和专用模拟器，执行结果记录在项目验证文档中。

`core`、`session`、`presets`、`scale`、`overlay` 组可直接使用源码内的素材。构建带 `-PemulatorTest` 的 Debug 与 AndroidTest APK 后，为脚本传入 `--adb <路径> --serial emulator-<端口> --output <输出目录>` 和所需 `--suite`。私人截图回放组另传素材目录。软件模拟器可通过 `--opencv-threads 1` 调整测试进程线程数，日志记录所用设置。

### 连续缩放回归（2.1.10）

`tools/run_scale_sweep.ps1` 直接编译生产用 `VisualLocator`、`ReferenceAnchors`、`LayoutTransform`，没有另写一套识别算法。使用 JDK 17 和与 APK 同版本的 [OpenCV 4.12.0 Windows 官方 Java 库](https://github.com/opencv/opencv/releases/tag/4.12.0)，将 `OpenCvJavaDir` 指向解压后的 `opencv/build/java`，输出目录必须显式指定。例如在本工程目录运行：

```powershell
tools/run_scale_sweep.ps1 -JavaHome <JDK目录> -OpenCvJavaDir <OpenCV目录> -OutputDir <本地验证目录> -Suite dense
```

`dense` 对 11 种源码内素材逐像素扫描约 80%～120% 的宽度，并加入 67%、73%、127%、133%、146% 比例，分别使用 AREA 和 LINEAR 插值，共 856 项。`boundary` 检查贴四边的完整目标与半遮挡负例（60 项）；`colors` 检查 20～42 像素宽的绿色／灰色按钮（46 项）。这三组不需要私人截图。

`-Suite all -Fixtures <私人素材目录>` 还加入 25 项实际页面缩放偏移及 35 项靶心锚点检查，总共 1022 项。目录需包含 `giant_search.png`、`giant_formation.png`、`beast_detail.png`、`beast_formation.png`、`huawei-bear-list.png`、`bullseye-home.png`、`bullseye-board.png`；图片沿用已有私人回放素材，不提交 Git。逐项结果、坐标误差与耗时写入 TSV。任何失败均使脚本退出失败；保留失败记录后修复，不能只挑通过的比例。

2.1.9 在该扫描中有 10 项失败：缩小的编队旗帜和“等级”标记被 12 像素短边下限排除。2.1.10 将最小短边改为 8 像素，未降低最终置信度。Android `--suite scale` 同时加入这 10 项，合计 50 项原生 Bitmap/OpenCV 检查。桌面扫描补充密集比例覆盖，不能替代 Android 测试；以上图像变换也不代表小米、华为手机的真实触控、性能或整场流程已经验证。

布局回放可用 `--adaptive-start-index` / `--adaptive-end-index` 按 `AdaptiveRuntimeCheck` 中的页面顺序分组，结束索引不包含在内。`--adaptive-hunt-only` 只检查选中的狩猎页；`--adaptive-bear-only` 使用打熊的三个页面编号（0 华为列表、1 历史列表、2 出征页），不传索引时检查全部三页。每页均检查左右两个偏移方向。各组使用独立模拟器与输出目录，最后按页面和偏移方向合并记录，重复的负例只计一次。

`--suite huawei-bear --huawei-fixtures <目录>` 可单独复测华为反馈的巨熊列表，检查长条名称、绿色／灰色加入按钮和被裁切的倒计时；该用例也包含在完整 `huawei` 组中。

华为组支持 `--huawei-start-index` / `--huawei-end-index`，按 `HuaweiRuntimeCheck` 的 0～19 号用例分段（结束索引不包含）。0～9 为详情及缺失标记，10～17 为出征及编队，18 为地图，19 为巨熊列表。每段输出起止编号及实际通过数，完整结果必须覆盖全部 20 项。

一个 launcher：ModeActivity；一个无障碍服务：BearService。cn.local.bullseye.GameService 和 HuntController 是该服务内的模块，不是其他 Android Service。ModeGate、运行代次和输入前检查共同限制旧任务回调。返回活动选择会停止所有模块。

2.1.8 的 `ForegroundWindows` 在缺少应用窗口、活动根属于助手时返回未知。`HuntController` 和靶心复用 `ForegroundGate`，未知前台等待，明确切换应用超过短暂宽限后暂停，每次输入仍要求游戏位于前台。靶心为截图和手势分别分配请求编号，截图回调等待 5 秒、手势回调等待 3 秒后提供恢复入口，旧回调无法释放新请求。截图处理期间保留资源所有权，4 秒画面时效使用包含休眠时间的单调时钟。`Config.matchesReferences` 校验保存设置与参考图，当前屏幕的点击位置继续由 `LiveLayout` 重新定位。

2.1.7 在 `HuntFlow.suspend` 中记录已发送动作，恢复时根据当前页面继续。出征进入结果确认，预设重新选择，未发送的罐头动作退回预留额度。`HuntController` 复用相同参数的任务，开始操作具有幂等性，准备代次使手动暂停或结束能取消延迟启动。打熊的手势超时保留发送证据，截图回调同时校验请求所属代次。`HuntActivity` 通过 Bundle 保存当前参数。

`run_feedback_regression.py --suite session` 执行 Bundle 状态往返、狩猎控制器方法和靶心回调所有权测试。完整 `Activity.recreate()` 回归在 `HuntDeviceCheck` 中，需要可启动 Activity 的模拟器框架或测试手机。

2.1.6 的 `PresetPicker` 共用于打熊、巨兽、野兽的主界面和游戏内菜单。`PresetOrder` 按所选编号循环，巨兽、野兽的 `Options` 默认构造仍选中全部六个编队。打熊继续读写旧的 `enabled0`～`enabled5` 配置，重新选择时从最小编号开始，改变勾选会废弃旧的暂停步骤。编队选择与活动队数分别传入流程。

2.1.5 的 `TaskChooser` 由这个无障碍服务显示游戏内任务选择与狩猎参数弹窗。四个活动通过 `OverlayFold.Controls` 共享尺寸、按钮和状态行，通过 `OverlayFold` 共享收起、展开和任务切换入口。切换前暂停旧任务，确认新任务后撤销旧任务回调，等游戏窗口恢复后启动。靶心首次仍需在助手中保存两张参考截图。

`MarchFlow` 会立即跳过已识别的短倒计时并向下翻页。空位读数过期时，先继续查找，找到可加入队伍后再核对空位。`BearOverlayGuard.nextPagePath` 只生成查看后续内容的滑动方向，并避开浮窗范围。待发送动作被浮窗挡住时撤销该动作，收起、重新截图后再决定下一步。手动暂停保留流程，提交中的出征先保留占用，恢复后检查结果。窗口身份暂时未知时等待新观察。

打熊实际流程在 MarchFlow.java。2.0.4 仅更新 Android，电脑版保持 2.0.3。BearFlow.java 只保留页面和动作数据类型。正中靶心保留实际券数、低券切换、结算退出；正中靶心悬浮条保持显示；仅在识别用的截图副本里遮罩悬浮条。固定尺寸避免状态更新时跳动，拖动时停发点击，落点自动避开券数、开始按钮、靶子、倍率及结算区域；旧位置采集的画面不再用于点击。

Release 开启 R8 与资源裁剪，仅打包 ARM64 和 ARMv7，四套流程共用 OpenCV / ML Kit。应用合并清单移除联网权限。

HuntFlow 管理巨兽集结、野兽攻击、1～6 活动队数、预设顺序及库存罐头额度；HuntVision 定位游戏控件并读取等级、行军和库存；HuntController 在同一无障碍服务中运行。HuntActivity 的设置仅保留在本次任务内。OverlayFold 为四个活动提供手动收起、点击展开，状态更新不会重新挂载窗口。

2.1.4 移除巨兽／野兽的运行分钟设置与总时长截止。任务使用现有体力，按需每批补 4 个库存罐头；最后一批补齐剩余额度，用满额度后继续消耗剩余体力，下一次体力不足时停止。暂停保留已用额度，结束／退出后重新设置。截图时效、动作响应和异常恢复的短超时仍保留。

`MarchRowVision` 共用 `VisualLocator`，在已找到的行军标题下定位资源、集结、返程图标。六个 24×24 局部素材仅包含游戏通用图标；完整私人截图不入库。已识别的资源类型不会因倒计时开始、重置或误读而变成本次狩猎队伍。图标行完整时倒计时可未知；未知图标仍走原有倒计时读法，部分列表和遮挡不释放额度。这也处理总数始终为 4/6、巨兽回城后新增采集行的情况。`HuntReturnRuntimeCheck` 使用私人小米截图验证两种活动的回城补队、六种尺寸、倒计时遮挡和行军面板位移；图像回放仍只标注总行军数，不代替真实手势与 ML Kit 测试。

## 公共识别逻辑与新增活动

`cn.local.automation` 不依赖活动名称，供现有四个活动与后续活动复用：

- `VisualLocator` 负责模板匹配、边界裁剪、重复候选去重和唯一性检查；返回本帧的实际中心坐标与匹配分数。全色块模板被拒绝，弱匹配只在原分数接近阈值时尝试轻微平滑。
- `LayoutTransform` 根据两个已观察到的标记求缩放和平移，拒绝旋转、过近、矛盾或非法坐标。
- `ReferenceAnchors` 从两张参考图选取相同且有辨识度的局部标记，排除券数等变化区域，至少三个标记一致才接受当前布局。

打熊的 `Matcher`、公共弹窗识别 `FeedbackVision`、巨兽/野兽的 `HuntVision` 和正中靶心的 `LiveLayout` 均调用 `VisualLocator`。搜索范围只用于排除不相关内容，识别成功后使用匹配到的坐标；等级、倒计时、旗帜跟随对应标记定位。`LiveLayout` 每帧重新定位靶心页面标记和开始按钮，完整靶盘可重新建立位置，部分靶子消失后保留编号；票数及结算区域随布局变化，浮窗避让区域同步更新。已有手动取样保留。

新增活动应增加页面标记和活动状态机，复用上述定位、`BearService` 的单一无障碍服务、`ForegroundProbe`、`ModeGate` 与 `OverlayFold`。识别层只输出本帧观察结果和实际坐标，不发手势；状态机决定下一步；控制器检查前台、截图代次、时效和浮窗遮挡后发送。不要在新活动里复制一套固定屏幕点击坐标，也不要让某个活动的特殊弹窗或罐头额度混入公共匹配器。

回归需包括原始画面、尺寸变化、内容偏移、关键标记被遮挡和相似按钮负例。`run_feedback_regression.py --adaptive-layouts` 增加狩猎/打熊偏移回放；`--bullseye-fixtures <目录>` 接收 `bullseye-home.png`、`bullseye-board.png`，验证靶心标记、开始按钮、靶盘和余券区域的实时坐标。`--suite` 可选择已有测试组。参考图、私人录屏和完整游戏截图不进入 Git。

2.1.3 的目标详情用橙色集结/攻击按钮和相对位置上的“等级”识别，不依赖背后的 Search 标签；预设模板仅保留旗帜图案。`ForegroundProbe` 根据交互窗口类型区分自身无障碍浮窗与实际应用/系统窗口，不缓存游戏包名来推断前台。`OverlayRecovery` 对定位和截图布局失效统一限时：1200 ms 后收起，6000 ms 仍不稳定则暂停；成功处理新画面或用户拖动时重置。`PermissionActivity` 每 500 ms 刷新系统启用状态与本地服务连接状态，不写系统设置。

本次私有原图回放：`run_feedback_regression.py --huawei-fixtures <目录>`（连同原 `--fixtures`）。目录包括 `huawei-giant.png`、`huawei-formation.png`、`huawei-bear-list.png`、`huawei-giant-video.png` 和 `huawei-map-video.png`。测试覆盖详情遮住 Search、改名/遮住编队名字、直接出征、等级不符停止、关键标记缺失负例和录屏地图。完整截图不进入 Git；缩放变体和被压缩录屏重采样明确属于合成回放，不当作真机点击验证。

2.1.2 中打熊、巨兽、野兽选好预设后直接点击识别到的出征按钮，不再读取或判断兵力数字。保留队列上限、目标识别、打熊少于 10 秒跳过、游戏错误弹窗恢复和出征结果确认。`run_feedback_regression.py --dispatch-fixtures <目录>` 接收私人原图 `reported-deploy.png`（还需原有 `--fixtures` 和 `--hunt-fixtures`），在 Android Bitmap / OpenCV 中回放出征页和遮住兵力数字的样本；出征页一旦调用数字 OCR，测试立即失败。

2.1.1 的 HuntMarches 跟踪本次派出的队伍：已存在的采集等队伍只占总行军空位，不占本次活动额度。使用图标指纹和倒计时结束时间匹配队伍，列表重排不改变归属；多个行的归属不明确时保留最大的可能任务队数，避免多派。缺失或部分识别的行军列表不用于释放额度。HuntGlyphs 单独读取“等级”后的蓝色数字，保留仅数字区域的 OCR 回退，避免整行中文干扰。

## 精简图像库

语言保持 Java。2.1.1 按[OpenCV 官方构建选项](https://docs.opencv.org/4.12.0/db/d05/tutorial_config_reference.html)只构建 `core,imgproc,java`，去掉本应用不用的算法、视频、网络模型等模块；保留两个手机 ABI、离线 ML Kit 和原有功能。使用官方 4.12.0 源码提交 `49486f61fb25722cbcf586b7f4320921d46fb38e`、NDK r30、CMake 3.31.6、Ninja 1.11.1.3。具体参数与库 SHA-256 写入生成的本地 `.build.json`。

先从官方渠道准备 OpenCV 源码、NDK 和原版 Maven AAR，设置 JDK/SDK 环境，再运行：

```text
python tools/build_minimal_opencv.py --source <opencv源码目录> --ndk <NDK目录> --original-aar <opencv-4.12.0.aar> --work <本地构建目录> --output app/libs/opencv-minimal-4.12.0.aar --cmake <cmake路径> --ninja <ninja路径>
```

打包脚本使用 NDK 的 `llvm-strip --strip-unneeded` 清除本地库调试符号，在副本上处理，不修改 NDK 或原始编译输出。生成的 AAR 不上传 Git。存在该文件时 Gradle 自动使用精简库；缺失时回退到原版 Maven 依赖，源码仍可直接构建，但 APK 会更大。Release 仍仅包含 ARM64/ARMv7；x86_64 只用于 `-PemulatorTest`。精简库须运行原报错、倒计时、屏幕兼容及狩猎图像回归。

`run_feedback_regression.py` 新增 `--reported-fixtures <目录>`：目录包含私人原图 `reported-map.png` 和 `reported-level.png`，另需 `--hunt-fixtures` 提供旧巨兽/野兽原图。HuntReportedRuntimeCheck 直接读取原图等级字形、四个采集队倒计时和新增集结队倒计时，再驱动状态机；仅总行军数字 4/6、1/6 使用标注，等级不允许用标注替代。全图、录屏和测试 APK 不提交仓库。

HuntOverlayGuard 使用当前窗口系统栏/刘海边距，在识别区域之外选择浮窗位置。BearService / GameService 放不下展开浮窗时尝试收起后重新避让；收起操作会使旧截图失效。2.1.9 的 ReferenceLayout 接受满足最小尺寸的竖屏画面，仍需通过实际页面识别后才能操作。宽竖屏窗口几何检查不等于对应机型的游戏流程已通过实机验证。

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
