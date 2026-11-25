# Reprendre les mises à jour automatiques après 0.10.0

La version stable 0.10.0 est signée avec le certificat SHA-256 `0b9374e1b4cc30e2b6a9d177817038d806c65637a15f47936fb5ed514ede6cd8`.
Ne jamais générer une autre clé, ni publier un APK debug à sa place.

## Configuration unique

Le fichier privé remis au propriétaire lors de la publication 0.10.0 est nommé LES-FAITS-NOUVELLE-CLE-PRIVEE.json.
Sur un ordinateur avec GitHub CLI, Python 3 et Java 17, depuis le dépôt :

```sh
gh auth login
python tools/import-existing-signing.py "chemin/LES-FAITS-NOUVELLE-CLE-PRIVEE.json"
```

Le script contrôle le certificat du vrai PKCS12, refuse tout remplacement de secrets existants, transmet le bundle par stdin à GitHub Secrets et lance release.yml.
Le secret peut aussi être créé manuellement dans Settings > Secrets and variables > Actions, sous le nom LF_SIGNING_BUNDLE, en utilisant le JSON original vérifié. Ne pas coller la clé dans le chat ou le dépôt.
Après cette configuration, chaque nouvelle version peut être publiée automatiquement avec la même signature. Ne pas désinstaller l'application.
