<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Usuarios y auditoría"/><c:set var="seccion" value="admin"/><c:set var="pestana" value="parametros"/>
<c:set var="cssExtra" value="modulo-seguridad"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<%@ include file="/WEB-INF/jsp/layout/pestanas-admin.jspf" %>
<p class="etiqueta-pantalla">P12 · Parámetros del sistema (RF-24 configurable, RNF-05)</p>

<section class="tarjeta">
  <c:choose>
    <c:when test="${empty parametros}"><p class="vacio">No hay parámetros configurados.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th scope="col">Parámetro</th><th scope="col" class="num">Valor actual</th><th scope="col">Rango permitido</th><th scope="col">Actualizado</th><th scope="col" class="derecha">Nuevo valor</th></tr></thead>
        <tbody>
        <c:forEach var="pm" items="${parametros}">
          <c:set var="rango" value="${rangos[pm.clave]}"/>
          <tr>
            <td><strong><c:out value="${pm.clave}"/></strong><br><small><c:out value="${pm.descripcion}"/></small></td>
            <td class="num"><strong><c:out value="${pm.valor}"/></strong></td>
            <td><c:choose>
              <c:when test="${empty rango}"><span class="badge badge-neutro">Solo lectura</span></c:when>
              <c:otherwise><c:out value="${rango.lowerEndpoint()}"/> a <c:out value="${rango.upperEndpoint()}"/></c:otherwise>
            </c:choose></td>
            <td><c:out value="${vs:fechaHora(pm.actualizadoEn)}"/></td>
            <td>
              <c:if test="${not empty rango and sesion.puede('USUARIO_ADMINISTRAR')}">
                <form method="post" action="${pageContext.request.contextPath}/admin/parametros" class="form-parametro"
                      data-confirmar="¿Cambiar ${fn:escapeXml(pm.clave)}?">
                  <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                  <input type="hidden" name="clave" value="<c:out value='${pm.clave}'/>">
                  <label class="sr-only" for="v-${pm.clave}">Nuevo valor de <c:out value="${pm.clave}"/></label>
                  <input id="v-${pm.clave}" name="valor" type="number" required min="${rango.lowerEndpoint()}" max="${rango.upperEndpoint()}" step="1" value="<c:out value='${pm.valor}'/>">
                  <button type="submit" class="btn btn-primario btn-chico">Guardar</button>
                </form>
              </c:if>
            </td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
    </c:otherwise>
  </c:choose>
  <ul class="ayuda">
    <li>MINUTOS_SESION y MAX_INTENTOS_LOGIN se aplican en el siguiente inicio de sesión; DIAS_POR_VENCER se refleja de inmediato en alertas y reportes.</li>
    <li>DESCUENTO_MAX_PCT: la base de datos mantiene además la regla RN-08 (un descuento mayor al 10 % exige autorización del administrador).</li>
    <li>Cada cambio queda en el log de auditoría con el usuario que lo hizo.</li>
  </ul>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
