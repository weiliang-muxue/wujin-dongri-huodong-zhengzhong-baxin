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
p.add_argument('--huawei-fixtures',type=Path)
p.add_argument('--adaptive-layouts',action='store_true')
p.add_argument('--bullseye-fixtures',type=Path)
p.add_argument('--return-fixtures',type=Path,help='Private Xiaomi rally / returning / returned screenshots')
p.add_argument('--paging-fixtures',type=Path,help='Private short-countdown recording frames')
p.add_argument('--suite',action='append',choices=['core','session','presets','bullseye','adaptive','huawei','direct','reported','marches','return','paging','overlay','hunt','feedback','countdown','compat'],help='Run selected suites; default runs every supplied suite')
p.add_argument('--adaptive-bear-only',action='store_true')
a=p.parse_args()
if not a.serial.startswith('emulator-'):p.error('Use a dedicated emulator')
if (a.reported_fixtures or a.dispatch_fixtures) and not a.hunt_fixtures:p.error('Hunt fixtures are required for reported/dispatch checks')
if a.adaptive_layouts and not (a.hunt_fixtures and a.huawei_fixtures):p.error('Adaptive layouts require hunt and Huawei fixtures')
root=Path(__file__).resolve().parents[1];a.output.mkdir(parents=True,exist_ok=True)
def wanted(name):return not a.suite or name in a.suite
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
if a.huawei_fixtures:
    for name in ('huawei-giant','huawei-formation','huawei-giant-video','huawei-map-video','huawei-bear-list'):
        (bundle/'fixtures'/f'{name}.png').write_bytes((a.huawei_fixtures/f'{name}.png').read_bytes())
if a.bullseye_fixtures:
    for name in ('bullseye-home','bullseye-board'):
        (bundle/'fixtures'/f'{name}.png').write_bytes((a.bullseye_fixtures/f'{name}.png').read_bytes())
if a.return_fixtures:
    for name in ('xiaomi-rally','xiaomi-returning','xiaomi-returned-toast','xiaomi-returned'):
        (bundle/'fixtures'/f'{name}.jpg').write_bytes((a.return_fixtures/f'{name}.jpg').read_bytes())
