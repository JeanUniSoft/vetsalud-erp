#!/bin/bash
# =====================================================================
# VetSalud ERP · Respaldo diario cifrado (RNF-07: RPO ≤ 24 h)
#   Programar en cron:  0 23 * * *  /opt/vetsalud/db/respaldo.sh
#   Requiere la variable VETSALUD_CLAVE_RESPALDO (nunca se guarda en el repositorio).
# =====================================================================
set -euo pipefail
DESTINO=${VETSALUD_RESPALDOS:-$HOME/vetsalud-respaldos}
BD=${VETSALUD_BD:-vetsalud_erp}
: "${VETSALUD_CLAVE_RESPALDO:?Defina VETSALUD_CLAVE_RESPALDO con la frase de cifrado}"
mkdir -p "$DESTINO"
ARCHIVO="$DESTINO/vetsalud_$(date +%Y%m%d_%H%M).dump.enc"
# Formato custom de pg_dump (comprimido) cifrado con AES-256 y derivación PBKDF2
pg_dump -Fc -d "$BD" | openssl enc -aes-256-cbc -salt -pbkdf2 -iter 200000 \
  -pass env:VETSALUD_CLAVE_RESPALDO -out "$ARCHIVO"
sha256sum "$ARCHIVO" 2>/dev/null > "$ARCHIVO.sha256" || shasum -a 256 "$ARCHIVO" > "$ARCHIVO.sha256"
# Retención: 30 días
find "$DESTINO" -name 'vetsalud_*.dump.enc*' -mtime +30 -delete
echo "Respaldo cifrado generado: $ARCHIVO"
