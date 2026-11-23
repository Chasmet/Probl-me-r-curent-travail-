# LES FAITS — SYNC 30

Application Android gratuite de suivi des faits, incidents et problèmes récurrents, pour la vie personnelle ou le terrain professionnel.

## Fonctions (v0.1)
- Deux espaces : **TRACE · Personnel / Terrain** et **RAPPORT · Affaires personnelles**.
- Signalement avec date/heure, catégorie, lieu, description, urgence et caractère récurrent.
- Photos depuis la galerie et prise de photo via l'application caméra, dictée vocale si disponible.
- Historique local hors connexion, recherche, état d'avancement, commentaires.
- Export PDF individuel ou de tous les signalements.
- Synchronisation **manuelle et volontaire** des métadonnées (pas de photos) vers un serveur HTTPS personnel.
- Interface entièrement en français, sans publicité ni abonnement.

## Construire l'APK
Dans GitHub, ouvrir **Actions → Build Android** : après un push réussi, récupérer `les-faits-debug-apk`. Pour Google Play, une signature officielle sera nécessaire. Version test, pas encore publiée.

## Raccordement au serveur CHK Universel
Dans l'application **Réglages**, saisir l'URL HTTPS de l'API et son jeton. Un serveur de démonstration autonome est fourni dans `server/`, à placer derrière HTTPS. Par défaut aucune donnée n'est transférée.

L'application Android utilise dorénavant la route isolée `/lesfaits/api/incidents` de CHK Universal Control. La mise à jour du dépôt serveur n'active pas le service en cours d'exécution : configurer LESFAITS_TOKEN puis redémarrer le serveur sur ton PC. En dehors du serveur CHK Universal, l'API d'exemple indépendante accepte la même route.

Le serveur MCP est **distinct** du client Android. Il est fourni en lecture seule dans `server/mcp-server.mjs`. **L'accès ChatGPT n'est pas automatique** : configurer une connexion MCP prise en charge, une authentification OAuth adaptée et un tunnel sécurisé le cas échéant. Ne pas exposer de jeton partagé ou de photos personnelles à une instance publique. Le serveur d'exemple exige un jeton API et reste lié à localhost par défaut.

Les rapports générés sont des constats personnels et ne constituent pas une notification officielle à l'employeur ni un registre réglementaire.

## Confidentialité
Données enregistrées dans le stockage privé de l'application, sauvegarde Android désactivée. Le jeton de synchronisation est chiffré via Android Keystore. Les permissions photo globales et localisation ne sont pas demandées. Une désinstallation supprime les données locales : **exporter les PDF auparavant**. 

Voir `server/README.md` pour démarrer la passerelle.

## v0.2.0 — connexion MCP sans jeton manuel

Le serveur MCP est préconfiguré : `https://chk-universal-1.taild9e4de.ts.net/mcp`.
Dans **Réglages**, utiliser **Se connecter à mon MCP**. Le serveur ouvre l'autorisation OAuth PKCE dans le navigateur ; valider l'accès avec le compte propriétaire. Les jetons d'accès sont ensuite protégés dans Android Keystore et renouvelés automatiquement (l'utilisateur n'a rien à copier).

Pour synchroniser, l'app utilise le même hôte via `/lesfaits/api/incidents`, sans modifier le lien MCP de ChatGPT. L'envoi reste volontaire. Le serveur doit être actualisé avec la prise en charge du retour OAuth mobile ; le simple téléchargement de l'APK ne met pas à jour le service PC.

## v0.3.0 — système de mise à jour intégré

