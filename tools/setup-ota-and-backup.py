#!/usr/bin/env python3
"""Local one-time signing setup and legacy Android backup. Never uploads reports or keys to chat."""
import argparse, base64, io, json, os, pathlib, re, secrets, sqlite3, subprocess, tarfile, tempfile, zipfile
REPO = "Chasmet/Probl-me-r-curent-travail-"
PACKAGE = "fr.sync30.lesfaits"
def run(args, **kw):
    return subprocess.run(args, check=True, **kw)
def setup():
    run(["gh","auth","status"])
    names={s["name"] for s in json.loads(run(["gh","secret","list","-R",REPO,"--json","name"],capture_output=True,text=True).stdout)}
    required={"LF_SIGNING_P12_BASE64","LF_SIGNING_PASSWORD","LF_EXPECTED_CERT_SHA256"}
    folder=pathlib.Path.home()/".lesfaits-signing"
    if names & required and not (folder/"lesfaits.p12").exists():
        raise SystemExit("Secrets existants : arrêt, aucune clé ne sera remplacée. Retrouver la clé officielle avant de continuer.")
    if folder.exists() and not (folder/"lesfaits.p12").exists():
        raise SystemExit("Dossier de clé incomplet : aucune régénération automatique.")
    if not folder.exists():
        folder.mkdir(mode=0o700)
        password=secrets.token_hex(32)
        p=folder/"password.txt"
        with p.open("x",encoding="ascii") as f: f.write(password)
        os.chmod(p,0o600)
        run(["keytool","-genkeypair","-storetype","PKCS12","-keystore",str(folder/"lesfaits.p12"),"-storepass",password,"-alias","lesfaits","-keyalg","RSA","-keysize","3072","-validity","10000","-dname","CN=LES FAITS SYNC 30"])
    password=(folder/"password.txt").read_text(encoding="ascii").strip()
    key=folder/"lesfaits.p12"
    cert=run(["keytool","-list","-v","-J-Duser.language=en","-keystore",str(key),"-storepass",password,"-alias","lesfaits"],capture_output=True,text=True).stdout
    fp=re.search(r"SHA256:\s*([0-9A-Fa-f:]+)",cert).group(1).replace(":","").upper()
    # Never replace existing complete credentials. A previous partial setup can be resumed with its local key.
    if required<=names:
        print("Secrets déjà configurés : aucune modification. Vérifier la clé locale et la publication.")
    else:
        if names & required:
            raise SystemExit("Configuration partielle : arrêt pour éviter de remplacer une signature existante. Vérifier les secrets sur GitHub.")
        for name,value in [("LF_SIGNING_P12_BASE64",base64.b64encode(key.read_bytes()).decode()),("LF_SIGNING_PASSWORD",password),("LF_EXPECTED_CERT_SHA256",fp)]:
            run(["gh","secret","set",name,"-R",REPO],input=value,text=True)
        print("Signature permanente enregistrée dans GitHub Secrets.")
    print("Conserver une copie privée chiffrée du dossier",folder,"(clé et mot de passe). Ne jamais publier ces fichiers.")
    run(["gh","workflow","run","release.yml","-R",REPO,"--ref","main"])
def backup(output):
    output=pathlib.Path(output).resolve()
    if output.exists(): raise SystemExit("Le fichier existe déjà. Aucun écrasement.")
    run(["adb","get-state"])
    run(["adb","shell","am","force-stop",PACKAGE])
    # run-as requires the old build to be debuggable; fail safely otherwise.
    run(["adb","shell","run-as",PACKAGE,"id"],capture_output=True)
    with tempfile.TemporaryDirectory() as tmp:
        tmp=pathlib.Path(tmp)
        db=tmp/"lesfaits.db"
        for name in ["lesfaits.db","lesfaits.db-wal"]:
            r=subprocess.run(["adb","exec-out","run-as",PACKAGE,"cat","databases/"+name],capture_output=True)
            if r.returncode==0 and r.stdout:
                (tmp/name).write_bytes(r.stdout)
            elif name=="lesfaits.db": raise RuntimeError("Base de données inaccessible.")
        clean=tmp/"snapshot.db"
        with sqlite3.connect(db) as source, sqlite3.connect(clean) as target:
            source.backup(target)
            if target.execute("PRAGMA quick_check").fetchone()!=("ok",): raise RuntimeError("Base non intègre.")
            version=target.execute("PRAGMA user_version").fetchone()[0]
            if version not in (1,2): raise RuntimeError("Cette migration est destinée à LES FAITS 0.7.1.")
            counts={t:target.execute("SELECT COUNT(*) FROM "+t).fetchone()[0] for t in ("incidents","notes","photos")}
            paths=[row[0] for row in target.execute("SELECT path FROM photos")]
        partial=output.with_suffix(output.suffix+".part")
        try:
            with zipfile.ZipFile(partial,"x",zipfile.ZIP_DEFLATED) as z:
                z.write(clean,"lesfaits.db")
                for name in sorted({pathlib.PurePosixPath(p).name for p in paths}):
                    if not re.fullmatch(r"[A-Za-z0-9_-]{1,100}\.(img|jpg|jpeg|png)",name): raise RuntimeError("Nom de photo inattendu.")
                    r=run(["adb","exec-out","run-as",PACKAGE,"cat","files/photos/"+name],capture_output=True)
                    if not r.stdout or len(r.stdout)>16*1024*1024: raise RuntimeError("Photo manquante ou trop grande : "+name)
                    z.writestr("photos/"+name,r.stdout)
            with zipfile.ZipFile(partial) as z:
                if z.testzip(): raise RuntimeError("Archive corrompue.")
            partial.rename(output)
        finally:
            if partial.exists(): partial.unlink()
    print("Sauvegarde vérifiée :",output,counts)
    print("Copier ce ZIP dans les téléchargements Android. Ne désinstaller qu'après avoir vérifié une copie indépendante.")
    run(["adb","push",str(output),"/sdcard/Download/"+output.name])
def main():
    parser=argparse.ArgumentParser()
    sub=parser.add_subparsers(dest="action",required=True)
    sub.add_parser("signature")
    b=sub.add_parser("sauvegarde"); b.add_argument("output")
    args=parser.parse_args()
    if args.action=="signature": setup()
    else: backup(args.output)
if __name__=="__main__": main()
