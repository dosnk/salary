param(
    [string]$BuildType = "Debug"
)

$ErrorActionPreference = "Stop"

# 设置工作目录
$workingDir = "F:\AIPoject\salary\salary-android"
Set-Location $workingDir

# 创建输出文件
$stdOut = "F:\AIPoject\salary\salary-android\gradle_stdout.log"
$stdErr = "F:\AIPoject\salary\salary-android\gradle_stderr.log"

# 删除旧的输出文件
if (Test-Path $stdOut) { Remove-Item $stdOut -Force }
if (Test-Path $stdErr) { Remove-Item $stdErr -Force }

# 构建 Gradle 命令
$gradleArgs = @()
$gradleArgs += "assemble$BuildType"
$gradleArgs += "--no-daemon"
$gradleArgs += "-Dorg.gradle.jvmargs=-Xmx2048m"

# 创建进程信息
$processInfo = New-Object System.Diagnostics.ProcessStartInfo
$processInfo.FileName = "F:\AIPoject\salary\salary-android\gradlew.bat"
$processInfo.Arguments = ($gradleArgs -join " ")
$processInfo.WorkingDirectory = $workingDir
$processInfo.RedirectStandardOutput = $true
$processInfo.RedirectStandardError = $true
$processInfo.UseShellExecute = $false
$processInfo.CreateNoWindow = $true

# 启动进程
try {
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $processInfo
    $process.Start() | Out-Null

    # 读取输出
    $output = $process.StandardOutput.ReadToEnd()
    $errors = $process.StandardError.ReadToEnd()

    $process.WaitForExit()

    # 写入输出文件
    $output | Out-File -FilePath $stdOut -Encoding UTF8
    $errors | Out-File -FilePath $stdErr -Encoding UTF8

    $exitCode = $process.ExitCode

    # 检查 APK 输出
    $apkDir = "F:\AIPoject\salary\salary-android\app\build\outputs\apk\debug"
    $apkFound = $false
    if (Test-Path $apkDir) {
        $apkFiles = Get-ChildItem -Path $apkDir -Filter "*.apk"
        if ($apkFiles.Count -gt 0) {
            $apkFound = $true
        }
    }

    # 创建结果文件
    $apkStatus = "否"
    if ($apkFound) { $apkStatus = "是" }

    $resultContent = "========================================`r`n"
    $resultContent += "构建结果报告`r`n"
    $resultContent += "========================================`r`n"
    $resultContent += "构建类型: $BuildType`r`n"
    $resultContent += "退出代码: $exitCode`r`n"
    $resultContent += "APK 生成: $apkStatus`r`n"
    $resultContent += "APK 目录: $apkDir`r`n"
    $resultContent += "`r`n"
    $resultContent += "构建时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')`r`n"
    $resultContent += "`r`n"
    $resultContent += "========================================`r`n"
    $resultContent += "详细信息:`r`n"
    $resultContent += "========================================`r`n"
    $resultContent += "工作目录: $workingDir`r`n"
    $resultContent += "Gradle 参数: $($gradleArgs -join ' ')`r`n"
    $resultContent += "标准输出日志: $stdOut`r`n"
    $resultContent += "标准错误日志: $stdErr`r`n"
    $resultContent += "`r`n"

    if ($apkFound) {
        $resultContent += "========================================`r`n"
        $resultContent += "生成的 APK 文件:`r`n"
        $resultContent += "========================================`r`n"
        $apkFiles = Get-ChildItem -Path $apkDir -Filter "*.apk"
        foreach ($apk in $apkFiles) {
            $fileSize = [math]::Round($apk.Length / 1MB, 2)
            $resultContent += "  - $($apk.Name) ($fileSize MB)`r`n"
        }
    }

    $resultContent | Out-File -FilePath "F:\AIPoject\salary\salary-android\build_result.txt" -Encoding UTF8

    Write-Host "Build completed with exit code: $exitCode"
    Write-Host "APK generated: $apkStatus"

} catch {
    $errorMessage = $_.Exception.Message
    $errorMessage | Out-File -FilePath "F:\AIPoject\salary\salary-android\build_fatal_error.txt" -Encoding UTF8
    Write-Host "Fatal error: $errorMessage"
}
