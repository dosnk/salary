Set-Location F:\AIPoject\salary\salary-android
$logFile = "F:\AIPoject\salary\salary-android\gradle_build.log"
& ".\gradlew.bat" "assembleDebug" "--no-daemon" > $logFile 2>&1
$code = $LASTEXITCODE
"=====BUILD_EXIT_CODE=$code=====" | Add-Content $logFile
# 列出输出目录
if (Test-Path "app\build\outputs\apk") {
    "=====APK_FILES=====" | Add-Content $logFile
    Get-ChildItem -Path "app\build\outputs\apk" -Recurse -Filter "*.apk" | ForEach-Object { $_.FullName } | Add-Content $logFile
} else {
    "NO APK OUTPUT DIRECTORY" | Add-Content $logFile
}
"BUILD_FINISHED_$code"