- **Réglages → Mises à jour automatiques** : vérification automatique sur GitHub Releases (une fois toutes les 24 heures lors de l'ouverture) ou manuelle.
- Les mises à jour de la distribution APK sont téléchargées depuis `https://github.com/Chasmet/Probl-me-r-curent-travail-/releases`, puis contrôlées par SHA-256, identifiant du paquet, numéro de version et **signature identique à l'application installée**.
- L'installation utilise PackageInstaller et exige l'accord explicite de l'utilisateur sur Android. Aucune installation silencieuse n'est possible pour une application classique.
- La mise à jour préserve la base SQLite et les photos ; **aucune désinstallation automatique**.
- Le canal Google Play devra utiliser les mises à jour de Play (sans autorisation REQUEST_INSTALL_PACKAGES dans le paquet Play). La distribution directe actuelle est dédiée à l'installation APK.

### Distribution signée et GitHub Releases

Le workflow `.github/workflows/release.yml` prépare un APK officiel signable **avec la même clé à chaque version**, puis crée une GitHub Release comprenant `LES-FAITS-SYNC30.apk`. La signature est récupérée sur CHK Universal Control via OIDC GitHub Actions lorsque la version serveur avec le signer est déployée sur le PC. Si ce service n'est pas disponible, le workflow refuse de publier une APK non signée ou signée avec une clé aléatoire.

**Attention aux anciennes APK de test :** elles ont été signées par les clés temporaires de GitHub Actions. Si tu les as installées, une nouvelle signature officielle ne pourra pas les remplacer directement. Exporte impérativement tes dossiers en PDF avant toute désinstallation. Les versions futures signées avec la clé officielle s'installeront ensuite les unes sur les autres sans effacer les données.

## v0.4.0 — connexion MCP CHK sans erreur OAuth

LES FAITS reprend le protocole **agent Android** existant de CHK Universal Control : `/api/onboard`, `/api/agent/<id>/poll`, `/api/agent/<id>/result`, disponible sur le serveur en version 1.0.4. Ce mode ne dépend pas de `/oauth/register` et évite l'erreur `HTTP 400 invalid_client_metadata`.

### Association (une seule fois)

1. Depuis le connecteur ChatGPT existant CHK Browser Local 3 ou CHK Pirate Serveur 3, générer une invitation avec `app_invite`. Elle expire au bout de 15 minutes et ne sert qu'une fois.
2. Ouvrir sur le téléphone Android `fr.sync30.lesfaits://pair?invite=<invitation>` (ou coller la valeur de l'invitation dans Réglages → Mon serveur MCP).
3. Confirmer le partage en lecture seule dans LES FAITS. Le serveur fournit alors un identifiant d'appareil et un secret d'agent conservé par Android Keystore, **sans saisie d'un jeton permanent**.
4. LES FAITS apparaît dans `apps_list`. Depuis ChatGPT, utiliser `task_submit` avec `app_id` et `lesfaits_list`, `lesfaits_detail` ou `lesfaits_stats`. L'application répond lorsqu'elle reçoit les demandes (immédiatement si elle est ouverte, puis par JobScheduler au mieux toutes les 15 minutes selon Android). Consulter les résultats avec `task_get`.

Les photos et les PDF ne sont pas envoyés, les données restent locales. L'application demande l'accord avant association et peut arrêter le partage dans Réglages. Il n'y a **aucun nouveau serveur MCP**, ni changement d'adresse.

**Attention** : ce protocole répond à des requêtes de consultation ; il ne remplace pas une sauvegarde ou une synchronisation permanente vers la base du serveur. La synchronisation via `/lesfaits/api` demandera toujours le déploiement de cette route si elle est souhaitée ultérieurement. Des limites Android (mode veille, économie de batterie) peuvent ralentir le traitement en arrière-plan.

## v0.5.0 — notification de mises à jour automatiques

La vérification quotidienne fonctionne à l'ouverture et en arrière-plan grâce à Android JobScheduler (selon le mode veille et le réseau). Si une nouvelle version officielle signée apparaît sur GitHub Releases, LES FAITS avertit l'utilisateur par notification, sans doublon pour une même version. Sur Android 13+, activer les notifications dans les réglages LES FAITS. Cliquer la notification ouvre la vérification de version puis le téléchargement volontaire. L'installation reste soumise à l'accord d'Android et au contrôle SHA-256 / certificat / package / version, sans suppression de données.

**Prérequis non levé au 23/11/2026 :** le serveur Windows CHK Universal en service utilise encore le code 1.0.4, sans endpoint `/lesfaits/api/ci/signing`. Le workflow de signature renvoie HTTP 404. Tant qu'aucune GitHub Release signée avec une clé privée stable n'a été publiée, l'application ne peut pas offrir de véritable mise à jour installable. La mise à niveau du logiciel Windows est distincte de la mise à jour du dépôt GitHub.


## v0.6.0 — Enregistrer les faits depuis ChatGPT

L'action `lesfaits_create` est déclarée avec `readOnly: false` dans le connecteur Android. Elle accepte `titre`, `description`, `categorie`, `espace` (`TERRAIN`/`PERSONNEL`), `lieu`, `date` (millisecondes UTC), `importance` et `recurrent`. Une requête crée un dossier dans la base SQLite existante et renvoie son ID. Une table dédiée évite les doublons en cas de retraitement de la même tâche MCP ; la migration SQLite de version 1 vers 2 conserve les dossiers, notes et photos.

**Important pour les appareils déjà associés :** le serveur CHK Universal mémorise les capacités lors de l'association initiale. Après installation de la nouvelle APK, ouvrir Réglages, générer une nouvelle invitation MCP et utiliser « Activer la création de signalements » pour réassocier l'application et enregistrer la nouvelle capacité. Les écritures restent soumises aux permissions et approbations du serveur ; la consultation demeure possible sur les anciens appareils. Aucun dossier local n'est supprimé.

**Mise à jour automatique :** le mécanisme de vérification et d'installation intégré est conservé. La version 0.6.0 doit être publiée en APK officiel signé avec la même clé de signature pour être installée sans perte de données. Tant que l'endpoint de signature CHK Universal `/lesfaits/api/ci/signing` n'est pas opérationnel, le workflow GitHub refusera la publication ; ne pas désinstaller l'application existante.


## Signature des APK sans changer le serveur PC

Le workflow de publication peut maintenant signer l'APK indépendamment de CHK Universal, avec un keystore PKCS12 **existant** conservé comme secret GitHub Actions. Dans les Secrets du dépôt, enregistrer `LF_SIGNING_P12_BASE64` (fichier PKCS12 existant encodé en base64), `LF_SIGNING_PASSWORD`, éventuellement `LF_SIGNING_KEY_PASSWORD`, et idéalement `LF_EXPECTED_CERT_SHA256` (empreinte SHA-256 du certificat attendu). Le workflow vérifie l'alias et l'empreinte si elle est fournie, puis signe et publie. Si aucun secret de signature n'est configuré, l'ancien signataire OIDC CHK Universal est utilisé ; il échouera si le serveur Windows ne fournit pas sa route dédiée. **La présence des secrets n'est pas connue par l'assistant et leur absence empêche la publication sans serveur compatible.**

**Protection des données** : pour mettre à jour une APK déjà installée sans désinstallation, la clé privée doit correspondre au certificat de signature de cette APK. Une clé nouvelle ou différente ne remplace pas l'ancienne application, même si GitHub publie le fichier : Android refuse la mise à jour. Ne transmettre aucun keystore ni mot de passe dans une conversation, un dépôt ou un ticket public.
