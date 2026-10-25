"""Local bear recognition, using the same reference crops and gates as Android."""
import json, re
from pathlib import Path
import cv2
import numpy as np
from PIL import Image
from vision import Vision
from bear_flow import Frame, Row

ASSETS=Path(__file__).resolve().parent/'bear_assets'

def read(path):
    result=cv2.imdecode(np.fromfile(str(path),np.uint8),cv2.IMREAD_COLOR)
    if result is None:raise ValueError('识别模板损坏：'+str(path))
    return result

def hits(screen,template,rect,limit=1,threshold=.88,color=None,expected=None):
    x1,y1,x2,y2=map(int,rect);x1=max(0,x1);y1=max(0,y1);x2=min(screen.shape[1],x2);y2=min(screen.shape[0],y2)
    th,tw=template.shape[:2]
    if x2-x1<tw or y2-y1<th:return []
    scores=cv2.matchTemplate(screen[y1:y2,x1:x2],template,cv2.TM_CCOEFF_NORMED);out=[]
    for _ in range(limit):
        _,score,_,(x,y)=cv2.minMaxLoc(scores)
        if not np.isfinite(score) or score<threshold:break
        if expected is None or float(np.abs(color[y1+y:y1+y+th,x1+x:x1+x+tw].astype(float)-expected.astype(float)).mean())<=35:
            out.append((x1+x+tw/2,y1+y+th/2))
        scores[max(0,y-th//2):min(scores.shape[0],y+th//2+1),max(0,x-tw//2):min(scores.shape[1],x+tw//2+1)]=-1
    return out

class BearVision:
    def __init__(self,ocr=None,calibration=None):
        self.ocr=ocr or Vision();self.layout=json.loads((ASSETS/'reference/layout.json').read_text())
        self.refs={p.stem:read(p) for p in (ASSETS/'reference').glob('*.png')}
        self.feedback={p.stem:read(p) for p in (ASSETS/'feedback').glob('*.png')}
        self.calibration=calibration or {}
        for key,v in self.calibration.items():
            if 'image' in v:self.refs[key]=read(v['image'])
    def text(self,color):
        if color.size==0:return ''
        image=Image.fromarray(cv2.cvtColor(color,cv2.COLOR_BGR2RGB))
        factor=max(1,360/image.width)
        image=image.resize((round(image.width*factor),round(image.height*factor)))
        return ''.join(str(item[1]) for item in self.ocr.text(image) if float(item[2])>=.70).replace(' ','').replace('，',',').replace('／','/')
    def observe(self,image):
        color=cv2.cvtColor(np.asarray(image.convert('RGB')),cv2.COLOR_RGB2BGR)
        if abs(image.width/image.height-.45)>.015:raise ValueError('请框选完整竖屏游戏画面（约 9:20），排除窗口边框')
        if color.shape[1]>1080:color=cv2.resize(color,(1080,round(color.shape[0]*1080/color.shape[1])),interpolation=cv2.INTER_AREA)
        h,w=color.shape[:2];s=w/1080;gray=cv2.cvtColor(color,cv2.COLOR_BGR2GRAY);f=Frame()
        def rect(values):return tuple(round(v*s) for v in values)
        def tpl(key,factor=1):
            source=self.refs.get(key)
            if source is None:return None
            return cv2.resize(source,None,fx=s*factor,fy=s*factor,interpolation=cv2.INTER_AREA if factor==1 else cv2.INTER_CUBIC)
        def find(key,region,limit=1,factor=1,colored=False):
            t=tpl(key,factor)
            if t is None:return []
            return hits(gray,cv2.cvtColor(t,cv2.COLOR_BGR2GRAY),rect(region),limit,color=color,expected=t if colored else None)
        def near(key,position=None):
            values=self.calibration.get(key,{}).get('rect',self.layout['crops'].get(key))
            if not values:return None
            x,y,tw,th=values;cx,cy=position or (x+tw/2,y+th/2)
            found=find(key,(cx-tw/2-16,cy-th/2-16,cx+tw/2+16,cy+th/2+16),colored=key in ('deploy','join_reference'))
            return found[0] if found else None
        def setpoint(name,p):
            if p:setattr(f,name+'X',round(p[0]/s));setattr(f,name+'Y',round(p[1]/s))
        small=cv2.resize(color,(324,720),interpolation=cv2.INTER_AREA);sg=cv2.cvtColor(small,cv2.COLOR_BGR2GRAY)
        def feedback(key,region,limit=1,colored=False):
            t=self.feedback[key]
            return hits(sg,cv2.cvtColor(t,cv2.COLOR_BGR2GRAY),region,limit,color=small,expected=t if colored else None)
        if feedback('troop_overflow',(55,355,275,420)) and feedback('confirm_title',(125,285,200,330)):
            ok=feedback('confirm_ok',(85,425,240,500))
            if ok:f.troopOverflow=True;f.dialogOkX=round(ok[0][0]/.3);f.dialogOkY=round(ok[0][1]/.3);return f
        mapchrome=bool(feedback('map_city',(275,695,322,720)) and feedback('map_search',(92,600,130,642)))
        f.header=bool(near('header'));f.formation=bool(near('formation'));deploy=near('deploy');f.deploy=bool(deploy);setpoint('deploy',deploy)
        march=near('march_anchor')
        if not march:
            found=find('march_anchor',(0,432,324,936));march=found[0] if found else None
        mapchrome=mapchrome or bool(near('map_city') and (near('alliance') or near('map_search')))
        f.map=not(f.header or f.formation or f.deploy) and bool(march or mapchrome)
        if f.map:
            cr=self.calibration.get('counter',{}).get('rect')
            if cr:cx,cy,cw,ch=cr;count=[cx,cy,cx+cw,cy+ch]
            else:count=list(self.layout['counter'])
            if march:
                x,y,tw,th=self.calibration.get('march_anchor',{}).get('rect',self.layout['crops']['march_anchor']);dx=march[0]/s-x-tw/2;dy=march[1]/s-y-th/2
                count=[count[0]+dx,count[1]+dy,count[2]+dx,count[3]+dy]
            a,b,c,d=rect(count);raw=self.text(color[max(0,b):d,max(0,a):c]);m=re.fullmatch(r'([0-6])/6',raw)
            if m:f.marchUsed=int(m[1])
        f.marchHidden=f.map and march is None and mapchrome and f.marchUsed<0
        for key,name in [('back','back'),('world','world'),('war','war'),('rally_tab','rally')]:setpoint(name,near(key))
        f.rallyList=f.header and f.rallyX>0
        if f.map:
            points=find('shortcut',(864,360,1080,1920));setpoint('shortcut',points[0] if points else None)
            if 'war' in self.refs:setpoint('alliance',near('alliance'))
        if f.formation or f.deploy:
            presets=self.calibration.get('presets',self.layout['presets'])
            f.savedPreset=[bool(near('preset_marker'+str(i),p)) for i,p in enumerate(presets)]
            if f.formation and not f.header:
                recorded=feedback('deploy',(228,678,267,702),colored=True)
                if recorded and not f.deploy:
                    f.deploy=True;f.deployX=round(recorded[0][0]/.3);f.deployY=round(recorded[0][1]/.3)
                for i,x in enumerate((29,63,96,129,163,196)):
                    if not f.savedPreset[i] and abs(presets[i][0]-x/.3)<20 and abs(presets[i][1]-427)<20 and feedback('preset'+str(i),(x-10,117,x+10,138)):
                        f.savedPreset[i]=True
            raw=self.text(color[round(h*.21):round(h*.24),round(w*.10):round(w*.38)])
            num=r'(?:[0-9]{1,7}|[0-9]{1,3}(?:[,.][0-9]{3}){1,2})'
            m=re.fullmatch(f'({num})/({num})[!！]?',raw)
            if m:
                a,b=(int(t.replace(',','').replace('.','')) for t in m.groups())
                if 100<=b<=2000000 and 0<=a<=2000000:f.troops=(a,b)
        if f.rallyList and not f.deploy:
            detections=[]
            for factor in (.85,.90,.95,1,1.05,1.1,1.15):
                for x,y in find('bear_label',(0,480,400,2184),8,factor):
                    joins=find('join_reference',(756,y/s-312,1080,y/s+312),4,colored=True)
                    if len(joins)==1:detections.append((x/s,y/s,joins[0][0]/s,joins[0][1]/s))
            targets=feedback('bear_label',(0,144,120,655),8)+feedback('bear_label_top',(0,144,120,655),8)
            for x,y in targets:
                region=(227,max(0,int(y)-94),324,min(720,int(y)+94));green=[]
                for key in ('green_plus','green_plus_top'):
                    for p in feedback(key,region,8,True):
                        if not any(abs(p[0]-q[0])<=6 and abs(p[1]-q[1])<=6 for q in green):green.append(p)
                if len(green)==1:detections.append((x/.3,y/.3,green[0][0]/.3,green[0][1]/.3))
            unique=[]
            for item in sorted(detections,key=lambda d:d[1]):
                if not any(abs(item[0]-q[0])<=200 and abs(item[1]-q[1])<=200 for q in unique):unique.append(item)
            f.rows=[Row(i,round(d[2]),round(d[3])) for i,d in enumerate(unique)]
        return f
