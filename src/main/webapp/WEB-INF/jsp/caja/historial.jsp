<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Historial de caja"/><c:set var="seccion" value="caja"/><c:set var="cssExtra" value="modulo-ventas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P09 · Historial de turnos con esperado, contado y diferencia (RF-30)</p>
<nav class="pestanas" aria-label="Secciones de caja">
  <a href="${ctx}/caja">Turno actual</a>
  <a class="activo" aria-current="page" href="${ctx}/caja/historial">Historial de turnos</a>
  <a href="${ctx}/ventas">Ventas</a>
</nav>
<section class="tarjeta">
  <form method="get" action="${ctx}/caja/historial" class="barra-herramientas">
    <div class="campo"><label for="desde">Apertura desde</label><input id="desde" name="desde" type="date" value="<c:out value='${desde}'/>"></div>
    <div class="campo"><label for="hasta">Hasta</label><input id="hasta" name="hasta" type="date" value="<c:out value='${hasta}'/>"></div>
    <button type="submit" class="btn btn-secundario">Filtrar</button>
    <span class="ayuda">${sesion.rol == 'ADMINISTRADOR' ? 'Turnos de todos los cajeros.' : 'Sus turnos de caja.'}</span>
  </form>
  <c:choose>
    <c:when test="${empty turnos}"><p class="vacio">No hay turnos en el periodo.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>Turno</th><th>Cajero</th><th>Apertura</th><th>Cierre</th><th class="num">Inicial</th><th class="num">Esperado</th>
          <th class="num">Contado</th><th class="num">Diferencia</th><th>Estado</th><th><span class="solo-lector">Acción</span></th></tr></thead>
        <tbody>
        <c:forEach var="t" items="${turnos}">
          <tr>
            <td>n.° <c:out value="${t.idTurno}"/></td><td><c:out value="${t.cajero}"/></td>
            <td><c:out value="${vs:fechaHora(t.apertura)}"/></td><td><c:out value="${vs:fechaHora(t.cierre)}"/></td>
            <td class="num"><c:out value="${vs:soles(t.montoInicial)}"/></td>
            <td class="num"><c:if test="${not empty t.efectivoEsperado}"><c:out value="${vs:soles(t.efectivoEsperado)}"/></c:if></td>
            <td class="num"><c:if test="${not empty t.efectivoContado}"><c:out value="${vs:soles(t.efectivoContado)}"/></c:if></td>
            <td class="num"><c:if test="${not empty t.diferencia}">
              <span class="${t.diferencia.signum() == 0 ? 'badge badge-ok' : 'badge badge-error'}">${t.diferencia.signum() < 0 ? '−' : ''}<c:out value="${vs:soles(t.diferencia.abs())}"/></span></c:if></td>
            <td><span class="${vs:claseEstado(t.estado)}"><c:out value="${vs:etiqueta(t.estado)}"/></span></td>
            <td class="acciones"><a class="btn btn-secundario btn-chico" href="${ctx}/caja/turno?id=${t.idTurno}">Ver</a></td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
    </c:otherwise>
  </c:choose>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
