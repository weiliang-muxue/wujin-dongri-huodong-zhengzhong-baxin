"""Install the emulator-only debug build and verify bundled OCR plus the four-mode UI.

The emulator framework must be running. Never enables accessibility or touches a game.
Full user screenshots stay in the supplied private directory, outside the repository.
"""
import argparse
import subprocess
import threading
import time
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

def adb(*args, data=None, timeout=240):
    result = subprocess.run([a.adb, '-s', a.serial, *args], input=data,
                            capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError((result.stdout + result.stderr).decode('utf-8', errors='replace'))
    return result.stdout

deadline = time.monotonic() + 180
for attempt in range(60):
    if time.monotonic() >= deadline:
        raise RuntimeError('Emulator package service did not become ready within 180 seconds')
    try:
        state = adb('shell', 'service', 'check', 'package', timeout=20).decode(errors='replace')
        booted = adb('shell', 'getprop', 'sys.boot_completed', timeout=20).strip() == b'1'
    except subprocess.TimeoutExpired:
        state, booted = 'not found', False
    if booted and 'not found' not in state and 'found' in state:
        break
    if attempt % 10 == 0:
        print('Waiting for emulator package service...', flush=True)
    time.sleep(5)
else:
    raise RuntimeError('Emulator package service did not start')

for apk in ('debug/app-debug.apk', 'androidTest/debug/app-debug-androidTest.apk'):
    print(adb('install', '-r', '-t', str(root / 'app/build/outputs/apk' / apk)).decode(), flush=True)
adb('shell', 'run-as', 'cn.local.winterbear', 'mkdir', '-p', 'files/hunt-fixtures')
for name in ('giant_search', 'beast_search', 'giant_detail', 'beast_detail', 'map_one', 'giant_formation', 'energy'):
    adb('shell', '-T', 'run-as', 'cn.local.winterbear', 'sh', '-c',
        f"'cat > files/hunt-fixtures/{name}.png'", data=(a.fixtures / f'{name}.png').read_bytes())
command = [a.adb, '-s', a.serial, 'shell', 'am', 'instrument', '-w',
           'cn.local.winterbear.test/cn.local.winterbear.HuntDeviceCheck']
process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           text=True, encoding='utf-8', errors='replace')
timer = threading.Timer(900, process.kill)
timer.start()
lines = []
try:
    with (a.output / 'hunt-device-check.txt').open('w', encoding='utf-8') as log:
        for line in process.stdout:
            lines.append(line)
            log.write(line)
            log.flush()
            print(line, end='', flush=True)
    process.wait()
finally:
    timer.cancel()
result = ''.join(lines)
if process.returncode or 'PASS: 7 actual bundled OCR fixtures;' not in result or 'INSTRUMENTATION_CODE: -1' not in result:
    raise RuntimeError('Hunt device check failed; see hunt-device-check.txt')
for name in ('ui-modes', 'ui-giant', 'ui-beast', 'ui-folded', 'ui-permissions'):
    (a.output / f'{name}.png').write_bytes(adb('exec-out', 'run-as', 'cn.local.winterbear', 'cat', f'files/{name}.png'))
