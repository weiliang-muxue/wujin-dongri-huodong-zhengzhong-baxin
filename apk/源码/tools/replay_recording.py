"""Replay local recording at one-second intervals, plus annotated regression frames.

No recording or full frame is bundled in the APK. This tests image cues, not Android OCR/touch.
Usage: python tools/replay_recording.py RECORDING.mp4 [NON_BEAR_SCREENSHOTS...]
"""
import json
import sys
from pathlib import Path

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"


def read(path):
    return cv2.imdecode(np.fromfile(str(path), dtype=np.uint8), cv2.IMREAD_GRAYSCALE)


def score(frame, template, region):
    x, y, w, h = region
    roi = frame[y:y+h, x:x+w]
    return float(cv2.minMaxLoc(cv2.matchTemplate(roi, template, cv2.TM_CCOEFF_NORMED))[1])


body = read(ASSETS / "feedback/troop_overflow.png")
title = read(ASSETS / "feedback/confirm_title.png")
button = read(ASSETS / "feedback/confirm_ok.png")
city_template = read(ASSETS / "feedback/map_city.png")
search_template = read(ASSETS / "feedback/map_search.png")
bear_template = read(ASSETS / "feedback/bear_label.png")
plus_template = read(ASSETS / "feedback/green_plus.png")
plus_color = cv2.imdecode(np.fromfile(str(ASSETS / "feedback/green_plus.png"),dtype=np.uint8),cv2.IMREAD_COLOR)
bear_top = read(ASSETS / "feedback/bear_label_top.png")
plus_top = read(ASSETS / "feedback/green_plus_top.png")
plus_top_color = cv2.imdecode(np.fromfile(str(ASSETS / "feedback/green_plus_top.png"),dtype=np.uint8),cv2.IMREAD_COLOR)


def overflow(frame):
    return (score(frame, body, (55,355,220,65)) >= .88
            and score(frame,title,(125,285,75,45)) >= .88
            and score(frame,button,(85,425,155,75)) >= .88)


def reference_score(frame, key, rect):
    template = read(ASSETS / f"reference/{key}.png")
    template = cv2.resize(template, None, fx=.3, fy=.3, interpolation=cv2.INTER_AREA)
    return score(frame, template, rect)


