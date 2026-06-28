$ErrorActionPreference = "Continue"

Set-Location "F:\AIPoject\salary\salary-android"

Write-Host "Step 1: Cleaning core/data KSP cache..."

# 删除 core/data 的构建缓存
$dataBuildDir = "F:\AIPoject\salary\salary-android\core\data\build"
if (Test-Path $dataBuildDir) {
    Remove-Item -Path $dataBuildDir -Recurse -Force
    Write-Host "  Cleaned core/data/build"
} else {
    Write-Host "  core/data/build already clean"
}

# 删除 app/build 目录
$appBuildDir = "F:\AIPoject\salary\salary-android\app\build"
if (Test-Path $appBuildDir) {
    Remove-Item -Path $appBuildDir -Recurse -Force
    Write-Host "  Cleaned app/build"
} else {
    Write-Host "  app/build already clean"
}

Write-Host ""
Write-Host "Step 2: Running Gradle assembleDebug..."
Write-Host ""

# 运行 Gradle 构建
& ".\gradlew.bat" "assembleDebug" "--no-daemon" "-Dorg.gradle.jvmargs=-Xmx2048m" 2>&1 | ForEach-Object {
    Write-Host $_
}

$exitCode = $LASTEXITCODE
Write-Host ""
Write-Host "Build completed with exit code: $exitCode"
Write-Host ""

Write-Host "Step 3: Checking for APK files..."
$apkDir = "F:\AIPoject\salary\salary-android\app\build\outputs\apk\debug"
if (Test-Path $apkDir) {
    $apkFiles = Get-ChildItem -Path $apkDir -Filter "*.apk"
    if ($apkFiles.Count -gt 0) {
        Write-Host "SUCCESS! APK files found:"
        foreach ($apk in $apkFiles) {
            $sizeMB = [math]::Round($apk.Length / 1MB, 2)
            Write-Host "  - $($apk.Name) ($sizeMB MB)"
        }
    } else {
        Write-Host "APK directory exists but no APK files found"
    }
} else {
    Write-Host "FAIL: APK output directory does not exist"
    Write-Host "Please check the build errors above"
}
