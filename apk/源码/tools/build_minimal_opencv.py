"""Build OpenCV 4.12.0 core/imgproc/Java for ARM32, ARM64 and the test emulator.

Uses upstream source and an installed Android NDK; no generated libraries are committed.
The upstream AAR's Java API/resources/licenses are retained, replacing only native libraries.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--source', type=Path, required=True)
p.add_argument('--ndk', type=Path, required=True)
p.add_argument('--original-aar', type=Path, required=True)
p.add_argument('--work', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--cmake', default='cmake')
p.add_argument('--ninja', default='ninja')
p.add_argument('--jobs', type=int, default=4)
p.add_argument('--package-only', action='store_true', help='Package already built, verified outputs')
a = p.parse_args()
commit = subprocess.check_output(['git', '-C', str(a.source), 'rev-parse', 'HEAD'], text=True).strip()
if commit != '49486f61fb25722cbcf586b7f4320921d46fb38e':
    p.error('Expected the upstream OpenCV 4.12.0 commit')
host = 'windows-x86_64' if os.name == 'nt' else 'darwin-x86_64' if sys.platform == 'darwin' else 'linux-x86_64'
triples = {'arm64-v8a': 'aarch64-linux-android', 'armeabi-v7a': 'arm-linux-androideabi', 'x86_64': 'x86_64-linux-android'}
libraries = {}
for abi, triple in triples.items():
    build = a.work / ('build-' + abi)
    options = {
        'CMAKE_TOOLCHAIN_FILE': str(a.ndk / 'build/cmake/android.toolchain.cmake'),
        'CMAKE_MAKE_PROGRAM': a.ninja, 'ANDROID_ABI': abi, 'ANDROID_PLATFORM': 'android-30',
        'ANDROID_STL': 'c++_shared', 'CMAKE_BUILD_TYPE': 'MinSizeRel',
        'BUILD_LIST': 'core,imgproc,java', 'BUILD_SHARED_LIBS': 'OFF', 'BUILD_FAT_JAVA_LIB': 'ON',
        'BUILD_ANDROID_PROJECTS': 'OFF', 'ANDROID_PROJECTS_BUILD_TYPE': 'GRADLE', 'BUILD_opencv_java': 'ON',
        'BUILD_ANDROID_EXAMPLES': 'OFF', 'BUILD_TESTS': 'OFF', 'BUILD_PERF_TESTS': 'OFF',
        'BUILD_EXAMPLES': 'OFF', 'BUILD_DOCS': 'OFF', 'WITH_OPENCL': 'OFF', 'WITH_IPP': 'OFF',
        'WITH_ITT': 'OFF', 'WITH_TBB': 'OFF', 'WITH_CAROTENE': 'OFF', 'WITH_OPENEXR': 'OFF',
        'BUILD_KOTLIN_EXTENSIONS': 'OFF', 'PYTHON3_EXECUTABLE': sys.executable,
        'PYTHON_DEFAULT_EXECUTABLE': sys.executable, 'OPENCV_SKIP_FAT_JAVA_LIB_LD_RULES': 'ON',
        'ENABLE_LTO': 'ON',
    }
    if not a.package_only:
        subprocess.run([a.cmake, '-S', str(a.source), '-B', str(build), '-G', 'Ninja'] +
                       ['-D' + k + '=' + v for k, v in options.items()], check=True)
        subprocess.run([a.cmake, '--build', str(build), '--target', 'opencv_java', '-j', str(a.jobs)], check=True)
    inputs = {
        'libopencv_java4.so': build / f'jni/{abi}/libopencv_java4.so',
        'libc++_shared.so': a.ndk / f'toolchains/llvm/prebuilt/{host}/sysroot/usr/lib/{triple}/libc++_shared.so',
    }
    strip = a.ndk / f'toolchains/llvm/prebuilt/{host}/bin' / ('llvm-strip.exe' if os.name == 'nt' else 'llvm-strip')
    for name, source in inputs.items():
        if not source.is_file():
            p.error('Missing native library: ' + str(source))
        # The NDK ships libc++ with debug symbols. Strip a packaging copy so that
        # offline Gradle builds without an SDK-installed NDK stay small too.
        target = a.work / 'package-libs' / abi / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        subprocess.run([str(strip), '--strip-unneeded', str(target)], check=True)
        libraries[f'jni/{abi}/{name}'] = target
a.output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(a.original_aar) as old, zipfile.ZipFile(a.output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as new:
    for entry in old.infolist():
        if not entry.filename.startswith(('jni/', 'prefab/')):
            new.writestr(entry, old.read(entry))
    for name, path in libraries.items():
        new.writestr(name, path.read_bytes())
    new.writestr('META-INF/LICENSE-OpenCV.txt', (a.source / 'LICENSE').read_bytes())
report = {'opencvCommit': commit, 'ndk': (a.ndk / 'source.properties').read_text(), 'strip': '--strip-unneeded',
          'sha256': {name: hashlib.sha256(path.read_bytes()).hexdigest() for name, path in libraries.items()}}
a.output.with_suffix('.build.json').write_text(json.dumps(report, indent=2), encoding='utf8')
print(f'Built {a.output.name}: {a.output.stat().st_size:,} bytes')
