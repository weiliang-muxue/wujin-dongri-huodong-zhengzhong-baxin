"""Compare actual Android MarchFlow and desktop port over deterministic 30-minute runs."""
import os,random,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT))
from bear_flow import MarchFlow,Frame,Row

def run(jdk):
    source=ROOT.parents[1]/'apk/源码/app/src/main/java/cn/local/winterbear'
    build=ROOT/'build/parity';build.mkdir(parents=True,exist_ok=True)
    subprocess.run([str(Path(jdk)/'bin/javac.exe'),'-encoding','UTF-8','-d',str(build),*[str(source/name) for name in ('MarchFlow.java','BearFlow.java','MarchCount.java','TroopCount.java')],str(ROOT/'tools/FlowOracle.java')],check=True)
    lines=[];expected=[];rng=random.Random(20261025)
    for scenario in range(35):
        flow=MarchFlow();flow.dry=False;flow.start(0);used=scenario%7
        for now in range(0,1801000,700):
            # Generate coherent pages plus full queues, hidden indicator, empty presets,
            # returns during dispatch, gray buttons, delayed flags and known overflow.
            phase=flow.phase;mask=1024 if now==0 else 0
            if phase in ('MAP','ENTER_LIST','RETURN_MAP'):
                mask|=1
                if scenario%3==0:mask|=32;used=-1
                elif now%21000==0:used=rng.randrange(7)
                if phase=='ENTER_LIST' and rng.random()<.8:mask=(mask&~1)|6
            elif phase in ('FIND','RETURN_LIST'):mask|=6
            elif phase in ('FORMATION','SEND'):mask|=24
            elif phase=='DISMISS_OVERFLOW':mask|=24
            else:mask|=6 if rng.random()<.8 else 24
            if scenario%2:mask|=128  # native auto-join must not change decisions
            if phase in ('SEND','VERIFY') and rng.random()<.04:mask=64
            if scenario==1 and now%7000==0:mask=24  # ambiguous navigation recovery
            troops=rng.choice([0,1,140000,160000,-1]);row=1 if rng.random()<.85 else 2;flags=63 if rng.random()<.95 else 0
            if now==0:mask|=1024
            success=0 if scenario==2 and now==2800 else 1
            f=Frame(map=bool(mask&1),header=bool(mask&2),rallyList=bool(mask&4),formation=bool(mask&8),deploy=bool(mask&16),marchHidden=bool(mask&32),troopOverflow=bool(mask&64),autoJoin=bool(mask&128),marchUsed=used,troops=(troops,150000) if troops>=0 else None,shortcutX=999,shortcutY=1060,backX=65,backY=300,deployX=832,deployY=2301,dialogOkX=540,dialogOkY=1540,savedPreset=[bool(flags&(1<<i)) for i in range(6)],rows=[Row(0,950,800,row==1)])
            d=flow.next(f,now)
            if d.kind not in ('WAIT','PAUSE'):flow.complete(bool(success),now)
            expected.append(f'{d.kind},{d.x},{d.y},{flow.phase},{flow.nextPreset},{flow.submitted},{str(flow.running).lower()}')
            lines.append(f'{now},{mask},{used},{troops},{row},{flags},{success}')
    proc=subprocess.run([str(Path(jdk)/'bin/java.exe'),'-cp',str(build),'cn.local.winterbear.FlowOracle'],input='\n'.join(lines)+'\n',text=True,capture_output=True,check=True)
    actual=proc.stdout.splitlines();assert len(actual)==len(expected),(len(actual),len(expected))
    for i,(a,b) in enumerate(zip(actual,expected)):
        assert a==b,f'Line {i}: input {lines[i]}; Android={a}; desktop={b}'
    print(f'PASS: {len(lines)} decisions across 35 virtual 30-minute sessions match Android, including clicks, phase, cursor, submitted and stopped state.')

if __name__=='__main__':run(sys.argv[1] if len(sys.argv)>1 else os.environ['JAVA_HOME'])
