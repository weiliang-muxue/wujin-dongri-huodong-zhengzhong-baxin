import threading,time
from bear_flow import MarchFlow
from bear_vision import BearVision

class BearRunner:
    def __init__(self,io,settings,report,save):
        self.io=io;self.report=report;self.save=save;self.settings=settings
        self.stop_event=threading.Event();self.gate=threading.Event();self.gate.set();self.lock=threading.RLock()
        self.thread=None;self.flow=MarchFlow();self.flow.dry=settings.get('dry',True)
        self.flow.nextPreset=settings.get('nextPreset',0);self.flow.enabled=settings.get('enabled',[True]*6)
        self.flow.maxMinutes=settings.get('minutes',30)
        presets=settings.get('calibration',{}).get('presets')
        if presets:self.flow.presetX=[p[0] for p in presets];self.flow.presetY=[p[1] for p in presets]
    def start(self):self.thread=threading.Thread(target=self.run,daemon=True);self.thread.start()
    def stop(self):
        with self.lock:self.stop_event.set();self.gate.set();self.flow.stop()
    def pause(self):
        with self.lock:self.gate.clear()
        self.report('已暂停 · F8 继续')
    def resume(self):
        if self.stop_event.is_set():return
        with self.lock:
            # An interrupted batch must obtain a fresh map reading; preserve session deadline.
            deadline=self.flow.deadline;self.flow.start(round(time.monotonic()*1000));self.flow.deadline=deadline;self.gate.set()
        self.report('继续前重新检查地图')
    def run(self):
        vision=BearVision(calibration=self.settings.get('calibration'));self.flow.start(round(time.monotonic()*1000))
        last=''
        try:
            while not self.stop_event.is_set():
                if time.monotonic()*1000>=self.flow.deadline:self.report('已达到运行时长');break
                if not self.gate.wait(.2):continue
                try:
                    captured=time.monotonic();image=self.io.capture();frame=vision.observe(image)
                    with self.lock:
                        if self.stop_event.is_set() or not self.gate.is_set():continue
                        if time.monotonic()-captured>4:
                            self.report('首次识别加载中，丢弃过期画面后重拍');continue
                        now=round(time.monotonic()*1000);d=self.flow.next(frame,now)
                        if d.message!=last:self.report(d.message);last=d.message
                        if d.kind not in ('WAIT','PAUSE'):
                            self.io.action(d);self.flow.complete(True,round(time.monotonic()*1000));self.save(self.flow.nextPreset)
                        if d.kind=='PAUSE':break
                except Exception as e:
                    self.report(str(e));self.pause()
                self.stop_event.wait(.65)
        finally:self.stop();self.report('已停止；点击「开始」可新开一轮')
