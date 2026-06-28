cd F:\AIPoject\salary\salary-android

echo "=== Starting Gradle build ===" 
echo ""

.\gradlew.bat assembleDebug --no-daemon -Dorg.gradle.jvmargs=-Xmx2048m

echo ""
echo "=== Build finished with exit code: $LASTEXITCODE"
echo ""
echo "=== Checking for APK files..."
echo ""

if (Test-Path "F:\AIPoject\salary\salary-android\app\build\outputs\apk\debug\") {
    Get-ChildItem -Path "F:\AIPoject\salary\salary-android\app\build\outputs\apk\debug\” -Filter *.apk
}
