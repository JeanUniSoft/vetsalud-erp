<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Usuarios y auditoría"/><c:set var="seccion" value="admin"/><c:set var="pestana" value="auditoria"/>
<c:set var="cssExtra" value="modulo-seguridad"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<%@ include file="/WEB-INF/jsp/layout/pestanas-admin.jspf" %>
<p class="etiqueta-pantalla">P12 · Auditoría de altas, modificaciones, anulaciones, firmas y cierres (RF-04, RNF-06)</p>

<section class="tarjeta">
  <form method="get" action="${pageContext.request.contextPath}/admin/auditoria" class="barra-herramientas">
    <div class="campo"><label for="desde">Desde</label><input id="desde" name="desde" type="date" value="${desde}" required></div>
    <div class="campo"><label for="hasta">Hasta</label><input id="hasta" name="hasta" type="date" value="${hasta}" required></div>
    <div class="campo"><label for="tabla">Tabla</label>
      <select id="tabla" name="tabla"><option value="">Todas</option>
        <c:forEach var="tb" items="${tablas}"><option value="<c:out value='${tb}'/>" ${tablaFiltro == tb ? 'selected' : ''}><c:out value="${tb}"/></option></c:forEach>
      </select></div>
    <div class="campo"><label for="operacion">Operación</label>
      <select id="operacion" name="operacion"><option value="">Todas</option>
        <option value="INSERT" ${operacionFiltro == 'INSERT' ? 'selected' : ''}>Alta (INSERT)</option>
        <option value="UPDATE" ${operacionFiltro == 'UPDATE' ? 'selected' : ''}>Modificación (UPDATE)</option>
        <option value="DELETE" ${operacionFiltro == 'DELETE' ? 'selected' : ''}>Eliminación (DELETE)</option>
      </select></div>
    <div class="campo crece"><label for="usuario">Usuario</label>
      <select id="usuario" name="usuario"><option value="">Todos</option>
        <c:forEach var="us" items="${usuarios}"><option value="${us.id}" ${usuarioFiltro == us.id ? 'selected' : ''}><c:out value="${us.etiqueta}"/></option></c:forEach>
      </select></div>
    <button type="submit" class="btn btn-primario">Filtrar</button>
  </form>

  <c:choose>
    <c:when test="${empty registros}"><p class="vacio">No hay registros de auditoría con esos filtros.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th scope="col">Fecha y hora</th><th scope="col">Usuario</th><th scope="col">Tabla</th><th scope="col" class="num">Id registro</th>
          <th scope="col">Operación</th><th scope="col">Evento</th><th scope="col" class="num">Campos</th><th scope="col"><span class="sr-only">Detalle</span></th></tr></thead>
        <tbody>
        <c:forEach var="a" items="${registros}">
          <tr>
            <td><c:out value="${vs:fechaHora(a.fechaHora)}"/></td>
            <td><c:out value="${empty a.usuario ? 'Sistema / script' : a.usuario}"/></td>
            <td><code><c:out value="${a.tabla}"/></code></td>
            <td class="num"><c:out value="${a.idRegistro}"/></td>
            <td><span class="badge ${a.operacion == 'INSERT' ? 'badge-ok' : (a.operacion == 'DELETE' ? 'badge-error' : 'badge-neutro')}"><c:out value="${a.operacion}"/></span></td>
            <td><c:set var="ev" value="${a.evento()}"/>
              <span class="badge ${ev == 'Anulación' ? 'badge-error' : (ev == 'Firma' or ev == 'Cierre' ? 'badge-aviso' : 'badge-neutro')}"><c:out value="${ev}"/></span></td>
            <td class="num"><c:out value="${a.cambios}"/></td>
            <td class="acciones"><a class="btn btn-secundario btn-chico" href="${pageContext.request.contextPath}/admin/auditoria/detalle?id=${a.idAuditoria}">Ver cambios</a></td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
      <p class="ayuda"><c:out value="${fn:length(registros)}"/> registro(s)<c:if test="${fn:length(registros) >= limite}"> · se muestran los <c:out value="${limite}"/> más recientes; acote los filtros</c:if>.</p>
    </c:otherwise>
  </c:choose>
  <p class="ayuda">Los registros los escribe la base de datos (trigger de auditoría) y no se pueden modificar ni borrar desde la aplicación (RNF-06).
    Los ajustes de inventario quedan en el kardex inmutable: <a href="${pageContext.request.contextPath}/reportes/inventario?vista=kardex">ver kardex</a>.</p>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
