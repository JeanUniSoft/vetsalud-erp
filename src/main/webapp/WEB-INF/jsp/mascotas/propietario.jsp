<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Cambio de propietario"/><c:set var="seccion" value="clientes"/><c:set var="cssExtra" value="modulo-mascotas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<p class="etiqueta-pantalla">P03 · Cambio de propietario como operación auditada (RF-08, RN-02)</p>
<c:choose>
  <c:when test="${empty mascota}">
    <section class="tarjeta"><p class="vacio">No se encontró la mascota solicitada.</p>
      <a class="btn btn-secundario" href="${ctx}/mascotas">Buscar mascotas</a></section>
  </c:when>
  <c:otherwise>
    <section class="tarjeta">
      <div class="cabecera-tarjeta">
        <h2>Transferir a <c:out value="${mascota.nombre}"/></h2>
        <a class="btn btn-secundario" href="${ctx}/mascotas/ver?id=${mascota.idMascota}">← Volver a la ficha</a>
      </div>
      <dl class="ficha">
        <dt>Mascota</dt><dd><c:out value="${mascota.nombre}"/> · <c:out value="${mascota.especie}"/> (<c:out value="${mascota.raza}"/>)</dd>
        <dt>Propietario actual</dt><dd><a href="${ctx}/clientes/ver?id=${mascota.idCliente}"><c:out value="${mascota.propietario}"/></a></dd>
      </dl>
      <div class="separador"></div>
      <h3>1. Buscar al nuevo propietario</h3>
      <form method="get" action="${ctx}/mascotas/propietario" class="barra-herramientas">
        <input type="hidden" name="id" value="${mascota.idMascota}">
        <div class="campo crece">
          <label for="q">Cliente destino</label>
          <input id="q" name="q" type="search" value="<c:out value='${q}'/>" placeholder="DNI, nombre, teléfono o correo" required>
        </div>
        <button type="submit" class="btn btn-secundario">Buscar</button>
      </form>
      <p class="ayuda">Solo se listan clientes activos y distintos del propietario actual (RN-02). Si el nuevo propietario no existe, regístrelo primero en <a href="${ctx}/clientes/nuevo">Nuevo cliente</a>.</p>

      <c:if test="${not empty q}">
        <c:choose>
          <c:when test="${empty candidatos}">
            <p class="vacio">No se encontraron clientes activos para «<c:out value="${q}"/>».</p>
          </c:when>
          <c:otherwise>
            <h3 class="mt18">2. Seleccionar y registrar el motivo</h3>
            <form method="post" action="${ctx}/mascotas/propietario" class="formulario"
                  data-confirmar="¿Confirma el cambio de propietario? Quedará registrado en el historial con su usuario.">
              <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
              <input type="hidden" name="id" value="${mascota.idMascota}">
              <input type="hidden" name="q" value="<c:out value='${q}'/>">
              <fieldset class="limpio">
                <legend>Nuevo propietario <span class="req">*</span></legend>
                <div class="tabla-scroll"><table class="tabla candidatos">
                  <thead><tr><th><span class="suave">Elegir</span></th><th>Cliente</th><th>Documento</th><th>Teléfono</th><th>Distrito</th></tr></thead>
                  <tbody>
                  <c:forEach var="c" items="${candidatos}">
                    <tr>
                      <td><input type="radio" id="cli-${c.idCliente}" name="idClienteNuevo" value="${c.idCliente}" required></td>
                      <td><label for="cli-${c.idCliente}"><c:out value="${c.nombreCompleto()}"/></label></td>
                      <td><c:out value="${c.tipoDocumento}"/> <c:out value="${vs:enmascarar(c.numDocumento)}"/></td>
                      <td><c:out value="${c.telefono}"/></td>
                      <td><c:out value="${c.distrito}"/></td>
                    </tr>
                  </c:forEach>
                  </tbody>
                </table></div>
              </fieldset>
              <div class="campo ancho"><label for="motivo">Motivo del cambio <span class="req">*</span></label>
                <textarea id="motivo" name="motivo" required minlength="10" maxlength="200" placeholder="Ej.: Adopción formal acordada entre ambas partes"></textarea>
                <span class="ayuda">Mínimo 10 caracteres. El cambio, el historial y la reasignación de recordatorios pendientes se guardan en una sola transacción.</span></div>
              <div class="acciones-form">
                <button type="submit" class="btn btn-primario">Registrar cambio de propietario</button>
                <a class="btn btn-secundario" href="${ctx}/mascotas/ver?id=${mascota.idMascota}">Cancelar</a>
              </div>
            </form>
          </c:otherwise>
        </c:choose>
      </c:if>
    </section>
  </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
