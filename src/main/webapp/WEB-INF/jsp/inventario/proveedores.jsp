<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Proveedores"/><c:set var="seccion" value="inventario"/><c:set var="cssExtra" value="modulo-inventario"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="edicion" value="${not empty proveedor}"/>
<p class="etiqueta-pantalla">P07 · Proveedores de los documentos de ingreso (RF-21)</p>
<c:set var="pestana" value="proveedores"/>
<%@ include file="/WEB-INF/jsp/inventario/pestanas.jspf" %>
<div class="rejilla rejilla-2">
  <section class="tarjeta">
    <h2>Proveedores registrados</h2>
    <c:choose>
      <c:when test="${empty proveedores}"><p class="vacio">Aún no hay proveedores.</p></c:when>
      <c:otherwise>
        <div class="tabla-scroll"><table class="tabla">
          <thead><tr><th>Razón social</th><th>RUC</th><th>Contacto</th><th>Estado</th><th><span class="solo-lector">Acciones</span></th></tr></thead>
          <tbody>
          <c:forEach var="pr" items="${proveedores}">
            <tr>
              <td><strong><c:out value="${pr.razonSocial}"/></strong></td>
              <td><c:out value="${pr.ruc}"/></td>
              <td><c:out value="${pr.telefono}"/><br><small><c:out value="${pr.correo}"/></small></td>
              <td><span class="${pr.activo ? 'badge badge-ok' : 'badge badge-error'}">${pr.activo ? 'Activo' : 'Inactivo'}</span></td>
              <td class="acciones"><c:if test="${sesion.puede('INVENTARIO_INGRESAR')}"><a class="btn btn-secundario btn-chico" href="${ctx}/inventario/proveedores?id=${pr.idProveedor}">Editar</a></c:if></td>
            </tr>
          </c:forEach>
          </tbody>
        </table></div>
      </c:otherwise>
    </c:choose>
  </section>
  <section class="tarjeta">
    <h2>${edicion ? 'Editar proveedor' : 'Nuevo proveedor'}</h2>
    <c:choose>
      <c:when test="${sesion.puede('INVENTARIO_INGRESAR')}">
        <form method="post" action="${ctx}/inventario/proveedores" class="formulario">
          <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
          <c:if test="${edicion}"><input type="hidden" name="id" value="${proveedor.idProveedor}"></c:if>
          <div class="campos">
            <div class="campo"><label for="ruc">RUC <span class="req">*</span></label>
              <input id="ruc" name="ruc" type="text" required inputmode="numeric" maxlength="11" pattern="(10|20)[0-9]{9}" value="<c:out value='${proveedor.ruc}'/>"></div>
            <div class="campo ancho"><label for="razonSocial">Razón social <span class="req">*</span></label>
              <input id="razonSocial" name="razonSocial" type="text" required maxlength="120" value="<c:out value='${proveedor.razonSocial}'/>"></div>
            <div class="campo"><label for="telefono">Teléfono</label>
              <input id="telefono" name="telefono" type="tel" maxlength="15" value="<c:out value='${proveedor.telefono}'/>"></div>
            <div class="campo"><label for="correo">Correo</label>
              <input id="correo" name="correo" type="email" maxlength="120" value="<c:out value='${proveedor.correo}'/>"></div>
            <c:if test="${edicion}">
              <div class="campo ancho"><label class="casilla"><input type="checkbox" name="activo" ${proveedor.activo ? 'checked' : ''}> Proveedor activo (los inactivos no aparecen en nuevos ingresos)</label></div>
            </c:if>
          </div>
          <div class="acciones-form">
            <button type="submit" class="btn btn-primario">${edicion ? 'Guardar cambios' : 'Registrar proveedor'}</button>
            <c:if test="${edicion}"><a class="btn btn-secundario" href="${ctx}/inventario/proveedores">Cancelar</a></c:if>
          </div>
        </form>
      </c:when>
      <c:otherwise><p class="vacio">Su rol no puede modificar proveedores.</p></c:otherwise>
    </c:choose>
  </section>
</div>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
