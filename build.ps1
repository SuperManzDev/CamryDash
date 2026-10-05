$ErrorActionPreference = "Stop"
$JDK = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin"
$SDK = "C:\Users\MNZ\AppData\Local\Android\Sdk"
$BUILD_TOOLS = "$SDK\build-tools\34.0.0"
$PLATFORM = "$SDK\platforms\android-34\android.jar"
$KEYSTORE = "C:\Users\MNZ\.android\debug.keystore"
$PROJ = "C:\Users\MNZ\Downloads\CamryDash"

Set-Location $PROJ

Write-Host "=== 1. Cleaning build directories ==="
if (Test-Path "build") { Remove-Item -Recurse -Force "build" }
New-Item -ItemType Directory -Force -Path "build\obj" | Out-Null
New-Item -ItemType Directory -Force -Path "build\apk" | Out-Null

Write-Host "=== 2. Compiling R.java with AAPT ==="
& "$BUILD_TOOLS\aapt.exe" package -f -m -J src -M AndroidManifest.xml -S res -I $PLATFORM

Write-Host "=== 3. Compiling Java sources ==="
$sources = Get-ChildItem -Path "src" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& "$JDK\javac.exe" -source 8 -target 8 -bootclasspath $PLATFORM -d "build\obj" $sources

Write-Host "=== 4. Dexing classes with D8 ==="
$classes = Get-ChildItem -Path "build\obj" -Recurse -Filter "*.class" | ForEach-Object { $_.FullName }
& "$BUILD_TOOLS\d8.bat" --release --min-api 21 --lib $PLATFORM --output "build\apk" $classes

Write-Host "=== 5. Packaging APK with AAPT ==="
& "$BUILD_TOOLS\aapt.exe" package -f -M AndroidManifest.xml -S res -I $PLATFORM -F "build\unaligned.apk" "build\apk"

Write-Host "=== 6. ZipAligning APK ==="
& "$BUILD_TOOLS\zipalign.exe" -f -p 4 "build\unaligned.apk" "CamryDash.apk"

Write-Host "=== 7. Signing APK ==="
& "$BUILD_TOOLS\apksigner.bat" sign --ks $KEYSTORE --ks-pass pass:android --key-pass pass:android CamryDash.apk

Write-Host "=== Build Completed Successfully! ==="
Get-Item CamryDash.apk | Select-Object Name, Length, LastWriteTime