if a.paging_fixtures:
    for second in (6,12,18):
        name=f'paging-{second}.png'
        (bundle/'fixtures'/name).write_bytes((a.paging_fixtures/name).read_bytes())
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
        # All pixel suites are slower under software-only emulation, including
        # the reported-level/glyph checks. Keep a bounded timeout for every suite.
        limit=300 if entry=='OverlayUiRuntimeCheck' else 600 if entry=='RuntimeCoreCheck' else 1800
        timer=threading.Timer(limit,process.kill);timer.start();lines=[]
        try:
            for line in process.stdout:
                lines.append(line);log.write(line);log.flush();print(line,end='',flush=True)
            process.wait()
        finally:timer.cancel()
        if process.returncode:raise RuntimeError('Android runtime exited early; see android-feedback-runtime.txt')
        return ''.join(lines)
    if wanted('core'):
        core=run('RuntimeCoreCheck',remote+'/simulation')
        if '0 failures' not in core:raise RuntimeError('ART test result missing')
    if wanted('session'):
        state=run('HuntStateRuntimeCheck','')
        if 'ANDROID HUNT STATE PASS: 4' not in state:raise RuntimeError('Hunt state regression missing')
        controller=run('HuntControllerStateRuntimeCheck','')
        if 'ANDROID HUNT CONTROLLER PASS: 10' not in controller:raise RuntimeError('Hunt controller regression missing')
        bullseye=run('BullseyeSessionRuntimeCheck','')
        if 'ANDROID BULLSEYE SESSION PASS: 14' not in bullseye:raise RuntimeError('Bullseye session regression missing')
    if wanted('presets'):
        result=run('PresetSettingsRuntimeCheck',f'{remote}/assets {remote}/preset-config')
        if 'ANDROID PRESET SETTINGS PASS: 68' not in result:raise RuntimeError('Preset settings regression result missing')
    if a.bullseye_fixtures and wanted('bullseye'):
        result=run('BullseyeLayoutRuntimeCheck',f'{remote}/lib {remote}/fixtures')
        if 'ANDROID BULLSEYE LAYOUT PASS:' not in result:raise RuntimeError('Bullseye layout result missing')
    if a.adaptive_layouts and wanted('adaptive'):
        result=run('AdaptiveRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets {remote}/adaptive-calibration'+(' bear' if a.adaptive_bear_only else ''))
        if 'ANDROID ADAPTIVE PASS:' not in result:raise RuntimeError('Adaptive image result missing')
    if a.huawei_fixtures and wanted('huawei'):
        result=run('HuaweiRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets {remote}/huawei-calibration')
        if 'ANDROID HUAWEI PASS:' not in result:raise RuntimeError('Huawei image result missing')
    if a.dispatch_fixtures and wanted('direct'):
        result=run('DirectDeployRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets {remote}/direct-deploy')
        if 'ANDROID DIRECT DEPLOY PASS: 18 image checks;' not in result:raise RuntimeError('Direct deployment result missing')
    if a.reported_fixtures and wanted('reported'):
        result=run('HuntReportedRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets')
        if 'ANDROID REPORTED PASS:' not in result:raise RuntimeError('Reported regression result missing')
    if a.reported_fixtures and a.suite and 'marches' in a.suite:
        result=run('HuntReportedRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets maps')
        if 'ANDROID REPORTED PASS: 12' not in result:raise RuntimeError('March image regression result missing')
    if a.return_fixtures and wanted('return'):
        result=run('HuntReturnRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets')
        if 'ANDROID RETURN PASS: 32' not in result:raise RuntimeError('Return image regression result missing')
    if a.paging_fixtures and wanted('paging'):
        result=run('BearPagingRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets {remote}/paging-calibration')
        if 'ANDROID PAGING PASS: 15' not in result:raise RuntimeError('Paging regression result missing')
    if a.suite and 'overlay' in a.suite:
        result=run('OverlayUiRuntimeCheck',f'{remote}/overlay-ui')
        if 'ANDROID OVERLAY UI PASS: 48' not in result:raise RuntimeError('Overlay UI regression result missing')
        if 'ANDROID PRESET PICKER PASS: 12' not in result:raise RuntimeError('Preset picker regression result missing')
        overlay_output=a.output/'overlay-ui'
        overlay_output.mkdir(parents=True,exist_ok=True)
        render_names=['preset-picker.png']
        for label in ('打熊','靶心','巨兽','野兽'):
            for state in ('expanded','collapsed'):
                render_names.append(f'{label}-{state}.png')
        for name in render_names:
            transfer=subprocess.run([a.adb,'-s',a.serial,'exec-out','cat',f'{remote}/overlay-ui/{name}'],
                                    capture_output=True,timeout=30,check=True)
            (overlay_output/name).write_bytes(transfer.stdout)
        print(f'Overlay renders saved to {overlay_output}',flush=True)
    if a.hunt_fixtures and wanted('hunt'):
        result=run('HuntRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets')
        if 'ANDROID HUNT PASS:' not in result:raise RuntimeError('Hunt image test result missing')
    if a.hunt_only:raise SystemExit(0)
    if wanted('feedback'):
        result=run('FeedbackRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets')
        if 'ANDROID OPENCV PASS: 17 image cases;' not in result:raise RuntimeError('Native image test result missing')
    if wanted('countdown'):
        result=run('CountdownRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets')
        if 'ANDROID COUNTDOWN PASS: 21 cases;' not in result:raise RuntimeError('Countdown/cancel image test result missing')
    if wanted('compat'):
        result=run('CompatibilityRuntimeCheck',f'{remote}/lib {remote}/fixtures {remote}/assets {remote}/calibration')
        if 'ANDROID COMPAT PASS: 16 real image cases;' not in result:raise RuntimeError('Compatibility image test result missing')
