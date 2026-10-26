<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Usuarios y auditoría"/><c:set var="seccion" value="admin"/><c:set var="pestana" value="alertas"/>
<c:set var="cssExtra" value="modulo-seguridad"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<%@ include file="/WEB-INF/jsp/layout/pestanas-admin.jspf" %>
<p class="etiqueta-pantalla">P12 · Alertas internas de todos los roles: pendientes y atendidas (RF-38)</p>

<div class="rejilla rejilla-4">
  <div class="kpi destacado"><div class="etiqueta">Pendientes</div><div class="valor"><c:out value="${pendientes}"/></div><div class="detalle">de todos los roles</div></div>
  <div class="kpi"><div class="etiqueta">Atendidas</div><div class="valor"><c:out value="${fn:length(alertas) - pendientes}"/></div><div class="detalle">con usuario y fecha</div></div>
  <div class="kpi"><div class="etiqueta">Mostradas</div><div class="valor"><c:out value="${fn:length(alertas)}"/></div><div class="detalle">alertas más recientes (máx. 50)</div></div>
  <div class="kpi"><div class="etiqueta">Su rol</div><div class="valor"><c:out value="${vs:etiqueta(sesion.rol)}"/></div><div class="detalle">puede atender alertas de cualquier rol</div></div>
</div>

<section class="tarjeta mt18">
  <c:choose>
    <c:when test="${empty alertas}"><p class="vacio">No hay alertas registradas.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th scope="col">Estado</th><th scope="col">Para</th><th scope="col">Tipo</th><th scope="col">Mensaje</th><th scope="col">Creada</th><th scope="col">Atendida por</th><th scope="col">Atendida el</th><th scope="col"><span class="sr-only">Acción</span></th></tr></thead>
        <tbody>
        <c:forEach var="al" items="${alertas}">
          <tr>
            <td><span class="badge ${al.pendiente() ? 'badge-aviso' : 'badge-ok'}">${al.pendiente() ? 'Pendiente' : 'Atendida'}</span></td>
            <td><c:out value="${vs:etiqueta(al.rolDestino)}"/></td>
            <td><span class="${vs:claseEstado(al.tipo)}"><c:out value="${vs:etiqueta(al.tipo)}"/></span></td>
            <td><c:out value="${al.mensaje}"/></td>
            <td><c:out value="${vs:fechaHora(al.creadaEn)}"/></td>
            <td><c:out value="${empty al.atendidaPor ? '—' : al.atendidaPor}"/></td>
            <td><c:out value="${empty al.atendidaEn ? '—' : vs:fechaHora(al.atendidaEn)}"/></td>
            <td class="acciones">
              <c:if test="${al.pendiente() and sesion.puede('ALERTA_ATENDER')}">
                <form method="post" action="${pageContext.request.contextPath}/admin/alertas" class="en-linea">
                  <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                  <input type="hidden" name="id" value="${al.idAlerta}">
                  <button type="submit" class="btn btn-primario btn-chico">Atender</button>
                </form>
              </c:if>
            </td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
    </c:otherwise>
  </c:choose>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
