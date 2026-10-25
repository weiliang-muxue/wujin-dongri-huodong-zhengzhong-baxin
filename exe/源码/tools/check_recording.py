"""Replay the user's local recording; no recording is committed or packaged."""
import json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import cv2
from PIL import Image
from bear_vision import BearVision

def run(video,output):
    vision=BearVision();cap=cv2.VideoCapture(video);results=[]
    for second in (170,250,270,315,358,411,691,718,751,771,780,816,820):
        cap.set(cv2.CAP_PROP_POS_MSEC,second*1000);ok,bgr=cap.read();assert ok,second
        frame=vision.observe(Image.fromarray(cv2.cvtColor(bgr,cv2.COLOR_BGR2RGB)))
        if second in (250,270):assert frame.map and frame.marchHidden
        if second==358:assert frame.troopOverflow
        if second==691:assert not frame.rallyList
        if second in (315,411,771,780,816):assert frame.rallyList and frame.rows
        if second in (411,771):assert all(row.y>1000 for row in frame.rows),'gray first button'
        if second==816:assert all(row.y<1800 for row in frame.rows),'gray third button'
        if second in (170,718,751,820):assert frame.deploy and all(frame.savedPreset)
        if second==820:assert frame.troops==(1,139400),frame.troops
        results.append({'second':second,'map':frame.map,'hidden':frame.marchHidden,'overflow':frame.troopOverflow,'deploy':frame.deploy,'flags':sum(frame.savedPreset),'troops':frame.troops,'joinY':[r.y for r in frame.rows]})
    cap.release();Path(output).write_text(json.dumps(results,indent=2),encoding='utf-8');print('PASS: 13 annotated scenes, real desktop OCR, all six flags, 1/139400, gray-plus rejection')

if __name__=='__main__':run(sys.argv[1],sys.argv[2])
