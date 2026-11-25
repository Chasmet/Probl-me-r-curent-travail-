# Signature automatique — méthode CHK Agent Browser

GitHub Actions obtient une identité OIDC de courte durée et contacte la route isolée
https://modeliseur-trellis-mcp.onrender.com/lesfaits/ci/signing.
Cette route est réservée au workflow release.yml sur main du dépôt LES FAITS.
Elle lit uniquement la variable privée LESFAITS_ANDROID_SIGNING_JSON du service existant.
Le certificat doit correspondre à celui de v0.10.0 : 0b9374e1b4cc30e2b6a9d177817038d806c65637a15f47936fb5ed514ede6cd8.
Aucun secret GitHub à saisir. Aucun changement de signature autorisé.
La clé d'origine doit être présente sur le serveur. Si elle manque, la publication s'arrête.
SkillMajAuto continue de lire les Releases stables. Ne jamais désinstaller pour résoudre une panne de publication.
