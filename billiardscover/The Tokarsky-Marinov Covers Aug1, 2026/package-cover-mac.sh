#!/usr/bin/env bash
#
# Build a distributable .dmg for the cover viewer.
#
# Unlike the main project's package-mac.sh, this CANNOT use jpackage: this subproject is pinned to
# Java 1.8 (the professor's toolchain) and jpackage only exists from JDK 14 on. Java 8 also predates
# the module system, so there is no jlink image to hand it either. So we assemble the .app bundle by
# hand and wrap it with hdiutil.
#
# Cover data: the bundle ships ONLY each folder's cover.pack, never the loose *.txt. A pack is
# roughly 20-60x smaller than the text it replaces and the viewer reads it directly
# (Viewer.loadCoverAction), so shipping text would bloat the .dmg by hundreds of megabytes for no
# gain. Any cover folder that has no cover.pack yet is packed here before it is bundled; the
# original *.txt stay untouched in the working tree.
#
# Usage: ./package-cover-mac.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

APP_NAME="BilliardsCover"
VERSION="1.1.0"
STAGE="$HERE/build-dmg"
APP="$STAGE/$APP_NAME.app"
DIST="$HERE/dist"
ICON_SRC="final-icon.icns"
# End-user install/launch notes, shipped next to the .dmg. Kept here rather than in dist/ because
# dist/ is gitignored, so a copy living only there would not survive a clean checkout.
INSTRUCTIONS_SRC="Instructions-Install.txt"

# Cover folders that exist on disk but no menu entry in Viewer.getDir can reach. Bundling them
# would only add weight to the .dmg.
SKIP_COVERS=("testcover")

# The bundled Zulu 8 JDK ships JavaFX (jfxrt.jar), which the viewer needs. Its jre/ subtree is
# 255M against 362M for the whole JDK, and we only ever run - never compile - inside the bundle.
JAVA8_HOME="$HERE/../jdk8/zulu8.96.0.19-ca-fx-jdk8.0.502-macosx_aarch64/Contents/Home"
if [ ! -x "$JAVA8_HOME/bin/javac" ]; then
  echo "[ERROR] Zulu 8 (with JavaFX) not found at:"
  echo "        $JAVA8_HOME"
  echo "        The viewer needs JDK 8's bundled JavaFX; a PATH java will not do."
  exit 1
fi

echo "[INFO] Using Java 8: $JAVA8_HOME"
"$JAVA8_HOME/bin/java" -version 2>&1 | sed 's/^/       /'

skipped() {
  local name=$1 s
  for s in "${SKIP_COVERS[@]}"; do [ "$name" = "$s" ] && return 0; done
  return 1
}

# === 1. Resolve the dependency jars =========================================================
G="$HOME/.gradle/caches/modules-2/files-2.1"
DEP_JARS=()
for name in eclipse-collections-9.2.0 eclipse-collections-api-9.2.0 guava-25.1-jre \
            commons-lang3-3.7 commons-math3-3.6.1 javaslang-2.0.5; do
  found=$(find "$G" -name "$name.jar" 2>/dev/null | head -1)
  if [ -z "$found" ]; then
    echo "[ERROR] Missing dependency jar: $name.jar (run ./gradlew build once to populate the cache)"
    exit 1
  fi
  DEP_JARS+=("$found")
done
DEP_JARS+=("$HERE/libs/jna-4.5.1.jar" "$HERE/libs/xz-1.9.jar")

# === 2. Compile from source, so the .dmg can never ship stale classes ========================
echo "[INFO] Compiling sources with Java 8 (-source/-target 1.8)..."
rm -rf "$STAGE"
mkdir -p "$STAGE/classes"
CP=$(IFS=:; echo "${DEP_JARS[*]}")
find src/java -name '*.java' > "$STAGE/srcs.txt"
"$JAVA8_HOME/bin/javac" -source 1.8 -target 1.8 -nowarn \
    -cp "$CP" -d "$STAGE/classes" @"$STAGE/srcs.txt"
echo "[INFO] Compiled $(find "$STAGE/classes" -name '*.class' | wc -l | tr -d ' ') classes"

