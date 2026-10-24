from __future__ import annotations
import random
import threading
import time
from vision import Vision

class Runner:
    def __init__(self, config, home, board, capture, tap, report):
        self.c=config;self.home=home;self.board=board
        self.capture=capture;self.tap=tap;self.report=report;self.vision=Vision()
        self.gate=threading.Event();self.gate.set();self.stopped=threading.Event()
        self.lock=threading.RLock();self.thread=None
        self.remaining=int(config['count']);self.rounds=0;self.in_round=False
        self.pending=None;self.visited=set();self.last_known=time.monotonic()
        self.last_action=0;self.retries=0;self.start_retries=0
        self.allow_initial_board=True
        self.exit_seen=0;self.exit_attempts=0
        self.count_candidate=None;self.count_seen=0;self.count_misses=0
        self.switch_attempts=0;self.button_misses=0

    def pause(self, reason='已暂停，可调整后继续'):
        with self.lock:self.gate.clear()
        self.report('paused',reason,self.remaining,self.rounds)

    def resume(self):
        if self.stopped.is_set():return
        self.count_candidate=None;self.count_seen=0;self.count_misses=0
        self.switch_attempts=0;self.button_misses=0
        self.last_known=time.monotonic();self.gate.set()
        self.report('running','继续前重新识别当前画面',self.remaining,self.rounds)

    def stop(self):
        with self.lock:self.stopped.set();self.gate.set()

    def click(self,p):
        with self.lock:
            if self.stopped.is_set() or not self.gate.is_set():return False
            self.tap(p);self.last_action=time.monotonic()
        return True

    def start(self):
        self.thread=threading.Thread(target=self.run,daemon=True);self.thread.start()

    def run(self):
        try:
            while not self.stopped.is_set():
                if not self.gate.wait(.2):continue
                if time.monotonic()-self.last_action<2.3:
                    self.stopped.wait(.15);continue
                frame=self.capture()
                if abs(frame.width/frame.height-self.home.width/self.home.height)>.015:
                    self.pause('屏幕比例已改变，请恢复原分辨率后继续');continue
                exit_point=self.vision.dismiss_point(frame,self.c['exit_region'])
                if not self.gate.is_set() or self.stopped.is_set():continue
                if exit_point:
                    self.last_known=time.monotonic();self.exit_seen+=1
                    if self.exit_seen<2:self.stopped.wait(.4);continue
                    if self.exit_attempts>=3:
                        self.pause('结算页退出没有响应，请检查画面后继续');self.exit_attempts=0;continue
                    if self.click(exit_point):
                        self.exit_attempts+=1;self.exit_seen=0
                        self.report('running','已识别结算退出提示，自动返回活动首页',self.remaining,self.rounds)
                    continue
                self.exit_seen=0
                text_items=self.vision.text(Vision.crop(frame,self.c['region']))
                if not self.gate.is_set() or self.stopped.is_set():continue
                joined=' '.join(str(t[1]) for t in text_items)
                if any(s in joined.replace(' ','') for s in ('点击任意位置','点击屏幕退出')):
                    match=next((t for t in text_items if '点击' in t[1]),None)
                    if match:
                        box=match[0];x=sum(p[0] for p in box)/len(box);y=sum(p[1] for p in box)/len(box)
                        r=self.c['region'];crop=Vision.crop(frame,r)
                        p=(r[0]+x/crop.width*(r[2]-r[0]),r[1]+y/crop.height*(r[3]-r[1]))
                    else:p=self.c.get('exit',(0.5,.93))
                    self.click(p);self.last_known=time.monotonic();continue
                start=self.c['start']
                home_match=Vision.similarity(Vision.patch(frame,start,.025),Vision.patch(self.home,start,.025))>.68
                if home_match:
                    self.exit_attempts=0
                    self.allow_initial_board=False
                    self.last_known=time.monotonic()
                    if self.in_round:
                        self.in_round=False;self.rounds+=1;self.visited.clear();self.pending=None
                        self.count_candidate=None;self.count_seen=0;self.count_misses=0
                        self.switch_attempts=0;self.button_misses=0
                    read=self.vision.number(Vision.crop(frame,self.c['count_region']))
                    if read is None:
                        self.count_candidate=None;self.count_seen=0;self.count_misses+=1
                        if self.count_misses>=3:self.pause('首页券数暂时读不清，请保持右上角券数可见后继续')
                        else:self.stopped.wait(.4)
                        continue
                    self.count_misses=0
                    self.count_seen=self.count_seen+1 if read==self.count_candidate else 1
                    self.count_candidate=read
                    # Confirm twice, including zero, before spending or declaring done.
                    if self.count_seen<2:self.stopped.wait(.4);continue
                    self.remaining=read
                    self.report('running',f'活动页：剩余 {self.remaining} 张，已完成 {self.rounds} 轮',self.remaining,self.rounds)
                    if self.remaining<=0:break
                    if self.remaining<5:
                        button=self.vision.low_balance_button(frame,self.c)
                        if button is None:
                            self.button_misses+=1
                            if self.button_misses>=3:self.pause('余券不足 5 张，右下角倍率按钮被遮挡或样式不匹配，请保持按钮可见后继续')
                            else:self.stopped.wait(.4)
                            continue
                        self.button_misses=0
                        value,point=button
                        if value==5:
                            if self.switch_attempts>=2:
                                self.pause('切换 1 倍没有响应，请检查游戏后继续');continue
                            if self.click(point):
                                self.switch_attempts+=1
                                self.report('running',f'剩余 {self.remaining} 张，正在切换 1 倍，等待画面确认',self.remaining,self.rounds)
                            continue
                        if self.switch_attempts:
                            self.report('running','已确认切换为 1 倍，继续使用余券',self.remaining,self.rounds)
                        self.switch_attempts=0
                    if self.pending=='start' and self.start_retries>=3:
                        self.pause('开始按钮没有响应，请检查游戏后继续');self.start_retries=0;continue
                    if self.click(start):
                        self.pending='start';self.retries=0;self.start_retries+=1
                        self.count_candidate=None;self.count_seen=0
                    continue
                available=[]
                for i,p in enumerate(self.c['targets']):
                    if Vision.similarity(Vision.patch(frame,p),Vision.patch(self.board,p))>.72:available.append(i)
                if available:
                    self.last_known=time.monotonic()
                    if self.pending=='start':
                        self.in_round=True;self.pending=None;self.start_retries=0
                    if not self.in_round:
                        if self.allow_initial_board and len(available)==12:
                            self.in_round=True;self.allow_initial_board=False
                            self.report('running','继续当前已开启的一轮，结束后读取最新券数',self.remaining,self.rounds)
                        else:
                            self.pause('当前靶子页不完整，请回到首页或完整的 12 靶子页再开始');continue
                    if isinstance(self.pending,int):
                        if self.pending not in available:
                            self.visited.add(self.pending);self.pending=None;self.retries=0
                        elif self.retries<2:
                            self.click(self.c['targets'][self.pending]);self.retries+=1;continue
                        else:self.pause('靶子没有响应，请检查位置或游戏状态');continue
                    choices=[i for i in available if i not in self.visited]
                    if choices:
                        i=random.choice(choices)
                        if self.click(self.c['targets'][i]):self.pending=i
                        self.report('running',f'第 {self.rounds+1} 轮：射击 {i+1} 号靶子',self.remaining,self.rounds)
                elif time.monotonic()-self.last_known>16:
                    self.pause('画面不属于已校准的活动，已暂停。请检查游戏窗口');continue
                self.stopped.wait(.4)
            self.report('stopped' if self.stopped.is_set() else 'done','已停止' if self.stopped.is_set() else '首页确认余券为 0，已全部用完',self.remaining,self.rounds)
        except Exception as e:self.report('error',str(e),self.remaining,self.rounds)
