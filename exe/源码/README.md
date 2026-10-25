# Windows 源码

Python 3.13 x64。安装 `requirements.txt` 后运行 `python main.py`，不要单独运行旧模块入口。

构建：`python -m PyInstaller --noconfirm --distpath .. --workpath build/pyinstaller Wujindongri.spec`。

输出一个 `../无尽冬日助手-2.0.1.exe`。两个活动共用 OpenCV、ONNX Runtime 和 OCR 模型。构建不收集 ONNX 开发工具、训练工具或 FFmpeg；实时截图使用 MSS。成品不需要 Python、Java 或 ADB。

验证命令：

- `python -m unittest test_core test_bear test_lifecycle -v`
- `python tools/gui_smoke.py`
- `python tools/check_parity.py <JDK17目录>`
- `python main.py --self-test build/selftest.json`
- `python tools/check_recording.py <本地录屏.mp4> <输出.json>`

跨端对照需 JDK 17，运行 35 个虚拟半小时场景，对比 Android 实际状态机和 Python 的 90,055 次决策。录屏不属于仓库。正中靶心可选图片测试通过环境变量 BULLSEYE_TEST_FIXTURES 指定，缺少私有样本时明确跳过。

打熊由 bear_flow.py 决策，bear_vision.py 识别，window_io.py 在前台窗口与位置校验后操作。暂停、停止、窗口遮挡、超时会阻止后续输入。engine.py 是正中靶心流程。

用户配置位于 %LOCALAPPDATA%/Wujindongri/bear，正中靶心沿用 %LOCALAPPDATA%/BullseyeAssistant。不把这些目录放入仓库。
