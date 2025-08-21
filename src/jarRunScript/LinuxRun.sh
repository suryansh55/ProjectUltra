#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd -- "$(dirname "$0")" && pwd)"

java \
  -Djna.library.path="$DIR/backend/shared" \
  -Djava.library.path="$DIR/backend/shared" \
-jar ./billiard-viewer.jar

# if you having issue of using the application in WSL use this line
# java \
# -Dprism.order=sw -Dprism.verbose=true \
#   -Djna.library.path="$DIR/backend/shared" \
#   -Djava.library.path="$DIR/backend/shared" \
# -jar ./billiard-viewer.jar
