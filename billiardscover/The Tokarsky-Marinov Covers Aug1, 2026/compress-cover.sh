#!/usr/bin/env bash
#
# Bundle one or more cover folders into a single cover.pack (~49x smaller than all the text
# files combined). The viewer loads everything it needs straight from cover.pack, so after
# packing you can DELETE every *.txt in the folder.
#
# Usage:
#   ./compress-cover.sh coversfolder/G14cover                       # pack (make cover.pack)
#   ./compress-cover.sh coversfolder/G14cover coversfolder/G20cover # several at once
#   ./compress-cover.sh --unpack coversfolder/G14cover             # restore all *.txt from pack
#
# Packing self-verifies (every text file round-trips exactly). --unpack regenerates the loose
# text files, which the external C++ "Check Cover" tool reads. Each folder to pack must contain
# cover.txt, stables.txt and triples.txt.
#
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

if [ "$#" -eq 0 ]; then
  echo "usage: $0 <coverDir> [<coverDir> ...]" >&2
  exit 2
fi

# 1) Java 8 (Zulu JDK installed under billiardscover/jdk8); fall back to PATH java.
JAVA8="$HERE/../jdk8/zulu8.96.0.19-ca-fx-jdk8.0.502-macosx_aarch64/Contents/Home"
if [ -x "$JAVA8/bin/java" ]; then
  JAVAC="$JAVA8/bin/javac"; JAVA="$JAVA8/bin/java"
else
  JAVAC="javac"; JAVA="java"
fi

# 2) Dependency jars from the Gradle cache (+ bundled JNA).
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

# 4) Run the converter (it self-verifies each file round-trips exactly).
echo "converting..."
"$JAVA" -Xmx3g -cp "$OUT:$CP" billiards.viewer.CoverConverter "$@"
