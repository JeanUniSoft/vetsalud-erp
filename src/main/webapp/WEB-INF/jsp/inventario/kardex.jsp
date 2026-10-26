<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Kardex de inventario"/><c:set var="seccion" value="inventario"/><c:set var="cssExtra" value="modulo-inventario"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P07 · Kardex por producto, lote y periodo; el saldo coincide con el stock (RF-22)</p>
<c:set var="pestana" value="kardex"/>
<%@ include file="/WEB-INF/jsp/inventario/pestanas.jspf" %>
<section class="tarjeta">
  <form method="get" action="${ctx}/inventario/kardex" class="barra-herramientas">
    <div class="campo crece"><label for="producto">Producto</label>
      <select id="producto" name="producto">
        <option value="">Todos</option>
        <c:forEach var="p" items="${productos}">
          <option value="${p.idProducto}" ${productoSel == p.idProducto ? 'selected' : ''}><c:out value="${p.codigo}"/> · <c:out value="${p.nombre}"/></option>
        </c:forEach>
      </select></div>
    <div class="campo"><label for="lote">Lote</label>
      <select id="lote" name="lote" ${empty lotes ? 'disabled' : ''}>
        <option value="">${empty lotes ? 'Elija un producto' : 'Todos'}</option>
        <c:forEach var="l" items="${lotes}">
          <option value="${l.idLote}" ${loteSel == l.idLote ? 'selected' : ''}><c:out value="${l.codigoLote}"/></option>
        </c:forEach>
      </select></div>
    <div class="campo"><label for="desde">Desde</label><input id="desde" name="desde" type="date" value="<c:out value='${desde}'/>"></div>
    <div class="campo"><label for="hasta">Hasta</label><input id="hasta" name="hasta" type="date" value="<c:out value='${hasta}'/>"></div>
    <button type="submit" class="btn btn-primario">Consultar</button>
  </form>
  <p class="totales-kardex">
    <span>Movimientos: <strong><c:out value="${fn:length(movimientos)}"/></strong></span>
    <span>Entradas: <strong><c:out value="${vs:numero(totalEntradas)}"/></strong></span>
    <span>Salidas: <strong><c:out value="${vs:numero(totalSalidas)}"/></strong></span>
    <c:if test="${not empty desde or not empty hasta}"><span>Periodo: <c:out value="${empty desde ? 'inicio' : vs:fecha(desde)}"/> – <c:out value="${empty hasta ? 'hoy' : vs:fecha(hasta)}"/></span></c:if>
  </p>
  <c:choose>
    <c:when test="${empty movimientos}"><p class="vacio">No hay movimientos para los filtros elegidos.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>Fecha y hora</th><th>Producto</th><th>Lote</th><th>Vence</th><th>Tipo</th><th class="num">Entrada</th><th class="num">Salida</th><th class="num">Saldo lote</th><th>Motivo</th><th>Usuario</th></tr></thead>
        <tbody>
        <c:forEach var="m" items="${movimientos}">
          <tr><td><c:out value="${vs:fechaHora(m.fechaHora)}"/></td>
            <td><c:out value="${m.codigo}"/> · <c:out value="${m.producto}"/></td><td><c:out value="${m.codigoLote}"/></td>
            <td><c:out value="${vs:fecha(m.fechaVencimiento)}"/></td>
            <td><span class="${not empty m.entrada ? 'badge badge-ok' : 'badge badge-aviso'}"><c:out value="${vs:etiqueta(m.tipo)}"/></span></td>
            <td class="num"><c:if test="${not empty m.entrada}"><c:out value="${vs:numero(m.entrada)}"/></c:if></td>
            <td class="num"><c:if test="${not empty m.salida}"><c:out value="${vs:numero(m.salida)}"/></c:if></td>
            <td class="num"><strong><c:out value="${vs:numero(m.saldoLote)}"/></strong></td>
            <td><small><c:out value="${m.motivo}"/></small></td><td><c:out value="${m.usuario}"/></td></tr>
        </c:forEach>
        </tbody>
      </table></div>
      <c:if test="${fn:length(movimientos) >= limite}"><p class="ayuda">Se muestran los <c:out value="${limite}"/> movimientos más recientes del filtro; acote el periodo para ver los anteriores.</p></c:if>
    </c:otherwise>
  </c:choose>
</section>

<c:if test="${not empty producto}">
<section class="tarjeta">
  <h2>Conciliación de <c:out value="${producto.nombre}"/>: saldo del kardex frente al stock</h2>
  <table class="tabla">
    <thead><tr><th>Lote</th><th>Vence</th><th class="num">Stock del lote</th><th class="num">Saldo según kardex</th><th>Resultado</th></tr></thead>
    <tbody>
    <c:forEach var="k" items="${conciliacion}">
      <tr><td><c:out value="${k.codigoLote}"/></td><td><c:out value="${vs:fecha(k.fechaVencimiento)}"/></td>
        <td class="num"><c:out value="${vs:numero(k.stockActual)}"/></td><td class="num"><c:out value="${vs:numero(k.saldoKardex)}"/></td>
        <td><span class="${k.cuadra() ? 'badge badge-ok' : 'badge badge-error'}">${k.cuadra() ? 'Cuadra' : 'Descuadre'}</span></td></tr>
    </c:forEach>
    </tbody>
  </table>
  <p class="ayuda">El saldo histórico suma todos los movimientos del lote (sin filtro de periodo). El stock solo cambia por el kardex (trigger fn_kardex_aplicar).</p>
</section>
</c:if>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
