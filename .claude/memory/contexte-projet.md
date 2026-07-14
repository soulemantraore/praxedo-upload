# Contexte projet

## Objet
Projet confié : concevoir et développer un **micro-service de gestion de fichiers sécurisés** capable de :
1. Recevoir et conserver des fichiers transmis par des utilisateurs ou des systèmes tiers.
2. Garantir qu'**aucun fichier n'est servi sans avoir été préalablement scanné par un antivirus**.
3. Permettre le téléchargement des fichiers validés via une interface programmatique (API).

## Contraintes
- Nombreux utilisateurs simultanés ; fichiers de **tailles très variables**.
- Stack **imposée** : Java / Spring Boot / React.
- Déploiement sur **GCP**.
- Respecter **SOLID**, **sans sur-ingénierie**.
- L'analyse antivirus est **déléguée à un antivirus disponible via une API**.

## Livrables attendus
- Code source sur un **dépôt Git public** (application fonctionnelle : recevoir + servir des fichiers, déléguer le scan).
- **README** décrivant : choix techniques et architecturaux, hypothèses formulées, pistes d'amélioration envisagées.
