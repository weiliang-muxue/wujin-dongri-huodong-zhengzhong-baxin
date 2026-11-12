"""Run bundled ML Kit on private clock crops in a dedicated Android emulator.

Build the emulator Debug and AndroidTest APKs first. This installs only the
test app pair; it does not enable accessibility or send game gestures.
"""
import argparse
import re
import subprocess
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--adb', required=True)
p.add_argument('--serial', required=True)
p.add_argument('--fixtures', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
if not a.serial.startswith('emulator-'):
    p.error('Use a dedicated emulator')
root = Path(__file__).resolve().parents[1]
a.output.mkdir(parents=True, exist_ok=True)
files = sorted(f for f in a.fixtures.glob('clock-*.png')
               if re.fullmatch(r'clock-[0-9]+-[0-9]+\.png', f.name))
if not files:
    p.error('No exported clock crops found')

def adb(*args, timeout=300):
    result = subprocess.run([a.adb, '-s', a.serial, *args], capture_output=True,
                            text=True, encoding='utf-8', errors='replace', timeout=timeout)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout

for apk in ('app/build/outputs/apk/debug/app-debug.apk',
            'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'):
    print(adb('install', '-r', str(root / apk)), flush=True)
remote = '/data/local/tmp/wujin-loop-ocr'
package = 'cn.local.winterbear'
print(adb('shell', 'mkdir', '-p', remote), flush=True)
print(adb('shell', f'run-as {package} mkdir -p files/loop-clocks'), flush=True)
# Only private fixtures in this dedicated test app's clock directory are reset.
print(adb('shell', f"run-as {package} sh -c 'rm -f files/loop-clocks/clock-*.png'"), flush=True)
for file in files:
    adb('push', str(file), remote + '/' + file.name)
    adb('shell', f'cat {remote}/{file.name} | run-as {package} tee files/loop-clocks/{file.name} > /dev/null')
result = adb('shell', 'am', 'instrument', '-w',
             'cn.local.winterbear.test/cn.local.winterbear.HuntLoopOcrCheck', timeout=600)
(a.output / 'bundled-clock-ocr.log').write_text(result, encoding='utf-8')
print(result, flush=True)
if f'BUNDLED CLOCK OCR PASS: {len(files)} actual numeric crops' not in result or 'FAIL:' in result:
    raise RuntimeError('Bundled clock OCR failed; see bundled-clock-ocr.log')
