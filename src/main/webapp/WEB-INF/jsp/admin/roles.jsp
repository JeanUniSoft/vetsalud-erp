<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Usuarios y auditoría"/><c:set var="seccion" value="admin"/><c:set var="pestana" value="roles"/>
<c:set var="cssExtra" value="modulo-seguridad"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<%@ include file="/WEB-INF/jsp/layout/pestanas-admin.jspf" %>
<p class="etiqueta-pantalla">P12 · Matriz de permisos por rol (RF-02)</p>

<section class="tarjeta">
  <h2>Roles del sistema</h2>
  <dl class="leyenda-roles">
    <c:forEach var="r" items="${roles}">
      <div>
        <dt><c:out value="${vs:etiqueta(r.nombre)}"/> <c:if test="${not r.activo}"><span class="badge badge-error">Inactivo</span></c:if></dt>
        <dd><c:out value="${r.descripcion}"/></dd>
        <dd><c:out value="${r.usuarios}"/> usuario(s) activo(s) · <c:out value="${r.permisos}"/> permiso(s)</dd>
      </div>
    </c:forEach>
  </dl>
</section>

<section class="tarjeta">
  <div class="cabecera-tarjeta">
    <h2>Permisos de acción por rol</h2>
    <span class="ayuda">El acceso a cada pantalla lo define además la matriz de navegación por rol.</span>
  </div>
  <form method="post" action="${pageContext.request.contextPath}/admin/roles"
        data-confirmar="¿Guardar la matriz de permisos? Los cambios se aplican en el próximo ingreso de cada usuario.">
    <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
    <div class="tabla-scroll"><table class="tabla matriz">
      <caption class="sr-only">Matriz de permisos: permisos en filas y roles en columnas</caption>
      <thead><tr>
        <th scope="col">Permiso</th>
        <c:forEach var="r" items="${roles}"><th scope="col" class="rol"><c:out value="${vs:etiqueta(r.nombre)}"/><small><c:out value="${r.usuarios}"/> usuario(s)</small></th></c:forEach>
      </tr></thead>
      <tbody>
        <c:set var="moduloAnterior" value=""/>
        <c:forEach var="p" items="${matriz}">
          <c:if test="${p.modulo != moduloAnterior}">
            <tr class="modulo"><th scope="rowgroup" colspan="${fn:length(roles) + 1}">Módulo <c:out value="${vs:etiqueta(p.modulo)}"/></th></tr>
            <c:set var="moduloAnterior" value="${p.modulo}"/>
          </c:if>
          <tr>
            <td class="permiso"><strong><c:out value="${p.descripcion}"/></strong><code><c:out value="${p.codigo}"/></code></td>
            <c:forEach var="r" items="${roles}">
              <c:set var="idCasilla" value="m-${r.nombre}-${p.codigo}"/>
              <c:set var="marcado" value="${vs:contiene(p.roles, r.nombre)}"/>
              <c:set var="bloqueada" value="${r.nombre == sesion.rol and p.codigo == 'USUARIO_ADMINISTRAR'}"/>
              <td class="celda">
                <label class="casilla-matriz" for="${idCasilla}">
                  <c:choose>
                    <c:when test="${bloqueada}">
                      <input type="checkbox" id="${idCasilla}" checked disabled aria-describedby="nota-bloqueo">
                      <input type="hidden" name="asignacion" value="${r.nombre}|${p.codigo}">
                    </c:when>
                    <c:otherwise>
                      <input type="checkbox" id="${idCasilla}" name="asignacion" value="${r.nombre}|${p.codigo}" ${marcado ? 'checked' : ''}>
                    </c:otherwise>
                  </c:choose>
                  <span class="sr-only"><c:out value="${vs:etiqueta(r.nombre)}"/>: <c:out value="${p.descripcion}"/></span>
                </label>
              </td>
            </c:forEach>
          </tr>
        </c:forEach>
      </tbody>
    </table></div>
    <p class="ayuda" id="nota-bloqueo">Su propio rol (<c:out value="${vs:etiqueta(sesion.rol)}"/>) conserva siempre USUARIO_ADMINISTRAR para que el sistema no se quede sin administrador.</p>
    <c:if test="${sesion.puede('USUARIO_ADMINISTRAR')}">
      <div class="acciones-form"><button type="submit" class="btn btn-primario">Guardar matriz</button></div>
    </c:if>
  </form>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
