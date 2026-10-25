"""Optional local screenshot calibration; not part of normal startup."""
import tkinter as tk
import customtkinter as ctk
from PIL import ImageTk
from ui_lifecycle import destroy_window

LABELS={'红色集结入口':'shortcut','出征按钮':'deploy','集结页标题':'header','集结标签':'rally_tab','编队页战争增益文字':'formation','地图右下角城镇文字':'map_city','地图左下角搜索按钮':'map_search','行军文字':'march_anchor','行军数字 x/6':'counter','返回箭头':'back','联盟按钮':'alliance','战争按钮':'war','野外按钮':'world','巨熊名称':'bear_label','绿色＋':'join_reference',**{f'第 {i+1} 个预设旗帜':f'preset_marker{i}' for i in range(6)}}

def open_calibration(owner,image,folder,settings,save):
    win=ctk.CTkToplevel(owner);win.title('框选要校准的位置');win.geometry('650x850');win.attributes('-topmost',True)
    menu=ctk.CTkOptionMenu(win,values=list(LABELS));menu.pack(pady=12)
    info=ctk.CTkLabel(win,text='选择项目，在图上框住对应文字或按钮，即可保存。');info.pack()
    canvas=tk.Canvas(win,bg='#e5ebe8',highlightthickness=0);canvas.pack(fill='both',expand=True,padx=12,pady=12)
    box={'drag':None};image=image.resize((1080,2400))
    def draw(e=None):
        scale=min(max(1,canvas.winfo_width())/1080,max(1,canvas.winfo_height())/2400);box['scale']=scale
        box['photo']=ImageTk.PhotoImage(image.resize((max(1,round(1080*scale)),max(1,round(2400*scale)))))
        canvas.delete('all');canvas.create_image(0,0,anchor='nw',image=box['photo'])
    def down(e):box['drag']=(e.x,e.y)
    def up(e):
        if box['drag'] is None:return
        a,b=box['drag'];box['drag']=None;s=box['scale'];x1=max(0,round(min(a,e.x)/s));y1=max(0,round(min(b,e.y)/s));x2=min(1080,round(max(a,e.x)/s));y2=min(2400,round(max(b,e.y)/s))
        if x2-x1<12 or y2-y1<12:return
        key=LABELS[menu.get()];data=settings.setdefault('calibration',{});folder.mkdir(exist_ok=True,parents=True);path=folder/(key+'.png')
        image.crop((x1,y1,x2,y2)).save(path);data[key]={'image':str(path),'rect':[x1,y1,x2-x1,y2-y1]}
        if key.startswith('preset_marker'):
            i=int(key[-1]);presets=data.setdefault('presets',[[x,427] for x in (98,209,319,431,542,654)]);presets[i]=[(x1+x2)//2,(y1+y2)//2]
        save();canvas.delete('selection');canvas.create_rectangle(x1*s,y1*s,x2*s,y2*s,outline='#159e6f',width=3,tags='selection');info.configure(text=menu.get()+' · 已保存')
    canvas.bind('<Configure>',draw);canvas.bind('<ButtonPress-1>',down);canvas.bind('<ButtonRelease-1>',up)
    win.protocol('WM_DELETE_WINDOW',lambda:destroy_window(win))
