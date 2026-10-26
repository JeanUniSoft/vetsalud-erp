<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="edicion" value="${not empty producto}"/>
<c:set var="titulo" value="${edicion ? 'Editar producto' : 'Nuevo producto'}"/><c:set var="seccion" value="inventario"/><c:set var="cssExtra" value="modulo-inventario"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P07 · ${edicion ? 'Cambio de datos sin borrar movimientos históricos (RF-20)' : 'Alta de producto con unidad, precio, mínimo y condición de venta (RF-20)'}</p>
<c:set var="pestana" value="catalogo"/>
<%@ include file="/WEB-INF/jsp/inventario/pestanas.jspf" %>
<section class="tarjeta">
  <form method="post" class="formulario" action="${ctx}${edicion ? '/inventario/producto/editar' : '/inventario/producto/nuevo'}">
    <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
    <c:if test="${edicion}"><input type="hidden" name="id" value="${producto.idProducto}"></c:if>
    <div class="campos">
      <div class="campo"><label for="codigo">Código (SKU) <span class="req">*</span></label>
        <input id="codigo" name="codigo" type="text" required maxlength="20" pattern="[A-Za-z0-9][A-Za-z0-9\-]{2,19}" value="<c:out value='${producto.codigo}'/>" placeholder="MED-006">
        <span class="ayuda">3 a 20 letras, números o guiones; único.</span></div>
      <div class="campo ancho"><label for="nombre">Nombre del producto <span class="req">*</span></label>
        <input id="nombre" name="nombre" type="text" required maxlength="120" value="<c:out value='${producto.nombre}'/>" placeholder="Ej. Meloxicam 1 mg x 10 tab"></div>
      <div class="campo"><label for="idCategoria">Categoría <span class="req">*</span></label>
        <select id="idCategoria" name="idCategoria" required>
          <option value="">Seleccione…</option>
          <c:forEach var="cat" items="${categorias}">
            <option value="${cat.idCategoria}" ${producto.idCategoria == cat.idCategoria ? 'selected' : ''}><c:out value="${cat.nombre}"/></option>
          </c:forEach>
        </select>
        <span class="ayuda"><a href="${ctx}/inventario/categorias">Administrar categorías</a></span></div>
      <div class="campo"><label for="unidad">Unidad <span class="req">*</span></label>
        <select id="unidad" name="unidad" required>
          <c:forEach var="un" items="${unidades}">
            <option value="${un}" ${producto.unidad == un ? 'selected' : ''}><c:out value="${un}"/></option>
          </c:forEach>
        </select>
        <span class="ayuda">Solo KG admite cantidades con decimales.</span></div>
      <div class="campo"><label for="precioVenta">Precio de venta (S/) <span class="req">*</span></label>
        <input id="precioVenta" name="precioVenta" type="number" required min="0" max="99999.99" step="0.01" value="<c:out value='${producto.precioVenta}'/>"></div>
      <div class="campo"><label for="stockMinimo">Stock mínimo</label>
        <input id="stockMinimo" name="stockMinimo" type="number" min="0" step="0.01" value="<c:out value='${edicion ? producto.stockMinimo : 0}'/>">
        <span class="ayuda">Debajo de este nivel se genera la alerta de stock mínimo (RF-24).</span></div>
      <div class="campo ancho">
        <label class="casilla"><input type="checkbox" name="requiereReceta" ${producto.requiereReceta ? 'checked' : ''}>
          Condición de venta: requiere receta (solo se vende al cobrar la consulta que lo prescribe)</label>
        <c:if test="${edicion}">
          <label class="casilla"><input type="checkbox" name="activo" ${producto.activo ? 'checked' : ''}>
            Producto activo (desmarque para desactivarlo; su kardex se conserva)</label>
        </c:if>
      </div>
    </div>
    <c:if test="${edicion}">
      <p class="ayuda">El stock no se edita aquí: solo cambia con ingresos, ventas, devoluciones y ajustes registrados en el kardex (RN-06).</p>
    </c:if>
    <div class="acciones-form">
      <button type="submit" class="btn btn-primario">${edicion ? 'Guardar cambios' : 'Registrar producto'}</button>
      <a class="btn btn-secundario" href="${ctx}${edicion ? '/inventario/producto?id='.concat(producto.idProducto) : '/inventario'}">Cancelar</a>
    </div>
  </form>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
