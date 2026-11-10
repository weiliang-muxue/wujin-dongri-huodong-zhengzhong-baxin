"""Check the built APK for supported assets, phone ABIs and removed OCR gates."""
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
        for name in ('assets/feedback/deploy.png','assets/reference/bear_label.png',
                     'assets/feedback/troop_overflow_v2.png','assets/feedback/confirm_ok_v2.png'):
            if name not in names:raise ValueError('Missing bear asset: '+name)
        if tuple(map(int,version.split('.'))) >= (2,1,0):
            for key in ('map_search','search','rally_action','attack_action','rally_start','energy_can','energy_use','energy_close'):
                if f'assets/hunt/{key}.png' not in names:raise ValueError('Missing hunt asset: '+key)
        if tuple(map(int,version.split('.'))) >= (2,1,1):
            for key in ('level_label','level_digits','march_digits'):
                if f'assets/hunt/{key}.png' not in names:raise ValueError('Missing hunt number asset: '+key)
        stamina_budget = tuple(map(int,version.split('.'))) >= (2,1,4)
        if stamina_budget:
            for key in ('meat','wood','coal','iron','rally','return'):
                if f'assets/hunt/march_{key}.png' not in names:raise ValueError('Missing march identity asset: '+key)
        if any('/x86' in name for name in names):raise ValueError('Unexpected desktop ABI')
        abis={name.split('/')[1] for name in names if name.startswith('lib/')}
        if abis!={'arm64-v8a','armeabi-v7a'}:raise ValueError('Missing supported phone ABI: '+str(abis))
        for abi in abis:
            for lib in ('libopencv_java4.so','libc++_shared.so','libmlkit_google_ocr_pipeline.so'):
                if f'lib/{abi}/{lib}' not in names:raise ValueError('Missing native runtime: '+abi+'/'+lib)
    if not dex:
        raise ValueError('APK contains no DEX bytecode')
    forbidden = ('倍率未识别清楚', '请重传首页截图', '第一张图的倍率按钮不清楚',
                 '每轮倍率（自己选择）', '程序不识别、不切换游戏倍率', '本次使用券数（自动识别，可更正）')
    for text in forbidden:
        if any(text.encode('utf-8') in blob for blob in dex):
            raise ValueError('APK still contains obsolete multiplier recognition: ' + text)
    direct_deploy = tuple(map(int, version.split('.'))) >= (2, 1, 2)
    if direct_deploy:
        for text in ('核对出征兵力', '读取出征兵力'):
            if any(text.encode('utf-8') in blob for blob in dex):
                raise ValueError('APK still contains removed troop OCR gate: ' + text)
    if stamina_budget:
        for text in ('运行时长  ', '运行分钟数', '已达到本次时长'):
            # The bear mode has a longer, unrelated dialog title containing
            # the same words. Match the old hunt dialog's complete DEX string.
            needle=text.encode('utf-8')+(b'\0' if text=='运行分钟数' else b'')
            if any(needle in blob for blob in dex):
                raise ValueError('APK still contains removed hunt duration: ' + text)
    in_game_switch = tuple(map(int,version.split('.'))) >= (2,1,5)
    if in_game_switch:
        for marker in ('切换活动任务', '选择任务', '同时派出几队（1–6）'):
            if not any(marker.encode('utf-8') in blob for blob in dex):
                raise ValueError('APK is missing in-game task controls: ' + marker)
    preset_selection = tuple(map(int,version.split('.'))) >= (2,1,6)
    if preset_selection:
        for marker in ('使用编队', '按选中的编号顺序循环使用。', '请至少选择一个编队'):
            if not any(marker.encode('utf-8') in blob for blob in dex):
                raise ValueError('APK is missing preset selection: ' + marker)
    session_recovery = tuple(map(int,version.split('.'))) >= (2,1,7)
    if session_recovery:
        for marker in ('回游戏继续', '开始新任务', '确认上次操作结果'):
            if not any(marker.encode('utf-8') in blob for blob in dex):
                raise ValueError('APK is missing session recovery: ' + marker)
    device_recovery = tuple(map(int,version.split('.'))) >= (2,1,8)
    if device_recovery:
        for marker in ('截图响应超时，点继续重试', '点击响应超时，点继续确认画面', '画面已更新，重新识别'):
            if not any(marker.encode('utf-8') in blob for blob in dex):
                raise ValueError('APK is missing device recovery: ' + marker)
        obsolete='靶心截图与当前屏幕不一致，请在助手中更新截图。'
        if any(obsolete.encode('utf-8') in blob for blob in dex):
            raise ValueError('APK still blocks relocated reference layouts')
    for text in ('首页实际余券', '正在切换 1 倍，等待画面确认', '首页确认余券为 0，已全部用完', '结算完成，自动返回首页'):
        if not any(text.encode('utf-8') in blob for blob in dex):
            raise ValueError('APK is missing expected behavior marker: ' + text)
    label = f'无尽冬日助手 {version} · 正中靶心'
    if not any(label.encode(encoding) in resources for encoding in ('utf-8', 'utf-16-le')):
        raise ValueError('APK is missing the visible version label: ' + label)
    return {'apk': str(apk), 'version_label': label, 'legacy_multiplier_ocr': False,
            'direct_deploy_without_troop_ocr': direct_deploy,
            'hunt_stamina_budget_without_duration': stamina_budget,
            'in_game_task_switch': in_game_switch,
            'preset_selection': preset_selection,
            'session_recovery': session_recovery,
            'device_recovery': device_recovery,
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
