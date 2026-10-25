# One-file distribution. Share the two modes' OCR/OpenCV, exclude developer tooling.
from pathlib import Path
from PyInstaller.utils.hooks import collect_data_files,collect_dynamic_libs,collect_all
root=Path(SPECPATH)
ocr_data,ocr_binaries,ocr_hidden=collect_all('rapidocr_onnxruntime')
datas=collect_data_files('customtkinter')+ocr_data+[(str(root/'bear_assets'),'bear_assets')]
a=Analysis([str(root/'main.py')],pathex=[str(root)],
 datas=datas,binaries=collect_dynamic_libs('onnxruntime')+ocr_binaries,
 hiddenimports=['pynput.keyboard._win32','pynput.mouse._win32']+ocr_hidden,
 excludes=['torch','torchvision','matplotlib','scipy','pandas','IPython','pytest','onnx','onnxruntime.tools','onnxruntime.transformers','onnxruntime.quantization','onnxruntime.datasets','onnxruntime.backend','onnxruntime.training'],
 noarchive=False,optimize=1)
# Live capture is MSS; no packaged VideoCapture/FFmpeg is used by either mode.
a.binaries=[item for item in a.binaries if 'opencv_videoio_ffmpeg' not in item[0].lower()]
pyz=PYZ(a.pure)
exe=EXE(pyz,a.scripts,a.binaries,a.datas,[],name='无尽冬日助手-2.0.1',version=str(root/'version_info.txt'),
 debug=False,bootloader_ignore_signals=False,strip=False,upx=False,console=False)
