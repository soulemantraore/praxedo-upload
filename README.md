# praxedo-upload-backend

Micro-service de gestion de fichiers sécurisés : **upload → scan antivirus → téléchargement contrôlé**.
Un fichier n'est jamais servi sans avoir été validé (`CLEAN`) par l'antivirus.

## Stack
- Java 21 · Spring Boot 3.3 · Maven.
- Architecture **hexagonale** : domaine POJO + ports (interfaces) + adapters d'infrastructure.
- Cible de déploiement : **GCP** (Cloud Run, GCS, Pub/Sub, Cloud SQL).

## Conception
La conception détaillée (spec + plan d'implémentation) est maintenue dans l'espace de specs du projet
(`docs/superpowers/specs` et `docs/superpowers/plans`).

## Modèle de branches
- **`main`** : branche par défaut, baseline stable.
- **`develop`** : branche d'intégration. Chaque **tâche** est développée sur une **branche dédiée**
  puis fusionnée dans `develop` via une **Pull Request** (une PR par tâche, revue de code par le mainteneur).

## Statut
Implémentation en cours, tâche par tâche. **Jalon 1** : fondation testable en local (sans dépendance GCP).
