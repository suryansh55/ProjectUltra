#!/bin/bash

set -e

# === Configuration ===
APP_NAME="BilliardViewer"
MAIN_JAR="billiard-viewer.jar"
MAIN_CLASS="billiards.viewer.Main"
ICON_PATH="icon.icns"
BUILD_DIR="build"
INPUT_DIR="${BUILD_DIR}/libs"
DIST_DIR="dist"
RUNTIME_IMAGE="${BUILD_DIR}/custom-runtime"
DYLIB_TEMP_DIR="${BUILD_DIR}/javafx-natives"

# === Step 1: Auto-detect JAVA_HOME for JDK 17 ===
export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
#export JAVA_HOME=$(/usr/libexec/java_home -v 17)
echo "[INFO] Detected JAVA_HOME: $JAVA_HOME"

# === Step 0: Rebuild the backend for portable distribution ===
# Dev builds use -march=native (tuned to this machine), which can crash on a
# different/older Apple Silicon chip. -Pportable rebuilds the dylib with the
# Apple Silicon M1 baseline (-mcpu=apple-m1) so the .dmg runs on any arm64 Mac.
echo "[INFO] Rebuilding backend dylib for portable distribution (-Pportable / -mcpu=apple-m1)..."
./gradlew backendSharedLibrary -Pportable -q

echo "[INFO] Ensuring main JAR is present..."
cp -f "$INPUT_DIR/$MAIN_JAR" "$BUILD_DIR/$MAIN_JAR"


# === Step 5.6: Ensure native dylib is present ===
echo "[INFO] Copying libbackend.dylib to input/backend/shared"
cp -f "build/libs/backend/shared/libbackend.dylib" "$BUILD_DIR/"

# === Step 2: Resolve JavaFX native libraries from Gradle ===
echo "[INFO] Resolving JavaFX runtime JARs..."
./gradlew copyRuntimeLibs -q

# === Step 3: Extract native .dylib files from JavaFX jars ===
echo "[INFO] Extracting JavaFX native libraries from jars..."

mkdir -p "$DYLIB_TEMP_DIR"
rm -rf "$DYLIB_TEMP_DIR"/*
find "$INPUT_DIR" -name "javafx-*.jar" | while read -r jar; do
  unzip -q "$jar" "*.dylib" -d "$DYLIB_TEMP_DIR" || true
done

echo "[INFO] Creating custom Java runtime with jlink..."
# jlink refuses to write into an existing directory, so clear any image from a
# previous run to keep this script re-runnable.
rm -rf "$RUNTIME_IMAGE"
"$JAVA_HOME/bin/jlink" \
  --module-path "$JAVA_HOME/jmods:$(./gradlew -q runtimeClasspathAsPath)" \
  --add-modules java.base,java.logging,java.desktop,javafx.controls,javafx.fxml,javafx.graphics,java.sql,jdk.crypto.ec,java.security.jgss \
  --output "$RUNTIME_IMAGE" \
  --strip-debug \
  --compress=2 \
  --no-header-files \
  --no-man-pages

# === Step 5: Copy .dylib files to custom runtime ===
echo "[INFO] Copying native .dylib files into runtime..."
cp "$DYLIB_TEMP_DIR"/*.dylib "$RUNTIME_IMAGE/bin" || echo "[WARN] No dylibs found to copy"

echo "[INFO] Ensuring main JAR is present..."
cp -f "$BUILD_DIR/$MAIN_JAR" "$INPUT_DIR/$MAIN_JAR"

# === Step 5.6: Ensure native dylib is present ===
# === Step 5.6: Bundle and Patch Native Libraries ===
NATIVE_DIR="$INPUT_DIR/backend/shared"
mkdir -p "$NATIVE_DIR"

echo "[INFO] Copying libbackend.dylib..."
#cp "$BUILD_DIR/libs/backend/shared/libbackend.dylib" "$NATIVE_DIR/"
cp "$BUILD_DIR/libbackend.dylib" "$NATIVE_DIR/"
# 1 & 2. Recursively bundle every non-system dynamic dependency.
# Starting from libbackend.dylib, we walk the dependency graph (otool -L) and
# copy each Homebrew/relative dependency into the bundle, repeating until no
# new libraries appear. This replaces the old hand-maintained DEPENDENCIES
# list, which silently missed transitive deps — e.g. libboost_thread depends
# on libboost_atomic / libboost_container / libboost_chrono / libboost_date_time
# (via @loader_path), none of which were in the list, causing dlopen to fail
# at launch with "Library not loaded: @loader_path/libboost_atomic.dylib".
HOMEBREW_PREFIX="/opt/homebrew"
# Search locations for deps referenced by bare name (@loader_path / @rpath).
SEARCH_DIRS=("$HOMEBREW_PREFIX/lib" "$HOMEBREW_PREFIX"/opt/*/lib)

