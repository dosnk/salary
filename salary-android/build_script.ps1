$ErrorActionPreference = "Continue"
Set-Location "F:\AIPoject\salary\salary-android"
$logFile = "F:\AIPoject\salary\salary-android\gradle_build.log"
$errFile = "F:\AIPoject\salary\salary-android\gradle_build.err"

"Starting build at $(Get-Date)" | Out-File $logFile
"Args: assembleDebug --no-daemon" | Out-File $logFile -Append

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "F:\AIPoject\salary\salary-android\gradlew.bat"
$psi.Arguments = "assembleDebug --no-daemon"
$psi.WorkingDirectory = "F:\AIPoject\salary\salary-android"
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false

$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $psi

$sbOut = New-Object System.Text.StringBuilder
$sbErr = New-Object System.Text.StringBuilder

$outEvent = Register-ObjectEvent -InputObject $proc -EventName OutputDataReceived -Action {
    param($sender, $e)
    if ($e.Data -ne $null) {
        [void]$sbOut.AppendLine($e.Data)
    }
} -MessageData $sbOut

$errEvent = Register-ObjectEvent -InputObject $proc -EventName ErrorDataReceived -Action {
    param($sender, $e)
    if ($e.Data -ne $null) {
        [void]$sbErr.AppendLine($e.Data)
    }
} -MessageData $sbErr

$proc.Start() | Out-Null
$proc.BeginOutputReadLine()
$proc.BeginErrorReadLine()
$proc.WaitForExit()

Start-Sleep -Seconds 2

Unregister-Event -SourceIdentifier $outEvent.Name -ErrorAction SilentlyContinue
Unregister-Event -SourceIdentifier $errEvent.Name -ErrorAction SilentlyContinue

$sbOut.ToString() | Out-File $logFile -Append
$sbErr.ToString() | Out-File $errFile -Append

"ExitCode: $($proc.ExitCode)" | Out-File $logFile -Append
"Build finished at $(Get-Date)" | Out-File $logFile -Append

Write-Output "BUILD_DONE_EXIT_$($proc.ExitCode)"
