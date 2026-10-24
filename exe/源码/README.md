# 无尽冬日活动正中靶心 · Windows 1.0.3

本文件夹只存放 Windows 源码。可执行文件在上一级：`../正中靶心-1.0.3.exe`。Android 源码单独放在 `apk/源码/`。

## 使用

上传活动首页和完整 12 靶子页两张全屏原图，自动识别总券数、开始按钮和 3 行 × 4 列靶子。手动选择 1 倍或 5 倍，并把游戏内倍率设为相同值。程序不识别、不点击倍率按钮。

出现两把钥匙并打开宝箱后，检测“点击任意位置退出”，自动返回首页并开始下一轮，无需打开全部靶子。券数不足所选倍率时停止提示，可手动改为 1 倍后继续。

直接操作电脑微信小程序或模拟器，不通过 ADB 连接手机。运行时不要操作鼠标或移动窗口。**F8** 暂停/继续，**Esc** 停止。

## 开发和打包

环境：Windows 10/11 64 位、Python 3.13。以下命令在当前“源码”文件夹执行：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe app.py
.\.venv\Scripts\python.exe -m unittest discover -s . -p test_core.py -v
.\.venv\Scripts\python.exe -m PyInstaller --noconfirm --distpath .. --workpath .build Bullseye-Windows.spec
```

打包输出到上一级 `exe/正中靶心-1.0.3.exe`。窗口标题及文件属性中的版本均为 1.0.3。

主要代码：`app.py` 是界面，`vision.py` 是识别，`engine.py` 是运行流程。构建配置为 `Bullseye-Windows.spec` 和 `version_info.txt`。

## 测试资料

基础流程使用生成的图片进行测试。用户原始截图和视频不上传 GitHub；依赖这些资料的回归检查在资料缺失时明确跳过。可选本地资料位于当前文件夹下的 `tests/fixtures/home-zero.png` 和 `tests/video-review/frame-2.jpg` 至 `frame-5.jpg`。

设置 `BULLSEYE_TEST_NO_PRIVATE_FIXTURES=1` 可强制只运行不依赖私人图片的检查。模拟测试通过不等于真实游戏实跑；程序不能预测隐藏奖励位置或保证宝箱最后出现。
