from __future__ import annotations
import re
import threading
import cv2
import numpy as np
from PIL import Image

class Vision:
    def __init__(self):
        self._ocr = None
        self._lock = threading.Lock()

    def text(self, image):
        with self._lock:
            if self._ocr is None:
                from rapidocr_onnxruntime import RapidOCR
                self._ocr = RapidOCR()
            result, _ = self._ocr(np.asarray(image.convert('RGB')))
        return result or []

    def number(self, image):
        hits = []
        for box, text, score in self.text(image):
            clean = text.strip().replace('O', '0').replace('o', '0')
            if re.fullmatch(r'\d{1,5}', clean) and float(score) > .55:
                hits.append((float(score), int(clean)))
        if hits:return max(hits)[1]
        # A lone white 0/1 is often discarded by the text detector. Only accept
        # a single digit-shaped component; a blank crop must never mean zero.
        rgb=np.asarray(image.convert('RGB'))
        mask=np.uint8((rgb.min(axis=2)>190)&(rgb.max(axis=2)-rgb.min(axis=2)<55))*255
        contours,hierarchy=cv2.findContours(mask,cv2.RETR_CCOMP,cv2.CHAIN_APPROX_SIMPLE)
        if hierarchy is None:return None
        outer=[i for i,c in enumerate(contours) if hierarchy[0,i,3]<0 and cv2.boundingRect(c)[3]>image.height*.18 and cv2.contourArea(c)>8]
        if len(outer)!=1:return None
        i=outer[0];x,y,w,h=cv2.boundingRect(contours[i]);child=hierarchy[0,i,2]
        if child<0 and .2<w/h<.55:
            glyph=mask[y:y+h,x:x+w]>0
            if glyph.sum(axis=0).max()/h>.85 and glyph.mean()>.35:return 1
        if child<0 or hierarchy[0,child,0]>=0 or not .4<w/h<1.05:return None
        hx,hy,hw,hh=cv2.boundingRect(contours[child])
        if .3<hh/h<.85 and abs((hy+hh/2)-(y+h/2))<h*.14 and abs((hx+hw/2)-(x+w/2))<w*.15:return 0
        return None

    @staticmethod
    def crop(image, box):
        w, h = image.size
        x1, y1, x2, y2 = box
        return image.crop((max(0,int(x1*w)),max(0,int(y1*h)),min(w,int(x2*w)),min(h,int(y2*h))))

    @staticmethod
    def patch(image, point, radius=.018):
        x,y=point
        return Vision.crop(image,(x-radius,y-radius,x+radius,y+radius)).resize((40,40))

    @staticmethod
    def low_balance_button(image, config):
        """Locate the green x1 / purple x5 disc beside Start, only at low balance.

        No multiplier OCR or calibration requirement. Ambiguous/covered controls
        return None; the runner must not click an assumed coordinate.
        """
        dx=config['targets'][1][0]-config['targets'][0][0]
        dy=config['targets'][4][1]-config['targets'][0][1]
        sx,sy=config['start'];w,h=image.size
        area=(max(0,sx+.65*dx),max(0,sy-.38*dy),min(1,sx+1.9*dx),min(1,sy+.55*dy))
        clipped=Vision.crop(image,area)
        if clipped.width<1 or clipped.height<1:return None
        hsv=cv2.cvtColor(np.asarray(clipped.convert('RGB')),cv2.COLOR_RGB2HSV)
        found=[]
        for value,lo,hi in ((1,(35,105,95),(90,255,255)),(5,(120,85,65),(160,255,255))):
            mask=cv2.inRange(hsv,lo,hi)
            contours,_=cv2.findContours(mask,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
            for contour in contours:
                x,y,bw,bh=cv2.boundingRect(contour);a=cv2.contourArea(contour)
                perimeter=cv2.arcLength(contour,True)
                if not (.25*dx*w<bw<.9*dx*w and .7<bw/max(1,bh)<1.4):continue
                if perimeter==0 or 4*np.pi*a/perimeter**2<.55 or a/(bw*bh)<.5:continue
                found.append((value,((int(area[0]*w)+x+bw/2)/w,(int(area[1]*h)+y+bh/2)/h)))
        if len(found)!=1:return None
        value,point=found[0]
        # The cost beneath Start is red when unaffordable and white otherwise.
        # Cross-check it with the disc so an OCR count error cannot cause a toggle.
        cost=Vision.crop(image,(max(0,sx-.08*dx),max(0,sy+.10*dy),min(1,sx+.40*dx),min(1,sy+.38*dy)))
        rgb=np.asarray(cost.convert('RGB'));hsv=cv2.cvtColor(rgb,cv2.COLOR_RGB2HSV)
        red=((hsv[:,:,0]<15)|(hsv[:,:,0]>165))&(hsv[:,:,1]>110)&(hsv[:,:,2]>95)
        white=(rgb.min(axis=2)>195)&(rgb.max(axis=2)-rgb.min(axis=2)<65)
        if value==5 and red.mean()>.06:return (value,point)
        if value==1 and red.mean()<.04 and white.mean()>.03:return (value,point)
        return None

    @staticmethod
    def exit_region(points):
        dx=points[1][0]-points[0][0];dy=points[4][1]-points[0][1]
        y=max(p[1] for p in points)
        return (max(0,points[0][0]-.75*dx),max(0,y+.5*dy),min(1,points[3][0]+.75*dx),min(1,y+1.65*dy))

    def dismiss_point(self,image,region):
        crop=self.crop(image,region)
        if crop.width<1 or crop.height<1:return None
        scale=max(1,min(3,1200/crop.width))
        items=self.text(crop.resize((int(crop.width*scale),int(crop.height*scale))))
        message=re.sub(r'\s+','',''.join(str(t[1]) for t in items))
        if '点击' in message and any(word in message for word in ('任意','空白','退出','关闭')):
            return ((region[0]+region[2])/2,(region[1]+region[3])/2)
        return None

    @staticmethod
    def similarity(a,b):
        a=np.asarray(a.convert('RGB').resize((40,40)),dtype=np.float32)
        b=np.asarray(b.convert('RGB').resize((40,40)),dtype=np.float32)
        if a.mean()<b.mean()*.6:return 0.0
        ga=cv2.cvtColor(a,cv2.COLOR_RGB2GRAY);gb=cv2.cvtColor(b,cv2.COLOR_RGB2GRAY)
        if ga.std()<2 or gb.std()<2:return float(1-np.abs(a-b).mean()/255)
        return float(cv2.matchTemplate(ga,gb,cv2.TM_CCOEFF_NORMED)[0,0])

    @staticmethod
    def targets(image, region=(0,0,1,1)):
        rgb=np.asarray(image.convert('RGB'));h,w=rgb.shape[:2]
        scale=min(1,1200/max(w,h));small=cv2.resize(rgb,None,fx=scale,fy=scale)
        hsv=cv2.cvtColor(small,cv2.COLOR_RGB2HSV)
        mask=cv2.inRange(hsv,(0,85,75),(14,255,255))|cv2.inRange(hsv,(165,85,75),(180,255,255))
        contours,_=cv2.findContours(mask,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
        found=[]
        for c in contours:
            area=cv2.contourArea(c);x,y,bw,bh=cv2.boundingRect(c)
            if area<80 or not .65<bw/max(bh,1)<1.5:continue
            perimeter=cv2.arcLength(c,True)
            if perimeter==0 or 4*np.pi*area/perimeter**2<.55:continue
            px=(x+bw/2)/(w*scale);py=(y+bh/2)/(h*scale)
            if region[0]<px<region[2] and region[1]<py<region[3]:found.append((px,py,area))
        if len(found)<12:return []
        # A valid board is exactly three rows by four columns. Reject unrelated red UI.
        pts=np.array([[p[0]*w,p[1]*h] for p in found]);best=None
        for origin,p0 in enumerate(pts):
            diameter=2*np.sqrt(found[origin][2]/np.pi)/scale
            rights=[p[0]-p0[0] for p in pts if abs(p[1]-p0[1])<diameter*.3 and diameter*.9<p[0]-p0[0]<diameter*3]
            downs=[p[1]-p0[1] for p in pts if abs(p[0]-p0[0])<diameter*.3 and diameter*.9<p[1]-p0[1]<diameter*3]
            for dx in rights:
                for dy in downs:
                    ids=[];error=0
                    for row in range(3):
                        for col in range(4):
                            distances=np.linalg.norm(pts-(p0+np.array([col*dx,row*dy])),axis=1)
                            i=int(distances.argmin());ids.append(i);error+=distances[i]
                    if len(set(ids))!=12 or error>diameter*1.5:continue
                    if best is None or error<best[0]:best=(error,ids)
        return [found[i][:2] for i in best[1]] if best else []

def grid_points(box, rows, cols):
    x1,y1,x2,y2=box
    return [(x1+(c+.5)*(x2-x1)/cols,y1+(r+.5)*(y2-y1)/rows) for r in range(rows) for c in range(cols)]

def auto_config(home,board,vision):
    if home.size!=board.size:raise ValueError('两张截图尺寸不同，请上传同一屏幕的两张全屏原图。')
    points=Vision.targets(board)
    if len(points)!=12:raise ValueError('第二张图未找到完整的 3 行 × 4 列靶子。请上传尚未射击、12 个靶子都完整可见的全屏图。')
    w,h=home.size;xs=sorted(p[0] for p in points[:4]);ys=[points[r*4][1] for r in range(3)]
    dx=float(np.median(np.diff(xs)));dy=float(np.median(np.diff(ys)))
    region=(max(0,xs[0]-.75*dx),max(0,ys[0]-3.9*dy),min(1,xs[-1]+.75*dx),min(1,ys[-1]+.9*dy))
    crop=Vision.crop(home,region);items=vision.text(crop)
    def center(item):
        box=item[0];return (region[0]+sum(p[0] for p in box)/4/w,region[1]+sum(p[1] for p in box)/4/h)
    starts=[t for t in items if '开始' in t[1] and center(t)[1]>region[1]+(region[3]-region[1])*.6]
    if not starts:raise ValueError('第一张图没有识别到“开始射击”。请上传活动首页全屏原图，确保按钮没有被遮挡。')
    start=center(max(starts,key=lambda t:float(t[2])))
    rgb=np.array(home);hsv=cv2.cvtColor(rgb,cv2.COLOR_RGB2HSV)
    mask=cv2.inRange(hsv,(35,105,100),(90,255,255))
    x1=int((region[0]+(region[2]-region[0])*.65)*w);x2=int(region[2]*w)
    y1=int((region[1]+(region[3]-region[1])*.12)*h);y2=int((region[1]+(region[3]-region[1])*.42)*h)
    sub=mask[y1:y2,x1:x2];contours,_=cv2.findContours(sub,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
    plus=[]
    for c in contours:
        x,y,bw,bh=cv2.boundingRect(c)
        if .5<bw/max(1,bh)<1.8 and cv2.contourArea(c)>12 and bw<(region[2]-region[0])*w*.09:plus.append((x+x1+bw/2,y+y1+bh/2))
    count=None;count_region=None
    for px,py in sorted(plus,reverse=True):
        gw=(region[2]-region[0])*w;gh=(region[3]-region[1])*h
        r=((px-gw*.14)/w,(py-gh*.023)/h,(px-gw*.025)/w,(py+gh*.023)/h)
        n=vision.number(Vision.crop(home,r).resize((240,140)))
        if n is not None:count=n;count_region=r;break
    if count is None:
        numbers=[t for t in items if re.fullmatch(r'\d{1,5}',t[1].strip()) and center(t)[0]>region[0]+(region[2]-region[0])*.65 and region[1]+(region[3]-region[1])*.12<center(t)[1]<region[1]+(region[3]-region[1])*.42]
        if numbers:
            t=max(numbers,key=lambda t:float(t[2]));count=int(t[1]);p=center(t);count_region=(p[0]-.04,p[1]-.023,p[0]+.04,p[1]+.023)
    if count is None:raise ValueError('第一张图的券数不清楚。请重传原始全屏截图，不要压缩或裁剪。')
    return dict(region=region,targets=points,start=start,count_region=count_region,count=count,exit_region=Vision.exit_region(points))
