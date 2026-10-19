#!/bin/bash
# Builds DarkFrame.apk without Android Studio or the Google SDK.
# Requirements (Ubuntu/Debian): openjdk, git, curl, unzip, and
#   sudo apt-get install aapt dalvik-exchange apksigner zipalign
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app/src/main"
WORK="${WORK:-$ROOT/build}"
T="$WORK/tools"
MAVEN=https://maven-central.storage-download.googleapis.com/maven2
mkdir -p "$T"

# 1. Dependencies: Android 2.3.3 API (level 10), 4.1 API for the framework, jasmin assembler, OpenMemories-Framework.
[ -f "$T/android-10.jar" ] || curl -sSfL -o "$T/android-10.jar" $MAVEN/com/google/android/android/2.3.3/android-2.3.3.jar
[ -f "$T/android-16.jar" ] || curl -sSfL -o "$T/android-16.jar" $MAVEN/com/google/android/android/4.1.1.4/android-4.1.1.4.jar
if [ ! -f "$T/jasmin-2.4/jasmin.jar" ]; then
    curl -sSfL -o "$T/jasmin.zip" https://downloads.sourceforge.net/project/jasmin/jasmin/jasmin-2.4/jasmin-2.4.zip
    unzip -q -o "$T/jasmin.zip" -d "$T"
fi
[ -d "$T/OpenMemories-Framework" ] || git clone -q --depth 1 https://github.com/ma1co/OpenMemories-Framework.git "$T/OpenMemories-Framework"

rm -rf "$WORK/stubs" "$WORK/fw" "$WORK/gen" "$WORK/classes" "$WORK/out"
mkdir -p "$WORK/stubs" "$WORK/fw" "$WORK/gen" "$WORK/classes" "$WORK/out"

# 2. Sony API stubs (provided by the camera, not bundled in the APK).
(cd "$T/OpenMemories-Framework/stubs/src/main/jasmin" && java -jar "$T/jasmin-2.4/jasmin.jar" -d "$WORK/stubs" $(find . -name '*.j') >/dev/null)

# 3. OpenMemories framework (bundled in the APK).
javac -nowarn -Xlint:-options --release 8 -encoding UTF-8 -cp "$T/android-16.jar:$WORK/stubs" -d "$WORK/fw" \
    $(find "$T/OpenMemories-Framework/framework/src/main/java" -name '*.java')

# 4. Resources, app code, dex.
aapt package -f -m -J "$WORK/gen" -M "$APP/AndroidManifest.xml" -S "$APP/res" -I "$T/android-10.jar"
javac -Xlint:-options --release 8 -encoding UTF-8 -cp "$T/android-10.jar:$WORK/stubs:$WORK/fw" -d "$WORK/classes" \
    $(find "$APP/java" "$WORK/gen" -name '*.java')
dalvik-exchange --dex --min-sdk-version=10 --output="$WORK/out/classes.dex" "$WORK/classes" "$WORK/fw"

# 5. Signed APK (v1 signature, the only one Android 2.3 reads).
cd "$WORK/out"
aapt package -f -M "$APP/AndroidManifest.xml" -S "$APP/res" -I "$T/android-10.jar" -F unsigned.apk
aapt add unsigned.apk classes.dex >/dev/null
zipalign -f 4 unsigned.apk aligned.apk
apksigner sign --ks "$ROOT/tools/debug.keystore" --ks-pass pass:android \
    --v1-signing-enabled true --v2-signing-enabled false --min-sdk-version 10 \
    --out "$ROOT/DarkFrame.apk" aligned.apk
echo "APK: $ROOT/DarkFrame.apk"
