# Image ClamAV derivee : releve les limites de taille de clamd pour supporter des
# fichiers jusqu'a ~1 Go (defaut StreamMaxLength = 25M). Utilisee comme sidecar du
# scanner (voir ../Makefile, cible build-push-clamav). 1200M laisse une marge
# au-dessus du plafond applicatif de 1 Go.
FROM clamav/clamav-debian:latest

# clamd refuse les directives dupliquees : on remplace la ligne existante (commentee
# ou non) si presente, sinon on l'ajoute. Echoue explicitement si la conf de base est
# absente (plutot que de produire une conf tronquee qui casserait clamd).
RUN CONF=/etc/clamav/clamd.conf; \
    test -f "$CONF" || { echo "ERREUR: $CONF absent de l'image de base"; exit 1; }; \
    for kv in "StreamMaxLength 1200M" "MaxFileSize 1200M" "MaxScanSize 1200M"; do \
      key="${kv%% *}"; \
      if grep -qiE "^[#[:space:]]*${key}[[:space:]]" "$CONF"; then \
        sed -i -E "s|^[#[:space:]]*${key}[[:space:]].*|${kv}|I" "$CONF"; \
      else \
        printf '%s\n' "$kv" >> "$CONF"; \
      fi; \
    done
