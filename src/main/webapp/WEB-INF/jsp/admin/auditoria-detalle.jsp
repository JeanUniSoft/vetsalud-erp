<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Usuarios y auditoría"/><c:set var="seccion" value="admin"/><c:set var="pestana" value="auditoria"/>
<c:set var="cssExtra" value="modulo-seguridad"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<%@ include file="/WEB-INF/jsp/layout/pestanas-admin.jspf" %>
<p class="etiqueta-pantalla">P12 · Detalle de auditoría: valores antes y después (RF-04)</p>

<c:choose>
  <c:when test="${empty registro}">
    <section class="tarjeta"><p class="vacio">El registro de auditoría no existe.</p>
      <a class="btn btn-secundario" href="${pageContext.request.contextPath}/admin/auditoria">Volver a la auditoría</a></section>
  </c:when>
  <c:otherwise>
    <div class="rejilla rejilla-2">
      <section class="tarjeta">
        <h2>Registro n.° <c:out value="${registro.idAuditoria}"/></h2>
        <dl class="ficha">
          <dt>Fecha y hora</dt><dd><c:out value="${vs:fechaHora(registro.fechaHora)}"/></dd>
          <dt>Usuario</dt><dd><c:out value="${empty registro.usuario ? 'Sistema / script' : registro.usuario}"/></dd>
          <dt>Tabla</dt><dd><code><c:out value="${registro.tabla}"/></code></dd>
          <dt>Id del registro</dt><dd><c:out value="${registro.idRegistro}"/></dd>
          <dt>Operación</dt><dd><c:out value="${registro.operacion}"/></dd>
          <dt>Evento</dt><dd><span class="badge badge-neutro"><c:out value="${registro.evento()}"/></span></dd>
        </dl>
      </section>
      <section class="tarjeta">
        <h2>Cómo leer este detalle</h2>
        <p class="ayuda">En una modificación se listan solo los campos que cambiaron; en un alta, los valores registrados; en una eliminación, los valores que tenía.
          La contraseña (clave_hash) nunca se guarda en la auditoría ni se muestra (RNF-15).</p>
        <a class="btn btn-secundario btn-chico" href="${pageContext.request.contextPath}/admin/auditoria?tabla=${fn:escapeXml(registro.tabla)}">Ver más cambios de <c:out value="${registro.tabla}"/></a>
      </section>
    </div>
    <section class="tarjeta">
      <h2>Campos <c:out value="${registro.operacion == 'UPDATE' ? 'modificados' : 'registrados'}"/> (<c:out value="${fn:length(cambios)}"/>)</h2>
      <c:choose>
        <c:when test="${empty cambios}"><p class="vacio">No hay diferencias visibles en este registro.</p></c:when>
        <c:otherwise>
          <div class="tabla-scroll"><table class="tabla">
            <thead><tr><th scope="col">Campo</th><th scope="col">Antes</th><th scope="col">Después</th></tr></thead>
            <tbody>
            <c:forEach var="cc" items="${cambios}">
              <tr>
                <td><code><c:out value="${cc.campo}"/></code></td>
                <td class="${cc.tipo == 'AGREGADO' ? '' : 'cambio-antes'}"><span class="valor-json"><c:out value="${cc.tipo == 'AGREGADO' ? '—' : (empty cc.antes ? '(vacío)' : cc.antes)}"/></span></td>
                <td class="${cc.tipo == 'ELIMINADO' ? '' : 'cambio-despues'}"><span class="valor-json"><c:out value="${cc.tipo == 'ELIMINADO' ? '—' : (empty cc.despues ? '(vacío)' : cc.despues)}"/></span></td>
              </tr>
            </c:forEach>
            </tbody>
          </table></div>
        </c:otherwise>
      </c:choose>
      <div class="acciones-form mt18"><a class="btn btn-secundario" href="${pageContext.request.contextPath}/admin/auditoria">Volver a la auditoría</a></div>
    </section>
  </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
