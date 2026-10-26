<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Nueva venta"/><c:set var="seccion" value="ventas"/><c:set var="cssExtra" value="modulo-ventas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P08 · Venta vinculada al cliente, la mascota y la consulta cuando corresponde (RF-26)</p>
<c:if test="${empty turno}">
  <div class="aviso aviso-aviso" role="alert"><strong>Abra su caja primero:</strong> las ventas nacen en el turno abierto del cajero.
    <a class="btn btn-primario btn-chico" href="${ctx}/caja">Ir a caja</a></div>
</c:if>

<section class="tarjeta">
  <div class="cabecera-tarjeta">
    <h2>Cobrar atención</h2>
    <span class="ayuda">Consultas firmadas sin venta: se cargan la consulta y sus procedimientos; la receta se ofrece con lote FEFO.</span>
  </div>
  <c:choose>
    <c:when test="${empty porCobrar}"><p class="vacio">No hay atenciones firmadas pendientes de cobro.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>Fecha</th><th>Mascota</th><th>Propietario</th><th>Veterinario</th><th>Motivo y diagnóstico</th><th class="num">Proced.</th><th>Receta</th><th><span class="solo-lector">Acción</span></th></tr></thead>
        <tbody>
        <c:forEach var="cpc" items="${porCobrar}">
          <tr>
            <td><c:out value="${vs:fechaHora(cpc.fechaHora)}"/></td>
            <td><strong><c:out value="${cpc.mascota}"/></strong></td>
            <td><c:out value="${cpc.cliente}"/></td>
            <td><c:out value="${cpc.veterinario}"/></td>
            <td><c:out value="${cpc.motivo}"/><br><small><c:out value="${cpc.diagnostico}"/></small></td>
            <td class="num"><c:out value="${cpc.procedimientos}"/></td>
            <td>${cpc.tieneReceta ? '<span class="badge badge-neutro">Con receta</span>' : '—'}</td>
            <td class="acciones">
              <c:if test="${not empty turno}">
                <form method="post" action="${ctx}/ventas/nueva" class="en-linea">
                  <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                  <input type="hidden" name="accion" value="cobrar"><input type="hidden" name="consulta" value="${cpc.idConsulta}">
                  <button type="submit" class="btn btn-primario btn-chico">Cobrar</button>
                </form>
              </c:if>
            </td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
    </c:otherwise>
  </c:choose>
</section>

<section class="tarjeta">
  <h2>Venta de mostrador</h2>
  <form method="get" action="${ctx}/ventas/nueva" class="barra-herramientas">
    <div class="campo crece"><label for="q">1. Buscar cliente</label>
      <input id="q" name="q" type="search" value="<c:out value='${q}'/>" placeholder="DNI, RUC, nombre, teléfono o correo" required></div>
    <button type="submit" class="btn btn-secundario">Buscar</button>
    <c:if test="${sesion.puede('CLIENTE_GESTIONAR')}"><a class="btn btn-secundario" href="${ctx}/clientes/nuevo">+ Registrar cliente</a></c:if>
  </form>
  <c:if test="${not empty q}">
    <c:choose>
      <c:when test="${empty clientes}"><p class="vacio">No se encontraron clientes para «<c:out value="${q}"/>».</p></c:when>
      <c:otherwise>
        <table class="tabla">
          <thead><tr><th>Cliente</th><th>Documento</th><th>Teléfono</th><th class="num">Mascotas</th><th><span class="solo-lector">Acción</span></th></tr></thead>
          <tbody>
          <c:forEach var="cl" items="${clientes}">
            <tr>
              <td><strong><c:out value="${cl.nombreCompleto()}"/></strong></td>
              <td><c:out value="${cl.tipoDocumento}"/> <c:out value="${vs:enmascarar(cl.numDocumento)}"/></td>
              <td><c:out value="${cl.telefono}"/></td>
              <td class="num"><c:out value="${cl.numMascotas}"/></td>
              <td class="acciones"><a class="btn btn-secundario btn-chico" href="${ctx}/ventas/nueva?cliente=${cl.idCliente}">Elegir</a></td>
            </tr>
          </c:forEach>
          </tbody>
        </table>
      </c:otherwise>
    </c:choose>
  </c:if>

  <c:if test="${not empty cliente}">
    <div class="separador"></div>
    <h3>2. Datos de la venta para <c:out value="${cliente.nombreCompleto()}"/></h3>
    <c:if test="${not cliente.activo}"><p class="aviso aviso-error">El cliente está inactivo: no se le pueden registrar ventas.</p></c:if>
    <form method="post" action="${ctx}/ventas/nueva" class="formulario">
      <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
      <input type="hidden" name="accion" value="crear"><input type="hidden" name="cliente" value="${cliente.idCliente}">
      <div class="campos">
        <div class="campo"><label for="mascota">Mascota (opcional)</label>
          <select id="mascota" name="mascota">
            <option value="">Sin mascota (venta de mostrador)</option>
            <c:forEach var="m" items="${mascotas}"><option value="${m.idMascota}"><c:out value="${m.nombre}"/> · <c:out value="${m.especie}"/>, <c:out value="${m.raza}"/></option></c:forEach>
          </select></div>
        <div class="campo"><label for="consulta">Consulta a cobrar (opcional)</label>
          <select id="consulta" name="consulta">
            <option value="">Ninguna</option>
            <c:forEach var="cc" items="${consultasCliente}"><option value="${cc.idConsulta}"><c:out value="${vs:fecha(cc.fechaHora)}"/> · <c:out value="${cc.mascota}"/> · <c:out value="${cc.motivo}"/></option></c:forEach>
          </select>
          <span class="ayuda">Si elige una consulta firmada se cargan sus servicios y la mascota de la atención.</span></div>
      </div>
      <div class="acciones-form">
        <button type="submit" class="btn btn-primario" ${empty turno or not cliente.activo ? 'disabled' : ''}>Crear venta</button>
        <a class="btn btn-secundario" href="${ctx}/ventas">Cancelar</a>
      </div>
    </form>
  </c:if>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
