# Convenciones de desarrollo · VetSalud ERP

Guía para que todo el equipo escriba el código de la misma forma. El módulo de **clientes**
(`ClientesServlet`, `ClienteService`, `ClienteDao`, `ClienteDaoJdbc`, `clientes/*.jsp`) es la referencia.

## 1. Arquitectura en capas (MVC + DAO)

| Capa | Paquete | Responsabilidad | No debe |
|---|---|---|---|
| Vista | `src/main/webapp/WEB-INF/jsp/<modulo>/` | Mostrar datos con JSTL | Tener lógica ni SQL |
| Controlador | `pe.vetsalud.erp.web` | Leer la solicitud, llamar al servicio, elegir la vista | Tener reglas de negocio ni SQL |
| Servicio (modelo de negocio) | `pe.vetsalud.erp.servicio` | Reglas de negocio, validaciones, auditoría | Conocer HTTP ni JDBC |
| DAO (interfaz) | `pe.vetsalud.erp.dao` | Contrato de acceso a datos | — |
| DAO JDBC | `pe.vetsalud.erp.dao.jdbc` | SQL con `PreparedStatement` | Tener reglas de negocio |
| Modelo | `pe.vetsalud.erp.modelo` | `record` inmutables | — |
| Seguridad | `pe.vetsalud.erp.seguridad` | Filtro, matriz de acceso, hash de claves | — |
| Persistencia | `pe.vetsalud.erp.persistencia` | `Db` (conexión/transacción), `Jdbc` (utilidades) | — |

**SOLID aplicado**
- *S*: una clase = una responsabilidad (servicio por caso de uso, DAO por agregado).
- *O*: nuevos formatos de exportación o notificadores se agregan implementando una interfaz, sin tocar el servicio.
- *L/I*: interfaces pequeñas por agregado (`ClienteDao`, `AlertaDao`...).
- *D*: los servicios reciben interfaces DAO por constructor; las pruebas usan dobles en memoria.

## 2. Plantilla de cada capa

**Modelo**: `public record Xxx(...) {}` (las vistas leen `${x.campo}` gracias a `RecordELResolver`).

**DAO JDBC**
```java
public class XxxDaoJdbc implements XxxDao {
    private final Db db;
    public XxxDaoJdbc(Db db) { this.db = db; }
    // lectura
    return db.consultar(cn -> Jdbc.lista(cn, "SELECT ... WHERE a = ?", XxxDaoJdbc::mapear, a));
    // escritura: SIEMPRE en transacción con el id del usuario (lo usan los triggers de auditoría)
    return db.enTransaccion(idUsuario, cn -> Jdbc.insertar(cn, "INSERT ... RETURNING id_x", ...));
}
```
- La URL JDBC usa `stringtype=unspecified`: los ENUM se envían como texto (`"CONFIRMADA"`). Al **leer** un ENUM, castear a texto: `estado::text AS estado`.
- Fechas: `Jdbc.fecha(rs, col)` (LocalDate) y `Jdbc.fechaHora(rs, col)` (LocalDateTime). Parámetros `LocalDate`/`LocalDateTime` se pasan directo.
- Varias sentencias que deben ser atómicas (venta, ingreso de stock, cambio de propietario) van en **un solo** `db.enTransaccion(...)`.

**Servicio**: constructor con `Preconditions.checkNotNull(dao)` (Guava); validar con `Validaciones` y lanzar `ReglaNegocioException("mensaje para el usuario")`; registrar operaciones críticas con `Auditoria.registrar("OPERACION", "entidad", "id=.. por=..")`.

**Controlador**
```java
@WebServlet({"/modulo", "/modulo/accion"})
public class ModuloServlet extends ControladorBase {
    private XxxService servicio;
    @Override public void init() { servicio = new XxxService(new XxxDaoJdbc(Db.get())); }
    // GET: mostrar(req, res, "modulo/vista", () -> req.setAttribute("datos", servicio.listar()));
    // POST: procesar(req, res, destinoExito, destinoError, "Mensaje de éxito", () -> { exigirPermiso(req, "PERMISO"); ... });
}
```
- Lectura de parámetros: `texto`, `entero`, `largo`, `decimal`, `fecha`, `fechaHora`, `hora`, `marcado` (de `ControladorBase`).
- Usuario actual: `usuario(req)` → `UsuarioSesion` (`idUsuario()`, `usuario()`, `rol()`, `puede("PERMISO")`, `idCliente()`).
- Rutas: el prefijo debe existir en `PoliticaAcceso` (ya están todos los de P02 a P12).

