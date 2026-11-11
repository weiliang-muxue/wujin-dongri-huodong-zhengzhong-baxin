param([Parameter(Mandatory=$true)][string]$OpenCvJavaDir,[Parameter(Mandatory=$true)][string]$OutputDir,[Parameter(Mandatory=$true)][string]$Fixtures,[string]$JavaHome=$env:JAVA_HOME,[ValidateSet('pairing','navigation')][string]$Suite='pairing')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$OutputDir=[IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Path "$OutputDir\temp","$OutputDir\classes","$OutputDir\adapter" -Force | Out-Null
$env:TEMP="$OutputDir\temp";$env:TMP=$env:TEMP
$jar=(Get-ChildItem -LiteralPath $OpenCvJavaDir -Filter 'opencv-*.jar' -File).FullName
$dll=(Get-ChildItem -LiteralPath "$OpenCvJavaDir\x64" -Filter 'opencv_java*.dll' -File).FullName
$source=Join-Path $project 'app\src\main\java'
$files=@('VisualLocator','VisualAnchor','ReferenceAnchors','LayoutTransform') | ForEach-Object {"$source\cn\local\automation\$_.java"}
$files+=@('HuntVision','HuntGlyphs','FeedbackVision','MarchRowVision','HuntFlow','HuntMarches','PresetOrder','BearFlow','MarchFlow','MarchCount','ReferenceLayout','RallyOrder','CountdownDigits','CountdownVision','Matcher') | ForEach-Object {"$source\cn\local\winterbear\$_.java"}
$layout=Get-Content -LiteralPath "$project\app\src\main\assets\reference\layout.json" -Raw | ConvertFrom-Json
$values=@()
foreach($crop in $layout.crops.PSObject.Properties){$r=$crop.Value;$values+="$($crop.Name)X=$($r[0]+[int][Math]::Floor($r[2]/2))";$values+="$($crop.Name)Y=$($r[1]+[int][Math]::Floor($r[3]/2))"}
for($i=0;$i -lt 6;$i++){$values+="presetX$i=$($layout.presets[$i][0])";$values+="presetY$i=$($layout.presets[$i][1])"}
$keys=@('countLeft','countTop','countRight','countBottom');for($i=0;$i -lt 4;$i++){$values+="$($keys[$i])=$($layout.counter[$i])"}
$values | Set-Content -LiteralPath "$OutputDir\layout.properties" -Encoding ascii
# The standard desktop adapter intentionally disables manual sampling. This isolated
# copy reproduces production saved-crop lookup/Scale=1 and its preference gate.
$adapter=Get-Content -LiteralPath "$PSScriptRoot\desktop\cn\local\winterbear\Config.java" -Raw
$adapter=$adapter.Replace('Bitmap image(String key){return BitmapFactory.decodeFile(assets.resolve("reference/"+key+".png").toString());}', 'private Path file(String key){Path sample=Path.of(System.getProperty("custom.samples")).resolve(key+".png");return Files.isRegularFile(sample)?sample:assets.resolve("reference/"+key+".png");} Bitmap image(String key){return BitmapFactory.decodeFile(file(key).toString());}')
$adapter=$adapter.Replace('boolean has(String key){return Files.isRegularFile(assets.resolve("reference/"+key+".png"));}', 'boolean has(String key){return Files.isRegularFile(file(key));}')
$adapter=$adapter.Replace('boolean customRallyReady(){return false;}', 'boolean customRallyReady(){return p.getBoolean("rallySampleReady",false)&&has("bear_row")&&has("join_row");}')
$adapter=$adapter.Replace('return name.equals("referenceLayout")||fallback;', 'return values.containsKey(name)?values.get(name)!=0:name.equals("referenceLayout")||fallback;')
$adapter=$adapter.Replace('return name.endsWith("Scale")?scale:fallback;', 'return values.containsKey(name)?values.get(name):name.endsWith("Scale")?scale:fallback;')
if(!$adapter.Contains('private Path file') -or $adapter.Contains('customRallyReady(){return false;}')){throw 'Desktop Config changed; review the isolated manual-crop adapter'}
$adapter | Set-Content -LiteralPath "$OutputDir\adapter\Config.java" -Encoding utf8
$files+=Get-ChildItem -LiteralPath "$PSScriptRoot\desktop" -Filter '*.java' -Recurse -File | Where-Object {$_.Name -ne 'Config.java'} | Select-Object -ExpandProperty FullName
$files+="$OutputDir\adapter\Config.java"
& "$JavaHome\bin\javac.exe" -encoding UTF-8 -cp $jar -d "$OutputDir\classes" @files
if($LASTEXITCODE -ne 0){throw 'Custom sample checker compilation failed'}
$checker=if($Suite -eq 'navigation'){'ManualNavigationScaleCheck'}else{'CustomSampleScaleCheck'}
& "$JavaHome\bin\java.exe" "-Djava.io.tmpdir=$OutputDir\temp" -cp "$OutputDir\classes;$jar" "cn.local.winterbear.$checker" $dll "$project\app\src\main\assets" "$OutputDir\layout.properties" $Fixtures $OutputDir 2>&1 | Tee-Object -FilePath "$OutputDir\custom-samples.log"
if($LASTEXITCODE -ne 0){throw 'Custom sample regression failed; preserve the report before fixing production code'}
