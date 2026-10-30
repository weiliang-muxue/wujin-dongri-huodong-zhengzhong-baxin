"""Construct both native screens, switch away, and verify workers are stopped. No game input."""
import sys,time,threading,tempfile
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from app import App
from bear_app import BearApp
from floating import FloatingControls

class Worker:
    stopped=False
    def __init__(self):self.gate=threading.Event();self.gate.set()
    def stop(self):self.stopped=True

with tempfile.TemporaryDirectory(prefix='wujindongri-gui-smoke-') as data, \
     patch('app.DATA',Path(data)),patch('bear_app.DATA',Path(data)):
    for cls in (BearApp,App):
        app=cls();errors=[]
        app.report_callback_exception=lambda *args:errors.append(str(args))
        def check():
            try:
                app.update_idletasks();assert app.winfo_width()>=900
                p=FloatingControls(app,'测试',lambda:None,lambda:None);app.panel=p
                p.state(True);assert p.button.cget('text')=='继续'
                worker=Worker();app.runner=worker;p.window.update_idletasks()
                size=(p.window.winfo_width(),p.window.winfo_height())
                def report(state,message):
                    if cls is App:app.events.put(('report',(state,message,3,2)))
                    else:app.events.put(('log',('等待悬浮条：' if state=='waiting' else '')+message))
                    app.poll();p.window.update_idletasks()
                    assert (p.window.winfo_width(),p.window.winfo_height())==size
                for reason,short in (
                    ('请把悬浮条拖到游戏画面外，移开后继续','请拖到游戏画面外'),
                    ('请把悬浮条移开目标按钮，移开后继续','请移开悬浮条 · 拖动'),
                    ('正在移动悬浮条，松手后重新识别','移动中，松手后继续')):
                    report('waiting',reason);assert p.handle.cget('text')==short
                    report('running','正在检查画面');assert p.handle.cget('text')==short
                    report('running','已恢复，继续当前进度' if cls is App else '已恢复：继续当前进度')
                    assert p.handle.cget('text')==p.title_text
                if hasattr(app,'toggle_options'):app.toggle_options();app.update_idletasks();assert app.options.winfo_ismapped()
                app.switch_mode();assert app.return_to_chooser and worker.stopped
            except Exception as e:errors.append(str(e));app.destroy()
        app.after(300,check);app.mainloop()
        assert not errors,(cls.__name__,errors)
        print('PASS',cls.__name__,'construct/layout/floating/pause-label/poll-wait-messages/fixed-size/restore-title/switch-stop')
