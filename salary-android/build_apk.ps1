# Android APK 构建脚本 - 增强版
# 这个脚本会运行完整的 Android 构建过程，并将所有输出写入日志文件

$ErrorActionPreference = "Continue"

# 设置工作目录
$workingDir = "F:\AIPoject\salary\salary-android"
Set-Location $workingDir

# 创建日志文件
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logFile = "F:\AIPoject\salary\salary-android\build_log_$timestamp.txt"
$errorFile = "F:\AIPoject\salary\salary-android\build_errors_$timestamp.txt"

Write-Host "========================================"
Write-Host "Android APK 构建过程开始"
Write-Host "========================================"
Write-Host "工作目录: $workingDir"
Write-Host "日志文件: $logFile"
Write-Host ""

# 检查 Java 是否可用
Write-Host "检查 Java 环境..."
try {
    $javaVersion = java -version 2>&1
    Write-Host "Java 版本: $javaVersion"
} catch {
    Write-Host "警告: 无法检测 Java 版本"
}
Write-Host ""

# 运行 Gradle 构建命令
Write-Host "开始构建 APK..."
Write-Host "命令: gradlew.bat assembleDebug --no-daemon --stacktrace"
Write-Host ""

# 使用 Start-Process 来运行构建，并捕获输出
$buildOutput = & ".\gradlew.bat" "assembleDebug" "--no-daemon" "--stacktrace" 2>&1
$exitCode = $LASTEXITCODE

# 将构建输出写入日志文件
$buildOutput | Out-File -FilePath $logFile -Encoding UTF8

# 检查是否有错误
$errors = $buildOutput | Select-String -Pattern "ERROR|FAILED|Exception"
if ($errors) {
    $errors | Out-File -FilePath $errorFile -Encoding UTF8
    Write-Host "发现错误! 详细信息请查看: $errorFile"
    Write-Host ""
    Write-Host "错误摘要:"
    $errors | ForEach-Object { Write-Host "  $_" }
}

Write-Host ""
Write-Host "构建退出代码: $exitCode"
Write-Host ""

# 检查 APK 是否生成
$apkDir = "F:\AIPoject\salary\salary-android\app\build\outputs\apk\debug"
if (Test-Path $apkDir) {
    Write-Host "APK 输出目录存在: $apkDir"
    $apkFiles = Get-ChildItem -Path $apkDir -Filter "*.apk"
    if ($apkFiles) {
        Write-Host ""
        Write-Host "========================================"
        Write-Host "✅ APK 构建成功!"
        Write-Host "========================================"
        Write-Host ""
        Write-Host "生成的 APK 文件:"
        foreach ($apk in $apkFiles) {
            $fileSize = [math]::Round($apk.Length / 1KB, 2)
            Write-Host "  📦 $($apk.FullName) ($fileSize KB)"
        }
    } else {
        Write-Host "⚠️ APK 输出目录存在，但没有找到 APK 文件"
    }
} else {
    Write-Host "⚠️ APK 输出目录不存在: $apkDir"
    Write-Host "构建可能在打包阶段失败了"
}

Write-Host ""
Write-Host "完整日志已保存到: $logFile"
Write-Host ""
Write-Host "========================================"
Write-Host "构建过程结束"
Write-Host "========================================"
