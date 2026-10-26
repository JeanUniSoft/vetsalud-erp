<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Clientes y mascotas"/><c:set var="seccion" value="clientes"/><c:set var="cssExtra" value="modulo-mascotas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P03 · Ficha del cliente y sus mascotas (RF-06, RF-07, RN-02)</p>
<c:choose>
  <c:when test="${empty cliente}">
    <section class="tarjeta">
      <p class="vacio">No se encontró el cliente solicitado.</p>
      <a class="btn btn-secundario" href="${ctx}/clientes">Volver a la búsqueda</a>
    </section>
  </c:when>
  <c:otherwise>
    <c:set var="gestiona" value="${sesion.puede('CLIENTE_GESTIONAR')}"/>
    <section class="tarjeta">
      <div class="cabecera-tarjeta">
        <h2 class="titulo-mascota"><c:out value="${cliente.nombreCompleto()}"/>
          <span class="${cliente.activo ? 'badge badge-ok' : 'badge badge-error'}">${cliente.activo ? 'Activo' : 'Inactivo'}</span></h2>
        <div class="acciones-form no-imprimir">
          <a class="btn btn-secundario" href="${ctx}/clientes">← Clientes</a>
          <c:if test="${gestiona}">
            <a class="btn btn-secundario" href="${ctx}/clientes/editar?id=${cliente.idCliente}">Editar datos</a>
            <c:if test="${cliente.activo}">
              <a class="btn btn-primario" href="${ctx}/mascotas/nueva?cliente=${cliente.idCliente}">+ Mascota</a>
            </c:if>
          </c:if>
        </div>
      </div>
      <%-- RNF-15: el documento completo solo se muestra en la ficha; en listados va enmascarado --%>
      <dl class="ficha">
        <dt>Documento</dt><dd><c:out value="${cliente.tipoDocumento}"/> <c:out value="${cliente.numDocumento}"/></dd>
        <dt>Teléfono</dt><dd><c:out value="${cliente.telefono}"/></dd>
        <dt>Correo</dt><dd><c:out value="${empty cliente.correo ? '—' : cliente.correo}"/></dd>
        <dt>Dirección</dt><dd><c:choose><c:when test="${empty cliente.direccion and empty cliente.distrito}">—</c:when>
          <c:otherwise><c:out value="${cliente.direccion}"/><c:if test="${not empty cliente.direccion and not empty cliente.distrito}"> · </c:if><c:out value="${cliente.distrito}"/></c:otherwise></c:choose></dd>
        <dt>Recordatorios</dt>
        <dd><span class="${cliente.aceptaRecordatorios ? 'badge badge-ok' : 'badge badge-neutro'}">${cliente.aceptaRecordatorios ? 'Autoriza recordatorios por correo' : 'No autoriza recordatorios'}</span> <small>(consentimiento RF-37)</small></dd>
        <dt>Registrado el</dt><dd><c:out value="${vs:fechaHora(cliente.creadoEn)}"/></dd>
      </dl>
      <c:if test="${not cliente.activo}">
        <p class="aviso aviso-aviso mt18">Cliente inactivo: no se le pueden registrar mascotas nuevas ni reactivar las existentes (RN-02).</p>
      </c:if>
    </section>

    <section class="tarjeta">
      <div class="cabecera-tarjeta">
        <h2>Mascotas <small>(<c:out value="${fn:length(mascotas)}"/>)</small></h2>
      </div>
      <c:choose>
        <c:when test="${empty mascotas}">
          <p class="vacio">El cliente aún no tiene mascotas registradas.<c:if test="${gestiona and cliente.activo}"> Use «+ Mascota» para registrar la primera.</c:if></p>
        </c:when>
        <c:otherwise>
          <div class="tabla-scroll"><table class="tabla">
            <thead><tr><th>Mascota</th><th>Especie / raza</th><th>Sexo</th><th>Edad</th><th class="num">Peso</th><th>Estado</th><th>Alertas clínicas</th><th></th></tr></thead>
            <tbody>
            <c:forEach var="m" items="${mascotas}">
              <tr>
                <td><strong><c:out value="${m.nombre}"/></strong><c:if test="${not empty m.microchip}"><br><small>Chip <c:out value="${m.microchip}"/></small></c:if></td>
                <td><c:out value="${m.especie}"/><br><small><c:out value="${m.raza}"/></small></td>
                <td><c:out value="${m.sexoTexto()}"/></td>
                <td><c:out value="${vs:edad(m.fechaNacimiento)}"/></td>
                <td class="num"><c:if test="${not empty m.pesoKg}"><c:out value="${vs:numero(m.pesoKg)}"/> kg</c:if></td>
                <td><span class="${vs:claseEstado(m.estado)}"><c:out value="${vs:etiqueta(m.estado)}"/></span></td>
                <td><c:choose>
                  <c:when test="${m.tieneAlertas()}"><div class="nota-clinica"><strong>⚠</strong> <c:out value="${m.alertas}"/></div></c:when>
                  <c:otherwise><small class="suave">Sin alertas</small></c:otherwise>
                </c:choose></td>
                <td class="acciones"><a class="btn btn-secundario btn-chico" href="${ctx}/mascotas/ver?id=${m.idMascota}">Ver ficha</a></td>
              </tr>
            </c:forEach>
            </tbody>
          </table></div>
        </c:otherwise>
      </c:choose>
    </section>
  </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
