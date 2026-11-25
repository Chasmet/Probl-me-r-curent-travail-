# LES FAITS · Audit de la version 0.8.6 et changements en 0.9.0

| Parcours | Constat en 0.8.6 | Changement |
| --- | --- | --- |
| Reprendre un dossier | Seuls le statut et les observations étaient modifiables | Accueil → Reprendre un rapport → Modifier le rapport. La sauvegarde conserve l’identifiant, la date de création, les pièces et le suivi. |
| Rechercher | Chaque frappe relisait toutes les lignes. Les accents devaient correspondre | La liste principale est chargée une fois par ouverture de l’historique. La recherche des rapports accepte les mots sans accents et le statut. |
| Saisir sur téléphone | Rotation et retour pouvaient interrompre la saisie | Restauration du formulaire après recréation Android, confirmation de sortie, erreurs au niveau des champs. |
| Utiliser un clavier | Des commandes visuelles étaient difficiles à atteindre | Boutons et navigation accessibles au focus, réglages nommés, libellés reliés aux champs. |
| Importer une pièce | Les filtres du sélecteur, du partage Android et du stockage différaient | Tous les fichiers accessibles par le sélecteur peuvent être copiés dans la bibliothèque privée, jusqu’à 250 Mo par fichier. |
| Consulter le contenu | L'ouverture dépendait d'une application externe | Aperçu texte UTF-8, DOCX, XLSX, PPTX, ODT, ODS et ODP. Images compatibles avec le décodeur Android et PDF rendus en image. Texte PDF disponible sur Android 15 et ultérieur quand le PDF comporte une couche texte. |
| Utiliser le MCP | Création, consultation et liens vers les pièces | Correction d’un champ avec sa valeur précédente, lecture d’une pièce sur autorisation, capture du formulaire actif, sélection de champ et saisie. |
| Installer une version | Progression indéterminée et vérification peu visible dans les réglages | Pourcentage quand la taille est annoncée, version installée et dernière vérification. Publication via le certificat permanent. |

## Utiliser les nouvelles capacités MCP

Les anciennes associations annoncent les capacités présentes au moment de leur création. Dans Réglages, « Actualiser les outils du connecteur » enregistre le catalogue de la nouvelle version avec une invitation du serveur habituel.

| Action | Paramètres | Résultat |
| --- | --- | --- |
| lesfaits_modifier | id, champ, avant, texte | Corrige le titre, la description ou le lieu si la valeur précédente correspond. Une tâche rejouée conserve le même résultat. |
| lesfaits_lire_piece | fichier_id, page facultative à partir de 1 | Métadonnées et extrait, ou aperçu JPEG en base64. L’option de partage du contenu doit être active. |
| lesfaits_vision | Aucun | Vue du formulaire, session et noms des champs. L’assistance doit être cochée sur le formulaire affiché. |
| lesfaits_souris | session, champ | Place le focus sur titre, description ou lieu et fait défiler le formulaire. |
| lesfaits_clavier | session, champ, texte | Remplace le contenu du champ. La personne qui utilise le formulaire peut le relire et l’enregistrer. |

## Portée des vérifications

La base SQLite conserve son schéma v3. La correction d’un dossier et l’ajout éventuel de photos utilisent une transaction. Le test Android vérifie le statut, la date de création, les observations, les liens, une relecture de tâche et le refus d’une édition périmée. Le contrôle de publication compare le certificat et l’intégrité de l’APK. Un contrôle sur émulateur exerce l’installation de la version publiée par-dessus la précédente avec un dossier témoin.

L’aperçu local limite les extraits à 12 000 caractères et les parties XML à 2 Mio. Un PDF scanné est consultable page par page en image. Les anciens fichiers Office binaires, les médias audio et vidéo et les formats propriétaires peuvent être conservés et ouverts avec une application compatible. L’aperçu de tableur donne des valeurs, sans reproduire les calculs ou la mise en page. Les vignettes de la bibliothèque sont encore décodées sur le fil d’interface lors du filtrage. Les essais en émulateur ne mesurent pas la réactivité d’un téléphone contenant des milliers de photos.
