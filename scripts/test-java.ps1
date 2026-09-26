$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path $PSScriptRoot -Parent)
$jdk = 'C:/Program Files/Java/jdk-21/bin'
$jsonJar = '.tools/json-test.jar'
if (!(Test-Path -LiteralPath $jsonJar)) { throw '缺少 JVM 测试依赖 .tools/json-test.jar（org.json）。' }
$shared = @('tests/stubs/android/content/Context.java','tests/stubs/android/content/SharedPreferences.java','app/src/main/java/cn/dailyledger/app/BillParser.java','app/src/main/java/cn/dailyledger/app/ScanIdentity.java','app/src/main/java/cn/dailyledger/app/LedgerStore.java','app/src/main/java/cn/dailyledger/app/QuadrantIconModel.java')
& "$jdk/javac.exe" -encoding UTF-8 -cp $jsonJar -d .tools/scan-tests @shared tests/BillParserTest.java tests/ScanIdentityTest.java tests/LedgerStoreTest.java tests/QuadrantIconModelTest.java
if ($LASTEXITCODE -ne 0) { throw 'Java 测试编译失败' }
foreach ($name in @('BillParserTest','ScanIdentityTest','LedgerStoreTest','QuadrantIconModelTest')) {
    & "$jdk/java.exe" -cp ".tools/scan-tests;$jsonJar" "cn.dailyledger.app.$name"
    if ($LASTEXITCODE -ne 0) { throw "$name 未通过" }
}
$desktopStubs = (Get-ChildItem tests/desktop-stubs -Recurse -Filter *.java).FullName
& "$jdk/javac.exe" -encoding UTF-8 -cp $jsonJar -d .tools/desktop-tests @shared @desktopStubs app/src/main/java/cn/dailyledger/app/DesktopShortcut.java tests/DesktopShortcutTest.java
if ($LASTEXITCODE -ne 0) { throw '桌面图标测试编译失败' }
& "$jdk/java.exe" -cp ".tools/desktop-tests;$jsonJar" cn.dailyledger.app.DesktopShortcutTest
if ($LASTEXITCODE -ne 0) { throw 'DesktopShortcutTest 未通过' }
