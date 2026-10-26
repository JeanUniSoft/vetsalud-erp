<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Comprobante de venta"/><c:set var="seccion" value="ventas"/><c:set var="cssExtra" value="modulo-ventas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="v" value="${c.venta}"/>
<p class="etiqueta-pantalla no-imprimir">P08 · Comprobante interno con serie, correlativo, detalle, totales y pagos (RF-29)</p>
<div class="barra-herramientas no-imprimir">
  <button type="button" class="btn btn-primario" data-imprimir>Imprimir</button>
  <a class="btn btn-secundario" href="${ctx}/ventas">Volver a ventas</a>
  <c:if test="${sesion.puede('VENTA_PROCESAR')}"><a class="btn btn-secundario" href="${ctx}/ventas/nueva">Nueva venta</a></c:if>
</div>
<section class="tarjeta comprobante" aria-label="Comprobante ${v.comprobante()}">
  <header class="comprobante-cabecera">
    <h2>VetSalud Integral S.A.C.</h2>
    <p>RUC 20612345678</p>
    <p>Av. Primavera 1450, Santiago de Surco, Lima</p>
    <p>Clínica veterinaria · Tel. 01 555 0101</p>
    <div class="comprobante-numero">TICKET <c:out value="${v.comprobante()}"/></div>
    <p>Comprobante interno de venta</p>
  </header>
  <c:if test="${v.esAnulada()}">
    <div class="sello-anulado" role="alert">ANULADO</div>
    <p class="nota-clinica">Anulado el <c:out value="${vs:fechaHora(v.anuladaEn)}"/>. Motivo: <c:out value="${v.motivoAnulacion}"/>.
      El correlativo se conserva y el stock de los productos volvió al kardex (RN-09, RN-11).</p>
  </c:if>
  <dl class="ficha">
    <dt>Fecha</dt><dd><c:out value="${vs:fechaHora(v.fechaHora)}"/></dd>
    <dt>Cliente</dt><dd><c:out value="${v.cliente}"/></dd>
    <dt>Documento</dt><dd><c:out value="${v.documentoCliente}"/></dd>
    <c:if test="${not empty v.mascota}"><dt>Mascota</dt><dd><c:out value="${v.mascota}"/></dd></c:if>
    <c:if test="${not empty v.idConsulta}"><dt>Atención</dt><dd>Consulta n.° <c:out value="${v.idConsulta}"/></dd></c:if>
    <dt>Cajero</dt><dd><c:out value="${v.cajero}"/> · turno n.° <c:out value="${v.idTurno}"/></dd>
  </dl>
  <table class="tabla mt18">
    <thead><tr><th class="num">Cant.</th><th>Descripción</th><th class="num">P. unit.</th><th class="num">Importe</th></tr></thead>
    <tbody>
    <c:forEach var="l" items="${c.lineas}">
      <tr><td class="num"><c:out value="${vs:numero(l.cantidad)}"/></td>
        <td><c:out value="${l.descripcion}"/><c:if test="${l.esProducto()}"><br><small>Lote <c:out value="${l.codigoLote}"/><c:if test="${not empty l.fechaVencimiento}"> · vence <c:out value="${vs:fecha(l.fechaVencimiento)}"/></c:if></small></c:if></td>
        <td class="num"><c:out value="${vs:soles(l.precioUnitario)}"/></td>
        <td class="num"><c:out value="${vs:soles(l.importe)}"/></td></tr>
    </c:forEach>
    </tbody>
  </table>
  <dl class="resumen-filas comprobante-totales">
    <div class="resumen-fila"><dt>Subtotal</dt><dd><c:out value="${vs:soles(v.subtotal)}"/></dd></div>
    <c:if test="${v.descuento.signum() > 0}">
      <div class="resumen-fila"><dt>Descuento<c:if test="${not empty v.autorizaDescuento}"> <small>(autorizado por <c:out value="${v.autorizaDescuento}"/>)</small></c:if></dt>
        <dd>− <c:out value="${vs:soles(v.descuento)}"/></dd></div>
    </c:if>
    <div class="resumen-fila resumen-total"><dt>TOTAL</dt><dd class="total-grande"><c:out value="${vs:soles(v.total)}"/></dd></div>
  </dl>
  <h3 class="mt18">Pagos</h3>
  <ul class="pagos-lista">
    <c:forEach var="pg" items="${c.pagos}">
      <li><span><c:out value="${vs:etiqueta(pg.medio)}"/><c:if test="${not empty pg.referencia}"> <small>· op. <c:out value="${pg.referencia}"/></small></c:if></span>
        <span><c:out value="${vs:soles(pg.monto)}"/></span></li>
    </c:forEach>
  </ul>
  <footer class="comprobante-pie">Gracias por confiar en VetSalud. Este comprobante interno no reemplaza a la boleta o factura electrónica.</footer>
</section>

<c:if test="${v.esConfirmada() and sesion.puede('VENTA_ANULAR')}">
<section class="tarjeta no-imprimir">
  <h2>Anular venta (RF-31)</h2>
  <c:choose>
    <c:when test="${v.turnoAbierto}">
      <form method="post" action="${ctx}/ventas/anular" class="formulario" data-confirmar="¿Anular el comprobante ${v.comprobante()}? El documento se conserva y el stock vuelve al inventario.">
        <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
        <input type="hidden" name="id" value="${v.idVenta}">
        <div class="campo"><label for="motivo">Motivo de la anulación <span class="req">*</span></label>
          <textarea id="motivo" name="motivo" required minlength="5" maxlength="180" placeholder="Ej. Cliente desistió de la compra; error en el producto"></textarea></div>
        <div class="acciones-form"><button type="submit" class="btn btn-peligro">Anular venta</button></div>
      </form>
    </c:when>
    <c:otherwise><p class="vacio">El turno de esta venta ya se cerró: no se puede anular (RN-09).</p></c:otherwise>
  </c:choose>
</section>
</c:if>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
