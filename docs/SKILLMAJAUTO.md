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

Le workflow `.github/workflows/release.yml` reconnaît le secret unique `LF_SIGNING_BUNDLE` (JSON privé PKCS12), ou les secrets séparés ci-dessous, mais jamais les deux :

- `LF_SIGNING_P12_BASE64` : base64 du fichier `.p12` privé.
- `LF_SIGNING_PASSWORD` : mot de passe du magasin.
- `LF_SIGNING_KEY_PASSWORD` : optionnel si identique au mot de passe du magasin.
- `LF_EXPECTED_CERT_SHA256` : recommandé, empreinte SHA-256 publique du certificat attendu.

L'APK sera publiée uniquement après une signature vérifiée. Ne jamais publier la clé, son base64 ou son mot de passe dans un dépôt public, un ticket, un chat ou des logs. Aucun circuit OIDC ou serveur externe. La publication automatique nécessite le secret GitHub existant. L’alternative hors ligne utilise le build exact et un manifeste public de signature produit avec la même clé privée ; voir .github/workflows/offline-release.yml.

## Limites techniques

- Sans Release stable signée, la détection SkillMajAuto fonctionne, mais n'a aucune nouvelle APK à proposer.
- Sans secret de signature permanent, la publication automatique est bloquée.
- Android impose une confirmation pour les APK installées hors Google Play : l'installation ne peut pas être silencieuse.
- Le rythme de vérification en arrière-plan n'est pas garanti à la minute par Android.

## Vérifications

Workflow `Build Android` sur GitHub Actions : compilation et tests. Workflow `Publier APK signé — LES FAITS` : signature PKCS12, `apksigner verify`, SHA-256, publication de l'APK dans GitHub Releases.

## Continuité v0.9.0 → v0.10.0

Le certificat historique est verrouillé : SHA-256 44d5eaf22860c8b45ec947940b2b2cc12a8a68f83a45089f1e6adf59422ddf04. Sans secret utilisable, le workflow de signature automatique échoue explicitement (il ne simule plus une publication réussie). Le workflow Build Android continue à produire un artifact non signé, qui n’est jamais une mise à jour officielle. Le circuit hors ligne doit recevoir un nouveau manifeste signé correspondant exactement au build de la version à publier. Ne jamais réutiliser le bloc de signature d’une ancienne APK.

## Migration unique approuvée vers une nouvelle clé (0.10.0)
La signature historique 0.9.0 est conservée dans l’historique. La version 0.10.0 utilise le nouveau certificat permanent SHA-256 0b9374e1b4cc30e2b6a9d177817038d806c65637a15f47936fb5ed514ede6cd8. Elle nécessite une installation propre après sauvegarde des données. Le contrôle de continuité reprend avec ce nouveau certificat pour toute version ultérieure. La clé privée n’est jamais déposée dans GitHub. Pour automatiser les publications, configurer une seule fois le nouveau bundle privé dans le secret Actions LF_SIGNING_BUNDLE, puis conserver une sauvegarde privée. Aucun renouvellement automatique de clé n'est autorisé. La release 0.10.0 est publiée depuis le build exact validé par le workflow hors ligne.
