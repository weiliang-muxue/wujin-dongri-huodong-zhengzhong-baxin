from __future__ import annotations
import ctypes, json, os, queue, sys, threading, time
from pathlib import Path
import tkinter as tk
from tkinter import filedialog, messagebox
import customtkinter as ctk
from PIL import Image, ImageTk
from vision import Vision, auto_config
from engine import Runner

ctk.set_appearance_mode('light')
ctk.set_default_color_theme('green')
DATA=Path(os.environ.get('LOCALAPPDATA',str(Path.home())))/'BullseyeAssistant'
DATA.mkdir(parents=True,exist_ok=True)

class App(ctk.CTk):
    def __init__(self):
        super().__init__()
        self.title('靶心助手 · Windows 1.0.1');self.geometry('1180x820');self.minsize(1000,700)
        self.configure(fg_color='#F4F6F5');self.images={};self.current='首页截图';self.analyzing=False
        self.config_data={'region':(0,0,1,1),'targets':[]};self.runner=None;self.events=queue.Queue();self.vision=Vision()
        self.anchor=None;self.scale=1;self.offset=(0,0);self.state_name='idle';self.listener=None;self.is_counting=False
        self.grid_columnconfigure(0,weight=1);self.grid_rowconfigure(2,weight=1)
        header=ctk.CTkFrame(self,fg_color='transparent');header.grid(row=0,column=0,sticky='ew',padx=28,pady=(24,12))
        ctk.CTkLabel(header,text='靶心助手',font=('Microsoft YaHei UI',27,'bold'),text_color='#152E27').pack(side='left')
        ctk.CTkLabel(header,text='WINDOWS  /  微信小程序 · 模拟器',font=('Microsoft YaHei UI',12),text_color='#5B6D65').pack(side='left',padx=20)
        self.status=ctk.CTkLabel(header,text='等待截图',fg_color='#E3ECE7',corner_radius=16,width=150,height=32,text_color='#315548');self.status.pack(side='right')
        steps=ctk.CTkFrame(self,fg_color='#E8EFEB',corner_radius=12);steps.grid(row=1,column=0,sticky='ew',padx=28,pady=(0,16))
        ctk.CTkLabel(steps,text='01 上传首页全图    →    02 上传 12 靶子全图    →    03 自动识别    →    04 选择倍率，开始',font=('Microsoft YaHei UI',14),height=48).pack()
        body=ctk.CTkFrame(self,fg_color='transparent');body.grid(row=2,column=0,sticky='nsew',padx=28)
        body.grid_columnconfigure(0,weight=1);body.grid_rowconfigure(0,weight=1)
        left=ctk.CTkFrame(body,fg_color='white',corner_radius=16);left.grid(row=0,column=0,sticky='nsew',padx=(0,18));left.grid_rowconfigure(2,weight=1);left.grid_columnconfigure(0,weight=1)
        uploads=ctk.CTkFrame(left,fg_color='transparent');uploads.grid(row=0,column=0,sticky='ew',padx=16,pady=16)
        ctk.CTkButton(uploads,text='上传首页全图',command=lambda:self.upload('首页截图'),height=40).pack(side='left',padx=(0,10))
        ctk.CTkButton(uploads,text='上传靶子全图',command=lambda:self.upload('靶子截图'),height=40,fg_color='#DDE8E2',text_color='#244B3C',hover_color='#CCDED4').pack(side='left')
        self.tabs=ctk.CTkSegmentedButton(left,values=['首页截图','靶子截图'],command=self.switch);self.tabs.set('首页截图');self.tabs.grid(row=1,column=0,padx=16,sticky='ew')
        self.canvas=tk.Canvas(left,bg='#EEF2EF',highlightthickness=0);self.canvas.grid(row=2,column=0,sticky='nsew',padx=16,pady=12)
        self.canvas.bind('<Configure>',lambda e:self.draw())
        self.info=ctk.CTkLabel(left,text='请上传未裁剪的屏幕全图。两张图应使用相同分辨率、窗口位置和缩放。',font=('Microsoft YaHei UI',12),wraplength=600,text_color='#576B60');self.info.grid(row=3,column=0,padx=16,pady=(0,14))
        right=ctk.CTkScrollableFrame(body,width=310,fg_color='white',corner_radius=16);right.grid(row=0,column=1,sticky='ns')
        self.label(right,'自动识别结果',20,True)
        self.label(right,'只需两张屏幕全图。位置由程序识别，无需框选。')
        self.calibration=ctk.CTkLabel(right,text='等待上传两张截图',justify='left',wraplength=280,text_color='#617168');self.calibration.pack(padx=12,pady=16)
        self.recognize_button=ctk.CTkButton(right,text='重新识别',command=self.analyze,width=280,fg_color='#E3ECE7',text_color='#244B3C',hover_color='#CCDCD2',state='disabled');self.recognize_button.pack(pady=4)
        self.label(right,'本次使用券数');self.count=ctk.CTkEntry(right,placeholder_text='识别后可手动更正',width=280,height=38);self.count.pack(padx=12)
        self.label(right,'每轮倍率（自己选择）');self.multiplier=ctk.CTkSegmentedButton(right,values=['1 倍','5 倍'],width=280);self.multiplier.set('1 倍');self.multiplier.pack(padx=12)
        self.label(right,'请先把游戏设为相同倍率。只读取总券数，不识别或切换倍率；不足所选倍率时停止并提示。')
        self.screen_label=ctk.CTkLabel(right,text='屏幕比例将自动读取',text_color='#617168');self.screen_label.pack(padx=12,pady=10)
        self.monitors=[]
        try:
            import mss
            with mss.MSS() as s:self.monitors=s.monitors[1:]
        except Exception as e:self.events.put(('log',str(e)))
        self.label(right,'运行过程',16,True)
        self.logbox=ctk.CTkTextbox(right,width=280,height=155,font=('Microsoft YaHei UI',12),fg_color='#F3F6F4');self.logbox.pack(padx=12,pady=(0,12));self.logbox.configure(state='disabled')
        bottom=ctk.CTkFrame(self,fg_color='white',corner_radius=14);bottom.grid(row=3,column=0,sticky='ew',padx=28,pady=18)
        self.warning=ctk.CTkLabel(bottom,text='运行时请勿操作鼠标或移动游戏窗口  ·  F8 暂停/继续  ·  Esc 停止',font=('Microsoft YaHei UI',13),text_color='#735626');self.warning.pack(side='left',padx=16,pady=20)
        self.stop_button=ctk.CTkButton(bottom,text='停止',width=75,fg_color='#EAECEB',text_color='#39473F',hover_color='#D9DEDA',command=self.stop,state='disabled');self.stop_button.pack(side='right',padx=(8,16))
        self.pause_button=ctk.CTkButton(bottom,text='暂停',width=75,command=self.toggle_pause,state='disabled');self.pause_button.pack(side='right',padx=8)
        self.start_button=ctk.CTkButton(bottom,text='开始运行',width=110,height=40,command=self.start);self.start_button.pack(side='right',padx=8)
        self.after(100,self.poll);self.protocol('WM_DELETE_WINDOW',self.close)
        try:
            from pynput import keyboard
            self.listener=keyboard.Listener(on_press=lambda key:self.events.put(('hotkey',key)));self.listener.start()
        except Exception as e:self.log('快捷键不可用：'+str(e))
        self.log('准备就绪。请先上传首页和靶子页的屏幕全图。')

    def label(self,parent,text,size=12,bold=False):
        ctk.CTkLabel(parent,text=text,font=('Microsoft YaHei UI',size,'bold' if bold else 'normal'),text_color='#334C40',wraplength=280,anchor='w',justify='left').pack(anchor='w',padx=12,pady=(12,6))

    def log(self,text):
        self.logbox.configure(state='normal');self.logbox.insert('end',time.strftime('%H:%M:%S')+'  '+text+'\n');self.logbox.see('end');self.logbox.configure(state='disabled')

    def upload(self,kind):
        if self.analyzing:return
        if self.runner and self.runner.thread and self.runner.thread.is_alive():messagebox.showinfo('请先停止','停止当前运行后才能更换截图。');return
        path=filedialog.askopenfilename(title='请选择未裁剪的屏幕全图',filetypes=[('图片','*.png *.jpg *.jpeg *.bmp')])
        if not path:return
        try:
            im=Image.open(path).convert('RGB')
            if not any(im.size==(m['width'],m['height']) for m in self.monitors):raise ValueError('这不是当前屏幕尺寸的全图。请使用系统全屏截图，保留整张图片，不要裁剪。')
            self.images[kind]=im;self.current=kind;self.tabs.set(kind);self.draw()
            self.info.configure(text=f'{im.width} × {im.height}  ·  比例 {im.width/im.height:.3f}  ·  请保持游戏窗口位置不变')
            self.log('已载入'+kind)
            self.config_data={'region':(0,0,1,1),'targets':[]};self.count.delete(0,'end');self.draw()
            if len(self.images)==2:self.analyze()
        except Exception as e:messagebox.showerror('无法载入',str(e))

    def switch(self,value):self.current=value;self.draw()

    def draw(self):
        self.canvas.delete('all');im=self.images.get(self.current)
        if im is None:
            self.canvas.create_text(max(200,self.canvas.winfo_width()/2),180,text='上传两张屏幕全图即可\n\n第 1 张：活动首页\n第 2 张：12 个靶子完整可见\n\n无需标记位置',fill='#728277',font=('Microsoft YaHei UI',18),justify='center');return
        cw,ch=max(1,self.canvas.winfo_width()),max(1,self.canvas.winfo_height());self.scale=min(cw/im.width,ch/im.height)
        iw,ih=max(1,int(im.width*self.scale)),max(1,int(im.height*self.scale));self.offset=((cw-iw)/2,(ch-ih)/2)
        self.photo=ImageTk.PhotoImage(im.resize((iw,ih),Image.Resampling.LANCZOS));self.canvas.create_image(*self.offset,image=self.photo,anchor='nw')
        def xy(p):return self.offset[0]+p[0]*iw,self.offset[1]+p[1]*ih
        for key,color in [('region','#2EA477'),('count_region','#F0B445')]:
            if key in self.config_data:
                r=self.config_data[key];self.canvas.create_rectangle(*xy(r[:2]),*xy(r[2:]),outline=color,width=2)
        points=self.config_data['targets'] if self.current=='靶子截图' else [self.config_data['start']] if 'start' in self.config_data else []
        for i,p in enumerate(points):
            x,y=xy(p);self.canvas.create_oval(x-11,y-11,x+11,y+11,fill='#167855',outline='white',width=2);self.canvas.create_text(x,y,text=str(i+1),fill='white',font=('Arial',10,'bold'))
        if len(self.config_data['targets'])==12 and 'start' in self.config_data:self.calibration.configure(text='已识别开始按钮和总券数\n12 / 12 个靶子位置已确认\n倍率由你选择，无需识别',text_color='#19734E')

    def analyze(self):
        if len(self.images)!=2 or self.analyzing:return
        if self.runner and self.runner.thread and self.runner.thread.is_alive():return
        self.analyzing=True
        self.recognize_button.configure(state='disabled');self.start_button.configure(state='disabled')
        self.status.configure(text='识别中');self.calibration.configure(text='正在读取两张截图…',text_color='#617168');self.log('自动识别次数、按钮和 12 个靶子…')
        home=self.images['首页截图'];board=self.images['靶子截图']
        def work():
            try:self.events.put(('configured',auto_config(home,board,self.vision)))
            except Exception as e:self.events.put(('config_error',str(e)))
        threading.Thread(target=work,daemon=True).start()

    def start(self):
        if self.runner and self.runner.thread and self.runner.thread.is_alive():return
        missing=[k for k in ('start','count_region') if k not in self.config_data]
        if len(self.images)!=2 or missing or not self.config_data['targets']:
            messagebox.showinfo('尚未识别完成','请上传首页、12 个靶子页两张屏幕全图，等待自动识别完成。');return
        if self.images['首页截图'].size!=self.images['靶子截图'].size:messagebox.showerror('截图尺寸不一致','请重新上传相同屏幕、相同分辨率的两张全图。');return
        try:n=int(self.count.get());assert 0<n<=99999
        except (ValueError,AssertionError):messagebox.showinfo('请输入次数','券数需要是 1～99999 的整数。');return
        chosen=5 if self.multiplier.get()=='5 倍' else 1
        if not messagebox.askokcancel('开始前确认',f'你选择了 {chosen} 倍，请先把游戏内倍率也设为 {chosen} 倍。\n程序不识别、不切换游戏倍率。\n\n请保持首页或完整的 12 靶子页可见。\n开始后不要操作鼠标、移动窗口或改变缩放。\nF8：暂停 / 继续；Esc：停止。\n\n点击确定后 5 秒开始。'):return
        mon=next(m for m in self.monitors if (m['width'],m['height'])==self.images['首页截图'].size)
        c=dict(self.config_data,count=n,multiplier=chosen)
        self.start_button.configure(state='disabled');self.pause_button.configure(state='normal');self.stop_button.configure(state='normal')
        def capture():
            import mss
            with mss.MSS() as s:
                shot=s.grab(mon);return Image.frombytes('RGB',shot.size,shot.rgb)
        def tap(p):
            from pynput.mouse import Controller,Button
            mouse=Controller();mouse.position=(mon['left']+int(p[0]*mon['width']),mon['top']+int(p[1]*mon['height']));mouse.click(Button.left)
        self.runner=Runner(c,self.images['首页截图'],self.images['靶子截图'],capture,tap,lambda *a:self.events.put(('report',a)))
        self.state_name='countdown';self.is_counting=True;self.countdown(5)

    def countdown(self,n):
        if not self.runner or self.runner.stopped.is_set():return
        if not self.runner.gate.is_set():self.after(200,lambda:self.countdown(n));return
        if n:
            self.status.configure(text=f'{n} 秒后开始');self.after(1000,lambda:self.countdown(n-1))
        else:self.is_counting=False;self.iconify();self.runner.start();self.state_name='running'

    def toggle_pause(self):
        if not self.runner:return
        if self.runner.gate.is_set():self.runner.pause()
        elif self.is_counting:self.runner.resume()
        else:
            self.iconify();self.after(500,self.runner.resume)
    def stop(self):
        if self.runner:self.runner.stop()
        self.state_name='stopped';self.status.configure(text='正在停止');self.start_button.configure(state='disabled' if self.runner and self.runner.thread and self.runner.thread.is_alive() else 'normal');self.pause_button.configure(state='disabled');self.stop_button.configure(state='disabled')
    def poll(self):
        while not self.events.empty():
            kind,payload=self.events.get()
            if kind=='log':self.log(payload)
            elif kind=='configured':
                self.analyzing=False
                self.config_data=payload;self.count.delete(0,'end');self.count.insert(0,str(payload['count']));self.draw()
                im=self.images['首页截图'];self.screen_label.configure(text=f'全屏 {im.width} × {im.height} · 比例 {im.width/im.height:.3f}')
                self.status.configure(text='可以开始');self.recognize_button.configure(state='normal');self.start_button.configure(state='normal')
                self.log(f'识别完成：{payload["count"]} 张券，12 个靶子。选择倍率即可开始。')
            elif kind=='config_error':
                self.analyzing=False
                self.calibration.configure(text=payload,text_color='#B34B31');self.status.configure(text='请重传截图');self.recognize_button.configure(state='normal');self.start_button.configure(state='disabled');self.log(payload)
            elif kind=='hotkey':
                from pynput.keyboard import Key
                if payload==Key.f8:self.toggle_pause()
                elif payload==Key.esc and self.runner:self.stop()
            elif kind=='report':
                state,text,left,rounds=payload;self.state_name=state;self.log(text)
                state_label={'running':'运行中','paused':'已暂停','done':'已完成','stopped':'已停止','error':'异常停止'}.get(state,state)
                self.status.configure(text=f'剩余 {left} · {state_label}');self.pause_button.configure(text='继续' if state=='paused' else '暂停')
                (DATA/'progress.json').write_text(json.dumps({'state':state,'remaining':left,'rounds':rounds},ensure_ascii=False),encoding='utf-8')
                if state in ('done','stopped','error'):
                    self.count.delete(0,'end');self.count.insert(0,str(left))
                    self.start_button.configure(state='normal');self.pause_button.configure(state='disabled');self.stop_button.configure(state='disabled');self.deiconify()
                elif state=='paused':self.deiconify()
        self.after(100,self.poll)
    def close(self):
        if self.runner:self.runner.stop()
        if self.listener:self.listener.stop()
        self.destroy()

if __name__=='__main__':
    import multiprocessing
    multiprocessing.freeze_support()
    try:ctypes.windll.shcore.SetProcessDpiAwareness(2)
    except Exception:pass
    if len(sys.argv)==5 and sys.argv[1]=='--verify-screenshots':
        try:
            result=auto_config(Image.open(sys.argv[2]).convert('RGB'),Image.open(sys.argv[3]).convert('RGB'),Vision())
            Path(sys.argv[4]).write_text(json.dumps(result,ensure_ascii=False),encoding='utf-8')
        except Exception as e:
            Path(sys.argv[4]).write_text(json.dumps({'error':str(e)},ensure_ascii=False),encoding='utf-8')
            sys.exit(1)
    else:App().mainloop()
