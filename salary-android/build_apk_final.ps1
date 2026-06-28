$ErrorActionPreference = "Stop"

Set-Location "F:\AIPoject\salary\salary-android"

$logFile = "F:\AIPoject\salary\salary-android\build_log.txt"

Write-Host "Starting build..."

& ".\gradlew.bat" "clean" "assembleDebug" "--no-daemon" "-Dorg.gradle.jvmargs=-Xmx2048m" > $logFile 2>&1

$exitCode = $LASTEXITCODE

Write-Host "Build finished with exit code: $exitCode"

# 检查 APK 是否生成
$apkDir = "F:\AIPoject\salary\salary-android\app\build\outputs\apk\debug"
if (Test-Path $apkDir) {
    $apks = Get-ChildItem -Path $apkDir -Filter "*.apk"
    if ($apks.Count -gt 0) {
        Write-Host "APK files found:"
        foreach ($apk in $apks) {
            $sizeMB = [math]::Round($apk.Length / 1MB, 2)
            Write-Host "  - $($apk.Name) ($sizeMB MB)"
        }
    } else {
        Write-Host "APK directory exists but no APK files found"
    }
} else {
    Write-Host "APK output directory does not exist"
}
