# Publication APK avec clé permanente hors ligne

La clé privée n'est jamais déposée dans le dépôt. La CI Android produit un APK release non signé. Une signature APK v2 est calculée hors ligne avec la clé permanente existante ; seuls le bloc public de signature, les SHA-256 et l'identifiant du build sont transmis dans .github/ota/signed.json. Le workflow reconstruit l'APK exact, vérifie le certificat attendu, le package, la version, la continuité et publie une release stable compatible SkillMajAuto. La méthode GitHub Secrets reste disponible pour l'automatisation complète ; la signature hors ligne nécessite une opération de signature par version. Android garde sa confirmation d'installation.


Migration unique 0.10.0 : certificat historique remplacé avec accord de l’utilisateur, nouveau certificat permanent 0b9374e1b4cc30e2b6a9d177817038d806c65637a15f47936fb5ed514ede6cd8. Les futures versions doivent utiliser exclusivement cette clé ; aucune dérogation de signature après la migration. L'ancien APK 0.9.0 ne peut pas être mis à jour en place vers 0.10.0. Sauvegarde préalable obligatoire avant une installation propre.
