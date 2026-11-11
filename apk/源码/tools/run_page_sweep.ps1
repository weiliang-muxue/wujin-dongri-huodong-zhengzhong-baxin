param([Parameter(Mandatory=$true)][string]$OpenCvJavaDir,[Parameter(Mandatory=$true)][string]$OutputDir,[Parameter(Mandatory=$true)][string]$Fixtures,[string]$JavaHome=$env:JAVA_HOME,[ValidateSet('all','bear','errors')][string]$Section='all',[string]$MarchFixtures='')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$OutputDir=[IO.Path]::GetFullPath($OutputDir)
New-Item -ItemType Directory -Path "$OutputDir\temp","$OutputDir\classes" -Force | Out-Null
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
$files+=Get-ChildItem -LiteralPath "$PSScriptRoot\desktop" -Filter '*.java' -Recurse -File | Select-Object -ExpandProperty FullName
& "$JavaHome\bin\javac.exe" -encoding UTF-8 -cp $jar -d "$OutputDir\classes" @files
if($LASTEXITCODE -ne 0){throw 'Page checker compilation failed'}
& "$JavaHome\bin\java.exe" "-Djava.io.tmpdir=$OutputDir\temp" "-Dsection=$Section" -cp "$OutputDir\classes;$jar" cn.local.winterbear.PageSweepCheck $dll "$project\app\src\main\assets" $Fixtures $OutputDir 2>&1 | Tee-Object -FilePath "$OutputDir\pages.log"
if($LASTEXITCODE -ne 0){throw 'Page sweep failed; preserve the report before fixing production code'}
& "$JavaHome\bin\java.exe" "-Djava.io.tmpdir=$OutputDir\temp" -cp "$OutputDir\classes;$jar" cn.local.winterbear.NumericCropScaleCheck $dll "$project\app\src\main\assets" $Fixtures 2>&1 | Tee-Object -FilePath "$OutputDir\numeric-crops.log"
if($LASTEXITCODE -ne 0){throw 'Numeric crop scale sweep failed'}
if($MarchFixtures){
    & "$JavaHome\bin\java.exe" "-Djava.io.tmpdir=$OutputDir\temp" -cp "$OutputDir\classes;$jar" cn.local.winterbear.MarchScaleCheck $dll "$project\app\src\main\assets" $MarchFixtures 2>&1 | Tee-Object -FilePath "$OutputDir\marches.log"
    if($LASTEXITCODE -ne 0){throw 'March row scale sweep failed'}
}
