<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="edicion" value="${not empty mascota}"/>
<c:set var="titulo" value="${edicion ? 'Editar mascota' : 'Nueva mascota'}"/><c:set var="seccion" value="clientes"/><c:set var="cssExtra" value="modulo-mascotas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P03 · ${edicion ? 'Actualización de datos e identificación de la mascota (RF-08)' : 'Registro de mascota vinculada a un cliente activo (RF-07, RN-02)'}</p>
<c:choose>
  <c:when test="${empty cliente}">
    <section class="tarjeta"><p class="vacio">No se encontró el propietario o la mascota solicitada.</p>
      <a class="btn btn-secundario" href="${ctx}/clientes">Volver a clientes</a></section>
  </c:when>
  <c:otherwise>
<section class="tarjeta">
  <div class="cabecera-tarjeta">
    <h2>${edicion ? 'Datos de ' : 'Mascota de '}<c:out value="${edicion ? mascota.nombre : cliente.nombreCompleto()}"/></h2>
  </div>
  <p>Propietario: <a href="${ctx}/clientes/ver?id=${cliente.idCliente}"><strong><c:out value="${cliente.nombreCompleto()}"/></strong></a>
    · <c:out value="${cliente.tipoDocumento}"/> <c:out value="${vs:enmascarar(cliente.numDocumento)}"/>
    <c:if test="${edicion}"><br><small class="suave">El propietario se cambia desde la ficha con «Cambiar propietario» (operación auditada).</small></c:if></p>
  <form method="post" class="formulario" action="${ctx}${edicion ? '/mascotas/editar' : '/mascotas/nueva'}">
    <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
    <c:choose>
      <c:when test="${edicion}"><input type="hidden" name="id" value="${mascota.idMascota}"></c:when>
      <c:otherwise><input type="hidden" name="idCliente" value="${cliente.idCliente}"></c:otherwise>
    </c:choose>
    <div class="campos">
      <div class="campo"><label for="nombre">Nombre <span class="req">*</span></label>
        <input id="nombre" name="nombre" type="text" required maxlength="50" value="<c:out value='${mascota.nombre}'/>"></div>
      <div class="campo"><label for="idEspecie">Especie <span class="req">*</span></label>
        <select id="idEspecie" name="idEspecie" required>
          <option value="">Seleccione…</option>
          <c:forEach var="e" items="${especies}">
            <option value="${e.idEspecie}" ${mascota.idEspecie == e.idEspecie ? 'selected' : ''}><c:out value="${e.nombre}"/></option>
          </c:forEach>
        </select></div>
      <div class="campo"><label for="idRaza">Raza <span class="req">*</span></label>
        <select id="idRaza" name="idRaza" required>
          <option value="">Seleccione…</option>
          <c:forEach var="e" items="${especies}">
            <optgroup label="<c:out value='${e.nombre}'/>" data-especie="${e.idEspecie}">
              <c:forEach var="r" items="${razas}">
                <c:if test="${r.idEspecie == e.idEspecie}">
                  <option value="${r.idRaza}" ${mascota.idRaza == r.idRaza ? 'selected' : ''}><c:out value="${r.nombre}"/></option>
                </c:if>
              </c:forEach>
            </optgroup>
          </c:forEach>
        </select>
        <span class="ayuda">Debe corresponder a la especie elegida.</span></div>
      <div class="campo"><label for="sexo">Sexo <span class="req">*</span></label>
        <select id="sexo" name="sexo" required>
          <option value="">Seleccione…</option>
          <option value="M" ${mascota.sexo == 'M' ? 'selected' : ''}>Macho</option>
          <option value="H" ${mascota.sexo == 'H' ? 'selected' : ''}>Hembra</option>
        </select></div>
      <div class="campo"><label for="fechaNacimiento">Fecha de nacimiento</label>
        <input id="fechaNacimiento" name="fechaNacimiento" type="date" max="${hoy}" value="<c:out value='${mascota.fechaNacimiento}'/>">
        <span class="ayuda">Aproximada si no se conoce; no puede ser futura.</span></div>
      <div class="campo"><label for="color">Color</label>
        <input id="color" name="color" type="text" maxlength="40" value="<c:out value='${mascota.color}'/>"></div>
      <div class="campo"><label for="pesoKg">Peso referencial (kg)</label>
        <input id="pesoKg" name="pesoKg" type="number" step="0.01" min="0.01" max="150" value="<c:out value='${mascota.pesoKg}'/>">
        <span class="ayuda">Mayor que 0 y hasta 150 kg.</span></div>
      <div class="campo"><label for="microchip">Microchip</label>
        <input id="microchip" name="microchip" type="text" inputmode="numeric" pattern="[0-9 ]{15,19}" maxlength="19" value="<c:out value='${mascota.microchip}'/>">
        <span class="ayuda">15 dígitos (ISO 11784); único por mascota.</span></div>
      <div class="campo ancho"><label for="alertas">Alertas clínicas</label>
        <textarea id="alertas" name="alertas" maxlength="300"><c:out value="${mascota.alertas}"/></textarea>
        <span class="ayuda">Alergias, agresividad o condiciones crónicas. Se muestran destacadas al personal y no se publican en el portal del cliente.</span></div>
      <div class="campo ancho">
        <label class="casilla"><input type="checkbox" name="esterilizado" ${mascota.esterilizado ? 'checked' : ''}> Esterilizado(a)</label>
      </div>
    </div>
    <div class="acciones-form">
      <button type="submit" class="btn btn-primario">${edicion ? 'Guardar cambios' : 'Registrar mascota'}</button>
      <a class="btn btn-secundario" href="${ctx}${edicion ? '/mascotas/ver?id='.concat(mascota.idMascota) : '/clientes/ver?id='.concat(cliente.idCliente)}">Cancelar</a>
    </div>
  </form>
</section>
<script src="${ctx}/static/js/modulo-mascotas.js"></script>
  </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
