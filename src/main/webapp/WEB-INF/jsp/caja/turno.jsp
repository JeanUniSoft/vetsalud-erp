<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Detalle de turno de caja"/><c:set var="seccion" value="caja"/><c:set var="cssExtra" value="modulo-ventas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P09 · Detalle del turno: cobros por medio de pago, devoluciones y cuadre (RF-30)</p>
<nav class="pestanas no-imprimir" aria-label="Secciones de caja">
  <a href="${ctx}/caja">Turno actual</a>
  <a href="${ctx}/caja/historial">Historial de turnos</a>
  <a href="${ctx}/ventas">Ventas</a>
</nav>
<c:if test="${not empty resumen}">
<c:set var="r" value="${resumen}"/><c:set var="t" value="${resumen.turno}"/>
<section class="tarjeta">
  <div class="cabecera-tarjeta">
    <h2>Turno n.° <c:out value="${t.idTurno}"/> · <c:out value="${t.cajero}"/> <span class="${vs:claseEstado(t.estado)}"><c:out value="${vs:etiqueta(t.estado)}"/></span></h2>
    <button type="button" class="btn btn-secundario btn-chico no-imprimir" data-imprimir>Imprimir arqueo</button>
  </div>
  <div class="rejilla rejilla-2">
    <dl class="cuadre">
      <div><dt>Apertura</dt><dd><c:out value="${vs:fechaHora(t.apertura)}"/></dd></div>
      <div><dt>Cierre</dt><dd><c:out value="${empty t.cierre ? '—' : vs:fechaHora(t.cierre)}"/></dd></div>
      <div><dt>Ventas confirmadas</dt><dd><c:out value="${r.ventasConfirmadas}"/> · <c:out value="${vs:soles(r.totalConfirmado)}"/></dd></div>
      <div><dt>Descuentos</dt><dd><c:out value="${vs:soles(r.descuentos)}"/></dd></div>
      <div><dt>Devoluciones (anuladas)</dt><dd><c:out value="${r.ventasAnuladas}"/> · <c:out value="${vs:soles(r.totalAnulado)}"/></dd></div>
      <div><dt>Ventas pendientes</dt><dd><c:out value="${r.ventasPendientes}"/></dd></div>
    </dl>
    <dl class="cuadre">
      <div><dt>Monto inicial</dt><dd><c:out value="${vs:soles(t.montoInicial)}"/></dd></div>
      <div><dt>+ Efectivo cobrado</dt><dd><c:out value="${vs:soles(r.efectivoCobrado())}"/></dd></div>
      <div class="total"><dt>= Efectivo esperado</dt><dd><c:out value="${vs:soles(empty t.efectivoEsperado ? r.efectivoEsperado : t.efectivoEsperado)}"/></dd></div>
      <c:if test="${not t.abierto()}">
        <div><dt>Efectivo contado</dt><dd><c:out value="${vs:soles(t.efectivoContado)}"/></dd></div>
        <div class="total"><dt>Diferencia</dt><dd><span class="${t.diferencia.signum() == 0 ? 'badge badge-ok' : 'badge badge-error'}">${t.diferencia.signum() < 0 ? 'Faltante ' : (t.diferencia.signum() > 0 ? 'Sobrante ' : 'Cuadra ')}<c:out value="${vs:soles(t.diferencia.abs())}"/></span></dd></div>
        <div><dt>Observación</dt><dd><c:out value="${empty t.observacion ? '—' : t.observacion}"/></dd></div>
      </c:if>
    </dl>
  </div>
</section>
<div class="rejilla rejilla-4">
  <c:forEach var="m" items="${r.medios}">
    <div class="kpi"><div class="etiqueta"><c:out value="${vs:etiqueta(m.medio)}"/></div>
      <div class="valor valor-medio"><c:out value="${vs:soles(m.monto)}"/></div><div class="detalle"><c:out value="${m.pagos}"/> pago(s)</div></div>
  </c:forEach>
</div>

<c:if test="${t.abierto() and sesion.puede('CAJA_CERRAR') and (sesion.rol == 'ADMINISTRADOR' or sesion.idUsuario == t.idCajero)}">
<section class="tarjeta mt18 no-imprimir">
  <h2>Cerrar este turno</h2>
  <form method="post" action="${ctx}/caja" class="formulario" data-confirmar="¿Cerrar el turno n.° ${t.idTurno}?">
    <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
    <input type="hidden" name="accion" value="cerrar"><input type="hidden" name="turno" value="${t.idTurno}">
    <div class="campos">
      <div class="campo"><label for="contado">Efectivo contado (S/) <span class="req">*</span></label>
        <input id="contado" name="contado" type="number" min="0" step="0.01" required>
        <span class="ayuda">Esperado: <c:out value="${vs:soles(r.efectivoEsperado)}"/>.</span></div>
      <div class="campo ancho"><label for="observacion">Observación</label>
        <textarea id="observacion" name="observacion" maxlength="300" placeholder="Obligatoria si hay diferencia (RN-10)"></textarea></div>
    </div>
    <div class="acciones-form"><button type="submit" class="btn btn-peligro" ${r.ventasPendientes > 0 ? 'disabled' : ''}>Cerrar turno</button></div>
  </form>
</section>
</c:if>

<section class="tarjeta mt18">
  <h2>Ventas del turno</h2>
  <c:choose>
    <c:when test="${empty ventas}"><p class="vacio">El turno no tiene ventas.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>Comprobante</th><th>Fecha y hora</th><th>Cliente</th><th>Estado</th><th class="num">Descuento</th><th class="num">Total</th><th class="no-imprimir"><span class="solo-lector">Acción</span></th></tr></thead>
        <tbody>
        <c:forEach var="vt" items="${ventas}">
          <tr><td><strong><c:out value="${empty vt.comprobante() ? 'Venta n.° '.concat(vt.idVenta) : vt.comprobante()}"/></strong></td>
            <td><c:out value="${vs:fechaHora(vt.fechaHora)}"/></td><td><c:out value="${vt.cliente}"/></td>
            <td><span class="${vs:claseEstado(vt.estado)}"><c:out value="${vs:etiqueta(vt.estado)}"/></span>
              <c:if test="${vt.esAnulada()}"><br><small><c:out value="${vt.motivoAnulacion}"/></small></c:if></td>
            <td class="num"><c:out value="${vs:soles(vt.descuento)}"/></td>
            <td class="num"><c:out value="${vs:soles(vt.total)}"/></td>
            <td class="acciones no-imprimir"><a class="btn btn-secundario btn-chico" href="${ctx}${vt.esPendiente() ? '/ventas/editar?id=' : '/ventas/comprobante?id='}${vt.idVenta}">Ver</a></td></tr>
        </c:forEach>
        </tbody>
      </table></div>
    </c:otherwise>
  </c:choose>
</section>
</c:if>
<p class="no-imprimir"><a class="btn btn-secundario" href="${ctx}/caja/historial">Volver al historial</a></p>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
