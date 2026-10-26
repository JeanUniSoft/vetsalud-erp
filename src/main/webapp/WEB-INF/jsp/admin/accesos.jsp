<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Usuarios y auditoría"/><c:set var="seccion" value="admin"/><c:set var="pestana" value="accesos"/>
<c:set var="cssExtra" value="modulo-seguridad"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<%@ include file="/WEB-INF/jsp/layout/pestanas-admin.jspf" %>
<p class="etiqueta-pantalla">P12 · Bitácora de accesos: fecha, hora, equipo, usuario y resultado (RF-01)</p>

<section class="tarjeta">
  <form method="get" action="${pageContext.request.contextPath}/admin/accesos" class="barra-herramientas">
    <div class="campo"><label for="desde">Desde</label><input id="desde" name="desde" type="date" value="${desde}" required></div>
    <div class="campo"><label for="hasta">Hasta</label><input id="hasta" name="hasta" type="date" value="${hasta}" required></div>
    <div class="campo crece"><label for="usuario">Usuario ingresado</label>
      <input id="usuario" name="usuario" type="search" maxlength="40" value="<c:out value='${usuarioFiltro}'/>" placeholder="Parte del usuario"></div>
    <div class="campo"><label for="resultado">Resultado</label>
      <select id="resultado" name="resultado"><option value="">Todos</option>
        <option value="EXITOSO" ${resultadoFiltro == 'EXITOSO' ? 'selected' : ''}>Exitoso</option>
        <option value="FALLIDO" ${resultadoFiltro == 'FALLIDO' ? 'selected' : ''}>Fallido</option>
      </select></div>
    <button type="submit" class="btn btn-primario">Filtrar</button>
    <a class="btn btn-secundario" href="${pageContext.request.contextPath}/admin/usuarios?estado=BLOQUEADOS">Ver cuentas bloqueadas</a>
  </form>
</section>

<div class="rejilla rejilla-4">
  <div class="kpi destacado"><div class="etiqueta">Intentos de ingreso</div><div class="valor"><c:out value="${resumen.total}"/></div><div class="detalle">en el periodo filtrado</div></div>
  <div class="kpi"><div class="etiqueta">Exitosos</div><div class="valor"><c:out value="${resumen.exitosos}"/></div><div class="detalle">sesiones iniciadas</div></div>
  <div class="kpi"><div class="etiqueta">Fallidos</div><div class="valor"><c:out value="${resumen.fallidos}"/></div><div class="detalle">clave incorrecta, cuenta bloqueada o inexistente</div></div>
  <div class="kpi"><div class="etiqueta">Usuarios distintos</div><div class="valor"><c:out value="${resumen.usuarios}"/></div><div class="detalle">nombres de usuario ingresados</div></div>
</div>

<section class="tarjeta mt18">
  <c:choose>
    <c:when test="${empty accesos}"><p class="vacio">No hay intentos de ingreso con esos filtros.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th scope="col">Fecha</th><th scope="col">Hora</th><th scope="col">Equipo (IP)</th><th scope="col">Usuario ingresado</th><th scope="col">Cuenta</th><th scope="col">Resultado</th></tr></thead>
        <tbody>
        <c:forEach var="ac" items="${accesos}">
          <tr>
            <td><c:out value="${vs:fecha(ac.fechaHora)}"/></td>
            <td><c:out value="${vs:hora(ac.fechaHora)}"/></td>
            <td><code><c:out value="${empty ac.ip ? 'desconocido' : ac.ip}"/></code></td>
            <td><c:out value="${ac.usuarioIngresado}"/></td>
            <td><c:choose>
              <c:when test="${empty ac.idUsuario}"><span class="badge badge-neutro">No existe</span></c:when>
              <c:otherwise><a href="${pageContext.request.contextPath}/admin/usuarios/editar?id=${ac.idUsuario}"><c:out value="${ac.nombre}"/></a> <small>(<c:out value="${vs:etiqueta(ac.rol)}"/>)</small></c:otherwise>
            </c:choose></td>
            <td><span class="badge ${ac.exitoso ? 'badge-ok' : 'badge-error'}">${ac.exitoso ? 'Exitoso' : 'Fallido'}</span></td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
      <p class="ayuda"><c:out value="${fn:length(accesos)}"/> intento(s)<c:if test="${fn:length(accesos) >= limite}"> · se muestran los <c:out value="${limite}"/> más recientes</c:if>. La bitácora es de solo lectura (RNF-06).</p>
    </c:otherwise>
  </c:choose>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
