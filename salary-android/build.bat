@echo off
chcp 65001 > nul
cd /d F:\AIPoject\salary\salary-android
echo Starting Gradle build...
echo Current directory: %cd%
echo.
call gradlew.bat assembleDebug --no-daemon --stacktrace
echo.
echo Exit code: %ERRORLEVEL%
echo.
echo Listing APK output directory...
if exist "app\build\outputs\apk" (
    dir /s /b "app\build\outputs\apk\*.apk"
) else (
    echo APK output directory does not exist
)
echo.
echo Build script finished
