#!/bin/sh
# Minimal Maven Wrapper for Unix/macOS (Windows: use mvnw.cmd)
set -e
BASE_DIR=$(cd "$(dirname "$0")" && pwd)
WRAPPER_JAR="$BASE_DIR/.mvn/wrapper/maven-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "ERROR: .mvn/wrapper/maven-wrapper.jar not found" >&2
  exit 1
fi
exec java -classpath "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
