#!/bin/bash
./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk apks/
echo "✓ APK listo en apks/"
