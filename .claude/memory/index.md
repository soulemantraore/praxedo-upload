# Index mémoire — praxedo-app

Une ligne par fichier sujet. Chargé au début de session, détail à la demande.

- [contexte-projet](contexte-projet.md) — Projet micro-service fichiers sécurisés : objet, stack imposée, contraintes, livrables.
- [decisions-archi](decisions-archi.md) — Journal des décisions d'architecture (ADR light). Inclut **D16** (bascule **monorepo** `praxedo-upload`, remplace D3) et **D17** (**Supabase Postgres** au lieu de Cloud SQL, 🔄 à implémenter).
- [bonnes-pratiques](bonnes-pratiques.md) — Bonnes pratiques transverses : injection de dépendances, testabilité, découplage du framework (ne pas être bloqué par Spring en test).
- [frontend-ui](frontend-ui.md) — Frontend `praxedo-upload-ui` (jalon 4) : stack Vite/React/TS/react-query/MSW, couches découplées, contract-first, fail-open, fidélité maquette + **alignement vérifié sur le contrat backend réel** (PR #18) + piège auto-trigger scan en local.
- [infra](infra.md) — Outillage déploiement GCP (non exécuté) : topologie Cloud Run **3 services** api + worker(léger) + **scanner** (Python+ClamAV, D15), Makefile gcloud, IAM moindre privilège, sidecar Cloud SQL Auth Proxy — **caduc avec D17 (Supabase Postgres remplace Cloud SQL)**.
