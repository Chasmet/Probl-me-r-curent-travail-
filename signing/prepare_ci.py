"""Fetch the LES FAITS permanent signer over the same OIDC pattern as CHK Browser."""
import json
import os
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

AUDIENCE = "https://modeliseur-trellis-mcp.onrender.com/lesfaits/ci/signing"
EXPECTED = "0b9374e1b4cc30e2b6a9d177817038d806c65637a15f47936fb5ed514ede6cd8"
url = os.environ["ACTIONS_ID_TOKEN_REQUEST_URL"]
url += ("&" if "?" in url else "?") + "audience=" + urllib.parse.quote(AUDIENCE, safe="")
for attempt in range(7):
    try:
        request = urllib.request.Request(url, headers={"Authorization": "Bearer " + os.environ["ACTIONS_ID_TOKEN_REQUEST_TOKEN"]})
        with urllib.request.urlopen(request, timeout=25) as response:
            token = json.load(response)["value"]
        request = urllib.request.Request(AUDIENCE, headers={"Authorization": "Bearer " + token, "User-Agent": "LES-FAITS-CI/1.0"})
        with urllib.request.urlopen(request, timeout=75) as response:
            bundle = json.load(response)
        break
    except urllib.error.HTTPError as error:
        if error.code in (401,403):
            raise SystemExit("OIDC refusé par le serveur (HTTP %s)." % error.code)
        if attempt == 6 or error.code not in (404,429,500,502,503,504):
            raise SystemExit("Signature LES FAITS indisponible sur Render (HTTP %s)." % error.code)
        print("Service de signature indisponible (HTTP %s), nouvelle tentative." % error.code)
    except (TimeoutError, urllib.error.URLError, OSError):
        if attempt == 6:
            raise SystemExit("Service de signature inaccessible.")
    time.sleep(min(8+attempt*4,24))

if bundle.get("certificateSha256","").replace(":","").lower() != EXPECTED or bundle.get("storeType") != "PKCS12":
    raise SystemExit("Refus : la signature ne correspond pas à la version 0.10.0.")
env = dict(os.environ)
for k in ("LF_SIGNING_BUNDLE","LF_SIGNING_P12_BASE64","LF_SIGNING_PASSWORD","LF_SIGNING_KEY_PASSWORD","LF_EXPECTED_CERT_SHA256"):
    env.pop(k,None)
env["LF_SIGNING_BUNDLE"] = json.dumps(bundle)
subprocess.run(["node",".github/scripts/select-release-signer.mjs"],env=env,check=True)
print("Signature permanente LES FAITS vérifiée via OIDC.")
