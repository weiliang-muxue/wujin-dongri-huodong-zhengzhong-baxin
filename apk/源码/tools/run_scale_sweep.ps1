param(
    [Parameter(Mandatory=$true)][string]$OpenCvJavaDir,
    [Parameter(Mandatory=$true)][string]$OutputDir,
    [string]$Fixtures,
    [string]$JavaHome=$env:JAVA_HOME,
    [ValidateSet('all','dense','boundary','colors','pages','anchors')][string]$Suite='all'
)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$OpenCvJavaDir=[IO.Path]::GetFullPath($OpenCvJavaDir)
$OutputDir=[IO.Path]::GetFullPath($OutputDir)
if(!$JavaHome){throw 'Provide JavaHome (JDK 17 or newer)'}
if($Suite -in @('all','pages','anchors') -and !$Fixtures){throw 'This suite needs private screenshot fixtures; use dense/boundary/colors for source-only checks'}
if(!$Fixtures){$Fixtures=$OutputDir}
New-Item -ItemType Directory -Path "$OutputDir\temp","$OutputDir\classes" -Force | Out-Null
$env:TEMP="$OutputDir\temp";$env:TMP=$env:TEMP
$jars=@(Get-ChildItem -LiteralPath $OpenCvJavaDir -Filter 'opencv-*.jar' -File)
$dlls=@(Get-ChildItem -LiteralPath "$OpenCvJavaDir\x64" -Filter 'opencv_java*.dll' -File)
if($jars.Count -ne 1 -or $dlls.Count -ne 1){throw 'Expected one OpenCV Java jar and matching x64 native library'}
$source=Join-Path $project 'app\src\main\java\cn\local\automation'
& "$JavaHome\bin\javac.exe" -encoding UTF-8 -cp $jars[0].FullName -d "$OutputDir\classes" `
    "$source\RecognitionWorker.java" "$source\VisualLocator.java" "$source\ReferenceAnchors.java" "$source\LayoutTransform.java" `
    "$PSScriptRoot\java\cn\local\automation\ScaleSweepCheck.java"
if($LASTEXITCODE -ne 0){throw 'Scale checker compilation failed'}
& "$JavaHome\bin\java.exe" "-Djava.io.tmpdir=$OutputDir\temp" -cp "$OutputDir\classes;$($jars[0].FullName)" `
    cn.local.automation.ScaleSweepCheck $dlls[0].FullName "$project\app\src\main\assets" $Fixtures "$OutputDir\$Suite.tsv" $Suite `
    2>&1 | Tee-Object -FilePath "$OutputDir\$Suite.log"
if($LASTEXITCODE -ne 0){throw "Scale sweep failed: $Suite; inspect the TSV before changing any assertions"}
