"""One executable, two activities. Each activity stops its worker before returning here."""
import ctypes,json,multiprocessing,sys
from pathlib import Path

def launch():
    import customtkinter as ctk
    ctk.set_appearance_mode('light');ctk.set_default_color_theme('green')
    while True:
        root=ctk.CTk();root.title('无尽冬日助手 2.0.3');root.geometry('620x480');root.minsize(540,440);chosen=[]
        ctk.CTkLabel(root,text='无尽冬日助手',font=('Microsoft YaHei UI',30,'bold')).pack(pady=(42,8))
        ctk.CTkLabel(root,text='选择本次活动',font=('Microsoft YaHei UI',16)).pack(pady=(0,24))
        def pick(mode):
            from ui_lifecycle import destroy_window
            chosen.append(mode);destroy_window(root,root=True)
        ctk.CTkButton(root,text='打熊',font=('Microsoft YaHei UI',24,'bold'),height=90,width=470,command=lambda:pick('bear')).pack(pady=8)
        ctk.CTkButton(root,text='正中靶心',font=('Microsoft YaHei UI',24,'bold'),height=90,width=470,command=lambda:pick('bullseye')).pack(pady=8)
        root.mainloop()
        if not chosen:break
        if chosen[0]=='bear':
            from bear_app import BearApp
            app=BearApp()
        else:
            from app import App
            app=App()
        app.mainloop()
        if not getattr(app,'return_to_chooser',False):break

def selftest(output):
    import cv2,numpy as np
    from PIL import Image,ImageDraw,ImageFont
    from bear_flow import MarchFlow,Frame
    from bear_vision import BearVision
    from vision import Vision
    from engine import Runner
    from app import App
    from bear_app import BearApp
    from floating import FloatingControls
    flow=MarchFlow();flow.dry=False;flow.start(0)
    f=Frame(map=True,marchUsed=6,shortcutX=999,shortcutY=1060)
    if not(flow.next(f,0).kind=='WAIT' and flow.next(f,1000).kind=='WAIT'):raise RuntimeError('Full march guard failed')
    flow.start(0);f.marchUsed=-1;f.marchHidden=True
    flow.next(f,0)
    if flow.next(f,1000).kind!='NAVIGATE':raise RuntimeError('Hidden march handling failed')
    b=BearVision()
    if not(len(b.refs)>=20 and len(b.feedback)==16):raise RuntimeError('Missing recognition assets')
    im=Image.new('RGB',(600,140),'white');draw=ImageDraw.Draw(im)
    draw.text((20,20),'5/6 12345',fill='black',font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',72))
    result=Vision().text(im)
    if not any('12345' in t[1] for t in result):raise RuntimeError('OCR model check failed: '+str(result))
    Path(output).write_text(json.dumps({'ok':True,'modes':['打熊','正中靶心'],'ocr':[t[1] for t in result],'opencv':cv2.__version__},ensure_ascii=False),encoding='utf-8')

if __name__=='__main__':
    multiprocessing.freeze_support()
    try:ctypes.windll.shcore.SetProcessDpiAwareness(2)
    except Exception:pass
    try:
        if len(sys.argv)==3 and sys.argv[1]=='--self-test':selftest(sys.argv[2])
        elif len(sys.argv)==5 and sys.argv[1]=='--verify-screenshots':
            from PIL import Image
            from vision import Vision,auto_config
            result=auto_config(Image.open(sys.argv[2]).convert('RGB'),Image.open(sys.argv[3]).convert('RGB'),Vision())
            Path(sys.argv[4]).write_text(json.dumps(result,ensure_ascii=False),encoding='utf-8')
        else:launch()
    except Exception as e:
        if len(sys.argv)>2 and sys.argv[1].startswith('--'):Path(sys.argv[-1]).write_text(json.dumps({'error':str(e)},ensure_ascii=False),encoding='utf-8');sys.exit(1)
        from tkinter import messagebox
        messagebox.showerror('无尽冬日助手',str(e))
