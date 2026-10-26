<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Usuarios y auditoría"/><c:set var="seccion" value="admin"/><c:set var="pestana" value="usuarios"/>
<c:set var="cssExtra" value="modulo-seguridad"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<%@ include file="/WEB-INF/jsp/layout/pestanas-admin.jspf" %>
<p class="etiqueta-pantalla">P12 · Usuarios: roles, estado, bloqueo y restablecimiento controlado (RF-01, RF-02, RF-03)</p>
<%@ include file="/WEB-INF/jsp/admin/_credencial.jspf" %>

<section class="tarjeta">
  <form method="get" action="${pageContext.request.contextPath}/admin/usuarios" class="barra-herramientas">
    <div class="campo crece"><label for="q">Buscar</label>
      <input id="q" name="q" type="search" maxlength="60" value="<c:out value='${q}'/>" placeholder="Nombre, usuario o correo"></div>
    <div class="campo"><label for="rol">Rol</label>
      <select id="rol" name="rol" data-autoenviar>
        <option value="">Todos</option>
        <c:forEach var="r" items="${roles}"><option value="${r}" ${rolFiltro == r ? 'selected' : ''}><c:out value="${vs:etiqueta(r)}"/></option></c:forEach>
      </select></div>
    <div class="campo"><label for="estado">Estado</label>
      <select id="estado" name="estado" data-autoenviar>
        <option value="">Todos</option>
        <option value="ACTIVOS" ${estadoFiltro == 'ACTIVOS' ? 'selected' : ''}>Activos</option>
        <option value="BLOQUEADOS" ${estadoFiltro == 'BLOQUEADOS' ? 'selected' : ''}>Bloqueados</option>
        <option value="INACTIVOS" ${estadoFiltro == 'INACTIVOS' ? 'selected' : ''}>Inactivos</option>
      </select></div>
    <button type="submit" class="btn btn-secundario">Buscar</button>
    <c:if test="${sesion.puede('USUARIO_ADMINISTRAR')}">
      <a class="btn btn-primario" href="${pageContext.request.contextPath}/admin/usuarios/nuevo">+ Nuevo usuario</a>
    </c:if>
  </form>

  <c:choose>
    <c:when test="${empty usuarios}"><p class="vacio">No hay usuarios con esos filtros.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th scope="col">Usuario</th><th scope="col">Rol</th><th scope="col">Estado</th><th scope="col">Último acceso</th><th scope="col"><span class="sr-only">Acciones</span></th></tr></thead>
        <tbody>
        <c:forEach var="u" items="${usuarios}">
          <c:set var="esYo" value="${u.idUsuario == sesion.idUsuario}"/>
          <tr class="${u.activo ? '' : 'fila-inactiva'}">
            <td><strong><c:out value="${u.nombreCompleto()}"/></strong><c:if test="${esYo}"> <small>(usted)</small></c:if><br>
              <small><c:out value="${u.usuario}"/> · <c:out value="${u.correo}"/></small>
              <c:if test="${not empty u.colegiatura}"><br><small>CMVP <c:out value="${u.colegiatura}"/></small></c:if>
              <c:if test="${not empty u.cliente}"><br><small>Cliente: <c:out value="${u.cliente}"/></small></c:if></td>
            <td><span class="badge badge-neutro"><c:out value="${vs:etiqueta(u.rol)}"/></span></td>
            <td><div class="estado-cuenta">
              <c:choose>
                <c:when test="${not u.activo}"><span class="badge badge-error">Inactivo</span></c:when>
                <c:when test="${u.bloqueado}"><span class="badge badge-error">Bloqueado</span></c:when>
                <c:otherwise><span class="badge badge-ok">Activo</span></c:otherwise>
              </c:choose>
              <c:if test="${u.debeCambiarClave}"><span class="badge badge-aviso">Clave temporal</span></c:if>
              <c:if test="${u.intentosFallidos > 0 and not u.bloqueado}"><span class="badge badge-neutro"><c:out value="${u.intentosFallidos}"/> intento(s) fallido(s)</span></c:if>
            </div></td>
            <td><c:out value="${empty u.ultimoAcceso ? 'Nunca' : vs:fechaHora(u.ultimoAcceso)}"/></td>
            <td class="acciones">
              <a class="btn btn-secundario btn-chico" href="${pageContext.request.contextPath}/admin/usuarios/editar?id=${u.idUsuario}">Editar</a>
              <c:if test="${sesion.puede('USUARIO_ADMINISTRAR')}">
                <c:if test="${u.bloqueado and u.activo}">
                  <form method="post" action="${pageContext.request.contextPath}/admin/usuarios/estado" class="en-linea">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                    <input type="hidden" name="id" value="${u.idUsuario}"><input type="hidden" name="accion" value="desbloquear">
                    <button type="submit" class="btn btn-primario btn-chico">Desbloquear</button>
                  </form>
                </c:if>
                <c:if test="${u.activo and not esYo}">
                  <form method="post" action="${pageContext.request.contextPath}/admin/usuarios/clave" class="en-linea"
                        data-confirmar="¿Generar una clave temporal para ${fn:escapeXml(u.usuario)}? La clave actual dejará de funcionar.">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                    <input type="hidden" name="id" value="${u.idUsuario}">
                    <button type="submit" class="btn btn-secundario btn-chico">Clave temporal</button>
                  </form>
                </c:if>
              </c:if>
            </td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
      <p class="ayuda"><c:out value="${fn:length(usuarios)}"/> usuario(s). Activar o desactivar una cuenta se hace desde «Editar».</p>
    </c:otherwise>
  </c:choose>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
