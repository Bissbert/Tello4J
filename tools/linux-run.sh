#!/bin/sh
# Builds the library and runs every measurement in docs/measurement.md inside
# a Linux container.
#
#   sh tools/linux-run.sh > media/captures/linux-run.txt
#
# The repository is mounted read-only and copied inside the container. The
# protocol probe talks only to a UDP stub on 127.0.0.1; no aircraft is used.
set -eu

REPO=$(cd "$(dirname "$0")/.." && pwd)
IMAGE=maven:3.9-eclipse-temurin-11

docker pull -q "$IMAGE" >/dev/null
docker run --rm -v "$REPO":/repo:ro "$IMAGE" sh -c '
section() { printf "\n=== %s\n" "$*"; }
apt-get -qq update >/dev/null 2>&1 && apt-get -qq install -y python3 >/dev/null 2>&1
cp -r /repo /tmp/t4 && cd /tmp/t4 && rm -rf target

section "environment"
uname -srm
java -version 2>&1 | head -1
mvn -v 2>/dev/null | head -1
python3 --version

section "mvn -B clean package (compiles and runs the JUnit suite)"
mvn -B clean package >/tmp/mvn.log 2>&1
echo "exit=$?"
grep -E "Tests run:|BUILD" /tmp/mvn.log | sed "s/^\[[A-Z]*\] //"

section "python3 tools/inventory.py"
python3 tools/inventory.py

section "python3 tools/command_table.py"
python3 tools/command_table.py

section "python3 tools/protocol_probe.py"
python3 tools/protocol_probe.py
echo "exit=$?"
'
