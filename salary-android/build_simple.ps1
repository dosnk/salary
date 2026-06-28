# 简单的 Gradle 构建脚本
# 清理缓存并重新构建 APK
Set-Location "F:\AIPoject\salary\salary-android"

# 停止 Gradle daemon
Write-Host "停止 Gradle daemon..."
.\gradlew.bat --stop

# 清理所有缓存
Write-Host "清理构建缓存..."
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue "app\build"
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue "core\data\build"
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue ".gradle"

# 执行构建
Write-Host "开始 Gradle 构建 APK..."
.\gradlew.bat assembleDebug --no-daemon --stacktrace 2>&1 | Out-File -FilePath "gradle_output.log" -Encoding UTF8

# 检查构建结果
$buildSuccess = $LASTEXITCODE -eq 0

if ($buildSuccess) {
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "构建成功！" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green

    # 检查 APK 文件
    $apkDir = "app\build\outputs\apk\debug"
    if (Test-Path $apkDir) {
        $apks = Get-ChildItem $apkDir -Filter "*.apk"
        Write-Host ""
        Write-Host "APK 文件列表:" -ForegroundColor Yellow
        foreach ($apk in $apks) {
            $sizeMB = [math]::Round($apk.Length / 1MB, 2)
            Write-Host "  - $($apk.Name) ($sizeMB MB)" -ForegroundColor Cyan
        }
    } else {
        Write-Host "警告: APK 输出目录不存在" -ForegroundColor Yellow
    }
} else {
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "构建失败！错误代码: $LASTEXITCODE" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red

    # 显示错误日志的最后几行
    if (Test-Path "gradle_output.log") {
        Write-Host ""
        Write-Host "错误日志摘要:" -ForegroundColor Yellow
        Get-Content "gradle_output.log" -Tail 100 | ForEach-Object { Write-Host $_ }
    }
}

Write-Host ""
Write-Host "按任意键退出..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
