<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Alertas de inventario"/><c:set var="seccion" value="inventario"/><c:set var="cssExtra" value="modulo-inventario"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P07 · Alertas por stock mínimo, próximos a vencer y vencidos (RF-24)</p>
<c:set var="pestana" value="alertas"/>
<%@ include file="/WEB-INF/jsp/inventario/pestanas.jspf" %>
<section class="tarjeta">
  <div class="cabecera-tarjeta">
    <h2>Productos que requieren atención</h2>
    <form method="post" action="${ctx}/inventario/alertas" class="en-linea">
      <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
      <button type="submit" class="btn btn-primario" ${empty alertas ? 'disabled' : ''}>Generar alertas internas para Farmacia</button>
    </form>
  </div>
  <c:choose>
    <c:when test="${empty alertas}"><p class="vacio">Sin alertas: ningún producto está en stock mínimo, por vencer ni vencido.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>Alerta</th><th>Producto</th><th>Lote(s)</th><th class="num">Stock</th><th class="num">Mínimo</th><th>Vence</th><th>Acción sugerida</th></tr></thead>
        <tbody>
        <c:forEach var="a" items="${alertas}">
          <tr>
            <td><span class="${vs:claseEstado(a.alerta)}"><c:out value="${vs:etiqueta(a.alerta)}"/></span></td>
            <td><a href="${ctx}/inventario/producto?id=${a.idProducto}"><c:out value="${a.producto}"/></a> <small>(<c:out value="${a.codigo}"/>)</small></td>
            <td><c:out value="${a.lotes}"/></td>
            <td class="num"><c:out value="${vs:numero(a.stock)}"/></td>
            <td class="num"><c:out value="${a.alerta == 'STOCK_MINIMO' ? vs:numero(a.stockMinimo) : ''}"/></td>
            <td><c:out value="${vs:fecha(a.vence)}"/></td>
            <td>
              <c:choose>
                <c:when test="${a.alerta == 'STOCK_MINIMO'}"><a class="btn btn-secundario btn-chico" href="${ctx}/inventario/ingreso">Reponer (ingreso)</a></c:when>
                <c:when test="${a.alerta == 'VENCIDO'}"><a class="btn btn-peligro btn-chico" href="${ctx}/inventario/ajuste?producto=${a.idProducto}">Dar de baja (ajuste)</a></c:when>
                <c:otherwise><span class="ayuda">Vender primero (FEFO)</span></c:otherwise>
              </c:choose>
            </td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
      <p class="ayuda">Fuente: vista v_alerta_inventario. "Por vencer" usa el parámetro DIAS_POR_VENCER (<c:out value="${diasPorVencer}"/> días).
        Al generar alertas internas no se duplican las que siguen pendientes; Farmacia las ve en su panel y en
        <a href="${ctx}/alertas">Alertas</a>.</p>
    </c:otherwise>
  </c:choose>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
