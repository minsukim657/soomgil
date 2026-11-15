#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT HUP INT TERM
# No Android SDK or third-party test dependencies required; JDK 17+ is sufficient.
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -encoding UTF-8 -d "$classes" \
    app/src/main/java/kr/carecabin/demo/VentilationPolicy.java \
    app/src/main/java/kr/carecabin/demo/WorkZoneApproach.java \
    app/src/main/java/kr/carecabin/demo/EnvironmentReading.java \
    app/src/main/java/kr/carecabin/demo/VentilationInputBridge.java \
    app/src/test/java/kr/carecabin/demo/VentilationInputBridgeTest.java \
    app/src/test/java/kr/carecabin/demo/VentilationPolicyTest.java
java -cp "$classes" kr.carecabin.demo.VentilationPolicyTest
java -cp "$classes" kr.carecabin.demo.VentilationInputBridgeTest
