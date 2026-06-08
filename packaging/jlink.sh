#!/usr/bin/env bash
# Build a trimmed JRE for the jpackage fallback bundle.
# Run after: ./mvnw -Pjpackage package   (which populates target/lib and the app jar)
set -euo pipefail
cd "$(dirname "$0")/.."

JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home)}"
JLINK="$JAVA_HOME/bin/jlink"
JDEPS="$JAVA_HOME/bin/jdeps"

APP_JAR="$(ls target/dstool-*.jar | grep -v -- '-sources' | head -1)"
[ -n "$APP_JAR" ] || { echo "Run './mvnw -Pjpackage package' first." >&2; exit 1; }

# Discover the JDK modules the app + its dependencies need.
MODS="$("$JDEPS" --multi-release 25 --ignore-missing-deps --print-module-deps -q \
        --class-path "target/lib/*" "$APP_JAR")"
# Modules jdeps cannot see for our use. NOTE: jdk.crypto.cryptoki is intentionally NOT
# needed — DSTool talks to the card via FFM, not SunPKCS11.
MODS="${MODS},java.naming,java.xml,jdk.crypto.ec,jdk.localedata"

echo "jlink modules: ${MODS}"
rm -rf target/runtime
"$JLINK" --add-modules "${MODS}" \
         --no-header-files --no-man-pages --strip-debug --compress=zip-9 \
         --output target/runtime
echo "Trimmed runtime written to target/runtime"
