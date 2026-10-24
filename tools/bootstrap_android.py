import pathlib, urllib.request, xml.etree.ElementTree as ET, zipfile, sys
root=pathlib.Path(__file__).resolve().parent
sdk=root/'android-sdk'; sdk.mkdir(exist_ok=True)
def download(url,dest):
    if dest.exists() and zipfile.is_zipfile(dest):return
    print('Downloading',url,flush=True)
    with urllib.request.urlopen(url,timeout=90) as r,open(str(dest)+'.part','wb') as f:
        total=0
        while data:=r.read(1024*1024):
            f.write(data);total+=len(data)
            if total%(20*1024*1024)<1024*1024:print(f'  {total//(1024*1024)} MB',flush=True)
    part=pathlib.Path(str(dest)+'.part')
    if not zipfile.is_zipfile(part):raise RuntimeError('Download is incomplete; retry: '+url)
    part.replace(dest)
def unpack(archive,dest,strip=1):
    dest.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        for info in z.infolist():
            parts=pathlib.PurePosixPath(info.filename).parts[strip:]
            if not parts:continue
            target=dest.joinpath(*parts)
            if not target.resolve().is_relative_to(dest.resolve()):raise ValueError('Invalid archive path')
            if info.is_dir():target.mkdir(parents=True,exist_ok=True)
            else:
                target.parent.mkdir(parents=True,exist_ok=True)
                with z.open(info) as src,target.open('wb') as out:
                    while block:=src.read(1024*1024):out.write(block)
    print('Installed',dest,flush=True)
xml=urllib.request.urlopen('https://dl.google.com/android/repository/repository2-1.xml',timeout=90).read()
tree=ET.fromstring(xml)
for package,dest in [('platforms;android-35-ext15',sdk/'platforms'/'android-35-ext15'),('build-tools;35.0.0',sdk/'build-tools'/'35.0.0')]:
    if (dest/'source.properties').exists():continue
    candidates=[p for p in tree.findall('remotePackage') if p.attrib.get('path')==package]
    if not candidates:raise RuntimeError('Package not found: '+package)
    archives=candidates[0].findall('./archives/archive')
    a=next(a for a in archives if a.findtext('host-os') in (None,'windows'))
    url=a.findtext('./complete/url');archive=root/url.rsplit('/',1)[-1]
    download('https://dl.google.com/android/repository/'+url,archive);unpack(archive,dest)
gradle=root/'gradle-8.9-bin.zip'
if not (root/'gradle-8.9'/'bin'/'gradle.bat').exists():
    download('https://downloads.gradle.org/distributions/gradle-8.9-bin.zip',gradle)
    unpack(gradle,root/'gradle-8.9')
print('ANDROID BUILD TOOLS READY',flush=True)
