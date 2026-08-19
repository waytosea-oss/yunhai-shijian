#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BUILD_TOOLS="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-35/android.jar"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
PATH="$JAVA_HOME/bin:$BUILD_TOOLS:$PATH"

OUT="$ROOT/build"
rm -rf "$OUT"
mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/dist"

aapt2 compile --dir "$ROOT/res" -o "$OUT/compiled/resources.zip"
aapt2 link \
  -I "$PLATFORM" \
  -A "$ROOT/assets" \
  --manifest "$ROOT/AndroidManifest.xml" \
  --min-sdk-version 23 \
  --target-sdk-version 35 \
  --java "$OUT/gen" \
  -o "$OUT/app-unsigned.apk" \
  "$OUT/compiled/resources.zip"

javac -source 8 -target 8 \
  -bootclasspath "$PLATFORM" \
  -classpath "$OUT/gen" \
  -d "$OUT/classes" \
  $(find "$ROOT/src" "$OUT/gen" -name '*.java' -print)

d8 --lib "$PLATFORM" --min-api 23 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class' -print)
cp "$OUT/app-unsigned.apk" "$OUT/app-with-dex-unsigned.apk"
(cd "$OUT/dex" && zip -q -r "$OUT/app-with-dex-unsigned.apk" classes.dex)
zipalign -f -p 4 "$OUT/app-with-dex-unsigned.apk" "$OUT/app-aligned.apk"

KEYSTORE="$ROOT/debug.keystore"
if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null
fi

apksigner sign \
  --ks "$KEYSTORE" \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$OUT/dist/yunhai-shijian-debug.apk" \
  "$OUT/app-aligned.apk"

apksigner verify "$OUT/dist/yunhai-shijian-debug.apk"
echo "$OUT/dist/yunhai-shijian-debug.apk"
