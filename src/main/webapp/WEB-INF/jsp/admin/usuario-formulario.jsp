<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Usuarios y auditoría"/><c:set var="seccion" value="admin"/><c:set var="pestana" value="usuarios"/>
<c:set var="cssExtra" value="modulo-seguridad"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<%@ include file="/WEB-INF/jsp/layout/pestanas-admin.jspf" %>
<p class="etiqueta-pantalla">P12 · ${nuevo ? 'Alta de usuario con clave temporal (RF-02, RF-03)' : 'Edición de usuario y estado de la cuenta (RF-02, RF-03)'}</p>
<%@ include file="/WEB-INF/jsp/admin/_credencial.jspf" %>

<div class="${nuevo ? '' : 'rejilla rejilla-2'}">
<section class="tarjeta">
  <h2>${nuevo ? 'Nuevo usuario' : 'Datos del usuario'}</h2>
  <c:choose>
    <c:when test="${not nuevo and empty actual}"><p class="vacio">El usuario solicitado no existe.</p></c:when>
    <c:otherwise>
  <form method="post" class="formulario"
        action="${pageContext.request.contextPath}${nuevo ? '/admin/usuarios/nuevo' : '/admin/usuarios/editar'}">
    <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
    <c:if test="${not nuevo}"><input type="hidden" name="id" value="${actual.idUsuario}"></c:if>
    <div class="campos">
      <div class="campo"><label for="nombres">Nombres <span class="req">*</span></label>
        <input id="nombres" name="nombres" type="text" required maxlength="80" value="<c:out value='${usuario.nombres}'/>"></div>
      <div class="campo"><label for="apellidos">Apellidos <span class="req">*</span></label>
        <input id="apellidos" name="apellidos" type="text" required maxlength="80" value="<c:out value='${usuario.apellidos}'/>"></div>
      <div class="campo"><label for="usuario">Usuario <span class="req">*</span></label>
        <input id="usuario" name="usuario" type="text" required minlength="4" maxlength="40" autocomplete="off"
               pattern="[a-z0-9._]{4,40}" value="<c:out value='${usuario.usuario}'/>">
        <span class="ayuda">Minúsculas, números, punto o guion bajo (4 a 40).</span></div>
      <div class="campo"><label for="correo">Correo <span class="req">*</span></label>
        <input id="correo" name="correo" type="email" required maxlength="120" value="<c:out value='${usuario.correo}'/>"></div>
      <div class="campo"><label for="rol">Rol <span class="req">*</span></label>
        <select id="rol" name="rol" required ${not nuevo and actual.idUsuario == sesion.idUsuario ? 'aria-describedby="ayuda-rol"' : ''}>
          <c:if test="${nuevo}"><option value="">Seleccione…</option></c:if>
          <c:forEach var="r" items="${roles}">
            <option value="${r}" ${usuario.rol == r ? 'selected' : ''}><c:out value="${vs:etiqueta(r)}"/></option>
          </c:forEach>
        </select>
        <c:if test="${not nuevo and actual.idUsuario == sesion.idUsuario}"><span class="ayuda" id="ayuda-rol">No puede cambiar su propio rol.</span></c:if></div>
      <div class="campo"><label for="colegiatura">Colegiatura CMVP</label>
        <input id="colegiatura" name="colegiatura" type="text" inputmode="numeric" maxlength="6" pattern="[0-9]{4,6}"
               value="<c:out value='${usuario.colegiatura}'/>">
        <span class="ayuda">Obligatoria para el rol Veterinario (4 a 6 dígitos).</span></div>
      <div class="campo"><label for="documentoCliente">Documento del cliente (portal)</label>
        <input id="documentoCliente" name="documentoCliente" type="text" maxlength="15" list="clientes-libres"
               value="<c:out value='${usuario.documentoCliente}'/>">
        <datalist id="clientes-libres">
          <c:forEach var="cl" items="${clientesLibres}">
            <c:set var="docCliente" value="${fn:substringBefore(cl.etiqueta, ' · ')}"/>
            <option value="<c:out value='${docCliente}'/>"><c:out value="${cl.etiqueta}"/></option>
          </c:forEach>
        </datalist>
        <span class="ayuda">Solo para el rol Cliente: vincula la cuenta a un cliente existente (uno por cliente).</span></div>
    </div>
    <c:if test="${nuevo}">
      <p class="ayuda">Al guardar se genera una <strong>clave temporal aleatoria</strong> que se muestra una sola vez; el usuario deberá cambiarla en su primer ingreso.</p>
    </c:if>
    <div class="acciones-form">
      <c:if test="${sesion.puede('USUARIO_ADMINISTRAR')}">
        <button type="submit" class="btn btn-primario">${nuevo ? 'Crear usuario' : 'Guardar cambios'}</button>
      </c:if>
      <a class="btn btn-secundario" href="${pageContext.request.contextPath}/admin/usuarios">Volver a la lista</a>
    </div>
  </form>
    </c:otherwise>
  </c:choose>
