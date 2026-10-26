<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Ventas y pagos"/><c:set var="seccion" value="ventas"/><c:set var="cssExtra" value="modulo-ventas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P08 · Ventas del turno y del día con estado y total (RF-26, RF-29, RF-31)</p>
<c:choose>
  <c:when test="${not empty turno}">
    <div class="aviso aviso-exito" role="status"><strong>Caja abierta:</strong> turno n.° <c:out value="${turno.idTurno}"/> desde las
      <c:out value="${vs:hora(turno.apertura)}"/> del <c:out value="${vs:fecha(turno.apertura)}"/>. <a href="${ctx}/caja">Ver caja</a></div>
  </c:when>
  <c:otherwise>
    <div class="aviso aviso-aviso" role="status"><strong>Sin caja abierta:</strong> para registrar ventas primero
      <a href="${ctx}/caja">abra su turno de caja</a> (RF-30).</div>
  </c:otherwise>
</c:choose>
<div class="rejilla rejilla-4">
  <div class="kpi destacado"><div class="etiqueta">Confirmadas</div><div class="valor"><c:out value="${vs:soles(totalConfirmado)}"/></div>
    <div class="detalle"><c:out value="${numConfirmadas}"/> comprobante(s) en el periodo</div></div>
  <div class="kpi"><div class="etiqueta">Pendientes</div><div class="valor"><c:out value="${numPendientes}"/></div>
    <div class="detalle">impiden cerrar la caja hasta confirmarlas</div></div>
  <div class="kpi"><div class="etiqueta">Anuladas</div><div class="valor"><c:out value="${numAnuladas}"/></div>
    <div class="detalle">el documento se conserva (RN-09)</div></div>
  <div class="kpi"><div class="etiqueta">Alcance</div><div class="valor valor-medio">${sesion.rol == 'ADMINISTRADOR' ? 'Todas las cajas' : 'Mis turnos'}</div>
    <div class="detalle">${sesion.rol == 'ADMINISTRADOR' ? 'el administrador ve a todos los cajeros' : 'ventas de sus turnos de caja'}</div></div>
</div>
<section class="tarjeta mt18">
  <form method="get" action="${ctx}/ventas" class="barra-herramientas">
    <div class="campo"><label for="desde">Desde</label><input id="desde" name="desde" type="date" value="<c:out value='${desde}'/>"></div>
    <div class="campo"><label for="hasta">Hasta</label><input id="hasta" name="hasta" type="date" value="<c:out value='${hasta}'/>"></div>
    <div class="campo"><label for="estado">Estado</label>
      <select id="estado" name="estado">
        <option value="">Todos</option>
        <c:forEach var="e" items="${estados}"><option value="${e}" ${estado == e ? 'selected' : ''}><c:out value="${vs:etiqueta(e)}"/></option></c:forEach>
      </select></div>
    <button type="submit" class="btn btn-secundario">Filtrar</button>
    <span class="crece"></span>
    <c:if test="${not empty turno and sesion.puede('VENTA_PROCESAR')}">
      <a class="btn btn-primario" href="${ctx}/ventas/nueva">+ Nueva venta / cobrar atención</a>
    </c:if>
  </form>
  <c:choose>
    <c:when test="${empty ventas}"><p class="vacio">No hay ventas en el periodo seleccionado.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>Comprobante</th><th>Fecha y hora</th><th>Cliente</th><th>Mascota</th><th>Cajero</th><th>Estado</th><th class="num">Total</th><th><span class="solo-lector">Acciones</span></th></tr></thead>
        <tbody>
        <c:forEach var="v" items="${ventas}">
          <tr>
            <td><strong><c:out value="${empty v.comprobante() ? 'Venta n.° '.concat(v.idVenta) : v.comprobante()}"/></strong>
              <c:if test="${not empty v.idConsulta}"><br><small>Atención (consulta n.° <c:out value="${v.idConsulta}"/>)</small></c:if></td>
            <td><c:out value="${vs:fechaHora(v.fechaHora)}"/></td>
            <%-- RNF-15: en listados el documento se enmascara --%>
            <td><c:out value="${v.cliente}"/><br><small><c:out value="${vs:enmascarar(v.documentoCliente)}"/></small></td>
            <td><c:out value="${v.mascota}"/></td>
            <td><c:out value="${v.cajero}"/></td>
            <td><span class="${vs:claseEstado(v.estado)}"><c:out value="${vs:etiqueta(v.estado)}"/></span></td>
            <td class="num"><strong><c:out value="${vs:soles(v.total)}"/></strong></td>
            <td class="acciones">
              <c:choose>
                <c:when test="${v.esPendiente()}"><a class="btn btn-primario btn-chico" href="${ctx}/ventas/editar?id=${v.idVenta}">Continuar</a></c:when>
                <c:otherwise><a class="btn btn-secundario btn-chico" href="${ctx}/ventas/comprobante?id=${v.idVenta}">Comprobante</a></c:otherwise>
              </c:choose>
            </td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
    </c:otherwise>
  </c:choose>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
