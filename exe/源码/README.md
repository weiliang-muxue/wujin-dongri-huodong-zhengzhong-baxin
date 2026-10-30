# Windows 源码

Python 3.13 x64。安装 `requirements.txt` 后运行 `python main.py`，不要单独运行旧模块入口。

构建：`python -m PyInstaller --noconfirm --distpath .. --workpath build/pyinstaller Wujindongri.spec`。

输出一个 `../无尽冬日助手-2.0.3.exe`。两个活动共用 OpenCV、ONNX Runtime 和 OCR 模型。构建不收集 ONNX 开发工具、训练工具或 FFmpeg；实时截图使用 MSS。成品不需要 Python、Java 或 ADB。

运行中悬浮条持续显示，Windows 10 2004 及以上通过窗口捕获排除避免干扰识别。系统不支持或排除失败时，需将悬浮条移到游戏识别区域外。输入前保留前台与遮挡检查，不会透过悬浮条点击。

拖动悬浮条时暂缓输入，松手后重新截图，拖动前或拖动期间的旧画面不能继续触发点击。正中靶心保留当前轮次、已命中靶子和待确认目标；打熊保留当前批次及预设顺序。悬浮条挡住目标按钮会等待，移开后继续；系统无法排除悬浮条时，会等待它移出整个识别区域再重拍，不结束当前轮次。已提交的出征不会因随后拖动而撤销重发；已开始的滚动会释放鼠标，再根据新画面决定后续动作。

等待原因会显示在悬浮条原有标题处，如“请移开悬浮条 · 拖动”“请拖到游戏画面外”“移动中，松手后继续”。提示去重且不改变窗口尺寸，恢复后还原活动标题；完整原因保留在主界面状态或日志中。

验证命令：

- `python -m unittest test_core test_bear test_bear_runner test_lifecycle test_floating -v`
- `python tools/gui_smoke.py`（检查两个界面的实际事件队列、悬浮条短提示、固定尺寸、恢复标题与切换停止）
- `python tools/verify_overlay_capture.py`（只截取测试程序自己创建的纯色窗口，检查悬浮条持续可见且截图中能读到底层颜色）
- `python tools/check_parity.py <JDK17目录>`
- `python main.py --self-test build/selftest.json`
- `python tools/check_recording.py <本地录屏.mp4> <输出.json>`

跨端对照需 JDK 17，运行 35 个虚拟半小时场景，对比 Android 实际状态机和 Python 的 90,055 次决策。录屏不属于仓库。正中靶心可选图片测试通过环境变量 BULLSEYE_TEST_FIXTURES 指定，缺少私有样本时明确跳过。

打熊由 bear_flow.py 决策，bear_vision.py 识别，window_io.py 在前台窗口与位置校验后操作。暂停、停止、窗口遮挡、超时会阻止后续输入。engine.py 是正中靶心流程。

用户配置位于 %LOCALAPPDATA%/Wujindongri/bear，正中靶心沿用 %LOCALAPPDATA%/BullseyeAssistant。不把这些目录放入仓库。
