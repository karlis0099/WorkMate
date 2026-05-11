#!/usr/bin/env bash
# WorkMate build & run script
# Usage: ./build.sh          — compile + run
#        ./build.sh compile  — compile only
#        ./build.sh run      — run already-compiled classes
set -e

JAR="lib/sqlite-jdbc.jar"
SRC_DIR="src"
OUT_DIR="out"
RES_DIR="resources"
MAIN="com.workmate.WorkMateApp"

if [ ! -f "$JAR" ]; then
  echo "SQLite JDBC driver not found at $JAR"
  echo "Download it from https://github.com/xerial/sqlite-jdbc/releases"
  echo "Place the JAR as: lib/sqlite-jdbc.jar"
  exit 1
fi

if [ "$1" != "run" ]; then
  echo "Compiling…"
  mkdir -p "$OUT_DIR"
  # Copy resources so they land on the classpath
  cp -r "$RES_DIR/static" "$OUT_DIR/static"
  find "$SRC_DIR" -name "*.java" > /tmp/wm_sources.txt
  javac -cp "$JAR" -d "$OUT_DIR" @/tmp/wm_sources.txt
  echo "Compilation successful."
fi

if [ "$1" != "compile" ]; then
  echo "Starting WorkMate…"
  java -cp "$OUT_DIR:$JAR" "$MAIN"
fi