</section>

<c:if test="${not nuevo and not empty actual}">
  <section class="tarjeta">
    <h2>Estado de la cuenta</h2>
    <dl class="ficha">
      <dt>Estado</dt>
      <dd><c:choose>
        <c:when test="${not actual.activo}"><span class="badge badge-error">Inactivo</span></c:when>
        <c:when test="${actual.bloqueado}"><span class="badge badge-error">Bloqueado</span></c:when>
        <c:otherwise><span class="badge badge-ok">Activo</span></c:otherwise>
      </c:choose> <c:if test="${actual.debeCambiarClave}"><span class="badge badge-aviso">Debe cambiar su clave</span></c:if></dd>
      <dt>Rol</dt><dd><c:out value="${vs:etiqueta(actual.rol)}"/></dd>
      <dt>Intentos fallidos</dt><dd><c:out value="${actual.intentosFallidos}"/></dd>
      <dt>Último acceso</dt><dd><c:out value="${empty actual.ultimoAcceso ? 'Nunca' : vs:fechaHora(actual.ultimoAcceso)}"/></dd>
      <dt>Creado</dt><dd><c:out value="${vs:fechaHora(actual.creadoEn)}"/></dd>
      <c:if test="${not empty actual.cliente}"><dt>Cliente vinculado</dt><dd><c:out value="${actual.cliente}"/> (<c:out value="${actual.documentoCliente}"/>)</dd></c:if>
    </dl>
    <div class="separador"></div>
    <c:if test="${sesion.puede('USUARIO_ADMINISTRAR')}">
      <div class="acciones-form">
        <c:if test="${actual.activo and actual.idUsuario != sesion.idUsuario}">
          <form method="post" action="${pageContext.request.contextPath}/admin/usuarios/clave" class="en-linea"
                data-confirmar="¿Generar una clave temporal? La clave actual dejará de funcionar y la cuenta se desbloqueará.">
            <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
            <input type="hidden" name="id" value="${actual.idUsuario}"><input type="hidden" name="volver" value="editar">
            <button type="submit" class="btn btn-primario">Generar clave temporal</button>
          </form>
        </c:if>
        <c:if test="${actual.bloqueado and actual.activo}">
          <form method="post" action="${pageContext.request.contextPath}/admin/usuarios/estado" class="en-linea">
            <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
            <input type="hidden" name="id" value="${actual.idUsuario}"><input type="hidden" name="accion" value="desbloquear">
            <input type="hidden" name="volver" value="editar">
            <button type="submit" class="btn btn-secundario">Desbloquear</button>
          </form>
        </c:if>
        <c:choose>
          <c:when test="${actual.idUsuario == sesion.idUsuario}"><span class="ayuda">No puede desactivar su propia cuenta.</span></c:when>
          <c:when test="${actual.activo}">
            <form method="post" action="${pageContext.request.contextPath}/admin/usuarios/estado" class="en-linea"
                  data-confirmar="¿Desactivar la cuenta? El usuario ya no podrá ingresar al sistema.">
              <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
              <input type="hidden" name="id" value="${actual.idUsuario}"><input type="hidden" name="accion" value="desactivar">
              <input type="hidden" name="volver" value="editar">
              <button type="submit" class="btn btn-peligro">Desactivar cuenta</button>
            </form>
          </c:when>
          <c:otherwise>
            <form method="post" action="${pageContext.request.contextPath}/admin/usuarios/estado" class="en-linea">
              <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
              <input type="hidden" name="id" value="${actual.idUsuario}"><input type="hidden" name="accion" value="activar">
              <input type="hidden" name="volver" value="editar">
              <button type="submit" class="btn btn-secundario">Activar cuenta</button>
            </form>
          </c:otherwise>
        </c:choose>
      </div>
    </c:if>
    <p class="ayuda mt18">Trazabilidad: <a href="${pageContext.request.contextPath}/admin/accesos?usuario=${fn:escapeXml(actual.usuario)}">accesos de este usuario</a> ·
      <a href="${pageContext.request.contextPath}/admin/auditoria?tabla=usuario">cambios en usuarios</a>.</p>
  </section>
</c:if>
</div>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
