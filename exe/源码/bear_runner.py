import copy,threading,time
from bear_flow import MarchFlow
from bear_vision import BearVision
from frame_guard import RetryFrame

class BearRunner:
    def __init__(self,io,settings,report,save):
        self.io=io;self.report=report;self.save=save;self.settings=settings
        self.stop_event=threading.Event();self.gate=threading.Event();self.gate.set();self.lock=threading.RLock()
        self.thread=None;self.flow=MarchFlow();self.flow.dry=settings.get('dry',True)
        self.flow.nextPreset=settings.get('nextPreset',0);self.flow.enabled=settings.get('enabled',[True]*6)
        self.flow.maxMinutes=settings.get('minutes',30)
        self.wait_message='';self.wait_source=''
        presets=settings.get('calibration',{}).get('presets')
        if presets:self.flow.presetX=[p[0] for p in presets];self.flow.presetY=[p[1] for p in presets]
    def start(self):
        with self.lock:
            if self.stop_event.is_set() or self.thread is not None:return
            self.flow.start(round(time.monotonic()*1000))
            self.thread=threading.Thread(target=self.run,daemon=True);self.thread.start()
    def stop(self):
        with self.lock:self.stop_event.set();self.gate.set();self.flow.stop()
    def pause(self,reason='已暂停 · 点继续恢复'):
        with self.lock:self.gate.clear()
        self.wait_message='';self.wait_source=''
        self.report(reason)
    def wait_frame(self,error,source):
        message=str(error)
        if message!=self.wait_message:self.report('等待悬浮条：'+message)
        self.wait_message=message;self.wait_source=source
    def recovered_frame(self,source,message='已恢复，继续当前进度'):
        if self.wait_message and (source=='input' or self.wait_source=='capture'):
            self.wait_message='';self.wait_source='';self.report('已恢复：'+message)
    def resume(self):
        if self.stop_event.is_set():return
        with self.lock:
            # An interrupted batch must obtain a fresh map reading; preserve session deadline.
            if self.thread is not None:
                deadline=self.flow.deadline;self.flow.start(round(time.monotonic()*1000));self.flow.deadline=deadline
            self.gate.set()
        self.report('继续前重新检查地图')
    def run(self):
        last=''
        try:
            if self.stop_event.is_set():return
            vision=BearVision(calibration=self.settings.get('calibration'))
            while not self.stop_event.is_set():
                if time.monotonic()*1000>=self.flow.deadline:self.report('已达到运行时长');break
                if not self.gate.wait(.2):continue
                try:
                    retry_source='capture';captured=time.monotonic();image=self.io.capture()
                    self.recovered_frame('capture');frame=vision.observe(image)
                    with self.lock:
                        if self.stop_event.is_set() or not self.gate.is_set():continue
                        if time.monotonic()-captured>4:
                            self.report('首次识别加载中，丢弃过期画面后重拍');continue
                        before=copy.deepcopy(self.flow.__dict__)
                        now=round(time.monotonic()*1000);d=self.flow.next(frame,now)
                        if d.message!=last and (not self.wait_message or d.kind=='PAUSE'):self.report(d.message);last=d.message
                        if d.kind not in ('WAIT','PAUSE'):
                            retry_source='input'
                            try:self.io.action(d)
                            except RetryFrame:
                                # The input guard rejected this decision before submitting it.
                                # Undo pending/navigation counters, retaining the current batch.
                                self.flow.__dict__.clear();self.flow.__dict__.update(before)
                                raise
                            self.flow.complete(True,round(time.monotonic()*1000))
                            self.recovered_frame('input',d.message);self.save(self.flow.nextPreset)
                        if d.kind=='PAUSE':break
                except RetryFrame as e:
                    self.wait_frame(e,retry_source)
                    self.stop_event.wait(.05);continue
                except Exception as e:
                    self.pause('已暂停：'+str(e))
                self.stop_event.wait(.65)
        except Exception as e:self.report('无法开始：'+str(e))
        finally:self.stop();self.report('已停止；点击「开始」可新开一轮')