def hits(frame, template, region):
    x,y,w,h = region
    th,tw = template.shape
    scores = cv2.matchTemplate(frame[y:y+h,x:x+w],template,cv2.TM_CCOEFF_NORMED)
    result = []
    for _ in range(8):
        _,confidence,_,(px,py) = cv2.minMaxLoc(scores)
        if not np.isfinite(confidence) or confidence < .88:
            break
        result.append((x+px+tw/2,y+py+th/2))
        scores[max(0,py-th//2):py+th//2+1,max(0,px-tw//2):px+tw//2+1] = -1
    return result


def recorded_rallies(frame):
    frame = cv2.resize(frame,(324,720),interpolation=cv2.INTER_AREA)
    gray = cv2.cvtColor(frame,cv2.COLOR_BGR2GRAY)
    result = []
    targets=hits(gray,bear_template,(0,144,120,511))+hits(gray,bear_top,(0,144,120,511))
    for tx,ty in targets:
        top,bottom = max(0,int(ty)-94),min(720,int(ty)+94)
        green = []
        for shape,expected in [(plus_template,plus_color),(plus_top,plus_top_color)]:
            for px,py in hits(gray,shape,(227,top,97,bottom-top)):
                h,w = shape.shape
                x,y = round(px-w/2),round(py-h/2)
                delta = float(np.abs(frame[y:y+h,x:x+w].astype(float)-expected.astype(float)).mean())
                if delta <= 35 and not any(abs(px-qx)<=6 and abs(py-qy)<=6 for qx,qy in green):
                    green.append((px,py))
        if len(green)==1:
            result.append({"bear":[tx,ty],"join":list(green[0])})
    unique=[]
    for r in result:
        if not any(abs(r['bear'][0]-q['bear'][0])<=60 and abs(r['bear'][1]-q['bear'][1])<=60 for q in unique):
            unique.append(r)
    return sorted(unique,key=lambda r:r['bear'][1])


capture = cv2.VideoCapture(sys.argv[1])
fps = capture.get(cv2.CAP_PROP_FPS)
duration = capture.get(cv2.CAP_PROP_FRAME_COUNT) / fps
dialog_times = []
sampled = 0
for second in range(int(duration)+1):
    capture.set(cv2.CAP_PROP_POS_MSEC,second*1000)
    ok, frame = capture.read()
    if not ok:
        continue
    frame = cv2.cvtColor(cv2.resize(frame,(324,720)),cv2.COLOR_BGR2GRAY)
    if overflow(frame):
        dialog_times.append(second)
        assert 350 <= second <= 373, f"False error dialog at {second}s"
    sampled += 1

assert 358 in dialog_times, dialog_times
# The bubble covers the dialog during most whole-second samples. Check a different decoded frame too.
capture.set(cv2.CAP_PROP_POS_MSEC,358200)
ok, separate_frame = capture.read()
assert ok and overflow(cv2.cvtColor(separate_frame,cv2.COLOR_BGR2GRAY))
checks = []
for second, expected_map, expected_dialog in [
    (170,False,False),(250,True,False),(270,True,False),
    (358,False,True),(751,False,False),(691,False,False),
    (780,False,False),(820,False,False),
]:
    capture.set(cv2.CAP_PROP_POS_MSEC,second*1000)
    ok, frame = capture.read()
    assert ok
    frame = cv2.cvtColor(frame,cv2.COLOR_BGR2GRAY)
    city = score(frame,city_template,(275,695,47,25))
    search = score(frame,search_template,(92,600,38,42))
    is_map = city >= .88 and search >= .88
    is_dialog = overflow(frame)
    header = reference_score(frame,"header",(36,77,72,30)) >= .88
    rally_tab = reference_score(frame,"rally_tab",(40,117,39,27)) >= .88
    is_list = header and rally_tab
    checks.append({"second":second,"mapScore":round(city,4),"searchScore":round(search,4),"map":is_map,"overflowDialog":is_dialog,"rallyList":is_list})
    assert is_map == expected_map, checks[-1]
    assert is_dialog == expected_dialog, checks[-1]
    assert is_list == (second==780), checks[-1]

bear_checks=[]
for second in [315,411,771,780,816]:
    capture.set(cv2.CAP_PROP_POS_MSEC,second*1000)
    ok,frame=capture.read()
    assert ok
    gray=cv2.cvtColor(frame,cv2.COLOR_BGR2GRAY)
    is_list=reference_score(gray,"header",(36,77,72,30))>=.88 and reference_score(gray,"rally_tab",(40,117,39,27))>=.88
    detected=recorded_rallies(frame) if is_list else []
    bear_checks.append({"second":second,"rallyList":is_list,"bearAndGreenPlus":detected})
    assert is_list and detected,bear_checks[-1]
    if second in [315,816]:
        assert any(row['join'][1]<300 for row in detected),bear_checks[-1]
    if second in [411,771]:
        assert not any(row['join'][1]<300 for row in detected),"Recorded first button is gray"
    if second==816:
        assert not any(row['join'][1]>550 for row in detected),"Recorded third button is gray"

negative_checks=[]
for filename in sys.argv[2:]:
    frame=cv2.imdecode(np.fromfile(filename,dtype=np.uint8),cv2.IMREAD_COLOR)
    assert frame is not None,filename
    detected=recorded_rallies(frame)
    negative_checks.append({"filename":Path(filename).name,"bearAndGreenPlus":detected})
    assert not detected,negative_checks[-1]

# Keep the real bear label, but replace its green button with the supplied gray button.
# This controlled negative checks color/shape rejection independently of monster filtering.
gray_plus_check=None
if len(sys.argv)>2:
    gray_source=cv2.imdecode(np.fromfile(sys.argv[2],dtype=np.uint8),cv2.IMREAD_COLOR)
    capture.set(cv2.CAP_PROP_POS_MSEC,315000)
    ok,composite=capture.read()
    assert ok
    gray_plus=cv2.resize(gray_source[790:886,902:1000],(29,30),interpolation=cv2.INTER_AREA)
    composite[421:451,277:306]=gray_plus
    gray_plus_check={"synthetic":True,"bearAndGrayPlus":recorded_rallies(composite)}
    assert not any(abs(row['join'][1]-436)<=10 for row in gray_plus_check["bearAndGrayPlus"]),gray_plus_check

report = {"version":"2.0.1","recordingSeconds":round(duration,3),"sampledSeconds":sampled,
          "overflowDetectedAt":dialog_times,"falseOverflowOutsideAnnotatedInterval":0,
          "annotatedFrameChecks":checks,"recordedBearChecks":bear_checks,"nonBearChecks":negative_checks,"grayPlusControl":gray_plus_check,
          "realDeviceTested":False,"androidOcrTested":False}
(ROOT / "video-replay-results.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
print(json.dumps(report,indent=2))
