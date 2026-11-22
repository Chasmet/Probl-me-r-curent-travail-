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
