# Session 2026-07-10 — Brainstorming architecture

## Objet
Démarrage du projet (micro-service de fichiers sécurisés). Phase de brainstorming (skill superpowers) pour poser l'architecture avant de coder.

## Ce qui s'est passé
- Mise en place de la mémoire projet : `CLAUDE.md` nettoyé, `.claude/memory/` (index, contexte-projet, decisions-archi, bonnes-pratiques), `.claude/sessions/`.
- Companion visuel de brainstorming utilisé (schémas : flux de scan, machine à états, topologie GCP, gros fichiers, frontend, récap).
- **13 décisions actées** (voir `decisions-archi.md`) : ambition GCP end-to-end, antivirus (port + ClamAV sidecar), polyrepo (`praxedo-upload-backend` / `praxedo-upload-ui`), flux async, machine à états, topologie Pub/Sub, abstractions FileStorage/FileMetadataRepository, URLs signées direct-to-GCS, clés API par-client + propriété, frontend suit la maquette Cloud Design (palette Praxedo), IaC gcloud+Makefile (pas de Terraform), testabilité/DI, batch pour systèmes tiers.
- Nettoyage : retrait de toute framing « entretien / senior » à la demande de l'utilisateur.
- **Spec écrite** : `docs/superpowers/specs/2026-07-10-micro-service-fichiers-securises-design.md` (validée par l'utilisateur).

## Plan d'implémentation
- **Jalon 1 backend écrit** : `docs/superpowers/plans/2026-07-10-backend-foundation.md` (16 tâches TDD, Maven + Java 21 + Spring Boot 3.3, adapters in-memory/local/fake, testable sans GCP).
- Choix build : **Maven + Java 21**. Plans **séparés par sous-système**, backend d'abord (jalons : fondation → ClamAV/async → GCP), puis frontend, puis infra.

## Prochaines étapes
- Exécuter le plan jalon 1 (subagent-driven ou inline) — en attente du choix utilisateur.
- Puis plans jalon 2 (ClamAV/Pub/Sub), jalon 3 (GCS/Cloud SQL), frontend, infra.
- Initialiser les 2 dépôts Git puis committer spec + mémoire (pas encore fait — attendre feu vert / création des repos).

## À retenir pour la prochaine session
- Sauvegarder les décisions au fil de l'eau dans `.claude/memory/decisions-archi.md`.
- Bonnes pratiques testabilité/DI à appliquer systématiquement (`.claude/memory/bonnes-pratiques.md`).
- Mémoire versionnée via Git (dépôts à initialiser).
