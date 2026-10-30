"""Capture only a test-owned solid-color window. Never reads the user's game."""
import ctypes,json,sys,time
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import customtkinter as ctk
import mss
from floating import FloatingControls
from ui_lifecycle import destroy_window
from window_io import u

app=ctk.CTk();app.overrideredirect(True);app.attributes('-topmost',True)
app.geometry('500x300+400+250');app.configure(fg_color='#1268ac')
app.update()
panel=FloatingControls(app,'截图排除测试',lambda:None,lambda:None)
panel.window.geometry('+460+300');app.update()
rect={'left':app.winfo_rootx(),'top':app.winfo_rooty(),'width':app.winfo_width(),'height':app.winfo_height()}
result={}
try:
    assert panel.excluded,'Windows did not accept WDA_EXCLUDEFROMCAPTURE'
    samples=[]
    with mss.MSS() as capture:
        for _ in range(8):
            app.update();ctypes.windll.dwmapi.DwmFlush();time.sleep(.04)
            assert u.IsWindowVisible(panel.hwnd),'Overlay became invisible'
            frame=panel.capture_call(lambda:capture.grab(rect),tuple(rect.values()))
            left,top,right,bottom=panel.bounds()
            # This point is inside the visible overlay; capture must show the solid test window.
            point=(int((left+right)/2)-rect['left'],int((top+bottom)/2)-rect['top'])
            rgb=frame.pixel(*point);samples.append(rgb)
            assert rgb==(18,104,172),(point,rgb)
            assert u.IsWindowVisible(panel.hwnd),'Overlay became invisible'
    result={'ok':True,'captures':len(samples),'overlay_visible':True,'underlay_rgb':samples[0]}
finally:
    panel.close();destroy_window(app)
print(json.dumps(result))
