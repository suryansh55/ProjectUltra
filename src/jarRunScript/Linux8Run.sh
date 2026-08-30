#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd -- "$(dirname "$0")" && pwd)"
JAVA_BIN="$DIR/runtime/bin/java"

exec "$JAVA_BIN" \
  -Djna.library.path="$DIR/backend/shared" \
  -Djava.library.path="$DIR/backend/shared" \
-jar "$DIR/billiard-viewer.jar"

# if you having issue of using the application in WSL use this line
# exec "$JAVA_BIN" \
# -Dprism.order=sw -Dprism.verbose=true \
#   -Djna.library.path="$DIR/backend/shared" \
#   -Djava.library.path="$DIR/backend/shared" \
# -jar "$DIR/billiard-viewer.jar"
