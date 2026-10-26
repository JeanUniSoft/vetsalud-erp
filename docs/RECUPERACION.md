# Plan de respaldo y recuperación · VetSalud ERP (RNF-07, RNF-18)

| Elemento | Definición |
|---|---|
| RPO (pérdida máxima de datos) | 24 horas: respaldo diario cifrado a las 23:00 (`db/respaldo.sh`) |
| RTO (tiempo máximo de recuperación) | 4 horas |
| Cifrado | `pg_dump -Fc` + AES-256-CBC con derivación PBKDF2 (frase en variable de entorno, no en el repositorio) |
| Integridad | Suma SHA-256 por archivo; retención de 30 días |
| Responsables | Administrador del sistema (ejecuta y verifica); Gerencia (autoriza la restauración en producción) |

## Secuencia de recuperación ante una interrupción

1. **Detectar y comunicar** (administrador): registrar la hora de la caída y avisar a gerencia y a las áreas.
2. **Contingencia operativa**: recepción y caja registran en formatos impresos numerados; farmacia no dispensa medicamentos con receta sin validar la historia.
3. **Restaurar en un entorno de validación**: `VETSALUD_CLAVE_RESPALDO=... db/restaurar.sh <último respaldo>`.
4. **Validar** (salida del script): conteos de clientes, mascotas, ventas y auditoría; cero lotes con stock distinto al kardex.
5. **Reemplazar la base de producción** con la restaurada (autoriza gerencia) y reiniciar Tomcat.
6. **Regularizar**: digitar las operaciones registradas en papel durante la contingencia y conciliar caja.
7. **Cerrar el incidente**: documentar causa, tiempo real de recuperación y acciones de mejora.
