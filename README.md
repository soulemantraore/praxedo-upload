# praxedo-upload-ui

Frontend React « Fichiers sécurisés » du micro-service de gestion de fichiers sécurisés
(`praxedo-upload-backend`). Il permet de déposer des fichiers, de suivre en direct leur
statut d'analyse antivirus, et de télécharger uniquement les fichiers déclarés sains
(`CLEAN`).

## Fonctionnalités

- **Upload** de fichiers (un ou plusieurs à la fois) via une modale dédiée.
- **Liste en direct** des fichiers avec statistiques globales (total, en attente, en
  analyse, sains, infectés) et statuts individuels (`PENDING`, `SCANNING`, `CLEAN`,
  `INFECTED`), rafraîchis par polling tant qu'un fichier n'a pas de statut final.
- **Téléchargement** strictement conditionné au statut `CLEAN` : un fichier `INFECTED`
  ou encore en cours d'analyse n'est jamais téléchargeable.
- **Rapport antivirus** consultable dans une modale de détail (moteur, verdict, date
  d'analyse, empreinte SHA-256 si fournie par le backend).
- **Relance d'analyse** (« rescan ») pour un fichier donné.
- Recherche et pagination de la liste.

## Stack technique

- **React 18** + **Vite 5** + **TypeScript** (mode `strict`).
- **TanStack Query v5** pour la synchronisation avec l'API (statistiques, liste des
  fichiers avec polling conditionnel, upload, rescan) — pas de gestionnaire d'état
  global, la donnée serveur suffit.
- **Vitest** + **Testing Library** + **MSW** (Mock Service Worker) pour les tests
  unitaires et de composants, avec un faux backend en mémoire.
- Polices **IBM Plex Sans** / **IBM Plex Mono**.
- Authentification par en-tête `X-API-Key` (clé API par client, machine-to-machine).

## Design

L'interface reproduit fidèlement la maquette Cloud Design validée
`Fichiers securises.dc.html` (une copie se trouve dans `../docs/design/Fichiers-securises.dc.html`
du monorepo de travail) : palette bleu `#005EA8` / vert `#28A15E`, cartes de
statistiques, badges de statut, icônes par type de fichier, modale d'upload et modale
de détail/rapport antivirus.

## Architecture

L'application est découpée pour rester testable et facile à faire évoluer, dans le
même esprit que les adaptateurs du backend :

| Emplacement | Rôle |
|---|---|
| `src/lib/format.ts` | Logique de présentation pure (formatage de tailles, dates, libellés), testée sans React. |
| `src/api/client.ts` | `FileApi`, client HTTP injectable (`fetch` en paramètre). **Seul point à adapter** si le JSON réellement renvoyé par le backend diffère du contrat (spec section 4). |
| `src/api/hooks.ts` | Hooks TanStack Query : statistiques, liste des fichiers (avec polling conditionnel), upload, rescan. |
| `src/components/*` | Composants de présentation (tableau de fichiers, badges, modales, pagination, recherche...). |
| `test/mocks/*` | Faux backend MSW en mémoire (`store.ts`, `handlers.ts`) qui sert **à la fois** les tests **et** la démo hors ligne dans le navigateur — l'équivalent, côté frontend, des adaptateurs in-memory du backend. |

## Démarrage — démo hors ligne (par défaut, sans backend)

Par défaut, `VITE_USE_MOCK=true` : l'application démarre un faux backend dans le
navigateur (MSW) et fonctionne donc **sans aucun backend Java**.

```bash
npm install
cp .env.example .env      # VITE_USE_MOCK=true est déjà la valeur par défaut
npm run dev                # http://localhost:5173
```

Le worker MSW sert un jeu de fichiers de démonstration déjà présents. Un nouvel
upload passe par les statuts `PENDING` -> `SCANNING` -> `CLEAN` ; un nom de fichier
contenant `eicar` ou `virus` termine en `INFECTED` (non téléchargeable), ce qui permet
de démontrer les deux chemins sans backend réel.

