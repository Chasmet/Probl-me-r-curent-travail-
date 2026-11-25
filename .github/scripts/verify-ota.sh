#!/usr/bin/env bash
# Runs only on a clean test emulator, with synthetic data.
set -euo pipefail
package=fr.sync30.lesfaits
version=$(jq -er '.version' .github/ota/signed.json)
gh api "repos/$GITHUB_REPOSITORY/releases?per_page=100" > "$RUNNER_TEMP/releases.json"
prior=$(jq -er --arg tag "v$version" '[.[]|select(.draft==false and .prerelease==false and .tag_name!=$tag and any(.assets[];.name=="LES-FAITS-SYNC30.apk"))][0].tag_name' "$RUNNER_TEMP/releases.json")
mkdir -p "$RUNNER_TEMP/previous"
gh release download "$prior" -p LES-FAITS-SYNC30.apk -D "$RUNNER_TEMP/previous"
adb install "$RUNNER_TEMP/previous/LES-FAITS-SYNC30.apk"
adb root
adb wait-for-device
adb shell am start -W -n "$package/.MainActivity"
sleep 3
adb shell am force-stop "$package"
db="/data/user/0/$package/databases/lesfaits.db"
mkdir -p "$RUNNER_TEMP/ota-db"
adb pull "$(dirname "$db")/." "$RUNNER_TEMP/ota-db/"
python3 - "$RUNNER_TEMP/ota-db/lesfaits.db" <<'PY'
import sqlite3,sys
c=sqlite3.connect(sys.argv[1])
c.execute("INSERT INTO incidents(space,category,title,description,location,occurred,created,severity,recurring,status) VALUES('TERRAIN','Autre','Témoin OTA','Contrôle sur émulateur','',1,1,'Normale',0,'En cours')")
c.commit()
c.execute("PRAGMA wal_checkpoint(TRUNCATE)")
c.close()
PY
# Write the existing inode to retain its application ownership and SELinux label.
adb push "$RUNNER_TEMP/ota-db/lesfaits.db" /data/local/tmp/lesfaits-fixture.db
adb shell "cat /data/local/tmp/lesfaits-fixture.db > '$db' && rm -f '$db-wal' '$db-shm'"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell am force-stop com.google.android.apps.nexuslauncher || true
adb shell appops set "$package" REQUEST_INSTALL_PACKAGES allow
adb shell appops get "$package" REQUEST_INSTALL_PACKAGES
adb shell am start -W -n "$package/.MainActivity"
# Exercise the same explicit action as a tap on the update notification.
adb shell am start -W -a fr.sync30.lesfaits.UPDATE_OPEN -n "$package/.MainActivity"
if python3 -u - "$version" <<'PY'
import re,sys,time,subprocess,xml.etree.ElementTree as ET
def adb(*args):
    return subprocess.run(["adb",*args],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True).stdout
def tap(node):
    x,y,r,b=map(int,re.findall(r"\d+",node.get("bounds","")))
    adb("shell","input","tap",str((x+r)//2),str((y+b)//2))
installer=False
deadline=time.monotonic()+150
last_check=0
last_view=None
while time.monotonic()<deadline:
    if "versionName="+sys.argv[1] in adb("shell","dumpsys","package","fr.sync30.lesfaits"):
        assert installer,"Android installer confirmation was not observed"
        print("OTA verified, installer confirmed and new version installed")
        break
    adb("shell","uiautomator","dump","/sdcard/ota-ui.xml")
    try: nodes=ET.fromstring(adb("exec-out","cat","/sdcard/ota-ui.xml")).iter("node")
    except ET.ParseError: continue
    nodes=list(nodes)
    labels=tuple((n.get("package",""),n.get("text","")[:200]) for n in nodes if n.get("text"))
    if labels!=last_view:
        print("Écran émulateur",labels[:25],flush=True);last_view=labels
    for node in nodes:
        text=node.get("text","").strip().lower()
        pkg=node.get("package","").lower()
        if ("packageinstaller" in pkg or "permissioncontroller" in pkg) and text in ("update","install","mettre à jour","installer","continuer","continue"):
            installer=True
            tap(node)
            break
        if pkg=="android" and text in ("close app","fermer l’application","wait","attendre"):
            tap(node);break
        if pkg=="fr.sync30.lesfaits" and text=="ok" and any("vérification" in label.lower() or "version plus récente" in label.lower() for _,label in labels):
            tap(node);break
        if pkg=="fr.sync30.lesfaits" and text in ("mises à jour","télécharger","télécharger et installer") and time.monotonic()-last_check>12:
            tap(node);last_check=time.monotonic()
            break
    time.sleep(1)
else:
    print(adb("shell","dumpsys","activity","activities").split("mLastPausedActivity")[0][-2200:])
    log=adb("logcat","-d","-t","2500")
    for line in log.splitlines():
        if any(word in line for word in ("LesFaits","SkillMajAuto","Background activity","BAL_BLOCK","UPDATE_STATUS","PackageInstallerSession")):print(line)
    raise SystemExit("Mise à jour non confirmée sur l’émulateur")
PY
then
  integrated=yes
else
  integrated=no
  echo "::warning::Le parcours interactif n'a pas abouti. Contrôle distinct de la compatibilité de l’APK et de la conservation des données."
  gh release download "v$version" -p LES-FAITS-SYNC30.apk -D "$RUNNER_TEMP"
  adb install -r "$RUNNER_TEMP/LES-FAITS-SYNC30.apk"
fi
adb shell am start -W -n "$package/.MainActivity"
adb shell am force-stop "$package"
mkdir -p "$RUNNER_TEMP/ota-result"
adb pull "$(dirname "$db")/." "$RUNNER_TEMP/ota-result/"
python3 - "$RUNNER_TEMP/ota-result/lesfaits.db" <<'PY'
import sqlite3,sys
with sqlite3.connect(sys.argv[1]) as db:
    assert db.execute("SELECT status FROM incidents WHERE title='Témoin OTA'").fetchone()==('En cours',)
print("Rapport témoin conservé après installation")
PY
test "$integrated" = yes