# === 3. Pack any cover folder that is still loose text ======================================
# CoverConverter self-verifies (text round-trips byte-for-byte, cover round-trips to the exact
# original token stream) and leaves the *.txt in place, so this is safe to re-run.
TO_PACK=()
for d in "$HERE"/coversfolder/*/; do
  name=$(basename "$d")
  skipped "$name" && continue
  if [ ! -f "$d/cover.pack" ]; then
    if [ -f "$d/cover.txt" ]; then
      TO_PACK+=("coversfolder/$name")
    else
      echo "[WARN] $name has neither cover.pack nor cover.txt - skipping"
    fi
  fi
done
if [ "${#TO_PACK[@]}" -gt 0 ]; then
  echo "[INFO] Packing ${#TO_PACK[@]} cover folder(s) that have no cover.pack yet:"
  printf '       %s\n' "${TO_PACK[@]}"
  echo "[INFO] (LZMA2; a multi-gigabyte cover.txt can take a while)"
  "$JAVA8_HOME/bin/java" -Xmx4g -cp "$STAGE/classes:$CP" \
      billiards.viewer.CoverConverter "${TO_PACK[@]}" | sed 's/^/       /'
fi

# === 4. Lay out the .app ====================================================================
echo "[INFO] Assembling $APP_NAME.app..."
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources/app" "$APP/Contents/Resources/data"

# 4a. Runtime: the JRE subset of the Zulu 8 JDK
echo "[INFO] Copying Java 8 runtime (about 255M)..."
cp -R "$JAVA8_HOME/jre" "$APP/Contents/Resources/jre"

# 4b. Application classes + dependency jars
cp -R "$STAGE/classes" "$APP/Contents/Resources/app/classes"
for j in "${DEP_JARS[@]}"; do cp "$j" "$APP/Contents/Resources/app/"; done

# 4c. Cover data: cover.pack only, one per folder.
COVERS_OUT="$APP/Contents/Resources/data/coversfolder"
mkdir -p "$COVERS_OUT"
bundled=0
for d in "$HERE"/coversfolder/*/; do
  name=$(basename "$d")
  skipped "$name" && { echo "       skip $name (not reachable from the cover menu)"; continue; }
  [ -f "$d/cover.pack" ] || { echo "[WARN] $name has no cover.pack - not bundled"; continue; }
  mkdir -p "$COVERS_OUT/$name"
  cp "$d/cover.pack" "$COVERS_OUT/$name/"
  bundled=$((bundled + 1))
done
echo "[INFO] Bundled $bundled cover(s), $(du -sh "$COVERS_OUT" | cut -f1) total"

# 4d. The native bits the viewer shells out to / dlopens. Both are referenced by RELATIVE path
#     from the working directory, which is why the launcher below seeds a writable working dir
#     instead of running straight out of the read-only bundle.
mkdir -p "$APP/Contents/Resources/data/build/exe/cover" \
         "$APP/Contents/Resources/data/build/libs/backend/shared"
[ -f "$HERE/build/exe/cover/cover" ] \
  && cp "$HERE/build/exe/cover/cover" "$APP/Contents/Resources/data/build/exe/cover/" \
  || echo "[WARN] build/exe/cover/cover not found - the viewer's 'Check Cover' action will fail"
[ -f "$HERE/build/libs/backend/shared/libbackend.dylib" ] \
  && cp "$HERE/build/libs/backend/shared/libbackend.dylib" \
        "$APP/Contents/Resources/data/build/libs/backend/shared/" \
  || echo "[WARN] libbackend.dylib not found - JNA calls into the backend will fail"

# 4e. Icon. This subproject has its own (final-icon.icns); the main project's icon is only a
#     fallback, so a missing file never silently ships the wrong artwork.
if [ -f "$HERE/$ICON_SRC" ]; then
  cp "$HERE/$ICON_SRC" "$APP/Contents/Resources/$APP_NAME.icns"
  echo "[INFO] Icon: $ICON_SRC"
elif [ -f "$HERE/../../icon.icns" ]; then
  cp "$HERE/../../icon.icns" "$APP/Contents/Resources/$APP_NAME.icns"
  echo "[WARN] $ICON_SRC not found - falling back to the main project's icon.icns"
else
  echo "[WARN] No icon found - the app will use the generic macOS application icon"
fi

# === 5. Info.plist ==========================================================================
cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key><string>$APP_NAME</string>
    <key>CFBundleIdentifier</key><string>ca.billiards.cover</string>
    <key>CFBundleName</key><string>$APP_NAME</string>
    <key>CFBundleDisplayName</key><string>Billiards Cover Viewer</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>$VERSION</string>
    <key>CFBundleVersion</key><string>$VERSION</string>
    <key>CFBundleIconFile</key><string>$APP_NAME.icns</string>
    <key>NSHighResolutionCapable</key><true/>
    <key>LSMinimumSystemVersion</key><string>11.0</string>
</dict>
</plist>
PLIST

