<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Ingreso de mercadería"/><c:set var="seccion" value="inventario"/><c:set var="cssExtra" value="modulo-inventario"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P07 · Ingreso por producto, lote, vencimiento, cantidad, costo y proveedor (RF-21)</p>
<c:set var="pestana" value="ingreso"/>
<%@ include file="/WEB-INF/jsp/inventario/pestanas.jspf" %>
<section class="tarjeta">
  <div class="cabecera-tarjeta">
    <h2>Documento de compra</h2>
    <c:if test="${filas < maximoFilas}">
      <a class="btn btn-secundario btn-chico" href="${ctx}/inventario/ingreso?filas=${maximoFilas}">Mostrar <c:out value="${maximoFilas}"/> líneas</a>
    </c:if>
  </div>
  <c:choose>
    <c:when test="${not sesion.puede('INVENTARIO_INGRESAR')}"><p class="vacio">Su rol no tiene el permiso INVENTARIO_INGRESAR.</p></c:when>
    <c:when test="${empty proveedores}"><p class="vacio">No hay proveedores activos. <a href="${ctx}/inventario/proveedores">Registre uno</a> antes del ingreso.</p></c:when>
    <c:otherwise>
  <form method="post" action="${ctx}/inventario/ingreso" class="formulario">
    <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
    <input type="hidden" name="filas" value="${filas}">
    <div class="campos">
      <div class="campo"><label for="proveedor">Proveedor <span class="req">*</span></label>
        <select id="proveedor" name="proveedor" required>
          <option value="">Seleccione…</option>
          <c:forEach var="pr" items="${proveedores}">
            <option value="${pr.idProveedor}" ${param.proveedor == pr.idProveedor ? 'selected' : ''}><c:out value="${pr.razonSocial}"/> (RUC <c:out value="${pr.ruc}"/>)</option>
          </c:forEach>
        </select>
        <span class="ayuda"><a href="${ctx}/inventario/proveedores">Administrar proveedores</a></span></div>
      <div class="campo"><label for="tipoDoc">Tipo de documento <span class="req">*</span></label>
        <select id="tipoDoc" name="tipoDoc" required>
          <c:forEach var="t" items="${tiposDocumento}">
            <option value="${t}" ${param.tipoDoc == t ? 'selected' : ''}>${t == 'GUIA' ? 'Guía de remisión' : (t == 'FACTURA' ? 'Factura' : 'Boleta')}</option>
          </c:forEach>
        </select></div>
      <div class="campo"><label for="numDoc">N.° de documento <span class="req">*</span></label>
        <input id="numDoc" name="numDoc" type="text" required maxlength="20" placeholder="F001-00012345" value="<c:out value='${param.numDoc}'/>">
        <span class="ayuda">Único por proveedor y tipo.</span></div>
      <div class="campo"><label for="fecha">Fecha del documento</label>
        <input id="fecha" name="fecha" type="date" max="${hoy}" value="<c:out value='${empty param.fecha ? hoy : param.fecha}'/>"></div>
    </div>

    <div class="tabla-scroll"><table class="tabla tabla-lineas">
      <caption class="solo-lector">Líneas del documento: producto, lote, vencimiento, cantidad y costo</caption>
      <thead><tr><th>#</th><th>Producto</th><th>Código de lote</th><th>Vencimiento</th><th class="num">Cantidad</th><th class="num">Costo unit. (S/)</th></tr></thead>
      <tbody>
      <c:forEach var="i" items="${indices}">
        <c:set var="kProd" value="producto_${i}"/><c:set var="kLote" value="lote_${i}"/><c:set var="kVence" value="vence_${i}"/>
        <c:set var="kCant" value="cantidad_${i}"/><c:set var="kCosto" value="costo_${i}"/>
        <tr>
          <td class="n-linea">${i + 1}</td>
          <td class="col-producto"><label for="${kProd}" class="solo-lector">Producto de la línea ${i + 1}</label>
            <select id="${kProd}" name="${kProd}">
              <option value="">—</option>
              <c:forEach var="p" items="${productos}">
                <option value="${p.idProducto}" ${param[kProd] == p.idProducto ? 'selected' : ''}><c:out value="${p.codigo}"/> · <c:out value="${p.nombre}"/> (<c:out value="${p.unidad}"/>)</option>
              </c:forEach>
            </select></td>
          <td class="col-lote"><label for="${kLote}" class="solo-lector">Lote de la línea ${i + 1}</label>
            <input id="${kLote}" name="${kLote}" type="text" maxlength="30" placeholder="AMX-2611" value="<c:out value='${param[kLote]}'/>"></td>
          <td class="col-fecha"><label for="${kVence}" class="solo-lector">Vencimiento de la línea ${i + 1}</label>
            <input id="${kVence}" name="${kVence}" type="date" min="${hoy}" value="<c:out value='${param[kVence]}'/>"></td>
          <td class="col-num"><label for="${kCant}" class="solo-lector">Cantidad de la línea ${i + 1}</label>
            <input id="${kCant}" name="${kCant}" type="number" min="0.01" step="0.01" value="<c:out value='${param[kCant]}'/>"></td>
          <td class="col-num"><label for="${kCosto}" class="solo-lector">Costo unitario de la línea ${i + 1}</label>
            <input id="${kCosto}" name="${kCosto}" type="number" min="0" step="0.01" value="<c:out value='${param[kCosto]}'/>"></td>
        </tr>
      </c:forEach>
      </tbody>
    </table></div>
    <p class="ayuda">Complete solo las líneas que use (mínimo una). Si el lote ya existe para el producto se reutiliza (debe coincidir el vencimiento)
      y su costo se promedia; si no, se crea. Los accesorios pueden ingresar sin vencimiento (lote SIN-LOTE).
      Todo el documento se guarda en una sola transacción: ingreso, lotes y movimientos INGRESO del kardex.</p>
    <div class="acciones-form">
      <button type="submit" class="btn btn-primario">Registrar ingreso</button>
      <a class="btn btn-secundario" href="${ctx}/inventario">Cancelar</a>
    </div>
  </form>
    </c:otherwise>
  </c:choose>
</section>

<section class="tarjeta">
  <h2>Últimos ingresos registrados</h2>
  <c:choose>
    <c:when test="${empty recientes}"><p class="vacio">Aún no hay ingresos.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>N.°</th><th>Fecha</th><th>Proveedor</th><th>Documento</th><th class="num">Líneas</th><th class="num">Unidades</th><th>Registró</th></tr></thead>
        <tbody>
        <c:forEach var="r" items="${recientes}">
          <tr><td><c:out value="${r.idIngreso}"/></td><td><c:out value="${vs:fecha(r.fecha)}"/></td><td><c:out value="${r.proveedor}"/></td>
            <td><c:out value="${r.tipoDoc}"/> <c:out value="${r.numDoc}"/></td><td class="num"><c:out value="${r.lineas}"/></td>
            <td class="num"><c:out value="${vs:numero(r.unidades)}"/></td><td><c:out value="${r.usuario}"/></td></tr>
        </c:forEach>
        </tbody>
      </table></div>
    </c:otherwise>
  </c:choose>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
