#!/bin/bash

# 1. Get the exact module path from Gradle
FX_PATH=$(./gradlew -q runtimeClasspathAsPath)

# 2. Get the absolute path to your project directory
PROJECT_DIR=$(pwd)

# 3. Run the program with the correct absolute library path
java \
  -Djava.library.path="$PROJECT_DIR/build/libs/backend/shared/" \
  -Djna.library.path="$PROJECT_DIR/build/libs/backend/shared/" \
  --module-path "$FX_PATH" \
  --add-modules javafx.controls,javafx.fxml \
  -Xss4m -Xmx4G \
  -cp "build/libs/billiard-viewer.jar" \
  billiards.viewer.Main