## Démarrage — contre le vrai backend

```bash
# .env
VITE_API_BASE_URL=http://localhost:8080
VITE_API_KEY=<votre-cle-api>
VITE_USE_MOCK=false
```

Le backend `praxedo-upload-backend` doit tourner en local (profil `local`) :

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  mvn -f ../praxedo-upload-backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

## Scripts npm

| Commande | Effet |
|---|---|
| `npm run dev` | Démarre le serveur de développement Vite. |
| `npm run build` | Vérifie les types (`tsc`) puis build de production (`vite build`). |
| `npm run preview` | Sert localement le build de production. |
| `npm test` | Lance la suite de tests (Vitest, mode non interactif). |
| `npm run test:watch` | Lance Vitest en mode watch. |
| `npm run typecheck` | Vérifie les types sans émettre de fichiers. |

## Configuration (variables d'environnement `VITE_*`)

Toutes les variables sont documentées avec leur valeur par défaut dans `.env.example`.

| Variable | Rôle | Défaut |
|---|---|---|
| `VITE_API_BASE_URL` | URL de base de l'API backend (sans slash final). | `http://localhost:8080` |
| `VITE_API_KEY` | Clé API du client, envoyée dans l'en-tête `X-API-Key`. À injecter au build/déploiement en production, jamais commitée. | `dev-local-key` |
| `VITE_PORTAL_NAME` | Nom du portail affiché dans l'en-tête de l'application. | `Praxedo` |
| `VITE_USE_MOCK` | `true` : démarre le faux backend MSW dans le navigateur (démo hors ligne). `false` : appelle le vrai backend. | `true` |
| `VITE_POLL_MS` | Intervalle (ms) du polling de statut tant qu'un fichier n'a pas de statut final. | `2500` |

## Choix techniques et hypothèses

- **Contract-first** : le frontend cible le contrat d'API défini dans la spécification
  (section 4) ; MSW encode ce contrat pour développer et tester l'interface hors ligne
  pendant que le backend est construit en parallèle. Toute différence de nommage JSON
  entre le contrat et le backend réel se corrige à un seul endroit : `src/api/client.ts`.
- **Upload multi-fichiers** : l'interface boucle sur `POST /api/files` (un ticket
  d'upload puis un `PUT` par fichier) pour chaque fichier sélectionné. L'endpoint
  `/api/batches` reste réservé à l'intégration système-à-système, hors périmètre de
  cette interface humaine.
- **Téléchargement** : compromis de démo — le client récupère les octets via
  `/content` puis déclenche l'enregistrement navigateur. Évolution documentée : faire
  renvoyer par le backend l'URL signée (JSON) pour que le navigateur télécharge
  directement depuis GCS, déchargeant ainsi le backend (et cette UI) du relais des
  gros fichiers — cohérent avec le principe « l'application ne relaie jamais les gros
  fichiers ».
- **Authentification** : machine-à-machine par `X-API-Key` ; une authentification
  humaine OAuth2/JWT est une évolution documentée (spec sections 8 et 13).
- **Modale de détail** : affiche le SHA-256 quand le backend le fournit (le mock de
  démo en génère un) ; « Base de signatures » et « Durée d'analyse » affichent un tiret
  quand le backend ne les fournit pas. La barre de progression du scan est
  indéterminée, car le backend expose un statut, pas un pourcentage d'avancement.
- **`VITE_USE_MOCK` vaut `true` par défaut** : un build de production réalisé sans
  configuration explicite embarque donc la démo hors ligne. Positionner
  `VITE_USE_MOCK=false` est **requis** pour un déploiement réel.

## Pistes d'amélioration

- Progression d'upload par fichier (au lieu d'un état binaire en cours/terminé).
- Upload reprenable pour les fichiers très volumineux.
- Authentification utilisateur OAuth2/JWT en complément des clés API.
- Notification push de fin d'analyse, pour supprimer le besoin de polling.
- Extraction i18n des libellés (actuellement en français en dur).