# === 6. Launcher ============================================================================
# The viewer resolves coversfolder/, build/exe/cover/cover and the backend dylib by RELATIVE path,
# and writes scratch files while loading. A .app launched from Finder starts with cwd "/", and a
# .dmg is read-only, so neither the bundle nor "/" will do. Seed a working directory in the user's
# home and cd into it - the same approach Main.redirectWorkingDirIfReadOnly takes in the main
# project.
#
# The bundled covers are SYMLINKED, not copied, so the app does not duplicate its own cover data
# in the user's home, and a reinstall picks up new/updated covers automatically. A user's own cover
# folder dropped into ~/BilliardsCover/coversfolder is a real directory and is never touched.
cat > "$APP/Contents/MacOS/$APP_NAME" <<'LAUNCHER'
#!/usr/bin/env bash
set -euo pipefail

BUNDLE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RES="$BUNDLE/Resources"
WORKDIR="$HOME/BilliardsCover"

mkdir -p "$WORKDIR/coversfolder"

# Link every cover the bundle ships. Stale links (app moved or renamed since last run) are
# recreated; real directories the user made are left alone.
for src in "$RES/data/coversfolder"/*/; do
  # An unmatched glob leaves the literal "*/" here; without this guard the loop would create a
  # symlink named "*" pointing at nothing.
  [ -d "$src" ] || continue
  name=$(basename "$src")
  dest="$WORKDIR/coversfolder/$name"
  if [ -L "$dest" ] || [ ! -e "$dest" ]; then
    rm -f "$dest"
    ln -s "${src%/}" "$dest"
  fi
done

# The native bits are small and must be executable/writable, so copy rather than link.
mkdir -p "$WORKDIR/build/exe/cover" "$WORKDIR/build/libs/backend/shared"
[ -f "$RES/data/build/exe/cover/cover" ] \
  && cp -f "$RES/data/build/exe/cover/cover" "$WORKDIR/build/exe/cover/cover"
[ -f "$RES/data/build/libs/backend/shared/libbackend.dylib" ] \
  && cp -f "$RES/data/build/libs/backend/shared/libbackend.dylib" \
           "$WORKDIR/build/libs/backend/shared/libbackend.dylib"
chmod +x "$WORKDIR/build/exe/cover/cover" 2>/dev/null || true

cd "$WORKDIR"

# Heap: the largest covers need several gigabytes of arrays, and a fixed -Xmx4g either wastes
# headroom on a big machine or fails to start on a small one (the JVM refuses an -Xmx it cannot
# reserve). Take 70% of physical RAM, with a 2g floor.
MEM_BYTES=$(sysctl -n hw.memsize 2>/dev/null || echo 0)
XMX_G=$(( MEM_BYTES * 7 / 10 / 1024 / 1024 / 1024 ))
[ "$XMX_G" -lt 2 ] && XMX_G=2

CP="$RES/app/classes"
for j in "$RES/app"/*.jar; do CP="$CP:$j"; done

exec "$RES/jre/bin/java" \
  -Xss8m "-Xmx${XMX_G}g" -server \
  -Djna.library.path="$WORKDIR/build/libs/backend/shared" \
  -cp "$CP" billiards.viewer.Main "$@"
LAUNCHER
chmod +x "$APP/Contents/MacOS/$APP_NAME"

# === 7. Ad-hoc sign =========================================================================
# The Zulu dylibs arrive signed and we do not rewrite them, but the bundle as a whole is new.
# Signing it ad-hoc keeps macOS on Apple Silicon from refusing to launch it outright.
echo "[INFO] Ad-hoc signing the bundle..."
codesign --force --deep --sign - "$APP" 2>&1 | sed 's/^/       /' || echo "[WARN] codesign failed; app may need a Gatekeeper override on first launch"

# === 8. Wrap in a .dmg ======================================================================
mkdir -p "$DIST"
DMG="$DIST/$APP_NAME-$VERSION.dmg"
rm -f "$DMG"
echo "[INFO] Creating $DMG..."
hdiutil create -volname "$APP_NAME" -srcfolder "$APP" -ov -format UDZO "$DMG" | sed 's/^/       /'

if [ -f "$HERE/$INSTRUCTIONS_SRC" ]; then
  cp "$HERE/$INSTRUCTIONS_SRC" "$DIST/Instructions.txt"
  echo "[INFO] Wrote $DIST/Instructions.txt"
else
  echo "[WARN] $INSTRUCTIONS_SRC not found - the .dmg ships without install notes"
fi

echo
echo "[SUCCESS] $DMG"
du -h "$DMG" | sed 's/^/          /'
