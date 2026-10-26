<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Ficha de producto"/><c:set var="seccion" value="inventario"/><c:set var="cssExtra" value="modulo-inventario"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P07 · Ficha: lotes con vencimiento y stock, FEFO y últimos movimientos (RF-20, RF-22, RF-23)</p>
<c:set var="pestana" value="catalogo"/>
<%@ include file="/WEB-INF/jsp/inventario/pestanas.jspf" %>
<c:if test="${not empty producto}">
<section class="tarjeta">
  <div class="cabecera-tarjeta">
    <div>
      <h2><c:out value="${producto.nombre}"/></h2>
      <small><c:out value="${producto.codigo}"/> · <c:out value="${producto.categoria}"/></small>
      <span class="${producto.activo ? 'badge badge-ok' : 'badge badge-error'}">${producto.activo ? 'Activo' : 'Inactivo'}</span>
      <c:if test="${producto.requiereReceta}"><span class="badge badge-neutro">Requiere receta</span></c:if>
    </div>
    <div class="acciones-form">
      <a class="btn btn-secundario btn-chico" href="${ctx}/inventario/kardex?producto=${producto.idProducto}">Ver kardex</a>
      <c:if test="${sesion.puede('INVENTARIO_AJUSTAR')}"><a class="btn btn-secundario btn-chico" href="${ctx}/inventario/ajuste?producto=${producto.idProducto}">Ajustar stock</a></c:if>
      <c:if test="${sesion.puede('INVENTARIO_INGRESAR')}">
        <a class="btn btn-secundario btn-chico" href="${ctx}/inventario/ingreso">Registrar ingreso</a>
        <a class="btn btn-primario btn-chico" href="${ctx}/inventario/producto/editar?id=${producto.idProducto}">Editar</a>
      </c:if>
    </div>
  </div>
  <div class="rejilla rejilla-2">
    <dl class="ficha">
      <dt>Unidad</dt><dd><c:out value="${producto.unidad}"/></dd>
      <dt>Precio de venta</dt><dd><c:out value="${vs:soles(producto.precioVenta)}"/></dd>
      <dt>Stock mínimo</dt><dd><c:out value="${vs:numero(producto.stockMinimo)}"/></dd>
      <dt>Condición de venta</dt><dd>${producto.requiereReceta ? 'Con receta veterinaria' : 'Venta libre'}</dd>
      <dt>Stock vendible</dt><dd><strong><c:out value="${vs:numero(producto.stockDisponible)}"/></strong> <small>(lotes vigentes)</small></dd>
      <dt>Stock físico</dt><dd><c:out value="${vs:numero(producto.stockTotal)}"/>
        <c:if test="${producto.bajoMinimo()}"> <span class="badge badge-aviso">Stock mínimo</span></c:if></dd>
    </dl>
    <div>
      <h3>Validar stock antes de dispensar (RF-23)</h3>
      <form method="get" action="${ctx}/inventario/producto" class="form-fila">
        <input type="hidden" name="id" value="${producto.idProducto}">
        <div class="campo"><label for="cantidad">Cantidad a dispensar</label>
          <input id="cantidad" name="cantidad" type="number" min="${producto.admiteDecimales() ? '0.01' : '1'}" step="${producto.admiteDecimales() ? '0.01' : '1'}" required value="<c:out value='${cantidadSimulada}'/>"></div>
        <button type="submit" class="btn btn-secundario">Sugerir lote (FEFO)</button>
      </form>
      <c:if test="${not empty cantidadSimulada}">
        <c:choose>
          <c:when test="${not empty sugerido}">
            <div class="resultado-fefo" role="status">Lote sugerido: <strong><c:out value="${sugerido.codigoLote}"/></strong>
              · vence <c:out value="${empty sugerido.fechaVencimiento ? 'sin vencimiento' : vs:fecha(sugerido.fechaVencimiento)}"/>
              · stock <c:out value="${vs:numero(sugerido.stockActual)}"/>.
              <br><small>Es el lote vigente con vencimiento más próximo que cubre <c:out value="${vs:numero(cantidadSimulada)}"/> (fn_lote_sugerido, RN-07).</small></div>
          </c:when>
          <c:otherwise>
            <div class="resultado-fefo sin-stock" role="alert">Ningún lote vigente cubre <c:out value="${vs:numero(cantidadSimulada)}"/>
              <c:out value="${producto.unidad}"/>. Stock vendible total: <c:out value="${vs:numero(producto.stockDisponible)}"/>
              (en una venta se puede repartir entre varios lotes; nunca se usa un lote vencido).</div>
          </c:otherwise>
        </c:choose>
      </c:if>
    </div>
  </div>
