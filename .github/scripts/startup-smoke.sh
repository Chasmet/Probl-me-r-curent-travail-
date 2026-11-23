#!/usr/bin/env bash
set -euo pipefail
mkdir -p startup-artifacts
gradle :app:assembleDebug --stacktrace
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am force-stop fr.sync30.lesfaits || true
adb shell am start -W -n fr.sync30.lesfaits/.MainActivity
sleep 9
adb logcat -d -v time > startup-artifacts/lesfaits-logcat.txt
adb exec-out screencap -p > startup-artifacts/lesfaits-startup.png || true
if grep -q -E 'FATAL EXCEPTION|Process: fr.sync30.lesfaits' startup-artifacts/lesfaits-logcat.txt; then
  grep -A 35 -B 5 -E 'FATAL EXCEPTION|Process: fr.sync30.lesfaits' startup-artifacts/lesfaits-logcat.txt | tail -180 || true
  echo 'ÉCHEC : exception fatale Android au démarrage'
  exit 1
fi
adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|mCurrentFocus|fr.sync30.lesfaits' | head -25 || true
adb shell pidof fr.sync30.lesfaits || { echo 'ÉCHEC : processus LES FAITS absent après lancement'; exit 1; }
echo 'SUCCÈS : LES FAITS démarre et reste actif après 9 secondes'
