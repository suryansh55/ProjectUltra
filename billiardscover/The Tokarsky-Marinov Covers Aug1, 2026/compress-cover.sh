#!/usr/bin/env bash
#
# Bundle one or more cover folders into a single cover.pack (~130x smaller than all the text
# files combined). The viewer loads everything it needs straight from cover.pack, so after
# packing you can DELETE every *.txt in the folder.
#
# The cover columns and the bundled text files are LZMA2-compressed (libs/xz-1.9.jar). That is
# about 40% smaller than the Deflate the format used through July 2026, but a good deal slower
# to write -- budget roughly half an hour for a multi-gigabyte cover.txt. Existing packs stay
# readable; set -Dbilliards.cbin.deflate=true to write the old Deflate form instead.
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
   \) 2>/dev/null | tr '\n' ':')libs/jna-4.5.1.jar:libs/xz-1.9.jar"

# 3) Compile the Java sources (into build-java8/classes).
OUT="$HERE/build-java8/classes"
mkdir -p "$OUT"
find src/java -name '*.java' > "$HERE/build-java8/srcs.txt"
echo "compiling..."
"$JAVAC" -source 1.8 -target 1.8 -cp "$CP" -d "$OUT" @"$HERE/build-java8/srcs.txt"

# 4) Run the converter (it self-verifies each file round-trips exactly).
#
# Heap: packing, verifying and unpacking all stream cover.txt through fixed buffers, so peak
# memory does not scale with the cover's size -- whether the cover is 50 MB or 50 GB. (It used
# to hold the whole cover.txt as bytes, then as a Java 8 String at two bytes per char, then as
# a trimmed copy: about 5x the file size, and a hard wall at 2 GB.)
#
# What does need room is the compressor: the three columns are LZMA2-encoded in parallel, and
# an encoder with a 64 MB dictionary wants roughly 700 MB for its match finder. Three of those
# plus the rest of the JVM is why this is 4g and not the old 2g.
echo "converting..."
"$JAVA" -Xmx4g -cp "$OUT:$CP" billiards.viewer.CoverConverter "$@"

