# VetSalud ERP

Sistema integral de ventas y gestión para la **Clínica Veterinaria VetSalud Integral S.A.C.**
Proyecto del curso *Curso Integrador I: Sistemas – Software* · Universidad Tecnológica del Perú · sección 20055 · 2026-2.

| | |
|---|---|
| Docente | Ing. Irani Guillén Gómez |
| Integrantes | Arango Lecca, Luis Jeanpier · Mamani Calizaya, Luis Fernando · Sanchez Cabrera, Javier Jean Piere · Tuñoque Alvarado, Easy Javier |
| Versión actual | **0.4.0** (Avance de Proyecto Final 3: construcción de la solución) |
| Tecnologías | Java 25 · Servlets/JSP en Apache Tomcat 9 (WAR) · PostgreSQL 18 · Maven · Google Guava · Apache Commons · Apache POI · SLF4J + Logback · JUnit 5 |

## Funcionalidades (alcance comprometido en el APF2)

| Pantalla | Rol | Requisitos |
|---|---|---|
| P01 Acceso y recuperación | Todos | RF-01 a RF-04 |
| P02 Panel principal | Personal | RF-32, RF-38 |
| P03 Clientes y mascotas | Recepción | RF-05 a RF-08 |
| P04 Citas y llegada | Recepción | RF-09 a RF-12 |
| P05 Historia clínica | Veterinario | RF-13 a RF-16, RF-19 |
| P06 Procedimientos y receta | Veterinario | RF-16 a RF-19 |
| P07 Inventario por lotes | Farmacia | RF-20 a RF-25 |
| P08 Venta y pago | Caja | RF-26 a RF-29, RF-31 |
| P09 Cierre de caja | Caja | RF-30 |
| P10 Reportes | Administrador | RF-32 a RF-36 |
| P11 Portal del cliente | Cliente | RF-09, RF-19, RF-37 |
| P12 Usuarios y auditoría | Administrador | RF-01 a RF-04, RF-24, RF-38 |

## Arquitectura

Aplicación web en capas con **MVC** (servlets controladores, servicios y records como modelo, JSP como vista) y
**DAO** (interfaces en `dao`, implementaciones JDBC en `dao.jdbc`). Los servicios dependen de interfaces
(**SOLID**, inversión de dependencias) y se prueban con dobles en memoria (**TDD**). Ver
[`docs/CONVENCIONES_DESARROLLO.md`](docs/CONVENCIONES_DESARROLLO.md) y los diagramas de [`docs/diagramas`](docs/diagramas).

```
src/main/java/pe/vetsalud/erp/
├── web/           controladores (servlets) y funciones de vista
├── servicio/      reglas de negocio por caso de uso
├── dao/           interfaces de acceso a datos
├── dao/jdbc/      implementaciones JDBC (PreparedStatement)
├── modelo/        records del dominio
├── persistencia/  conexión, transacciones con usuario auditado, errores de BD
├── seguridad/     filtro de seguridad, matriz de acceso por rol, hash de claves
├── log/           auditoría y enmascarado de datos en Logback
└── reporte/       reportes Excel con Apache POI
```

## Seguridad

PBKDF2-SHA256 con sal · bloqueo por intentos · sesión de 15 minutos · nuevo id de sesión al ingresar ·
cookie HttpOnly/SameSite · matriz de acceso por rol y permisos de acción · token CSRF en todo POST ·
PreparedStatement (inyección SQL) · `c:out` y Content-Security-Policy (XSS) · rol de BD con privilegio mínimo ·
auditoría inmutable · logs con datos sensibles enmascarados · respaldo diario cifrado ([`docs/RECUPERACION.md`](docs/RECUPERACION.md)).

## Instalación y ejecución

Requisitos: JDK 25, Maven 3.9 y PostgreSQL 18.

