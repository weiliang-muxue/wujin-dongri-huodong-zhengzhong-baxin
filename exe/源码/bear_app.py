import json,os,queue,time
from pathlib import Path
import tkinter as tk
from tkinter import messagebox
import customtkinter as ctk
from PIL import ImageTk
from window_io import windows,WindowIO,u
from bear_runner import BearRunner

DATA=Path(os.environ.get('LOCALAPPDATA',str(Path.home())))/'Wujindongri'/'bear'
DATA.mkdir(parents=True,exist_ok=True)

class BearApp(ctk.CTk):
    def __init__(self):
        super().__init__();self.title('无尽冬日助手 2.0.3 · 打熊');self.geometry('1000x800');self.minsize(900,680)
        self.return_to_chooser=False;self.runner=None;self.panel=None;self.events=queue.Queue();self.preview=None;self.crop=(0,0,1,1);self.drag=None;self.listener=None
        try:self.settings=json.loads((DATA/'settings.json').read_text(encoding='utf-8'))
        except Exception:self.settings={}
        self.crop=self.settings.get('crop',self.crop)
        top=ctk.CTkFrame(self);top.pack(fill='x',padx=20,pady=16)
        ctk.CTkLabel(top,text='打熊',font=('Microsoft YaHei UI',26,'bold')).pack(side='left',padx=14,pady=14)
        ctk.CTkButton(top,text='返回活动选择',command=self.switch_mode,width=140).pack(side='right',padx=14)
        body=ctk.CTkFrame(self,fg_color='transparent');body.pack(fill='both',expand=True,padx=20)
        left=ctk.CTkFrame(body);left.pack(side='left',fill='both',expand=True,padx=(0,16))
        self.canvas=tk.Canvas(left,bg='#e8eeeb',highlightthickness=0);self.canvas.pack(fill='both',expand=True,padx=10,pady=10)
        self.canvas.bind('<Configure>',lambda e:self.draw());self.canvas.bind('<ButtonPress-1>',self.down);self.canvas.bind('<ButtonRelease-1>',self.up)
        right=ctk.CTkScrollableFrame(body,width=340);right.pack(side='right',fill='y')
        self.label(right,'1  选择游戏窗口',18)
        self.choice=ctk.CTkOptionMenu(right,values=['请刷新窗口']);self.choice.pack(fill='x',padx=12,pady=8)
        ctk.CTkButton(right,text='刷新窗口列表',command=self.refresh).pack(padx=12,pady=5)
        ctk.CTkButton(right,text='2  获取画面',command=self.capture,height=46).pack(padx=12,pady=12)
        self.label(right,'在左图框住游戏，再点「开始打熊」。',15)
        self.dry=tk.BooleanVar(value=False)
        self.options=ctk.CTkFrame(right);self.options_open=False
        ctk.CTkButton(right,text='更多设置',command=self.toggle_options,fg_color='#6c7e75').pack(padx=12,pady=20)
        self.flags=[];flags=ctk.CTkFrame(self.options);flags.pack(fill='x',padx=8)
        for i in range(6):
            v=tk.BooleanVar(value=self.settings.get('enabled',[True]*6)[i]);self.flags.append(v)
            ctk.CTkCheckBox(flags,text=f'第 {i+1} 个',variable=v,width=95).grid(row=i//3,column=i%3,pady=8,padx=3)
        self.label(self.options,'游戏内先保存前 6 个编队，旗帜栏保持最左端。',12)
        self.minutes=ctk.CTkOptionMenu(self.options,values=['30','20','10','5']);self.minutes.set('30');self.minutes.pack(pady=8)
        self.label(self.options,'运行分钟数 · 到时停止',12)
        self.label(self.options,'窗口保持前台。运行后可在悬浮条暂停或停止，也可用 F8 / Esc。',12)
        ctk.CTkButton(self.options,text='识别不准？校准位置',command=self.calibrate).pack(padx=12,pady=8)
        self.status=ctk.CTkLabel(self,text='先获取游戏预览，再开始',wraplength=900,height=38);self.status.pack(fill='x',padx=20,pady=8)
        controls=ctk.CTkFrame(self);controls.pack(fill='x',padx=20,pady=(0,16))
        ctk.CTkButton(controls,text='开始打熊',command=lambda:self.start(False),height=44).pack(side='right',padx=12,pady=14)
        ctk.CTkButton(controls,text='试识别',command=lambda:self.start(True),fg_color='#6c7e75').pack(side='right',padx=8)
        ctk.CTkButton(controls,text='停止',command=self.stop,fg_color='#6c7e75').pack(side='right',padx=8)
        self.protocol('WM_DELETE_WINDOW',self.close);self.after(150,self.poll);self.refresh()
        from pynput import keyboard
        self.listener=keyboard.Listener(on_press=lambda k:self.events.put(('key',k)));self.listener.start()
    def label(self,parent,text,size):ctk.CTkLabel(parent,text=text,font=('Microsoft YaHei UI',size),wraplength=310,justify='left').pack(anchor='w',padx=12,pady=8)
    def toggle_options(self):
        self.options_open=not self.options_open
        if self.options_open:self.options.pack(fill='x',padx=6)
        else:self.options.pack_forget()
    def calibrate(self):
        if self.preview is None:messagebox.showinfo('先获取画面','先获取需要校准的游戏页面，再框住完整游戏画面。');return
        if self.runner and self.runner.thread and self.runner.thread.is_alive():return
        from calibration import open_calibration
        a,b,c,d=self.crop;w,h=self.preview.size
        open_calibration(self,self.preview.crop((round(a*w),round(b*h),round(c*w),round(d*h))),DATA/'samples',self.settings,self.persist)
    def refresh(self):
        self.items={f'{title[:40]}  [{hwnd}]':hwnd for hwnd,title in windows()};self.choice.configure(values=list(self.items) or ['没有可用窗口'])
        preferred=next((k for k in self.items if '无尽' in k),next(iter(self.items),'没有可用窗口'));self.choice.set(preferred)
    def selected(self):
        hwnd=self.items.get(self.choice.get())
        if not hwnd:raise ValueError('请先选择游戏窗口')
        return hwnd
    def capture(self):
        if self.runner and self.runner.thread and self.runner.thread.is_alive():self.status.configure(text='请先停止运行');return
        try:
            hwnd=self.selected();self.iconify();u.SetForegroundWindow(hwnd)
            self.after(600,lambda:self.finish_capture(hwnd))
        except Exception as e:self.deiconify();messagebox.showerror('无法预览',str(e))
    def finish_capture(self,hwnd):
        try:self.preview=WindowIO(hwnd).capture();self.preview_hwnd=hwnd;self.draw();self.status.configure(text='在预览里框选完整游戏画面；若已正确可直接开始')
        except Exception as e:messagebox.showerror('无法预览',str(e))
        finally:self.deiconify()
    def draw(self):
        self.canvas.delete('all');cw,ch=max(1,self.canvas.winfo_width()),max(1,self.canvas.winfo_height())
        if self.preview is None:self.canvas.create_text(cw/2,ch/2,text='游戏画面预览\n\n选择窗口 → 获取预览 → 框选游戏画面',justify='center',font=('Microsoft YaHei UI',14),fill='#52695c');return
        self.scale=min(cw/self.preview.width,ch/self.preview.height);self.offset=((cw-self.preview.width*self.scale)/2,(ch-self.preview.height*self.scale)/2)
        self.photo=ImageTk.PhotoImage(self.preview.resize((max(1,round(self.preview.width*self.scale)),max(1,round(self.preview.height*self.scale)))))
        self.canvas.create_image(*self.offset,image=self.photo,anchor='nw');a,b,c,d=self.crop;x,y=self.offset
        self.canvas.create_rectangle(x+a*self.preview.width*self.scale,y+b*self.preview.height*self.scale,x+c*self.preview.width*self.scale,y+d*self.preview.height*self.scale,outline='#149e6d',width=3)
    def down(self,e):
        if self.preview is not None:self.drag=(e.x,e.y)
    def up(self,e):
        if self.drag is None:return
        x,y=self.offset;w=self.preview.width*self.scale;h=self.preview.height*self.scale
        a,b=self.drag;c,d=e.x,e.y;self.drag=None
        if abs(a-c)<20 or abs(b-d)<20:return
        self.crop=(max(0,min(a,c)-x)/w,max(0,min(b,d)-y)/h,min(w,max(a,c)-x)/w,min(h,max(b,d)-y)/h)
        self.draw()
    def persist(self,cursor=None):
        if cursor is not None:self.settings['nextPreset']=cursor
        (DATA/'settings.json').write_text(json.dumps(self.settings,ensure_ascii=False),encoding='utf-8')
    def start(self,dry=False):
        if self.runner and self.runner.thread and self.runner.thread.is_alive():self.status.configure(text='当前任务尚未结束，请先停止');return
        try:
            hwnd=self.selected()
            if self.preview is None or self.preview_hwnd!=hwnd:raise ValueError('请先获取所选游戏窗口的预览')
            a,b,c,d=self.crop;ratio=self.preview.width*(c-a)/(self.preview.height*(d-b))
            if abs(ratio-.45)>.015:raise ValueError('请框选完整竖屏游戏，比例约 9:20；不要包含电脑桌面或窗口边框')
            if not any(v.get() for v in self.flags):raise ValueError('至少启用一个预设')
            self.settings.update(crop=self.crop,dry=dry,enabled=[v.get() for v in self.flags],minutes=int(self.minutes.get()));self.persist()
            from floating import FloatingControls
            if self.panel:self.panel.close()
            self.panel=FloatingControls(self,'打熊',self.toggle_pause,self.stop)
            io=WindowIO(hwnd,self.crop);capture=io.capture;action=io.action;panel=self.panel
            io.input_guard=panel.check_input_window
            io.capture=lambda:panel.capture_call(capture,io.rect)
            io.action=lambda d:panel.action_call(action,d)
            self.runner=BearRunner(io,dict(self.settings),lambda s:self.events.put(('log',s)),self.persist)
            self.iconify();u.SetForegroundWindow(hwnd);self.after(1500,self.runner.start)
        except Exception as e:messagebox.showerror('尚未准备好',str(e))
    def stop(self):
        if self.runner:self.runner.stop()
        if self.panel:self.panel.close();self.panel=None
        self.deiconify()
    def toggle_pause(self):
        if not self.runner:return
        if self.runner.gate.is_set():self.runner.pause()
        else:u.SetForegroundWindow(self.runner.io.hwnd);self.after(500,self.runner.resume)
        if self.panel:self.panel.state(not self.runner.gate.is_set())
    def poll(self):
        while not self.events.empty():
            kind,value=self.events.get()
            if kind=='log':
                self.status.configure(text=value)
                if self.panel:
                    self.panel.state(not self.runner.gate.is_set())
                    if value.startswith('等待悬浮条：'):self.panel.message(value.removeprefix('等待悬浮条：'))
                    elif value.startswith(('已恢复：','已暂停','已停止','无法开始')):self.panel.message()
                if value.startswith('已停止') and self.panel:self.panel.close();self.panel=None;self.deiconify()
            else:
                from pynput.keyboard import Key
                if value==Key.esc:self.stop();self.deiconify()
                if value==Key.f8 and self.runner:self.toggle_pause()
        self.after(150,self.poll)
    def switch_mode(self):self.return_to_chooser=True;self.close()
    def close(self):
        self.stop()
        if self.listener:self.listener.stop()
        from ui_lifecycle import destroy_window
        destroy_window(self,root=True)
