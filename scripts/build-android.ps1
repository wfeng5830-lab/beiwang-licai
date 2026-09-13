$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location -LiteralPath $projectRoot
$sdkPath = Join-Path $projectRoot '.tools/android-sdk'
$gradlePath = Join-Path $projectRoot '.tools/gradle-8.11.1/bin/gradle.bat'
if (!(Test-Path -LiteralPath $gradlePath)) { throw '缺少 Gradle 8.11.1，请先运行 scripts/download-build-tools.py 或使用 Android Studio 构建。' }
if (!(Test-Path -LiteralPath (Join-Path $sdkPath 'platforms/android-35/android.jar'))) { throw '缺少 Android SDK Platform 35。' }
$env:ANDROID_HOME = $sdkPath
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools/gradle-cache'
if (Test-Path -LiteralPath 'C:/Program Files/Java/jdk-21') { $env:JAVA_HOME = 'C:/Program Files/Java/jdk-21' }
& $gradlePath --no-daemon assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Android 构建失败，请查看以上错误。' }
New-Item -ItemType Directory -Force (Join-Path $projectRoot 'output') | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk') -Destination (Join-Path $projectRoot 'output/日常账本-1.4.apk')
Write-Output 'APK 已生成：output/日常账本-1.4.apk'

