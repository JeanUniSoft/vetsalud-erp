<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Caja"/><c:set var="seccion" value="caja"/><c:set var="cssExtra" value="modulo-ventas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P09 · Turno de caja: apertura, cobros por medio de pago y cierre conciliado (RF-30, RN-10)</p>
<nav class="pestanas" aria-label="Secciones de caja">
  <a class="activo" aria-current="page" href="${ctx}/caja">Turno actual</a>
  <a href="${ctx}/caja/historial">Historial de turnos</a>
  <a href="${ctx}/ventas">Ventas</a>
</nav>
<c:choose>
  <c:when test="${empty turno}">
    <section class="tarjeta">
      <h2>Abrir turno de caja</h2>
      <p>No tiene un turno abierto. Registre el fondo inicial para empezar a vender (un solo turno abierto por cajero, RN-10).</p>
      <c:choose>
        <c:when test="${sesion.puede('CAJA_CERRAR')}">
          <form method="post" action="${ctx}/caja" class="form-fila">
            <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
            <input type="hidden" name="accion" value="abrir">
            <div class="campo"><label for="monto">Monto inicial (S/) <span class="req">*</span></label>
              <input id="monto" name="monto" type="number" min="0" max="5000" step="0.01" required value="150.00"></div>
            <button type="submit" class="btn btn-primario">Abrir caja</button>
          </form>
        </c:when>
        <c:otherwise><p class="aviso aviso-error">Su rol no tiene el permiso CAJA_CERRAR.</p></c:otherwise>
      </c:choose>
    </section>
  </c:when>
  <c:otherwise>
    <c:set var="r" value="${resumen}"/>
    <div class="aviso aviso-exito" role="status"><strong>Turno n.° <c:out value="${turno.idTurno}"/> abierto</strong> desde el
      <c:out value="${vs:fechaHora(turno.apertura)}"/> con un fondo de <c:out value="${vs:soles(turno.montoInicial)}"/>.
      <c:if test="${sesion.puede('VENTA_PROCESAR')}"><a href="${ctx}/ventas/nueva">Registrar una venta</a></c:if></div>
    <div class="rejilla rejilla-4">
      <c:forEach var="m" items="${r.medios}">
        <div class="kpi ${m.medio == 'EFECTIVO' ? 'destacado' : ''}">
          <div class="etiqueta"><c:out value="${vs:etiqueta(m.medio)}"/></div>
          <div class="valor valor-medio"><c:out value="${vs:soles(m.monto)}"/></div>
          <div class="detalle"><c:out value="${m.pagos}"/> pago(s) en ventas confirmadas</div>
        </div>
      </c:forEach>
    </div>
    <div class="rejilla rejilla-2 mt18">
      <section class="tarjeta">
        <h2>Cuadre del turno</h2>
        <dl class="cuadre">
          <div><dt>Ventas confirmadas</dt><dd><c:out value="${r.ventasConfirmadas}"/> · <c:out value="${vs:soles(r.totalConfirmado)}"/></dd></div>
          <div><dt>Descuentos otorgados</dt><dd><c:out value="${vs:soles(r.descuentos)}"/></dd></div>
          <div><dt>Devoluciones (ventas anuladas)</dt><dd><c:out value="${r.ventasAnuladas}"/> · <c:out value="${vs:soles(r.totalAnulado)}"/></dd></div>
          <div><dt>Efectivo devuelto por anulaciones</dt><dd><c:out value="${vs:soles(r.efectivoDevuelto)}"/></dd></div>
          <div><dt>Monto inicial</dt><dd><c:out value="${vs:soles(turno.montoInicial)}"/></dd></div>
          <div><dt>+ Efectivo cobrado</dt><dd><c:out value="${vs:soles(r.efectivoCobrado())}"/></dd></div>
          <div class="total"><dt>= Efectivo esperado en caja</dt><dd><c:out value="${vs:soles(r.efectivoEsperado)}"/></dd></div>
        </dl>
      </section>
      <section class="tarjeta">
        <h2>Cerrar turno</h2>
        <c:if test="${r.ventasPendientes > 0}">
          <div class="aviso aviso-error" role="alert">Hay <c:out value="${r.ventasPendientes}"/> venta(s) pendiente(s): confírmelas o descártelas antes de cerrar
            (ver la lista de abajo).</div>
        </c:if>
        <form method="post" action="${ctx}/caja" class="formulario" data-confirmar="¿Cerrar el turno de caja? Ya no podrá registrar ventas ni anulaciones en él.">
          <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
          <input type="hidden" name="accion" value="cerrar"><input type="hidden" name="turno" value="${turno.idTurno}">
          <div class="campo"><label for="contado">Efectivo contado (S/) <span class="req">*</span></label>
            <input id="contado" name="contado" type="number" min="0" step="0.01" required>
            <span class="ayuda">Cuente billetes y monedas. Esperado: <c:out value="${vs:soles(r.efectivoEsperado)}"/>.</span></div>
          <div class="campo"><label for="observacion">Observación</label>
            <textarea id="observacion" name="observacion" maxlength="300" placeholder="Obligatoria si el contado no coincide con lo esperado (RN-10)"></textarea></div>
          <div class="acciones-form"><button type="submit" class="btn btn-peligro" ${r.ventasPendientes > 0 ? 'disabled' : ''}>Cerrar turno</button></div>
        </form>
      </section>
    </div>
    <section class="tarjeta">
      <h2>Ventas del turno</h2>
      <c:choose>
        <c:when test="${empty ventas}"><p class="vacio">Aún no hay ventas en este turno.</p></c:when>
        <c:otherwise>
          <div class="tabla-scroll"><table class="tabla">
            <thead><tr><th>Comprobante</th><th>Hora</th><th>Cliente</th><th>Estado</th><th class="num">Total</th><th><span class="solo-lector">Acción</span></th></tr></thead>
            <tbody>
            <c:forEach var="vt" items="${ventas}">
              <tr><td><strong><c:out value="${empty vt.comprobante() ? 'Venta n.° '.concat(vt.idVenta) : vt.comprobante()}"/></strong></td>
                <td><c:out value="${vs:hora(vt.fechaHora)}"/></td><td><c:out value="${vt.cliente}"/></td>
                <td><span class="${vs:claseEstado(vt.estado)}"><c:out value="${vs:etiqueta(vt.estado)}"/></span></td>
                <td class="num"><c:out value="${vs:soles(vt.total)}"/></td>
                <td class="acciones"><a class="btn btn-secundario btn-chico" href="${ctx}${vt.esPendiente() ? '/ventas/editar?id=' : '/ventas/comprobante?id='}${vt.idVenta}">${vt.esPendiente() ? 'Completar' : 'Ver'}</a></td></tr>
            </c:forEach>
            </tbody>
          </table></div>
        </c:otherwise>
      </c:choose>
    </section>
  </c:otherwise>
</c:choose>

<c:if test="${not empty abiertos}">
  <section class="tarjeta">
    <h2>Turnos abiertos de todos los cajeros (supervisión)</h2>
    <table class="tabla">
      <thead><tr><th>Turno</th><th>Cajero</th><th>Apertura</th><th class="num">Monto inicial</th><th><span class="solo-lector">Acción</span></th></tr></thead>
      <tbody>
      <c:forEach var="t" items="${abiertos}">
        <tr><td>n.° <c:out value="${t.idTurno}"/></td><td><c:out value="${t.cajero}"/></td><td><c:out value="${vs:fechaHora(t.apertura)}"/></td>
          <td class="num"><c:out value="${vs:soles(t.montoInicial)}"/></td>
          <td class="acciones"><a class="btn btn-secundario btn-chico" href="${ctx}/caja/turno?id=${t.idTurno}">Ver detalle</a></td></tr>
      </c:forEach>
      </tbody>
    </table>
  </section>
</c:if>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
