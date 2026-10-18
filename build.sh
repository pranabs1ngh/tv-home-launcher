#!/bin/zsh
# Build TV Home with the plain SDK tools (no Gradle) and optionally install it.
# Usage: ./build.sh            -> build/tvhome.apk
#        ./build.sh install    -> also install on the TV and make it the home screen
set -euo pipefail
cd "$(dirname "$0")"

T=$PWD/.toolchain
export JAVA_HOME=$(echo $T/jdk-17*/Contents/Home)
BT=$T/sdk/build-tools/34.0.0
JAR=$T/sdk/platforms/android-34/android.jar
TV=${TV_HOST:-192.168.0.5}:5555
ADB=${ADB:-$T/sdk/platform-tools/adb}

rm -rf build && mkdir -p build/gen build/classes build/dex
$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -o build/unsigned.apk -I $JAR --manifest AndroidManifest.xml build/res.zip \
  --java build/gen --min-sdk-version 26 --target-sdk-version 34 --version-code 1 --version-name 1.0 \
  -0 ttf  # stored uncompressed so fonts are memory-mapped, not inflated into RAM
$JAVA_HOME/bin/javac --release 11 -g:none -classpath $JAR \
  -d build/classes $(find src build/gen -name '*.java')
$BT/d8 --lib $JAR --min-api 26 --release --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q ../unsigned.apk classes.dex)
$BT/zipalign -f -p 4 build/unsigned.apk build/aligned.apk

[ -f debug.keystore ] || $JAVA_HOME/bin/keytool -genkeypair -keystore debug.keystore -storepass android \
  -keypass android -alias tvhome -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=TV Home" >/dev/null 2>&1
$BT/apksigner sign --v4-signing-enabled false --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out build/tvhome.apk build/aligned.apk
echo "built build/tvhome.apk ($(du -h build/tvhome.apk | cut -f1))"

[ "${1:-}" = install ] || exit 0
$ADB connect $TV >/dev/null
$ADB -s $TV install -r build/tvhome.apk
$ADB -s $TV shell pm grant com.bedroomtv.home android.permission.ACCESS_FINE_LOCATION
$ADB -s $TV shell pm grant com.bedroomtv.home android.permission.WRITE_SECURE_SETTINGS
# Brings TV Home back if Google TV's home ever shows, and maps the settings key (see README).
$ADB -s $TV shell settings put secure enabled_accessibility_services com.bedroomtv.home/.HomeGuardService
$ADB -s $TV shell settings put secure accessibility_enabled 1
# Started as HOME so it reuses the home task instead of opening a second copy of the launcher.
$ADB -s $TV shell am start -a android.intent.action.MAIN -c android.intent.category.HOME -n com.bedroomtv.home/.MainActivity