# Resolve a dylib by basename across the Homebrew search dirs.
find_brew_lib() {
    local name=$1 d
    for d in "${SEARCH_DIRS[@]}"; do
        if [ -f "$d/$name" ]; then echo "$d/$name"; return 0; fi
    done
    return 1
}

echo "[INFO] Recursively bundling native dependencies..."
while :; do
    before=$(find "$NATIVE_DIR" -maxdepth 1 -name '*.dylib' | wc -l)
    for dylib in "$NATIVE_DIR"/*.dylib; do
        for dep in $(otool -L "$dylib" | tail -n +2 | awk '{print $1}'); do
            # Skip OS-provided libraries — they must NOT be bundled.
            case "$dep" in
                /usr/lib/*|/System/*) continue ;;
            esac
            depname=$(basename "$dep")
            # Skip self-reference (the library's own install-name ID).
            [ "$depname" = "$(basename "$dylib")" ] && continue
            # Already bundled?
            [ -f "$NATIVE_DIR/$depname" ] && continue
            # Absolute path -> use directly; bare/relative -> resolve by name.
            if [ "${dep:0:1}" = "/" ]; then
                src="$dep"
            else
                src=$(find_brew_lib "$depname") || src=""
            fi
            if [ -n "$src" ] && [ -f "$src" ]; then
                echo "  -> Bundling $depname"
                cp "$src" "$NATIVE_DIR/"
                chmod 755 "$NATIVE_DIR/$depname"
            else
                echo "[WARN] Could not resolve dependency '$dep' (referenced by $(basename "$dylib"))"
            fi
        done
    done
    after=$(find "$NATIVE_DIR" -maxdepth 1 -name '*.dylib' | wc -l)
    # Fixpoint: stop once a full pass adds no new libraries.
    [ "$before" -eq "$after" ] && break
done

# 3. Patching the libraries (The Magic Step)
# This changes absolute paths (/opt/homebrew/...) to relative paths (@loader_path/...)
echo "[INFO] Patching library paths (install_name_tool)..."

cd "$NATIVE_DIR"

# A. Helper function to change the ID of a library to be relative
fix_dylib_id() {
    local lib_name=$1
    echo "  -> Setting ID for $lib_name"
    install_name_tool -id "@loader_path/$lib_name" "$lib_name"
}

# B. Helper function to fix dependency references inside a library
fix_dependencies() {
    local target_lib=$1
    # Check for references to Homebrew libs and rewrite them to @loader_path
    otool -L "$target_lib" | grep "/opt/homebrew" | awk '{print $1}' | while read -r dep_path; do
        local dep_name=$(basename "$dep_path")
        echo "     Fixing ref in $target_lib: $dep_name"
        install_name_tool -change "$dep_path" "@loader_path/$dep_name" "$target_lib"
    done
}

# Apply fixes to ALL .dylib files in the directory (backend + dependencies)
for dylib in *.dylib; do
    fix_dylib_id "$dylib"
    fix_dependencies "$dylib"
done

# Re-sign after install_name_tool. (Suryansh Ankur, 2026)
# Each install_name_tool rewrite above invalidates that dylib's code signature.
# macOS on Apple Silicon (26.x) SIGKILLs any process the instant dyld loads a
# dylib whose signature is invalid ("SIGKILL — Code Signature Invalid"), which
# crashes the app at launch with no useful error. An ad-hoc re-sign re-hashes
# the modified code and restores a valid signature the loader accepts.
echo "[INFO] Re-signing dylibs (ad-hoc) after install_name_tool..."
for dylib in *.dylib; do
    codesign --force --sign - "$dylib"
done

# Return to build root
cd - > /dev/null

# ... other copy commands
echo "[INFO] Copying updater.sh to input directory..."
cp -f "updater.sh" "$INPUT_DIR/"
chmod +x "$INPUT_DIR/updater.sh"

cp "updater.bat" "$INPUT_DIR/"

# === Step 6: Package with jpackage ===
# Remove a previously built installer so jpackage doesn't fail on an existing file.
rm -f "$DIST_DIR/$APP_NAME"*.dmg
echo "[INFO] Running jpackage..."
"$JAVA_HOME/bin/jpackage" \
  --type dmg \
  --name "$APP_NAME" \
  --input "$INPUT_DIR" \
  --main-jar "$MAIN_JAR" \
  --main-class "$MAIN_CLASS" \
  --runtime-image "$RUNTIME_IMAGE" \
  --dest "$DIST_DIR" \
  --icon "$ICON_PATH" \
  --java-options "-Djna.library.path=\$APPDIR/backend/shared" \
  --java-options "--add-modules=javafx.controls,javafx.fxml,java.sql" \
  --app-version 3.0


echo "[✅ SUCCESS] Installer created at $DIST_DIR"
