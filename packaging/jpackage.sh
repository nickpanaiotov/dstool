#!/usr/bin/env bash
# Build the self-contained jpackage fallback bundle (app-image) and tar it.
# Run after: ./mvnw -Pjpackage package  &&  packaging/jlink.sh
set -euo pipefail
cd "$(dirname "$0")/.."

JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home)}"
JPACKAGE="$JAVA_HOME/bin/jpackage"

APP_JAR_PATH="$(ls target/dstool-*.jar | grep -v -- '-sources' | head -1)"
APP_JAR="$(basename "$APP_JAR_PATH")"
VERSION="$(echo "$APP_JAR" | sed -E 's/^dstool-(.*)\.jar$/\1/')"
ARCH="$(uname -m)"
NAME="dstool-${VERSION}-macos-${ARCH}"

[ -d target/runtime ] || { echo "Run packaging/jlink.sh first." >&2; exit 1; }

# Stage the app jar + lib/ together (jpackage copies --input into Contents/app).
rm -rf target/jpackage-input target/dist
mkdir -p target/jpackage-input target/dist
cp "$APP_JAR_PATH" target/jpackage-input/
cp -R target/lib target/jpackage-input/

"$JPACKAGE" --type app-image --name DSTool \
  --app-version "$VERSION" \
  --input target/jpackage-input \
  --main-jar "$APP_JAR" \
  --main-class org.dstool.DsTool \
  --runtime-image target/runtime \
  --java-options "-Dfile.encoding=UTF-8" \
  --java-options "--enable-native-access=ALL-UNNAMED" \
  --mac-package-identifier org.dstool.cli \
  --dest target/dist

# Convenience CLI launcher next to the .app so users get a clean `dstool` command.
cat > "target/dist/dstool" <<'LAUNCH'
#!/bin/sh
DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$DIR/DSTool.app/Contents/MacOS/DSTool" "$@"
LAUNCH
chmod +x "target/dist/dstool"

# Package as tar.gz + checksum.
( cd target/dist && tar czf "${NAME}.tar.gz" DSTool.app dstool && shasum -a 256 "${NAME}.tar.gz" > "${NAME}.tar.gz.sha256" )
echo "Bundle: target/dist/${NAME}.tar.gz"
