# 无尽冬日活动正中靶心

“正中靶心”活动的本地截图识别与操作助手，当前版本为 **Windows 1.0.1 / Android 1.0.2**。Windows EXE 和 Android APK 是两个独立程序。

## 功能

- 上传活动首页和完整靶子页两张全屏原图，自动定位开始按钮、总券数和固定的 **3 行 × 4 列、12 个靶子**。
- 用户选择 1 倍或 5 倍，并把游戏内倍率设成相同值。程序不识别、不点击倍率按钮。
- 首页可以开始新一轮，完整靶子页可以接着当前轮执行。
- 宝箱打开后，识别底部“点击任意位置退出”，自动返回首页并开始下一轮；无需打开所有靶子。
- 剩余券数不足所选倍率时停止提示，用户可改为 1 倍后继续使用余券。
- 支持暂停、继续和停止。随机选择未打开的靶子，不预测隐藏奖励位置。

## 使用

两张截图必须来自同一屏幕，保持分辨率、方向、游戏窗口位置和缩放一致，不要裁剪。

Windows 直接操作电脑上的微信小程序或模拟器，不通过 ADB 连接手机。开始后不要操作鼠标或移动游戏窗口。**F8** 暂停/继续，**Esc** 停止。

Android 直接安装到手机，要求 **Android 11 及以上**。用户在系统辅助功能中开启“靶心助手”，通过悬浮条暂停、继续或停止。截图与识别在本地完成。

Android 1.0.2 的主界面和悬浮窗都会显示“靶心助手 1.0.2 · 手动倍率”。安装包使用带版本号的文件名，避免与旧版同名 APK 混淆。

## 源码结构

- `windows/app.py`：Windows 界面、截图导入和快捷键。
- `windows/vision.py`：总券数、按钮、靶子和结算退出提示识别。
- `windows/engine.py`：Windows 自动运行流程。
- `windows/test_core.py`：识别及自动运行回归检查。
- `android/app/src/main/`：Android 界面、ML Kit 识别和辅助功能服务。
- `Bullseye-Windows.spec`：EXE 打包配置。

## Windows 开发与打包

环境：Windows 10/11 64 位、Python 3.13。

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r windows/requirements.txt
.\.venv\Scripts\python.exe windows/app.py
.\.venv\Scripts\python.exe -m unittest discover -s windows -p test_core.py -v
.\.venv\Scripts\python.exe -m PyInstaller --noconfirm --distpath dist --workpath tools/pyinstaller-build Bullseye-Windows.spec
```

输出：`dist/Bullseye-Windows.exe`。

## Android 开发与打包

环境：JDK 17、Android SDK `platforms;android-35-ext15` 和 `build-tools;35.0.0`。Gradle Wrapper 使用 Gradle 8.9。

在本地创建 `android/local.properties`，填写 SDK 路径，例如：

```properties
sdk.dir=C:/Android/Sdk
```

```powershell
cd android
.\gradlew.bat :app:assembleRelease :app:lintRelease
```

输出：`android/app/build/outputs/apk/release/app-release.apk`。当前测试版本使用本机 Android debug 签名；继续更新已安装的测试版本需要保留同一签名密钥。签名文件不在仓库中。

发包前检查 APK 内部不存在旧倍率识别提示，并确认可见版本标识：

```powershell
python tools/verify_android_package.py android/app/build/outputs/apk/release/app-release.apk --version 1.0.2
```

## 验证与测试资料

已验证打包后的 Windows 截图识别、模拟整轮运行、手动倍率、暂停/停止、结算退出和自动开始下一轮。Android 已通过编译、静态检查与 APK 签名检查；最新修改尚未进行手机整轮实测。

用户原始截图、视频、视频帧、账号信息和本地构建缓存不上传。基础流程测试可使用代码生成的图运行。依赖真实截图的回归测试在资料缺失时明确跳过；如需本地运行这些测试，可放置：

- `tests/fixtures/home-zero.png`：开发时使用的首页截图样本。
- `tests/video-review/frame-2.jpg` 至 `frame-5.jpg`：开发时使用的结算视频帧。

设置环境变量 `BULLSEYE_TEST_NO_PRIVATE_FIXTURES=1` 可以强制只运行不依赖私人资料的检查。
