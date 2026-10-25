"""Windows client-area capture. Always re-check foreground and geometry before input."""
import ctypes as C
from ctypes import wintypes as W
import os, time
from PIL import Image

u=C.windll.user32
u.GetForegroundWindow.restype=W.HWND
u.SetForegroundWindow.argtypes=[W.HWND];u.SetForegroundWindow.restype=W.BOOL
u.WindowFromPoint.argtypes=[W.POINT];u.WindowFromPoint.restype=W.HWND
u.GetWindowRect.argtypes=[W.HWND,C.POINTER(W.RECT)]
u.GetAncestor.argtypes=[W.HWND,W.UINT];u.GetAncestor.restype=W.HWND
u.IsWindow.argtypes=[W.HWND];u.IsIconic.argtypes=[W.HWND]
u.GetClientRect.argtypes=[W.HWND,C.POINTER(W.RECT)]
u.ClientToScreen.argtypes=[W.HWND,C.POINTER(W.POINT)]
u.GetWindowThreadProcessId.argtypes=[W.HWND,C.POINTER(W.DWORD)]
u.GetWindowTextW.argtypes=[W.HWND,W.LPWSTR,C.c_int]
u.IsWindowVisible.argtypes=[W.HWND]

def foreground():return int(u.GetForegroundWindow() or 0)

def windows():
    result=[]
    callback=C.WINFUNCTYPE(W.BOOL,W.HWND,W.LPARAM)
    @callback
    def visit(hwnd,_):
        if not u.IsWindowVisible(hwnd):return True
        pid=W.DWORD();u.GetWindowThreadProcessId(hwnd,C.byref(pid))
        name=C.create_unicode_buffer(512);u.GetWindowTextW(hwnd,name,512)
        if name.value and pid.value!=os.getpid():result.append((int(hwnd),name.value))
        return True
    u.EnumWindows(visit,0)
    return result

class WindowIO:
    def __init__(self,hwnd,crop=(0,0,1,1)):
        self.hwnd=hwnd;self.crop=crop;self.last_rect=None
    def active(self):return bool(u.IsWindow(self.hwnd) and not u.IsIconic(self.hwnd) and foreground()==self.hwnd)
    def rect(self):
        if not self.active():raise RuntimeError('游戏不在前台，已停止操作；切回游戏后继续')
        r=W.RECT();p=W.POINT(0,0)
        if not u.GetClientRect(self.hwnd,C.byref(r)) or not u.ClientToScreen(self.hwnd,C.byref(p)):raise RuntimeError('游戏窗口已关闭')
        a,b,c,d=self.crop;w=r.right;h=r.bottom
        result=(p.x+round(a*w),p.y+round(b*h),round((c-a)*w),round((d-b)*h))
        if result[2]<120 or result[3]<250:raise RuntimeError('游戏画面太小，请放大窗口')
        return result
    def capture(self):
        import mss
        r=self.rect()
        with mss.MSS() as s:shot=s.grab(dict(zip(('left','top','width','height'),r)))
        if self.rect()!=r:raise RuntimeError('截图时窗口发生移动，请重新开始')
        self.last_rect=r
        return Image.frombytes('RGB',shot.size,shot.rgb)
    def action(self,decision):
        from pynput.mouse import Controller,Button
        r=self.rect()
        if r!=self.last_rect:raise RuntimeError('窗口位置已改变，请重新开始')
        mouse=Controller()
        def point(x,y):return (r[0]+round(x*r[2]/1080),r[1]+round(y*r[3]/2400))
        if decision.kind=='SCROLL':
            mouse.position=point(540,1820);mouse.press(Button.left)
            try:
                for i in range(1,9):
                    if not self.active():raise RuntimeError('游戏已离开前台')
                    mouse.position=point(540,1820-i*100);time.sleep(.025)
            finally:mouse.release(Button.left)
        else:
            if not (0<decision.x<1080 and 0<decision.y<2400):raise RuntimeError('点击位置无效')
            mouse.position=point(decision.x,decision.y)
            if not self.active():raise RuntimeError('游戏已离开前台')
            at=u.WindowFromPoint(W.POINT(*mouse.position))
            if u.GetAncestor(at,2)!=self.hwnd:raise RuntimeError('游戏按钮被其他窗口遮挡，请移开后继续')
            mouse.click(Button.left)
