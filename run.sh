#!/bin/zsh

# assembledebug and ensure success
./gradlew assembleDebug || { echo 'Build failed' ; exit 1; }
echo 'Build succeeded'
adb shell pm uninstall com.flandolf.workout
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.flandolf.workout/.MainActivity