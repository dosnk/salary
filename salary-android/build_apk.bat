@echo off
echo ============================================
echo Building Android APK
echo ============================================
cd /d "F:\AIPoject\salary\salary-android"

echo.
echo [1/2] Stopping Gradle daemon and cleaning...
call gradlew.bat --stop
if exist "app\build" rmdir /s /q "app\build"
if exist "core\data\build" rmdir /s /q "core\data\build"
if exist ".gradle" rmdir /s /q ".gradle"

echo.
echo [2/2] Running Gradle assembleDebug...
call gradlew.bat assembleDebug --no-daemon --stacktrace > gradle_full.log 2>&1
set BUILD_ERROR=%ERRORLEVEL%

echo.
echo ============================================
if %BUILD_ERROR%==0 (
    echo BUILD SUCCESSFUL!
    echo.
    echo Looking for APK files...
    if exist "app\build\outputs\apk\debug\" (
        dir "app\build\outputs\apk\debug\*.apk" /b
    ) else (
        echo APK directory not found!
    )
) else (
    echo BUILD FAILED with error code: %BUILD_ERROR%
    echo.
    echo Last 50 lines of the log:
    findstr /n ".*" "gradle_full.log" | findstr /r "^[0-9]*:" | findstr /c:"FAILED" /c:"error" /c:"BUILD"
)
echo ============================================
echo.
echo Full log saved to: gradle_full.log
echo.
pause
