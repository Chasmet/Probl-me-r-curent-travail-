# Migration de LES FAITS 0.7.1 vers la signature permanente

Ne pas désinstaller l'application actuelle tant que l'archive de sauvegarde n'est pas vérifiée et l'APK stable disponible.

## 1. Sur le PC du propriétaire
Prérequis : Python 3, JDK (keytool), GitHub CLI (gh), Android SDK Platform Tools (adb).
Authentifier GitHub CLI avec `gh auth login`. La commande suivante crée une clé permanente localement, la configure comme secrets GitHub Actions et déclenche la publication :
```
python tools/setup-ota-and-backup.py signature
```
La clé et son mot de passe ne sont jamais envoyés dans le dépôt ni dans ChatGPT. Conserver une copie privée chiffrée de ~/.lesfaits-signing. Ne pas supprimer ou régénérer la clé. La publication stable vérifie son empreinte et refuse toute rupture de signature. Aucune dépendance au serveur CHK Universal.

## 2. Sauvegarde de la version 0.7.1
Connecter le téléphone via USB, activer temporairement le débogage USB et autoriser ce PC. Puis :
```
python tools/setup-ota-and-backup.py sauvegarde LES-FAITS-sauvegarde.zip
```
La commande arrête temporairement l'application, lit la base SQLite et les photos via run-as (version debug uniquement), consolide le WAL, vérifie l'intégrité et conserve une copie sur le PC et dans Downloads sur Android. Elle ne supprime pas l'application. En cas d'erreur, ne pas désinstaller. Les jetons Android Keystore ne sont pas transférables : réassocier le MCP après migration. Les PDF déjà exportés sont conservés séparément. Désactiver le débogage USB après usage.

## 3. Migration unique si l'ancienne clé est réellement perdue
Seulement après vérification des deux copies et disponibilité de l'APK stable : désinstaller l'ancienne APK, installer la nouvelle stable, ouvrir Réglages > Restauration de l'ancienne version > Restaurer une sauvegarde ZIP. La restauration v1/v2 s'effectue dans une installation vide, conserve dossiers, observations et photos et ne remplace pas de données existantes. Comparer le nombre de dossiers et vérifier les photos. Conserver l'archive dans un espace privé. Les futures mises à jour signées avec la même clé passent directement par SkillMajAuto, avec confirmation Android si nécessaire.
