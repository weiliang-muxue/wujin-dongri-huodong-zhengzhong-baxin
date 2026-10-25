"""Movable, non-activating controls; hidden during capture and game input."""
import ctypes,time
import customtkinter as ctk

class FloatingControls:
    def __init__(self,owner,title,toggle,stop):
        self.owner=owner;self.closed=False;self.paused=False
        self.window=ctk.CTkToplevel(owner);self.window.overrideredirect(True);self.window.attributes('-topmost',True)
        self.window.geometry('270x112+30+90');self.window.configure(fg_color='#e6eee9')
        handle=ctk.CTkLabel(self.window,text=title+'  ⠿ 拖动',height=38,font=('Microsoft YaHei UI',14,'bold'));handle.pack(fill='x')
        handle.bind('<ButtonPress-1>',self.begin);handle.bind('<B1-Motion>',self.drag)
        bar=ctk.CTkFrame(self.window,fg_color='transparent');bar.pack(fill='x',padx=10,pady=8)
        self.button=ctk.CTkButton(bar,text='暂停',width=115,height=42,command=toggle);self.button.pack(side='left')
        ctk.CTkButton(bar,text='停止',width=115,height=42,fg_color='#65796d',command=stop).pack(side='right')
        self.window.update_idletasks()
        from window_io import u
        self.hwnd=u.GetAncestor(self.window.winfo_id(),2)
        get=ctypes.windll.user32.GetWindowLongPtrW;get.argtypes=[ctypes.c_void_p,ctypes.c_int];get.restype=ctypes.c_ssize_t
        put=ctypes.windll.user32.SetWindowLongPtrW;put.argtypes=[ctypes.c_void_p,ctypes.c_int,ctypes.c_ssize_t];put.restype=ctypes.c_ssize_t
        put(self.hwnd,-20,get(self.hwnd,-20)|0x08000000|0x00000080)
        ctypes.windll.user32.ShowWindowAsync.argtypes=[ctypes.c_void_p,ctypes.c_int]
    def begin(self,e):self.anchor=(e.x_root-self.window.winfo_x(),e.y_root-self.window.winfo_y())
    def drag(self,e):
        x=max(0,min(e.x_root-self.anchor[0],self.window.winfo_screenwidth()-270));y=max(0,min(e.y_root-self.anchor[1],self.window.winfo_screenheight()-112));self.window.geometry(f'+{x}+{y}')
    def state(self,paused):
        if not self.closed:self.paused=paused;self.button.configure(text='继续' if paused else '暂停')
    def hidden_call(self,func,*args):
        if self.closed:raise RuntimeError('任务已停止')
        ctypes.windll.user32.ShowWindowAsync(self.hwnd,0);time.sleep(.10)
        try:
            if self.closed:raise RuntimeError('任务已停止')
            return func(*args)
        finally:
            if not self.closed:ctypes.windll.user32.ShowWindowAsync(self.hwnd,4)
    def close(self):
        self.closed=True
        try:
            from ui_lifecycle import destroy_window
            destroy_window(self.window)
        except Exception:pass
