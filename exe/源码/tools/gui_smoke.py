"""Construct both native screens, switch away, and verify workers are stopped. No game input."""
import sys,time
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from app import App
from bear_app import BearApp
from floating import FloatingControls

class Worker:
    stopped=False
    def stop(self):self.stopped=True

for cls in (BearApp,App):
    app=cls();errors=[]
    app.report_callback_exception=lambda *args:errors.append(str(args))
    def check():
        try:
            app.update_idletasks();assert app.winfo_width()>=900
            p=FloatingControls(app,'测试',lambda:None,lambda:None);p.state(True);assert p.button.cget('text')=='继续';p.close()
            if hasattr(app,'toggle_options'):app.toggle_options();app.update_idletasks();assert app.options.winfo_ismapped()
            worker=Worker();app.runner=worker;app.switch_mode();assert app.return_to_chooser and worker.stopped
        except Exception as e:errors.append(str(e));app.destroy()
    app.after(300,check);app.mainloop()
    assert not errors,(cls.__name__,errors)
    print('PASS',cls.__name__,'construct/layout/floating/pause-label/switch-stop')
