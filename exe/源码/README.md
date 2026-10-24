# 无尽冬日活动正中靶心 · Windows 1.0.3

本文件夹只存放 Windows 源码。可执行文件在上一级：`../正中靶心-1.0.3.exe`。Android 源码单独放在 `apk/源码/`。

## 使用

上传活动首页和完整 12 靶子页两张全屏原图，自动识别总券数、开始按钮和 3 行 × 4 列靶子。倍率直接在游戏中设置，助手无需再次选择。首页总券数只显示、不手动填写；每轮回到首页后重新读取实际余券，不按估计倍率扣减。

出现两把钥匙并打开宝箱后，检测“点击任意位置退出”，自动返回首页并开始下一轮，无需打开全部靶子。剩 1～4 张券时，检查右侧绿色 / 紫色按钮，并结合开始按钮下方的白字 / 红字确认状态；5 倍不足时自动切到 1 倍，确认画面后再开始。首页连续两次读到 0 时停止。读数不清、按钮被遮挡或切换失败时暂停，保留进度。

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

打包输出到上一级 `exe/正中靶心-1.0.3.exe`。窗口标题及文件属性中的版本均为 1.0.3，新版标题含“自动余券”。运行中的数字代表最近一次首页读数，下次回首页更新。

主要代码：`app.py` 是界面，`vision.py` 是识别，`engine.py` 是运行流程。构建配置为 `Bullseye-Windows.spec` 和 `version_info.txt`。

## 测试资料

基础流程使用生成的图片进行测试。用户原始截图和视频不上传 GitHub；依赖这些资料的回归检查在资料缺失时明确跳过。可选本地资料位于当前文件夹下的 `tests/fixtures/home-zero.png` 和 `tests/video-review/frame-2.jpg` 至 `frame-5.jpg`。

设置 `BULLSEYE_TEST_NO_PRIVATE_FIXTURES=1` 可强制只运行不依赖私人图片的检查。模拟测试通过不等于真实游戏实跑；程序不能预测隐藏奖励位置或保证宝箱最后出现。
