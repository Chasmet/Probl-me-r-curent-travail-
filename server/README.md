# Passerelle privée LES FAITS — SYNC 30

## Installation
Requiert Node.js 20+.

```bash
cd server
npm install
export SYNC30_API_TOKEN="$(openssl rand -hex 32)"
npm run api
```

Par défaut, l'API écoute sur **127.0.0.1:8787**, avec les routes `/health` et `/api/incidents`. Données stockées dans `server/data/incidents.json` (ignoré par Git, dossier privé). Un reverse proxy HTTPS sécurisé est nécessaire pour la synchronisation depuis Android : ne jamais ouvrir directement le port 8787 sur Internet.

Démarrer le serveur MCP séparément avec le **même** jeton et le même `SYNC30_DATA_DIR` :

```bash
npm run mcp
```

Le MCP tourne sur **127.0.0.1:8788/mcp**. Outils en lecture seule : `lister_les_faits`, `lire_un_fait`, `statistiques_les_faits`.

**Important** : cette version fournit les endpoints et les outils, pas une connexion ChatGPT opérationnelle. Pour connecter ChatGPT à distance, utiliser un reverse proxy HTTPS avec une authentification OAuth 2.1 compatible MCP et consentement utilisateur. Un jeton bearer partagé est réservé à des clients privés/administrateurs de confiance ; il ne remplace pas un mécanisme OAuth public. Ne jamais publier ce jeton. Un accès sans authentification est proscrit.

## Fonctionnement

- L'app Android utilise HTTPS POST `/api/incidents` et un jeton Bearer.
- Chaque synchronisation remplace le jeu de dossiers de **cet appareil uniquement** (pas les autres appareils).
- Photos non synchronisées : seul le texte, les dates, lieux et notes sont envoyés avec confirmation explicite.
- Le serveur MCP donne accès aux dossiers reçus via la synchronisation, pas aux dossiers exclusivement locaux.
- L'application ne pousse pas ses données en arrière-plan et ne les transmet jamais sans action volontaire.
- Sauvegarder le dossier `data/` sur un support protégé si les fichiers sont importants.

### Limites actuelles
Cette passerelle mono-propriétaire n'offre ni gestion multi-utilisateur, ni OAuth intégré, ni conflits bidirectionnels, ni sauvegardes gérées, ni chiffrement du fichier de données au repos. Prévoir ces composants avant une mise à disposition publique multi-utilisateurs.

Compatibilité Android v0.1.1 : l'API accepte aussi `/lesfaits/api/incidents`, utilisé par le serveur CHK Universal Control (fusionné avec tests au vert).
