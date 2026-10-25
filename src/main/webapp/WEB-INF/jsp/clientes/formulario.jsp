<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="edicion" value="${not empty cliente}"/>
<c:set var="titulo" value="${edicion ? 'Editar cliente' : 'Nuevo cliente'}"/><c:set var="seccion" value="clientes"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P03 · ${edicion ? 'Actualización con trazabilidad (RF-06)' : 'Registro con documento único (RF-05, RN-01)'}</p>
<section class="tarjeta">
  <form method="post" class="formulario"
        action="${pageContext.request.contextPath}${edicion ? '/clientes/editar' : '/clientes/nuevo'}">
    <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
    <c:if test="${edicion}"><input type="hidden" name="id" value="${cliente.idCliente}"></c:if>
    <div class="campos">
      <div class="campo"><label for="tipoDocumento">Tipo de documento <span class="req">*</span></label>
        <select id="tipoDocumento" name="tipoDocumento" required>
          <c:forEach var="t" items="${['DNI','CE','RUC','PASAPORTE']}">
            <option value="${t}" ${cliente.tipoDocumento == t ? 'selected' : ''}>${t}</option>
          </c:forEach>
        </select></div>
      <div class="campo"><label for="numDocumento">Número de documento <span class="req">*</span></label>
        <input id="numDocumento" name="numDocumento" type="text" required maxlength="15" value="<c:out value='${cliente.numDocumento}'/>"></div>
      <div class="campo"><label for="nombres">Nombres o razón social <span class="req">*</span></label>
        <input id="nombres" name="nombres" type="text" required maxlength="80" value="<c:out value='${cliente.nombres}'/>"></div>
      <div class="campo"><label for="apellidos">Apellidos</label>
        <input id="apellidos" name="apellidos" type="text" maxlength="80" value="<c:out value='${cliente.apellidos}'/>">
        <span class="ayuda">Obligatorio salvo para RUC.</span></div>
      <div class="campo"><label for="telefono">Teléfono <span class="req">*</span></label>
        <input id="telefono" name="telefono" type="tel" required maxlength="15" value="<c:out value='${cliente.telefono}'/>"></div>
      <div class="campo"><label for="correo">Correo</label>
        <input id="correo" name="correo" type="email" maxlength="120" value="<c:out value='${cliente.correo}'/>"></div>
      <div class="campo"><label for="direccion">Dirección</label>
        <input id="direccion" name="direccion" type="text" maxlength="200" value="<c:out value='${cliente.direccion}'/>"></div>
      <div class="campo"><label for="distrito">Distrito</label>
        <input id="distrito" name="distrito" type="text" maxlength="60" value="<c:out value='${cliente.distrito}'/>"></div>
      <div class="campo ancho">
        <label class="casilla"><input type="checkbox" name="aceptaRecordatorios" ${cliente.aceptaRecordatorios ? 'checked' : ''}>
          Acepta recibir recordatorios de citas y controles por correo (consentimiento, RF-37)</label>
        <c:if test="${edicion}">
          <label class="casilla"><input type="checkbox" name="activo" ${cliente.activo ? 'checked' : ''}> Cliente activo</label>
        </c:if>
      </div>
    </div>
    <div class="acciones-form">
      <button type="submit" class="btn btn-primario">${edicion ? 'Guardar cambios' : 'Registrar cliente'}</button>
      <a class="btn btn-secundario" href="${pageContext.request.contextPath}${edicion ? '/clientes/ver?id='.concat(cliente.idCliente) : '/clientes'}">Cancelar</a>
    </div>
  </form>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