```bash
# 1. Base de datos (crea vetsalud_erp desde cero, ejecuta las pruebas de reglas y carga datos de demostración)
cd db && psql -d postgres -f instalar.sql && cd ..

# 2. Pruebas unitarias y cobertura (target/site/jacoco/index.html)
mvn test

# 3. Levantar el sistema en desarrollo (Tomcat 9 embebido) → http://localhost:8080
mvn compile exec:java

# 4. Generar el WAR para un Tomcat 9 instalado → target/vetsalud-erp.war
mvn package
```

La clave de la base de datos se toma de la variable `VETSALUD_DB_CLAVE` (en desarrollo se usa la de
`vetsalud.properties`, solo para laboratorio).

## Usuarios de demostración

Clave de laboratorio para todos: `VetSalud2026!` (cargada por `db/09_datos_demo.sql`; reemplazar en producción).

| Usuario | Rol |
|---|---|
| rparedes | Administrador |
| ltorres | Recepción |
| cmendoza, asalazar | Veterinario |
| jhuaman | Técnico |
| mccori | Farmacia |
| dramos | Caja |
| mgonzales | Cliente (portal) |

## Pruebas y evidencias

| Verificación | Resultado | Evidencia |
|---|---|---|
| Pruebas unitarias JUnit 5 (`mvn test`) | 237 pruebas, 0 fallas | [`docs/evidencias/pruebas_unitarias_mvn_test.txt`](docs/evidencias/pruebas_unitarias_mvn_test.txt) |
| Cobertura JaCoCo (líneas) | 81,8 % en `servicio` (RNF-12 pide 70 %) · 45,1 % total | `target/site/jacoco/index.html` tras `mvn test` |
| Reglas de negocio en PostgreSQL (`db/06_pruebas.sql`) | 15 comprobaciones OK: 14 violaciones bloqueadas y la venta FEFO con anulación | [`docs/evidencias/pruebas_reglas_bd.txt`](docs/evidencias/pruebas_reglas_bd.txt) |
| Pruebas funcionales por pantalla | 32 casos aprobados | [`docs/evidencias/pruebas_funcionales.md`](docs/evidencias/pruebas_funcionales.md) |
| Recorrido de las 12 pantallas | 48 capturas | [`docs/capturas`](docs/capturas) · [`recorrido_capturas.py`](docs/evidencias/recorrido_capturas.py) |
| TDD (rojo → verde) | Autenticación | [`docs/evidencias/tdd_01_rojo_autenticacion.txt`](docs/evidencias/tdd_01_rojo_autenticacion.txt) |
| Respaldo y restauración cifrados | Restauración validada | [`docs/evidencias/respaldo_restauracion.txt`](docs/evidencias/respaldo_restauracion.txt) |
| Tiempos de respuesta locales | Mediana ≤ 23 ms en 17 pantallas | [`docs/evidencias/tiempos_respuesta_local.txt`](docs/evidencias/tiempos_respuesta_local.txt) |

## Flujo de trabajo con Git

- `main`: versiones entregadas (etiquetas `v0.x.0`); `develop`: integración; `feature/<módulo>`: una rama por funcionalidad, integrada con `merge --no-ff`.
- Commits: `tipo: verbo en infinitivo + descripción (RF-xx)` con tipos `feat`, `fix`, `test`, `docs`, `style`, `refactor`, `build`, `chore`.

## Entregables del proyecto

| Avance | Contenido | Ubicación |
|---|---|---|
| APF1 (S04) | Análisis de la empresa, problema, Lean Canvas y alternativas | `docs/entregables/APF1` |
| S06 | Diseño e implementación de la base de datos | `db/01` a `db/06` |
| APF2 (S08) | Diseño: SRS, casos de uso, arquitectura, modelo de datos y prototipo de 12 pantallas | `docs/entregables/APF2` |
| S10 | Recursos Java: reportes Excel con POI y logs con Logback | `src/main/java/.../reporte`, `log` |
| APF3 (S12) | Construcción de la solución (v0.4.0), informe y presentación | `docs/entregables/APF3` |
