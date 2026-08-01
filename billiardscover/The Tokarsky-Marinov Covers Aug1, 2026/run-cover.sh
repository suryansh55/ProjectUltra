#!/usr/bin/env bash
#
# Build and launch the cover viewer on this machine using the bundled Java 8 (Zulu, with
# JavaFX). No Gradle needed. The GUI window opens; close it to return to the shell.
#
# Usage:
#   ./run-cover.sh
#
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

# 1) Java 8 (Zulu JDK with JavaFX, installed under billiardscover/jdk8); else PATH java.
JAVA8="$HERE/../jdk8/zulu8.96.0.19-ca-fx-jdk8.0.502-macosx_aarch64/Contents/Home"
if [ -x "$JAVA8/bin/java" ]; then
  JAVAC="$JAVA8/bin/javac"; JAVA="$JAVA8/bin/java"
else
  JAVAC="javac"; JAVA="java"
fi

# 2) Dependency jars from the Gradle cache (+ bundled JNA). JavaFX comes from the JDK.
G="$HOME/.gradle/caches/modules-2/files-2.1"
CP="$(find "$G" \( \
      -name 'eclipse-collections-9.2.0.jar' -o -name 'eclipse-collections-api-9.2.0.jar' \
   -o -name 'guava-25.1-jre.jar' -o -name 'commons-lang3-3.7.jar' \
   -o -name 'commons-math3-3.6.1.jar' -o -name 'javaslang-2.0.5.jar' \
   \) 2>/dev/null | tr '\n' ':')libs/jna-4.5.1.jar"

# 3) Compile the Java sources (into build-java8/classes).
OUT="$HERE/build-java8/classes"
mkdir -p "$OUT"
find src/java -name '*.java' > "$HERE/build-java8/srcs.txt"
echo "compiling..."
"$JAVAC" -source 1.8 -target 1.8 -cp "$CP" -d "$OUT" @"$HERE/build-java8/srcs.txt"

# 4) Launch the viewer. Relative paths (coversfolder/..., build/exe/...) resolve from here.
echo "launching viewer..."
exec "$JAVA" -Xss8m -Xmx4g -server \
  -Djna.library.path=./build/libs/backend/shared \
  -cp "$OUT:$CP" billiards.viewer.Main
