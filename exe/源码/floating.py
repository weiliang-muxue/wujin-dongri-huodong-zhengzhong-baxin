"""Persistent controls excluded from capture, with normal input-occlusion checks."""
import ctypes,sys
import customtkinter as ctk
from frame_guard import RetryFrame

class FloatingControls:
    def __init__(self,owner,title,toggle,stop):
        self.owner=owner;self.closed=False;self.paused=False;self.dragging=False
        self.revision=0;self.frame_revision=None
        self.window=ctk.CTkToplevel(owner);self.window.overrideredirect(True);self.window.attributes('-topmost',True)
        self.window.geometry('270x112+30+90');self.window.configure(fg_color='#e6eee9')
        self.title_text=title+'  ⠿ 拖动';self.display_text=self.title_text
        handle=ctk.CTkLabel(self.window,text=self.title_text,height=38,font=('Microsoft YaHei UI',14,'bold'));handle.pack(fill='x');self.handle=handle
        handle.bind('<ButtonPress-1>',self.begin);handle.bind('<B1-Motion>',self.drag);handle.bind('<ButtonRelease-1>',self.end)
        bar=ctk.CTkFrame(self.window,fg_color='transparent');bar.pack(fill='x',padx=10,pady=8)
        self.button=ctk.CTkButton(bar,text='暂停',width=115,height=42,command=toggle);self.button.pack(side='left')
        ctk.CTkButton(bar,text='停止',width=115,height=42,fg_color='#65796d',command=stop).pack(side='right')
        self.window.update_idletasks()
        from window_io import u
        self.hwnd=u.GetAncestor(self.window.winfo_id(),2)
        get=ctypes.windll.user32.GetWindowLongPtrW;get.argtypes=[ctypes.c_void_p,ctypes.c_int];get.restype=ctypes.c_ssize_t
        put=ctypes.windll.user32.SetWindowLongPtrW;put.argtypes=[ctypes.c_void_p,ctypes.c_int,ctypes.c_ssize_t];put.restype=ctypes.c_ssize_t
        put(self.hwnd,-20,get(self.hwnd,-20)|0x08000000|0x00000080)
        u.SetWindowDisplayAffinity.argtypes=[ctypes.c_void_p,ctypes.c_ulong];u.SetWindowDisplayAffinity.restype=ctypes.c_bool
        u.GetWindowDisplayAffinity.argtypes=[ctypes.c_void_p,ctypes.POINTER(ctypes.c_ulong)];u.GetWindowDisplayAffinity.restype=ctypes.c_bool
        self.excluded=False
        if sys.getwindowsversion().build>=19041:
            affinity=ctypes.c_ulong()
            self.excluded=bool(u.SetWindowDisplayAffinity(self.hwnd,0x11) and u.GetWindowDisplayAffinity(self.hwnd,ctypes.byref(affinity)) and affinity.value==0x11)
    def begin(self,e):
        self.dragging=True;self.revision+=1;self.frame_revision=None
        self.anchor=(e.x_root-self.window.winfo_x(),e.y_root-self.window.winfo_y())
    def drag(self,e):
        x=max(0,min(e.x_root-self.anchor[0],self.window.winfo_screenwidth()-270));y=max(0,min(e.y_root-self.anchor[1],self.window.winfo_screenheight()-112));self.window.geometry(f'+{x}+{y}')
    def end(self,e):
        self.revision+=1;self.frame_revision=None;self.dragging=False
    def state(self,paused):
        if not self.closed:self.paused=paused;self.button.configure(text='继续' if paused else '暂停')
    def message(self,reason=''):
        if self.closed:return
        if not reason:text=self.title_text
        elif '游戏画面外' in reason:text='请拖到游戏画面外'
        elif '移开' in reason:text='请移开悬浮条 · 拖动'
        elif '松手' in reason:text='移动中，松手后继续'
        else:text='重新识别，稍候继续'
        if text!=self.display_text:self.display_text=text;self.handle.configure(text=text)
    def bounds(self):
        from window_io import u,W
        r=W.RECT()
        if not u.GetWindowRect(self.hwnd,ctypes.byref(r)):raise RuntimeError('悬浮窗已关闭')
        return r.left,r.top,r.right,r.bottom
    def check_open(self):
        if self.closed:raise RuntimeError('任务已停止')
        if self.dragging:raise RetryFrame('正在移动悬浮条，松手后重新识别')
    def check_frame(self):
        self.check_open()
        if self.frame_revision!=self.revision:raise RetryFrame('悬浮条已移动，重新识别当前画面')
    def check_input_window(self,hwnd):
        self.check_frame()
        if hwnd==self.hwnd:raise RetryFrame('请把悬浮条移开目标按钮，移开后继续')
    def capture_call(self,func,region):
        self.check_open()
        revision=self.revision
        # Older Windows cannot exclude an overlay. Keep it visible outside the game ROI.
        if not self.excluded:
            left,top,right,bottom=self.bounds();x,y,w,h=region() if callable(region) else region
            if left<x+w and right>x and top<y+h and bottom>y:
                raise RetryFrame('请把悬浮条拖到游戏画面外，移开后继续')
        result=func();self.check_open()
        if revision!=self.revision:raise RetryFrame('截图期间悬浮条已移动，重新识别')
        self.frame_revision=revision
        return result
    def action_call(self,func,*args):
        self.check_frame()
        # WindowIO / App.tap check WindowFromPoint before input, so a visible panel
        # covering a game button cannot receive an automated click.
        return func(*args)
    def close(self):
        self.closed=True
        try:
            from ui_lifecycle import destroy_window
            destroy_window(self.window)
        except Exception:pass