</section>

<section class="tarjeta">
  <h2>Lotes</h2>
  <c:choose>
    <c:when test="${empty lotes}"><p class="vacio">El producto aún no tiene lotes: registre un ingreso.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>Lote</th><th>Vencimiento</th><th class="num">Días</th><th class="num">Stock</th><th class="num">Costo unit.</th><th>Condición</th></tr></thead>
        <tbody>
        <c:set var="fefoMarcado" value="false"/>
        <c:forEach var="l" items="${lotes}">
          <c:set var="esFefo" value="${not fefoMarcado and l.condicion != 'VENCIDO' and l.stockActual.signum() > 0}"/>
          <tr class="${esFefo ? 'fila-fefo' : ''}">
            <td><strong><c:out value="${l.codigoLote}"/></strong>
              <c:if test="${esFefo}"><c:set var="fefoMarcado" value="true"/> <span class="badge badge-ok">Sale primero (FEFO)</span></c:if></td>
            <td><c:out value="${empty l.fechaVencimiento ? 'Sin vencimiento' : vs:fecha(l.fechaVencimiento)}"/></td>
            <td class="num"><c:out value="${l.diasParaVencer()}"/></td>
            <td class="num"><c:out value="${vs:numero(l.stockActual)}"/></td>
            <td class="num"><c:out value="${vs:soles(l.costoUnitario)}"/></td>
            <td><span class="${l.condicion == 'VIGENTE' ? 'badge badge-ok' : vs:claseEstado(l.condicion)}"><c:out value="${vs:etiqueta(l.condicion)}"/></span></td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
      <p class="ayuda">"Por vencer" = vence en los próximos <c:out value="${diasPorVencer}"/> días (parámetro DIAS_POR_VENCER). Un lote vencido no se puede vender (RN-06).</p>
    </c:otherwise>
  </c:choose>
</section>

<div class="rejilla rejilla-2">
  <section class="tarjeta">
    <div class="cabecera-tarjeta"><h2>Últimos movimientos</h2>
      <a class="btn btn-secundario btn-chico" href="${ctx}/inventario/kardex?producto=${producto.idProducto}">Kardex completo</a></div>
    <c:choose>
      <c:when test="${empty movimientos}"><p class="vacio">Sin movimientos registrados.</p></c:when>
      <c:otherwise>
        <div class="tabla-scroll"><table class="tabla">
          <thead><tr><th>Fecha</th><th>Lote</th><th>Tipo</th><th class="num">Entrada</th><th class="num">Salida</th><th class="num">Saldo lote</th></tr></thead>
          <tbody>
          <c:forEach var="m" items="${movimientos}">
            <tr><td><c:out value="${vs:fechaHora(m.fechaHora)}"/></td><td><c:out value="${m.codigoLote}"/></td>
              <td><span class="badge badge-neutro"><c:out value="${vs:etiqueta(m.tipo)}"/></span></td>
              <td class="num"><c:if test="${not empty m.entrada}"><c:out value="${vs:numero(m.entrada)}"/></c:if></td>
              <td class="num"><c:if test="${not empty m.salida}"><c:out value="${vs:numero(m.salida)}"/></c:if></td>
              <td class="num"><c:out value="${vs:numero(m.saldoLote)}"/></td></tr>
          </c:forEach>
          </tbody>
        </table></div>
      </c:otherwise>
    </c:choose>
  </section>
  <section class="tarjeta">
    <h2>Conciliación kardex ↔ stock (RF-22)</h2>
    <c:choose>
      <c:when test="${empty conciliacion}"><p class="vacio">Sin lotes.</p></c:when>
      <c:otherwise>
        <table class="tabla">
          <thead><tr><th>Lote</th><th class="num">Stock del lote</th><th class="num">Saldo según kardex</th><th>Resultado</th></tr></thead>
          <tbody>
          <c:forEach var="k" items="${conciliacion}">
            <tr><td><c:out value="${k.codigoLote}"/></td><td class="num"><c:out value="${vs:numero(k.stockActual)}"/></td>
              <td class="num"><c:out value="${vs:numero(k.saldoKardex)}"/></td>
              <td><span class="${k.cuadra() ? 'badge badge-ok' : 'badge badge-error'}">${k.cuadra() ? 'Cuadra' : 'Descuadre'}</span></td></tr>
          </c:forEach>
          </tbody>
        </table>
        <p class="ayuda">El saldo se recalcula sumando todos los movimientos del lote (entradas − salidas).</p>
      </c:otherwise>
    </c:choose>
  </section>
</div>
</c:if>
<p><a class="btn btn-secundario" href="${ctx}/inventario">Volver al catálogo</a></p>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
