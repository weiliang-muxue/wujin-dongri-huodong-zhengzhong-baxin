"""Replay exported page-scale fixtures with actual Android Bitmap/Config/OpenCV.

Use a dedicated emulator and the x86_64 bundle produced by
run_feedback_regression.py. No app installation, permissions or game gestures.
"""
import argparse
import re
import subprocess
import threading
import zipfile
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--adb', required=True)
p.add_argument('--serial', required=True)
p.add_argument('--bundle', type=Path, required=True)
p.add_argument('--fixtures', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--case', action='append', required=True)
a = p.parse_args()
if not a.serial.startswith('emulator-'):
    p.error('Use a dedicated emulator')
if len(a.case) != len(set(a.case)) or any(not re.fullmatch(r'[A-Za-z0-9_.-]+', n) for n in a.case):
    p.error('Cases must be unique fixture names without paths')
for name in a.case:
    for suffix in ('.png', '.properties'):
        if not (a.fixtures / (name + suffix)).is_file():
            p.error(f'Missing fixture: {name}{suffix}')
for name in ('app.apk', 'tests.apk', 'lib/libopencv_java4.so', 'lib/libc++_shared.so'):
    if not (a.bundle / name).is_file():
        p.error(f'Missing x86_64 runtime bundle member: {name}')
with zipfile.ZipFile(a.bundle / 'tests.apk') as archive:
    if not any(b'Lcn/local/winterbear/PageScaleRuntimeCheck;' in archive.read(n)
               for n in archive.namelist() if n.endswith('.dex')):
        p.error('Stale test APK: rebuild assembleDebugAndroidTest and refresh the bundle')
a.output.mkdir(parents=True, exist_ok=True)
remote = '/data/local/tmp/wujin-page-scale'
def adb(*args):
    return subprocess.run([a.adb, '-s', a.serial, *args], check=True, capture_output=True,
                          text=True, encoding='utf-8', errors='replace', timeout=300).stdout
adb('shell', 'mkdir', '-p', remote + '/fixtures')
for name in ('app.apk', 'tests.apk', 'lib', 'assets'):
    source = a.bundle / name
    if source.is_dir():
        adb('shell', 'mkdir', '-p', remote + '/' + name)
        adb('push', str(source) + '/.', remote + '/' + name)
    else:
        adb('push', str(source), remote + '/' + name)
for name in a.case:
    for suffix in ('.png', '.properties'):
        adb('push', str(a.fixtures / (name + suffix)), remote + '/fixtures/' + name + suffix)
command = (f'OPENCV_FOR_THREADS_NUM=1 CLASSPATH={remote}/app.apk:{remote}/tests.apk '
           f'app_process /system/bin cn.local.winterbear.PageScaleRuntimeCheck '
           f'{remote}/lib {remote}/assets {remote}/fixtures {remote}/work ' + ' '.join(a.case))
with (a.output / 'page-runtime.txt').open('w', encoding='utf-8') as log:
    process = subprocess.Popen([a.adb, '-s', a.serial, 'shell', command], stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
    timer = threading.Timer(7200, process.kill)
    timer.start()
    lines = []
    try:
        for line in process.stdout:
            lines.append(line)
            log.write(line)
            log.flush()
            print(line, end='', flush=True)
        process.wait()
    finally:
        timer.cancel()
    if process.returncode or f'ANDROID PAGE SCALE COMPLETE: {len(a.case)} checks;' not in ''.join(lines):
        raise RuntimeError('Android page replay incomplete; see page-runtime.txt')
