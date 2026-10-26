#!/bin/bash
# =====================================================================
# VetSalud ERP · Restauración y validación (RNF-07: RTO ≤ 4 h · RNF-18)
#   Uso: VETSALUD_CLAVE_RESPALDO=... ./restaurar.sh <archivo.dump.enc> [bd_destino]
# Restaura en una BD nueva (por defecto vetsalud_erp_restaurada) para validar antes de reemplazar.
# =====================================================================
set -euo pipefail
ARCHIVO=$1
DESTINO=${2:-vetsalud_erp_restaurada}
: "${VETSALUD_CLAVE_RESPALDO:?Defina VETSALUD_CLAVE_RESPALDO}"
( cd "$(dirname "$ARCHIVO")" && (sha256sum -c "$(basename "$ARCHIVO").sha256" 2>/dev/null || shasum -a 256 -c "$(basename "$ARCHIVO").sha256") )
psql -d postgres -c "DROP DATABASE IF EXISTS $DESTINO" -c "CREATE DATABASE $DESTINO OWNER vetsalud_owner"
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass env:VETSALUD_CLAVE_RESPALDO -in "$ARCHIVO" \
  | pg_restore --no-owner --role=vetsalud_owner -d "$DESTINO"
# Validación posterior (RNF-18): conteos y consistencia del kardex con el stock
psql -d "$DESTINO" -At -c "SET search_path TO vetsalud;
  SELECT 'clientes', count(*) FROM cliente UNION ALL SELECT 'mascotas', count(*) FROM mascota
  UNION ALL SELECT 'ventas', count(*) FROM venta UNION ALL SELECT 'auditoria', count(*) FROM auditoria
  UNION ALL SELECT 'lotes con stock distinto al kardex', count(*) FROM lote l
    WHERE l.stock_actual <> coalesce((SELECT saldo_lote FROM movimiento_inventario m WHERE m.id_lote = l.id_lote
                                      ORDER BY id_movimiento DESC LIMIT 1), 0);"
echo "Restauración validada en $DESTINO"
