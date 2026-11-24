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
# The Pixel launcher can show an unrelated ANR dialog during emulator boot.
# Recover the foreground app and retry accessibility inspection before failing.
adb shell am force-stop com.google.android.apps.nexuslauncher || true
adb shell am start -n fr.sync30.lesfaits/.MainActivity >/dev/null || true
sleep 2
FOUND=0
for attempt in 1 2 3; do
    adb shell uiautomator dump /sdcard/lesfaits-ui.xml >/dev/null || true
    adb exec-out cat /sdcard/lesfaits-ui.xml > startup-artifacts/lesfaits-startup-ui.xml || true
    if python3 - <<'PY' > startup-artifacts/vault-tap.txt
import re, sys, xml.etree.ElementTree as ET
root=ET.parse("startup-artifacts/lesfaits-startup-ui.xml").getroot()
nodes=[n for n in root.iter("node") if n.attrib.get("text")=="Fichiers"]
if not nodes:
    sys.exit("Onglet Fichiers non visible dans la hiérarchie UI")
nums=list(map(int,re.findall(r"\d+",nodes[0].attrib.get("bounds",""))))
if len(nums)!=4:sys.exit("Bornes onglet Fichiers invalides")
print((nums[0]+nums[2])//2,(nums[1]+nums[3])//2)
PY
    then FOUND=1; break; fi
    adb shell input keyevent 4 || true
    adb shell am force-stop com.google.android.apps.nexuslauncher || true
    adb shell am start -n fr.sync30.lesfaits/.MainActivity >/dev/null || true
    sleep 2
done
if [ "$FOUND" != 1 ]; then
    echo "ÉCHEC : impossible de repérer l'onglet Fichiers après trois tentatives"
    exit 1
fi
read -r X Y < startup-artifacts/vault-tap.txt
adb shell input tap "$X" "$Y"
sleep 3
adb shell uiautomator dump /sdcard/lesfaits-vault.xml >/dev/null
adb exec-out cat /sdcard/lesfaits-vault.xml > startup-artifacts/lesfaits-vault-ui.xml
python3 - <<'PY'
import xml.etree.ElementTree as ET
root=ET.parse("startup-artifacts/lesfaits-vault-ui.xml").getroot()
texts={n.attrib.get("text","") for n in root.iter("node")}
if "Ma bibliothèque de preuves" not in texts:
    raise SystemExit("L'onglet Fichiers ne s'est pas ouvert")
print("SUCCÈS : onglet Fichiers et bibliothèque accessibles")
PY
adb logcat -d -v time > startup-artifacts/lesfaits-logcat.txt
if grep -q -E 'FATAL EXCEPTION|Process: fr.sync30.lesfaits' startup-artifacts/lesfaits-logcat.txt; then
    echo 'ÉCHEC : crash en ouvrant les fichiers'
    exit 1
fi
adb shell pidof fr.sync30.lesfaits || { echo 'ÉCHEC : LES FAITS a quitté en ouvrant la bibliothèque'; exit 1; }

