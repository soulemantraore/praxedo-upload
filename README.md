# praxedo-upload

Micro-service de gestion de fichiers sécurisés : aucun fichier n'est servi sans avoir été scanné par un antivirus.

Monorepo regroupant :
- `praxedo-upload-backend/` — API Java / Spring Boot.
- `praxedo-upload-scanner/` — service antivirus externe (FastAPI + ClamAV).
- `praxedo-upload-ui/` — portail React.

> Documentation détaillée en cours de rédaction (voir `docs/`).
