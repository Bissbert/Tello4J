#!/bin/sh
# Run the JUnit suite in a Linux container with the working tree as it is now.
#
#   sh tools/docker-test.sh
#
# The tests talk only to a UDP endpoint on 127.0.0.1; no aircraft is used.
set -eu

REPO=$(cd "$(dirname "$0")/.." && pwd)
docker run --rm -v "$REPO":/repo:ro -v tello4j-m2:/root/.m2 maven:3.9-eclipse-temurin-11 sh -c '
cp -r /repo /tmp/t4 && cd /tmp/t4 && rm -rf target
mvn -B test > /tmp/mvn.log 2>&1; rc=$?
grep -E "Tests run|FAIL|ERROR\\]|BUILD" /tmp/mvn.log
exit $rc
'
