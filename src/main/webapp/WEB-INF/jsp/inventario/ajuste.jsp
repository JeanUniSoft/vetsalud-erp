<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Ajustes de inventario"/><c:set var="seccion" value="inventario"/><c:set var="cssExtra" value="modulo-inventario"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P07 · Ajuste autorizado con motivo y evidencia que modifica el kardex (RF-25)</p>
<c:set var="pestana" value="ajuste"/>
<%@ include file="/WEB-INF/jsp/inventario/pestanas.jspf" %>
<section class="tarjeta">
  <h2>1. Producto</h2>
  <form method="get" action="${ctx}/inventario/ajuste" class="form-fila">
    <div class="campo crece"><label for="producto">Producto a ajustar</label>
      <select id="producto" name="producto" required data-autoenviar>
        <option value="">Seleccione…</option>
        <c:forEach var="p" items="${productos}">
          <option value="${p.idProducto}" ${productoSel.idProducto == p.idProducto ? 'selected' : ''}><c:out value="${p.codigo}"/> · <c:out value="${p.nombre}"/></option>
        </c:forEach>
      </select></div>
    <button type="submit" class="btn btn-secundario">Ver lotes</button>
  </form>
</section>

<c:if test="${not empty productoSel}">
<section class="tarjeta">
  <h2>2. Ajuste de <c:out value="${productoSel.nombre}"/></h2>
  <c:choose>
    <c:when test="${not sesion.puede('INVENTARIO_AJUSTAR')}"><p class="aviso aviso-error">Su rol no tiene el permiso INVENTARIO_AJUSTAR: no puede registrar ajustes (RF-25).</p></c:when>
    <c:when test="${empty lotes}"><p class="vacio">El producto no tiene lotes. Registre primero un ingreso.</p></c:when>
    <c:otherwise>
  <form method="post" action="${ctx}/inventario/ajuste" class="formulario" data-confirmar="¿Registrar el ajuste? Quedará en el kardex y en la auditoría.">
    <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
    <input type="hidden" name="producto" value="${productoSel.idProducto}">
    <fieldset class="campo ancho">
      <legend>Lote <span class="req">*</span></legend>
      <ul class="lista-opciones">
        <c:forEach var="l" items="${lotes}" varStatus="st">
          <li><label for="lote_${l.idLote}"><input type="radio" id="lote_${l.idLote}" name="lote" value="${l.idLote}" required ${st.first ? 'checked' : ''}>
            <span><strong><c:out value="${l.codigoLote}"/></strong> · vence <c:out value="${empty l.fechaVencimiento ? 'sin vencimiento' : vs:fecha(l.fechaVencimiento)}"/>
              · stock <c:out value="${vs:numero(l.stockActual)}"/></span>
            <span class="${l.condicion == 'VIGENTE' ? 'badge badge-ok' : vs:claseEstado(l.condicion)}"><c:out value="${vs:etiqueta(l.condicion)}"/></span></label></li>
        </c:forEach>
      </ul>
    </fieldset>
    <div class="campos">
      <div class="campo"><label for="tipo">Tipo de ajuste <span class="req">*</span></label>
        <select id="tipo" name="tipo" required>
          <c:forEach var="t" items="${tiposAjuste}"><option value="${t}">${t == 'AJUSTE_ENTRADA' ? 'Entrada (+): aumenta el stock' : 'Salida (−): disminuye el stock'}</option></c:forEach>
        </select></div>
      <div class="campo"><label for="cantidad">Cantidad <span class="req">*</span></label>
        <input id="cantidad" name="cantidad" type="number" required min="0.01" step="${productoSel.admiteDecimales() ? '0.01' : '1'}"></div>
      <div class="campo"><label for="motivo">Motivo <span class="req">*</span></label>
        <select id="motivo" name="motivo" required>
          <option value="">Seleccione…</option>
          <c:forEach var="m" items="${motivos}"><option value="<c:out value='${m}'/>"><c:out value="${m}"/></option></c:forEach>
        </select></div>
      <div class="campo ancho"><label for="evidencia">Evidencia <span class="req">*</span></label>
        <input id="evidencia" name="evidencia" type="text" required minlength="4" maxlength="120" placeholder="N.° de acta, conteo u observación (ej. Acta de baja 0007)">
        <span class="ayuda">Queda en el kardex junto al motivo y en el registro de auditoría con su usuario.</span></div>
    </div>
    <div class="acciones-form"><button type="submit" class="btn btn-primario">Registrar ajuste</button></div>
  </form>
    </c:otherwise>
  </c:choose>
</section>
</c:if>

<section class="tarjeta">
  <h2>Últimos ajustes</h2>
  <c:choose>
    <c:when test="${empty recientes}"><p class="vacio">No hay ajustes registrados.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>Fecha</th><th>Producto</th><th>Lote</th><th>Tipo</th><th class="num">Cantidad</th><th class="num">Saldo lote</th><th>Motivo y evidencia</th><th>Usuario</th></tr></thead>
        <tbody>
        <c:forEach var="m" items="${recientes}">
          <tr><td><c:out value="${vs:fechaHora(m.fechaHora)}"/></td><td><c:out value="${m.producto}"/></td><td><c:out value="${m.codigoLote}"/></td>
            <td><span class="${m.tipo == 'AJUSTE_ENTRADA' ? 'badge badge-ok' : 'badge badge-aviso'}"><c:out value="${vs:etiqueta(m.tipo)}"/></span></td>
            <td class="num"><c:out value="${vs:numero(empty m.entrada ? m.salida : m.entrada)}"/></td>
            <td class="num"><c:out value="${vs:numero(m.saldoLote)}"/></td>
            <td><c:out value="${m.motivo}"/></td><td><c:out value="${m.usuario}"/></td></tr>
        </c:forEach>
        </tbody>
      </table></div>
    </c:otherwise>
  </c:choose>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
