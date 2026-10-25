"""Check the built APK, not just the source, for obsolete multiplier OCR."""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path


def verify(apk: Path, version: str) -> dict:
    with zipfile.ZipFile(apk) as archive:
        dex = [archive.read(name) for name in archive.namelist() if name.endswith('.dex')]
        resources = archive.read('resources.arsc')
        names=archive.namelist()
        for name in ('assets/feedback/deploy.png','assets/reference/bear_label.png'):
            if name not in names:raise ValueError('Missing bear asset: '+name)
        if any('/x86' in name for name in names):raise ValueError('Unexpected desktop ABI')
    if not dex:
        raise ValueError('APK contains no DEX bytecode')
    forbidden = ('倍率未识别清楚', '请重传首页截图', '第一张图的倍率按钮不清楚',
                 '每轮倍率（自己选择）', '程序不识别、不切换游戏倍率', '本次使用券数（自动识别，可更正）')
    for text in forbidden:
        if any(text.encode('utf-8') in blob for blob in dex):
            raise ValueError('APK still contains obsolete multiplier recognition: ' + text)
    for text in ('首页实际余券', '正在切换 1 倍，等待画面确认', '首页确认余券为 0，已全部用完', '结算完成，自动返回首页'):
        if not any(text.encode('utf-8') in blob for blob in dex):
            raise ValueError('APK is missing expected behavior marker: ' + text)
    label = f'无尽冬日助手 {version} · 正中靶心'
    if not any(label.encode(encoding) in resources for encoding in ('utf-8', 'utf-16-le')):
        raise ValueError('APK is missing the visible version label: ' + label)
    return {'apk': str(apk), 'version_label': label, 'legacy_multiplier_ocr': False,
            'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--version', required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.apk, args.version), ensure_ascii=False, indent=2))
    except (ValueError, KeyError, zipfile.BadZipFile) as error:
        print('APK verification failed: ' + str(error))
        sys.exit(1)
