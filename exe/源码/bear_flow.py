"""Desktop port of Android MarchFlow. Millisecond clock; no screenshots or clicks here."""
from dataclasses import dataclass, field

@dataclass
class Row:
    order: int
    x: int
    y: int
    available: bool = True

@dataclass
class Frame:
    header: bool = False
    deploy: bool = False
    map: bool = False
    formation: bool = False
    autoJoin: bool = False
    marchHidden: bool = False
    rallyList: bool = False
    troopOverflow: bool = False
    marchUsed: int = -1
    dialogOkX: int = 0
    dialogOkY: int = 0
    troops: tuple | None = None
    shortcutX: int = 0
    shortcutY: int = 0
    allianceX: int = 0
    allianceY: int = 0
    warX: int = 0
    warY: int = 0
    rallyX: int = 0
    rallyY: int = 0
    worldX: int = 0
    worldY: int = 0
    backX: int = 0
    backY: int = 0
    deployX: int = 0
    deployY: int = 0
    savedPreset: list = field(default_factory=lambda: [False]*6)
    rows: list = field(default_factory=list)

@dataclass
class Decision:
    kind: str = 'WAIT'
    x: int = 0
    y: int = 0
    message: str = ''

class MarchFlow:
    def __init__(self):
        self.presetX=[98,209,319,431,542,654];self.presetY=[427]*6
        self.enabled=[True]*6;self.dry=True;self.running=False
        self.maxMinutes=30;self.nextPreset=0;self.submitted=0;self.phase='MAP';self.pending=None
    def start(self, now):
        self.running=True;self.pending=None;self.phase='MAP';self.phaseSince=now;self.waitUntil=now;self.lastScroll=now
        self.deadline=now+max(1,min(30,self.maxMinutes))*60000
        self.checkedAt=-1;self.unknownSince=-1;self.lastNav=-1;self.remaining=0;self.charged=False;self.attemptedMask=0;self.retryAt=0
        self.failedRowY=-1;self.lastJoinY=-1;self.dialogAttempts=0;self.avoidRowUntil=0;self.markerMissingSince=-1
        self.selected=-1;self.resetEvidence();self.nextPreset%=6
    def stop(self):
        self.running=False;self.pending=None;self.checkedAt=-1;self.remaining=0;self.resetEvidence()
    def resetEvidence(self):self.stableUsed=-9;self.stableReads=0
    def wait(self, message):return Decision(message=message)
    def pause(self, message):self.stop();return Decision('PAUSE',message=message)
    def act(self, kind,x,y,nxt,message):
        self.pending=kind;self.afterAction=nxt;return Decision(kind,x,y,message)
    def choosePreset(self):
        return next((i for n in range(6) if not self.attemptedMask&(1<<(i:=(self.nextPreset+n)%6)) and self.enabled[i] and self.presetX[i]>0 and self.presetY[i]>0),-1)
    def returnToMap(self,now):
        self.phase='RETURN_MAP';self.returnSince=now;self.backAttempts=0;self.checkedAt=-1;self.remaining=0;self.resetEvidence()
    def returnToList(self,now):self.phase='RETURN_LIST';self.returnSince=now;self.backAttempts=0;self.unknownSince=-1
    def avoidLastRow(self,now):self.failedRowY=self.lastJoinY;self.avoidRowUntil=now+12000
    def unrecognized(self,now,message):
        if self.unknownSince<0:self.unknownSince=now
        return self.pause(message) if now-self.unknownSince>=15000 else self.wait(message+' · 重新识别')
    def navigate(self,node,x,y,now,message):
        if self.lastNav==node:return self.pause('页面未切换，请检查入口') if now-self.phaseSince>8000 else self.wait('等待页面切换')
        self.lastNav=node;self.phaseSince=now;return self.act('NAVIGATE',x,y,'ENTER_LIST',message)
    def nextFormation(self,now,reason):
        self.attemptedMask|=1<<self.selected;self.selected=self.choosePreset();self.markerMissingSince=-1
        if self.selected<0:
            self.retryAt=now+8000;self.returnToMap(now);return self.wait(reason+' · 本轮预设不可用，稍后重查')
        self.phase='FORMATION';self.phaseSince=now;self.unknownSince=-1
        return self.wait(reason+f' · 尝试第 {self.selected+1} 个预设')
    def next(self,f,now):
        if not self.running:return self.wait('已暂停')
        if now>=self.deadline:return self.pause('已达到本次运行时长')
        if self.pending or now<self.waitUntil:return self.wait('等待界面响应')
        if sum([f.map,f.header,f.formation])>1:return self.pause('页面识别冲突，请核对游戏区域')
        hidden=f.marchHidden and not 0<=f.marchUsed<=6
        if self.dry:
            if f.map:return self.wait('地图 · '+('行军栏未显示，可尝试加入' if hidden else f'行军 {f.marchUsed}/6')+' · 只识别')
            return self.wait('集结列表 · 只识别' if f.header else '等待游戏地图或集结列表 · 只识别')
        if self.phase=='DISMISS_OVERFLOW':
            if f.troopOverflow:
                if now-self.dialogSince>=8000:return self.pause('超编提示未关闭')
                if now-self.dialogSince>=3000 and self.dialogAttempts<2 and f.dialogOkX>0:
                    self.dialogAttempts+=1;return self.act('NAVIGATE',f.dialogOkX,f.dialogOkY,'DISMISS_OVERFLOW','再次关闭超编提示')
                return self.wait('等待超编提示关闭')
            if f.formation or f.deploy:self.phase='FORMATION';self.phaseSince=now;self.unknownSince=-1
            elif f.header:self.returnToList(now)
            elif f.map:self.phase='MAP';self.checkedAt=-1;self.remaining=0;self.resetEvidence()
            else:return self.unrecognized(now,'等待提示关闭后的页面')
        if f.troopOverflow and f.dialogOkX>0:
            if self.phase not in ('VERIFY','SEND','FORMATION'):return self.pause('请处理当前游戏提示')
            if self.charged:self.remaining+=1;self.charged=False
            if self.selected>=0:self.attemptedMask|=1<<self.selected
            self.selected=self.choosePreset();self.markerMissingSince=-1;self.dialogSince=now;self.dialogAttempts=1
            return self.act('NAVIGATE',f.dialogOkX,f.dialogOkY,'DISMISS_OVERFLOW','超出兵力上限，关闭后换预设')
        if self.phase=='RETURN_MAP':
            if f.map:
                self.phase='MAP';self.phaseSince=now;self.unknownSince=-1;self.attemptedMask=0;self.resetEvidence();return self.wait('已回地图，检查空位')
            if now-self.returnSince>20000 or self.backAttempts>=6:return self.pause('未能返回地图')
            if f.worldX>0:self.backAttempts+=1;return self.act('NAVIGATE',f.worldX,f.worldY,'RETURN_MAP','进入野外')
            if (f.header or f.formation or f.deploy or f.warX>0 or f.rallyX>0) and f.backX>0:
                self.backAttempts+=1;return self.act('BACK',f.backX,f.backY,'RETURN_MAP','返回地图检查空位')
            return self.wait('等待可识别的返回页面')
        if self.phase=='MAP':
            if now<self.retryAt:return self.wait('等待编队恢复')
            if not f.map:
                if f.worldX>0:return self.act('NAVIGATE',f.worldX,f.worldY,'MAP','进入野外')
                if f.header or f.formation or f.warX>0 or f.rallyX>0:self.returnToMap(now);return self.wait('先确认地图状态')
                return self.unrecognized(now,'未识别游戏地图')
            reading=-2 if hidden else f.marchUsed
            if not hidden and not 0<=reading<=6:self.resetEvidence();return self.unrecognized(now,'行军数字模糊')
            self.unknownSince=-1
            if reading!=self.stableUsed:self.stableUsed=reading;self.stableSince=now;self.stableReads=1
            elif now-self.stableSince>=500:self.stableReads+=1
            if self.stableReads<2:return self.wait('确认地图状态')
            if reading==6:self.checkedAt=-1;return self.wait('行军 6/6，等待返回')
            if self.deadline-now<12000:return self.wait('即将结束，停止新上车')
            self.remaining=6 if hidden else 6-reading;self.attemptedMask=0;self.selected=self.choosePreset()
            if self.selected<0:return self.pause('没有启用的预设')
            self.checkedAt=now;self.phase='ENTER_LIST';self.lastNav=-1;self.phaseSince=now
        if f.map and self.phase in ('FIND','FORMATION','SEND'):
            self.phase='MAP';self.checkedAt=-1;self.remaining=0;self.resetEvidence();self.unknownSince=-1;return self.wait('重新检查地图空位')
        if self.phase in ('ENTER_LIST','FIND','FORMATION','SEND','RETURN_LIST'):
            if self.checkedAt<0 or now-self.checkedAt>90000 or self.remaining<=0:self.returnToMap(now);return self.wait('本批已用完或需更新空位')
        if self.phase=='ENTER_LIST':
            if f.header and f.rallyList:self.phase='FIND';self.phaseSince=now;self.lastScroll=now;self.unknownSince=-1
            elif f.map:
                if f.marchUsed==6:self.phase='MAP';self.checkedAt=-1;self.resetEvidence();return self.wait('行军已满')
                if f.shortcutX>0:return self.navigate(0,f.shortcutX,f.shortcutY,now,'打开红色集结入口')
                if f.allianceX>0:return self.navigate(0,f.allianceX,f.allianceY,now,'打开联盟')
                self.phase='MAP';self.checkedAt=-1;self.resetEvidence();self.waitUntil=now+2000;return self.wait('等待红色集结入口')
            elif f.warX>0:return self.navigate(1,f.warX,f.warY,now,'打开联盟战争')
            elif f.rallyX>0:return self.navigate(2,f.rallyX,f.rallyY,now,'打开集结列表')
            elif f.header and f.backX>0:
                self.returnToList(now);self.backAttempts+=1;return self.act('BACK',f.backX,f.backY,'RETURN_LIST','退出集结详情')
            else:return self.unrecognized(now,'等待集结入口中间页面')
        if self.phase=='RETURN_LIST':
            if f.header and f.rallyList:self.phase='FIND';self.phaseSince=now;self.unknownSince=-1
            elif f.map:self.phase='MAP';self.checkedAt=-1;self.resetEvidence();return self.wait('已回地图')
            elif now-self.returnSince>=20000 or self.backAttempts>=6:return self.pause('未能返回集结列表')
            elif (f.header or f.formation or f.deploy) and f.backX>0:
                self.backAttempts+=1;return self.act('BACK',f.backX,f.backY,'RETURN_LIST','返回集结列表')
            else:return self.unrecognized(now,'等待回到集结列表')
        if self.phase=='FIND':
            if not f.header or not f.rallyList or f.deploy:return self.unrecognized(now,'等待集结列表')
            self.unknownSince=-1
            row=min((r for r in f.rows if r.order>=0 and r.available and r.x>0 and r.y>0 and (now>=self.avoidRowUntil or abs(r.y-self.failedRowY)>60)),key=lambda r:r.order,default=None)
            if row:
                if self.deadline-now<8000:return self.wait('即将结束，停止新上车')
                self.selected=self.choosePreset()
                if self.selected<0:self.retryAt=now+8000;self.returnToMap(now);return self.wait('已检查全部预设，稍后重查')
                self.lastJoinY=row.y;self.charged=False;self.markerMissingSince=-1
                return self.act('JOIN',row.x,row.y,'FORMATION',f'点击绿色＋ · 预设 {self.selected+1}')
            if now-self.lastScroll>=5000:self.lastScroll=now;return self.act('SCROLL',0,0,'FIND','翻看下一屏巨熊')
            return self.wait('等待可加入的巨熊绿色＋')
        if self.phase=='FORMATION':
            if self.selected<0:self.retryAt=now+8000;self.returnToMap(now);return self.wait('预设暂时不可用')
            if (f.formation or f.deploy) and not f.header:
                if not f.savedPreset[self.selected]:
                    if self.markerMissingSince<0:self.markerMissingSince=now
                    return self.pause('预设旗帜持续未匹配，请核对旗帜栏') if now-self.markerMissingSince>=6000 else self.wait('等待预设旗帜清晰')
                self.markerMissingSince=-1
                return self.act('PRESET',self.presetX[self.selected],self.presetY[self.selected],'SEND',f'选择第 {self.selected+1} 个预设')
            if f.header and not f.rallyList:self.avoidLastRow(now);self.returnToList(now);return self.wait('集结已发车，换下一队')
            if now-self.phaseSince>5000 and f.header:self.avoidLastRow(now);self.phase='FIND';return self.wait('未进入出征页，跳过此集结')
            return self.unrecognized(now,'等待出征页面')
        if self.phase=='SEND':
            if f.header:self.returnToList(now);self.avoidLastRow(now);return self.wait('集结状态已变化')
            if f.deploy and not f.header and not f.map:
                if f.troops is not None and not 0<f.troops[0]<=f.troops[1]:return self.nextFormation(now,'兵力为空或超限')
                if f.troops is None:return self.nextFormation(now,'兵力未读清') if now-self.phaseSince>6000 else self.wait('检查实际兵力')
                return self.act('DEPLOY',f.deployX,f.deployY,'VERIFY',f'提交预设 {self.selected+1} 出征')
            return self.unrecognized(now,'等待出征按钮')
        if self.phase=='VERIFY':
            if (f.header or f.map) and not f.deploy:
                self.submitted+=1;self.nextPreset=(self.selected+1)%6;self.attemptedMask|=1<<self.selected;self.charged=False;self.unknownSince=-1
                if f.header and f.rallyList and self.remaining>0 and self.choosePreset()>=0 and now-self.checkedAt<90000:
                    self.phase='FIND';self.phaseSince=now;return self.wait('已提交 · 留在列表继续下一队')
                self.returnToMap(now);return self.wait('本批已提交，检查新的空位')
            if now-self.phaseSince>7000 and (f.formation or f.deploy):
                self.nextPreset=(self.selected+1)%6;self.attemptedMask|=1<<self.selected;self.returnToList(now);self.avoidLastRow(now);return self.wait('结果未确认，保留占用并继续')
            return self.unrecognized(now,'等待出征结果')
        return self.wait('等待画面')
    def complete(self,success,now):
        kind=self.pending;self.pending=None
        if not self.running or not kind:return
        if not success or now>=self.deadline:self.stop();return
        if kind=='DEPLOY':self.remaining=max(0,self.remaining-1);self.charged=True;self.nextPreset=(self.selected+1)%6;self.attemptedMask|=1<<self.selected
        if self.phase!=self.afterAction:self.phase=self.afterAction;self.phaseSince=now;self.unknownSince=-1
        self.waitUntil=now+(550 if kind=='PRESET' else 700)
