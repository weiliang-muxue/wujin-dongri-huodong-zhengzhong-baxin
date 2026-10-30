"""Install a test build on an explicit emulator and replay private local fixtures on ART.
Uses the actual Android Matcher, bundled ML Kit and OpenCV; never taps a game.
"""
import argparse,subprocess,threading
from pathlib import Path
import cv2
from PIL import Image

parser=argparse.ArgumentParser()
parser.add_argument('--adb',required=True)
parser.add_argument('--serial',required=True)
parser.add_argument('--screenshot')
parser.add_argument('--recording')
parser.add_argument('--fixtures',help='Reuse the 14 locally prepared PNG fixtures')
parser.add_argument('--output',required=True)
args=parser.parse_args()
if not args.serial.startswith('emulator-'):parser.error('Use a dedicated emulator, not a personal phone.')
root=Path(__file__).resolve().parents[1]
out=Path(args.output).resolve();out.mkdir(parents=True,exist_ok=True)
seconds=(170,250,270,315,358,411,691,718,751,771,780,816,820)
names=['reported-formation.png']+[f'video-{second}.png' for second in seconds]
fixtures=Path(args.fixtures).resolve() if args.fixtures else out/'runtime-fixtures'
if not args.fixtures:
    if not args.screenshot or not args.recording:parser.error('Supply --fixtures or both --screenshot and --recording')
    fixtures.mkdir(exist_ok=True)
    image=Image.open(args.screenshot).convert('RGB')
    if image.size!=(1080,2400):raise ValueError('Reported screenshot must be 1080 x 2400')
    image.save(fixtures/'reported-formation.png')
    cap=cv2.VideoCapture(args.recording)
    try:
        for second in seconds:
            cap.set(cv2.CAP_PROP_POS_MSEC,second*1000);ok,bgr=cap.read()
            if not ok:raise ValueError(f'Missing recording frame: {second}')
            Image.fromarray(cv2.cvtColor(bgr,cv2.COLOR_BGR2RGB)).resize((1080,2400),Image.Resampling.LANCZOS).save(fixtures/f'video-{second}.png')
    finally:cap.release()
for name in names:
    with Image.open(fixtures/name) as image:
        if image.size!=(1080,2400):raise ValueError(f'Invalid fixture size: {name}')

def adb(*command,input=None,timeout=180):
    result=subprocess.run([args.adb,'-s',args.serial,*command],input=input,capture_output=True,timeout=timeout)
    if result.returncode:raise RuntimeError(result.stderr.decode('utf-8',errors='replace')+result.stdout.decode('utf-8',errors='replace'))
    return result.stdout.decode('utf-8',errors='replace')

print(adb('install','-r','-t',str(root/'app/build/outputs/apk/debug/app-debug.apk')),flush=True)
print(adb('install','-r','-t',str(root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')),flush=True)
adb('shell','run-as','cn.local.winterbear','mkdir','-p','files/runtime-regression')
for name in names:
    p=fixtures/name
    # File names are generated above, with no user-controlled shell syntax.
    adb('shell','-T','run-as','cn.local.winterbear','sh','-c',f"'cat > files/runtime-regression/{p.name}'",input=p.read_bytes())
print('Uploaded 14 private fixtures; starting Android runtime checks.',flush=True)
command=[args.adb,'-s',args.serial,'shell','am','instrument','-w','cn.local.winterbear.test/cn.local.winterbear.RuntimeRegression']
process=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
timeout=threading.Timer(900,process.kill);timeout.start();lines=[]
try:
    with (out/'android-runtime.txt').open('w',encoding='utf-8') as log:
        for line in process.stdout:
            lines.append(line);log.write(line);log.flush();print(line,end='',flush=True)
    process.wait()
finally:timeout.cancel()
result=''.join(lines)
if process.returncode:raise RuntimeError('Android instrumentation exited early; see android-runtime.txt')
if 'PASS: old exception reproduced;' not in result or 'INSTRUMENTATION_CODE: -1' not in result:
    raise RuntimeError('Android runtime regression failed; see android-runtime.txt')
