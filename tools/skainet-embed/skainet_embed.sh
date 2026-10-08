#!/bin/sh
# LocalRAG embedding sidecar backed by SKaiNET (see localrag-tooling/localrag-embedder-skainet).
#
# Speaks the same protocol as tools/embed: JSON objects on stdin, JSON float arrays on stdout.
# Invoked by the Gradle plugin as: sh skainet_embed.sh --dimensions 256
#
# The jar is located relative to this script so no absolute path enters the embed task's cache
# key, mirroring how the Python sidecar keeps its interpreter a bare name.
set -e

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$SCRIPT_DIR/../../localrag-embedder-skainet/build/libs/localrag-embedder-skainet-0.1.0-sidecar.jar"

if [ ! -f "$JAR" ]; then
    echo "skainet-embed: sidecar jar not built. Run:" >&2
    echo "  ./gradlew -p localrag-embedder-skainet sidecarJar" >&2
    exit 1
fi

# SKaiNET needs a Java 21 runtime; the Vector API module lets it pick SIMD ops where available.
exec java --add-modules jdk.incubator.vector -jar "$JAR" "$@"