**Vista JSP**
```jsp
<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="..."/><c:set var="seccion" value="clientes"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P0X · descripción (RF-xx)</p>
<section class="tarjeta"> ... </section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
```
- Todo dato del usuario se imprime con `<c:out value="${...}"/>` (escapa HTML: prevención de XSS). En atributos: `value="<c:out value='${x}'/>"`.
- Todo `<form method="post">` lleva `<input type="hidden" name="_csrf" value="${sessionScope.csrf}">` (el filtro rechaza el POST sin token).
- **Prohibido** `style="..."`, `onclick=...` y `<script>` en línea: la CSP lo bloquea. Usar las clases de `app.css`; si un módulo necesita estilos propios, crear `static/css/modulo-<nombre>.css` y poner `<c:set var="cssExtra" value="modulo-<nombre>"/>` antes de incluir `inicio.jspf`.
- Confirmaciones: `<form ... data-confirmar="¿Seguro?">`. Imprimir: `<button type="button" class="btn btn-secundario" data-imprimir>Imprimir</button>`. Envío automático de un select: `data-autoenviar`.
- Gráficos: SVG con atributos (ver `panel/panel.jsp`), nunca alturas con `style`.
- Funciones `vs:` (formato peruano, RNF-17): `vs:fecha`, `vs:fechaHora`, `vs:hora`, `vs:inputFechaHora`, `vs:soles`, `vs:numero`, `vs:enmascarar`, `vs:etiqueta`, `vs:claseEstado`, `vs:edad`, `vs:contiene`.
- Clases CSS disponibles: `tarjeta`, `cabecera-tarjeta`, `rejilla rejilla-2/3/4`, `kpi`, `tabla`, `tabla-scroll`, `num`, `acciones`, `vacio`, `badge badge-ok/aviso/error/neutro`, `aviso aviso-exito/error/aviso`, `nota-clinica`, `formulario`, `campos`, `campo`, `campo ancho`, `casilla`, `ayuda`, `acciones-form`, `btn btn-primario/secundario/peligro btn-chico`, `en-linea`, `ficha` (dl), `pestanas`, `linea-tiempo`, `total-grande`, `separador`, `barra-herramientas`, `crece`, `derecha`, `mt18`, `no-imprimir`.

## 3. Base de datos (reglas que ya protege PostgreSQL)

- `fn_confirmar_venta(id_venta, descuento, id_autoriza)`, `fn_anular_venta(id_venta, motivo)`, `fn_cerrar_caja(id_turno, efectivo, observacion)`, `fn_lote_sugerido(id_producto, cantidad)` (FEFO).
- Triggers: transiciones de cita válidas, consulta firmada inmutable (solo adendas), kardex que actualiza `lote.stock_actual`, auditoría de cliente/mascota/consulta/producto/venta/turno/usuario.
- Rol `vetsalud_app` (privilegio mínimo): **sin DELETE** salvo `venta_detalle`, `pago`, `rol_permiso`; sin UPDATE en `auditoria`, `acceso_log`, `movimiento_inventario`, `cita_estado_hist`, `adenda`; en `lote` solo puede actualizar `codigo_lote`, `fecha_vencimiento`, `costo_unitario`.
- Mensajes de error de BD → `ErroresBD.mensaje(e)` (ya lo usa `ControladorBase`).

## 4. Pruebas (TDD)

- JUnit 5 en `src/test/java/pe/vetsalud/erp/servicio/<Xxx>ServiceTest.java`.
- Dobles en memoria en `src/test/java/pe/vetsalud/erp/fakes/<Xxx>DaoEnMemoria.java`.
- `@DisplayName` indica el RF/RN que verifica. Primero la prueba (rojo), luego el código (verde).

## 5. Git

- Ramas: `main` (versiones entregadas), `develop` (integración), `feature/<modulo>`.
- Commits: `tipo: verbo en infinitivo + descripción (RF-xx)`; tipos `feat`, `fix`, `test`, `docs`, `style`, `refactor`, `build`, `chore`.
- Integración a `develop` con `merge --no-ff` (queda la evidencia de cada funcionalidad).
