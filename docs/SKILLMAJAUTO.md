# SkillMajAuto — LES FAITS — SYNC 30

**Intégré à l'application Android native**, sans application supplémentaire ni mise à jour du serveur Windows CHK Universal.

## Fonctionnement

1. À l'ouverture, SkillMajAuto vérifie la dernière GitHub Release stable, avec protection contre les vérifications excessives.
2. Le JobScheduler Android contrôle la présence d'une nouvelle version environ toutes les 12 heures, au mieux selon le réseau et les contraintes de batterie.
3. Si une nouvelle Release officielle est disponible, l'application envoie une notification, si l'autorisation Android est accordée.
4. Depuis Réglages → **SkillMajAuto · Mises à jour**, le bouton « SkillMajAuto · Vérifier les mises à jour » permet de vérifier immédiatement.
5. Le téléchargement de l'APK s'effectue directement depuis la Release officielle `vX.Y.Z`, fichier nommé `LES-FAITS-SYNC30.apk`.
6. Le moteur contrôle le SHA-256 déclaré par GitHub, l'identifiant de package `fr.sync30.lesfaits`, une versionCode strictement supérieure, et une signature Android compatible.
7. Android demande l'accord de l'utilisateur avant la finalisation de l'installation. L'application n'efface pas les données lors d'une mise à jour correctement signée.

## Première installation après désinstallation d'une APK de test

La suppression d'une application Android efface les données SQLite locales et les photos internes. Avant de désinstaller, exporter les dossiers importants en PDF. La première APK stable doit être publiée avec une **signature permanente**. Une APK debug temporaire ne peut pas garantir les mises à jour futures.

## Publication des versions futures sans toucher au PC

Le workflow `.github/workflows/release.yml` reconnaît des **secrets GitHub Actions chiffrés** contenant un keystore PKCS12 de signature permanent :

- `LF_SIGNING_P12_BASE64` : base64 du fichier `.p12` privé.
- `LF_SIGNING_PASSWORD` : mot de passe du magasin.
- `LF_SIGNING_KEY_PASSWORD` : optionnel si identique au mot de passe du magasin.
- `LF_EXPECTED_CERT_SHA256` : recommandé, empreinte SHA-256 publique du certificat attendu.

L'APK sera publiée uniquement après une signature vérifiée. Ne jamais publier la clé, son base64 ou son mot de passe dans un dépôt public, un ticket, un chat ou des logs. Aucun circuit OIDC ou serveur externe : les secrets GitHub sont obligatoires.

## Limites techniques

- Sans Release stable signée, la détection SkillMajAuto fonctionne, mais n'a aucune nouvelle APK à proposer.
- Sans secret de signature permanent, la publication automatique est bloquée.
- Android impose une confirmation pour les APK installées hors Google Play : l'installation ne peut pas être silencieuse.
- Le rythme de vérification en arrière-plan n'est pas garanti à la minute par Android.

## Vérifications

Workflow `Build Android` sur GitHub Actions : compilation et tests. Workflow `Publier APK signé — LES FAITS` : signature PKCS12, `apksigner verify`, SHA-256, publication de l'APK dans GitHub Releases.
