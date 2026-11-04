"""Replay private overflow fixtures using actual Android Bitmap/OpenCV on a test emulator.
Build :app:assembleDebug :app:assembleDebugAndroidTest -PemulatorTest first.
Does not install apps, enable accessibility, or send game gestures.
"""
import argparse,subprocess,zipfile,threading
from pathlib import Path

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--adb',required=True);p.add_argument('--serial',required=True)
p.add_argument('--fixtures',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
p.add_argument('--hunt-fixtures',type=Path);p.add_argument('--hunt-only',action='store_true')
p.add_argument('--reported-fixtures',type=Path)
p.add_argument('--dispatch-fixtures',type=Path)
a=p.parse_args()
if not a.serial.startswith('emulator-'):p.error('Use a dedicated emulator')
if (a.reported_fixtures or a.dispatch_fixtures) and not a.hunt_fixtures:p.error('Hunt fixtures are required for reported/dispatch checks')
root=Path(__file__).resolve().parents[1];a.output.mkdir(parents=True,exist_ok=True)
main=root/'app/build/outputs/apk/debug/app-debug.apk'
tests=root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
bundle=a.output/'android-feedback-input';bundle.mkdir(exist_ok=True)
for folder in ('lib','assets/feedback','assets/reference','assets/hunt','fixtures'):(bundle/folder).mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(main) as z:
    for lib in ('libc++_shared.so','libopencv_java4.so'):
        (bundle/'lib'/lib).write_bytes(z.read('lib/x86_64/'+lib))
    for name in z.namelist():
        if name.startswith(('assets/feedback/','assets/reference/','assets/hunt/')) and name.endswith(('.png','.json')):
            (bundle/name).write_bytes(z.read(name))
for name in ['reported-overflow.png','huawei-map.png','xiaomi-list.png','countdown-list.png','too-late.png']+[f'video-{s}.png' for s in (170,250,270,315,358,411,691,718,751,771,780,816,820)]:
    (bundle/'fixtures'/name).write_bytes((a.fixtures/name).read_bytes())
(bundle/'app.apk').write_bytes(main.read_bytes());(bundle/'tests.apk').write_bytes(tests.read_bytes())
if a.hunt_fixtures:
    for name in ['map','giant_search','giant_detail','rally','giant_formation','map_one','beast_search','beast_detail','beast_formation','low_energy','energy']:
        (bundle/'fixtures'/f'{name}.png').write_bytes((a.hunt_fixtures/f'{name}.png').read_bytes())
if a.reported_fixtures:
    for name in ('reported-map','reported-level'):
        (bundle/'fixtures'/f'{name}.png').write_bytes((a.reported_fixtures/f'{name}.png').read_bytes())
if a.dispatch_fixtures:
    (bundle/'fixtures/reported-deploy.png').write_bytes((a.dispatch_fixtures/'reported-deploy.png').read_bytes())
def adb(*args,timeout=180):
    r=subprocess.run([a.adb,'-s',a.serial,*args],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout)
    if r.returncode:raise RuntimeError(r.stdout+r.stderr)
    return r.stdout
remote='/data/local/tmp/wujin-feedback-210'
print(adb('shell','mkdir','-p',remote),flush=True)
print(adb('push',str(bundle)+'/.',remote,timeout=300),flush=True)
with (a.output/'android-feedback-runtime.txt').open('w',encoding='utf-8') as log:
    def run(entry,args):
        command=[a.adb,'-s',a.serial,'shell',f'CLASSPATH={remote}/app.apk:{remote}/tests.apk app_process /system/bin cn.local.winterbear.{entry} '+args]
        process=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
        # Full-resolution Matcher replay is slower under software-only emulation.
        timer=threading.Timer(900 if entry in ('CompatibilityRuntimeCheck','HuntRuntimeCheck','DirectDeployRuntimeCheck') else 300,process.kill);timer.start();lines=[]
        try:
            for line in process.stdout:
                lines.append(line);log.write(line);log.flush();print(line,end='',flush=True)
            process.wait()
        finally:timer.cancel()
        if process.returncode:raise RuntimeError('Android runtime exited early; see android-feedback-runtime.txt')
        return ''.join(lines)
    core=run('RuntimeCoreCheck',remote+'/simulation')
    if '0 failures' not in core:raise RuntimeError('ART test result missing')
    if a.dispatch_fixtures:
        result=run('DirectDeployRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets {remote}/direct-deploy')
        if 'ANDROID DIRECT DEPLOY PASS: 18 image checks;' not in result:raise RuntimeError('Direct deployment result missing')
    if a.reported_fixtures:
        result=run('HuntReportedRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets')
        if 'ANDROID REPORTED PASS:' not in result:raise RuntimeError('Reported regression result missing')
    if a.hunt_fixtures:
        result=run('HuntRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets')
        if 'ANDROID HUNT PASS:' not in result:raise RuntimeError('Hunt image test result missing')
    if a.hunt_only:raise SystemExit(0)
    result=run('FeedbackRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets')
    if 'ANDROID OPENCV PASS: 17 image cases;' not in result:raise RuntimeError('Native image test result missing')
    result=run('CountdownRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets')
    if 'ANDROID COUNTDOWN PASS: 21 cases;' not in result:raise RuntimeError('Countdown/cancel image test result missing')
    result=run('CompatibilityRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets {remote}/calibration')
    if 'ANDROID COMPAT PASS: 16 real image cases;' not in result:raise RuntimeError('Compatibility image test result missing')
