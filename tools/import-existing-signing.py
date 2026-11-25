#!/usr/bin/env python3
"""Configure the existing 0.10.0 signing key, never generate or print a key."""
import argparse
import base64
import json
import os
import pathlib
import re
import subprocess
import tempfile

REPO = "Chasmet/Probl-me-r-curent-travail-"
EXPECTED = "0b9374e1b4cc30e2b6a9d177817038d806c65637a15f47936fb5ed514ede6cd8"
KEYS = {"LF_SIGNING_BUNDLE", "LF_SIGNING_P12_BASE64", "LF_SIGNING_PASSWORD", "LF_SIGNING_KEY_PASSWORD", "LF_EXPECTED_CERT_SHA256"}

def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=pathlib.Path)
    args = parser.parse_args()
    try:
        bundle = json.loads(args.bundle.read_text(encoding="utf-8-sig"))
        fields = ("keystoreBase64", "storePassword", "keyPassword", "certificateSha256")
        if bundle.get("storeType") != "PKCS12" or not all(isinstance(bundle.get(k), str) and bundle[k] for k in fields):
            raise ValueError()
        fp = re.sub(r"[:\s]", "", bundle["certificateSha256"]).lower()
        if fp != EXPECTED:
            raise ValueError()
        raw = base64.b64decode(bundle["keystoreBase64"], validate=True)
        with tempfile.TemporaryDirectory() as tmp:
            key = pathlib.Path(tmp) / "local.p12"
            key.write_bytes(raw)
            env = dict(os.environ, LF_LOCAL_STOREPASS=bundle["storePassword"])
            cert = run(["keytool", "-list", "-v", "-J-Duser.language=en", "-storetype", "PKCS12", "-keystore", str(key), "-storepass:env", "LF_LOCAL_STOREPASS"], env=env, capture_output=True, text=True).stdout
            fingerprints = re.findall(r"SHA256:\s*([0-9A-Fa-f:]+)", cert)
            if len(fingerprints)!=1 or fingerprints[0].replace(":","").lower()!=EXPECTED or "PrivateKeyEntry" not in cert:
                raise ValueError()
    except (ValueError, KeyError, TypeError, OSError, subprocess.CalledProcessError):
        raise SystemExit("Clé invalide ou différente de la version 0.10.0. Aucun secret modifié.")
    run(["gh","auth","status"])
    names = {x["name"] for x in json.loads(run(["gh","secret","list","-R",REPO,"--json","name"],capture_output=True,text=True).stdout)}
    existing = names & KEYS
    if existing:
        raise SystemExit("Des secrets de signature existent déjà. Aucun remplacement automatique.")
    run(["gh","secret","set","LF_SIGNING_BUNDLE","-R",REPO], input=json.dumps(bundle), text=True)
    print("Secret enregistré. Certificat 0.10.0 vérifié. Aucune clé affichée.")
    run(["gh","workflow","run","release.yml","-R",REPO,"--ref","main"])
    print("Publication déclenchée. Vérifier GitHub Actions puis Releases/latest.")

if __name__ == "__main__":
    main()
