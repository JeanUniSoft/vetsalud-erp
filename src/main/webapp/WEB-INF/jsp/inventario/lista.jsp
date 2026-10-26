<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Inventario por lotes"/><c:set var="seccion" value="inventario"/><c:set var="cssExtra" value="modulo-inventario"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P07 · Catálogo con stock disponible, mínimos y alertas (RF-20, RF-24)</p>
<c:set var="pestana" value="catalogo"/>
<%@ include file="/WEB-INF/jsp/inventario/pestanas.jspf" %>

<div class="rejilla rejilla-4">
  <div class="kpi destacado">
    <div class="etiqueta">Productos listados</div>
    <div class="valor"><c:out value="${fn:length(productos)}"/></div>
    <div class="detalle">${soloAlertas ? 'solo con alertas' : 'según los filtros aplicados'}</div>
  </div>
  <a class="kpi kpi-enlace ${totalStockMinimo > 0 ? 'kpi-aviso' : ''}" href="${ctx}/inventario/alertas">
    <div class="etiqueta">En stock mínimo</div>
    <div class="valor"><c:out value="${totalStockMinimo}"/></div>
    <div class="detalle">productos en o bajo su mínimo</div>
  </a>
  <a class="kpi kpi-enlace ${totalPorVencer > 0 ? 'kpi-aviso' : ''}" href="${ctx}/inventario/alertas">
    <div class="etiqueta">Lotes por vencer</div>
    <div class="valor"><c:out value="${totalPorVencer}"/></div>
    <div class="detalle">vencen en los próximos <c:out value="${diasPorVencer}"/> días</div>
  </a>
  <a class="kpi kpi-enlace ${totalVencidos > 0 ? 'kpi-error' : ''}" href="${ctx}/inventario/alertas">
    <div class="etiqueta">Lotes vencidos con stock</div>
    <div class="valor"><c:out value="${totalVencidos}"/></div>
    <div class="detalle">no se venden (RN-06): retírelos con un ajuste</div>
  </a>
</div>

<section class="tarjeta mt18">
  <form method="get" action="${ctx}/inventario" class="barra-herramientas">
    <div class="campo crece">
      <label for="q">Buscar producto</label>
      <input id="q" name="q" type="search" value="<c:out value='${q}'/>" placeholder="Código o nombre">
    </div>
    <div class="campo">
      <label for="categoria">Categoría</label>
      <select id="categoria" name="categoria">
        <option value="">Todas</option>
        <c:forEach var="cat" items="${categorias}">
          <option value="${cat.idCategoria}" ${categoriaSel == cat.idCategoria ? 'selected' : ''}><c:out value="${cat.nombre}"/></option>
        </c:forEach>
      </select>
    </div>
    <label class="casilla"><input type="checkbox" name="alertas" value="1" ${soloAlertas ? 'checked' : ''}> Solo con alertas</label>
    <label class="casilla"><input type="checkbox" name="inactivos" value="1" ${inactivos ? 'checked' : ''}> Incluir inactivos</label>
    <button type="submit" class="btn btn-secundario">Filtrar</button>
    <c:if test="${sesion.puede('INVENTARIO_INGRESAR')}">
      <a class="btn btn-secundario" href="${ctx}/inventario/ingreso">Registrar ingreso</a>
      <a class="btn btn-primario" href="${ctx}/inventario/producto/nuevo">+ Nuevo producto</a>
    </c:if>
  </form>
  <c:choose>
    <c:when test="${empty productos}">
      <p class="vacio">No hay productos que coincidan con los filtros<c:if test="${not empty q}"> para «<c:out value="${q}"/>»</c:if>.</p>
    </c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>Código</th><th>Producto</th><th>Categoría</th><th>Unidad</th><th class="num">Precio</th>
          <th class="num">Stock vendible</th><th class="num">Mínimo</th><th>Estado</th><th><span class="solo-lector">Acciones</span></th></tr></thead>
        <tbody>
        <c:forEach var="p" items="${productos}">
          <tr>
            <td><c:out value="${p.codigo}"/></td>
            <td><strong><c:out value="${p.nombre}"/></strong>
              <c:if test="${p.requiereReceta}"><br><span class="badge badge-neutro">Requiere receta</span></c:if></td>
            <td><c:out value="${p.categoria}"/></td>
            <td><c:out value="${p.unidad}"/></td>
            <td class="num"><c:out value="${vs:soles(p.precioVenta)}"/></td>
            <td class="num"><strong><c:out value="${vs:numero(p.stockDisponible)}"/></strong>
              <c:if test="${p.stockTotal.compareTo(p.stockDisponible) != 0}"><br><small>físico <c:out value="${vs:numero(p.stockTotal)}"/></small></c:if></td>
            <td class="num"><c:out value="${vs:numero(p.stockMinimo)}"/></td>
            <td><div class="badges">
              <c:if test="${not p.activo}"><span class="badge badge-error">Inactivo</span></c:if>
              <c:if test="${p.bajoMinimo()}"><span class="badge badge-aviso">Stock mínimo</span></c:if>
              <c:if test="${p.lotesPorVencer > 0}"><span class="badge badge-aviso"><c:out value="${p.lotesPorVencer}"/> por vencer</span></c:if>
              <c:if test="${p.lotesVencidos > 0}"><span class="badge badge-error"><c:out value="${p.lotesVencidos}"/> vencido(s)</span></c:if>
              <c:if test="${p.activo and not p.conAlerta()}"><span class="badge badge-ok">Normal</span></c:if>
            </div></td>
            <td class="acciones">
              <a class="btn btn-secundario btn-chico" href="${ctx}/inventario/producto?id=${p.idProducto}">Ver ficha</a>
              <c:if test="${sesion.puede('INVENTARIO_INGRESAR')}">
                <a class="btn btn-secundario btn-chico" href="${ctx}/inventario/producto/editar?id=${p.idProducto}">Editar</a>
              </c:if>
            </td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
      <p class="ayuda">El stock vendible excluye lotes vencidos. Los productos no se eliminan: se desactivan para conservar el kardex (RF-20).</p>
    </c:otherwise>
  </c:choose>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
