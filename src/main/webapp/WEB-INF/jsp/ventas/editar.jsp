<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Punto de venta"/><c:set var="seccion" value="ventas"/><c:set var="cssExtra" value="modulo-ventas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="v" value="${c.venta}"/>
<c:set var="urlVenta" value="${ctx}/ventas/editar?id=${v.idVenta}"/>
<p class="etiqueta-pantalla">P08 · Venta pendiente: líneas con lote FEFO, descuento, pagos y confirmación (RF-23, RF-26 a RF-28)</p>
<c:if test="${not puedeEditar}">
  <div class="aviso aviso-aviso" role="status"><strong>Solo lectura:</strong> esta venta pertenece al turno de <c:out value="${v.cajero}"/>; solo ese cajero puede modificarla.</div>
</c:if>
<div class="pos">
  <div class="pos-lineas">
    <section class="tarjeta">
      <div class="pos-cabecera">
        <div>
          <h2>Venta n.° <c:out value="${v.idVenta}"/> <span class="${vs:claseEstado(v.estado)}"><c:out value="${vs:etiqueta(v.estado)}"/></span></h2>
          <div class="datos">
            <span>Cliente: <strong><c:out value="${v.cliente}"/></strong></span>
            <span>Mascota: <strong><c:out value="${empty v.mascota ? '—' : v.mascota}"/></strong></span>
            <c:if test="${not empty v.idConsulta}"><span>Atención: <strong>consulta n.° <c:out value="${v.idConsulta}"/></strong></span></c:if>
            <span>Turno: <strong>n.° <c:out value="${v.idTurno}"/></strong> · <c:out value="${v.cajero}"/></span>
          </div>
        </div>
        <a class="btn btn-secundario btn-chico" href="${ctx}/ventas">Volver a ventas</a>
      </div>
      <div class="separador"></div>
      <c:choose>
        <c:when test="${empty c.lineas}"><p class="vacio">La venta aún no tiene líneas: agregue servicios o productos.</p></c:when>
        <c:otherwise>
          <div class="tabla-scroll"><table class="tabla">
            <thead><tr><th>Descripción</th><th class="num">Cant.</th><th class="num">P. unit.</th><th class="num">Importe</th><th><span class="solo-lector">Quitar</span></th></tr></thead>
            <tbody>
            <c:forEach var="l" items="${c.lineas}">
              <tr>
                <td><span class="tipo-linea">${l.esProducto() ? 'Producto' : 'Servicio'}</span><br><strong><c:out value="${l.descripcion}"/></strong>
                  <c:if test="${l.esProducto()}"><br><small>Lote <c:out value="${l.codigoLote}"/> · vence <c:out value="${empty l.fechaVencimiento ? 'sin vencimiento' : vs:fecha(l.fechaVencimiento)}"/></small></c:if></td>
                <td class="num"><c:out value="${vs:numero(l.cantidad)}"/></td>
                <td class="num"><c:out value="${vs:soles(l.precioUnitario)}"/></td>
                <td class="num"><strong><c:out value="${vs:soles(l.importe)}"/></strong></td>
                <td class="acciones">
                  <c:if test="${puedeEditar}">
                  <form method="post" action="${ctx}/ventas/editar" class="en-linea">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                    <input type="hidden" name="id" value="${v.idVenta}"><input type="hidden" name="accion" value="quitar">
                    <input type="hidden" name="detalle" value="${l.idDetalle}">
                    <button type="submit" class="btn btn-peligro btn-chico" aria-label="Quitar ${fn:escapeXml(l.descripcion)}">Quitar</button>
                  </form>
                  </c:if>
                </td>
              </tr>
            </c:forEach>
            </tbody>
          </table></div>
        </c:otherwise>
      </c:choose>
    </section>

    <c:if test="${puedeEditar}">
    <section class="tarjeta">
      <h2>Agregar servicio</h2>
      <form method="post" action="${ctx}/ventas/editar" class="form-fila">
        <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
        <input type="hidden" name="id" value="${v.idVenta}"><input type="hidden" name="accion" value="servicio">
        <div class="campo"><label for="servicio">Servicio</label>
          <select id="servicio" name="servicio" required>
            <option value="">Seleccione…</option>
            <c:forEach var="s" items="${servicios}"><option value="${s.idServicio}"><c:out value="${s.nombre}"/> · <c:out value="${vs:soles(s.tarifa)}"/></option></c:forEach>
          </select></div>
        <div class="campo chico"><label for="cantidadServicio">Cantidad</label>
          <input id="cantidadServicio" name="cantidad" type="number" min="1" max="100" step="1" value="1" required></div>
        <button type="submit" class="btn btn-secundario">Agregar servicio</button>
      </form>
    </section>

    <section class="tarjeta">
      <h2>Agregar producto</h2>
      <form method="get" action="${ctx}/ventas/editar" class="form-fila">
        <input type="hidden" name="id" value="${v.idVenta}">
        <div class="campo"><label for="producto">Producto</label>
          <select id="producto" name="producto" required data-autoenviar>
            <option value="">Seleccione…</option>
            <c:forEach var="p" items="${productos}">
              <option value="${p.idProducto}" ${productoSel.idProducto == p.idProducto ? 'selected' : ''}><c:out value="${p.codigo}"/> · <c:out value="${p.nombre}"/> · <c:out value="${vs:soles(p.precioVenta)}"/> · stock <c:out value="${vs:numero(p.stockDisponible)}"/>${p.requiereReceta ? ' · receta' : ''}</option>
            </c:forEach>
          </select></div>
        <button type="submit" class="btn btn-secundario">Ver lotes</button>
      </form>
      <c:if test="${not empty productoSel}">
        <div class="separador"></div>
        <p><strong><c:out value="${productoSel.nombre}"/></strong> · <c:out value="${vs:soles(productoSel.precioVenta)}"/> por <c:out value="${productoSel.unidad}"/>
          · stock vendible <strong><c:out value="${vs:numero(productoSel.stockDisponible)}"/></strong>
          <c:if test="${productoSel.requiereReceta}"><span class="badge badge-aviso">Requiere receta de la consulta</span></c:if></p>
        <c:choose>
          <c:when test="${empty lotesVendibles}"><p class="aviso aviso-error">Sin stock vendible: no hay lotes vigentes con stock (RF-23).</p></c:when>
          <c:otherwise>
            <form method="post" action="${ctx}/ventas/editar" class="formulario">
              <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
              <input type="hidden" name="id" value="${v.idVenta}"><input type="hidden" name="accion" value="producto">
              <input type="hidden" name="producto" value="${productoSel.idProducto}">
              <fieldset class="campo ancho">
                <legend>Lote</legend>
                <ul class="lista-opciones">
                  <li><label for="lote_auto"><input type="radio" id="lote_auto" name="lote" value="" checked>
                    <span><strong>Automático (FEFO)</strong>: primero el lote que vence antes; si no alcanza, reparte entre lotes.</span></label></li>
                  <c:forEach var="lv" items="${lotesVendibles}" varStatus="st">
                    <li><label for="lote_${lv.idLote}"><input type="radio" id="lote_${lv.idLote}" name="lote" value="${lv.idLote}">
                      <span>Lote <strong><c:out value="${lv.codigoLote}"/></strong> · vence <c:out value="${empty lv.fechaVencimiento ? 'sin vencimiento' : vs:fecha(lv.fechaVencimiento)}"/>
                        · stock <c:out value="${vs:numero(lv.stockActual)}"/></span>
                      <c:if test="${st.first}"><span class="badge badge-ok">Sugerido FEFO</span></c:if>
                      <c:if test="${lv.condicion == 'POR_VENCER'}"><span class="badge badge-aviso">Por vencer</span></c:if></label></li>
                  </c:forEach>
                </ul>
                <span class="ayuda">Los lotes vencidos no se listan ni se pueden vender (RN-06).</span>
              </fieldset>
              <div class="form-fila">
                <div class="campo chico"><label for="cantidadProducto">Cantidad</label>
                  <input id="cantidadProducto" name="cantidad" type="number" min="0.01" step="${productoSel.admiteDecimales() ? '0.01' : '1'}" value="1" required></div>
                <button type="submit" class="btn btn-primario">Agregar a la venta</button>
              </div>
            </form>
          </c:otherwise>
        </c:choose>
      </c:if>
    </section>

    <c:if test="${not empty receta}">
    <section class="tarjeta">
      <div class="cabecera-tarjeta">
        <h2>Receta de la consulta</h2>
        <form method="post" action="${ctx}/ventas/editar" class="en-linea">
          <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
          <input type="hidden" name="id" value="${v.idVenta}"><input type="hidden" name="accion" value="receta">
          <button type="submit" class="btn btn-primario btn-chico">Agregar todos (FEFO)</button>
        </form>
      </div>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th>Producto</th><th>Indicación</th><th class="num">Cant.</th><th>Lote sugerido</th><th class="num">Disponible</th><th><span class="solo-lector">Acción</span></th></tr></thead>
        <tbody>
        <c:forEach var="r" items="${receta}">
          <tr>
            <td><strong><c:out value="${r.nombre}"/></strong><br><small><c:out value="${r.codigo}"/> · <c:out value="${vs:soles(r.precio)}"/></small></td>
            <td><c:out value="${r.dosis}"/>, <c:out value="${r.frecuencia}"/>, <c:out value="${r.duracionDias}"/> días</td>
            <td class="num"><c:out value="${vs:numero(r.cantidad)}"/></td>
            <td>${empty r.loteSugerido ? '<span class="badge badge-error">Sin lote que cubra</span>' : ''}<c:out value="${r.loteSugerido}"/></td>
            <td class="num"><c:out value="${vs:numero(r.disponible)}"/></td>
            <td class="acciones">
              <c:choose>
                <c:when test="${vs:contiene(productosEnVenta, r.idProducto)}"><span class="badge badge-ok">En la venta</span></c:when>
                <c:otherwise>
                  <form method="post" action="${ctx}/ventas/editar" class="en-linea">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                    <input type="hidden" name="id" value="${v.idVenta}"><input type="hidden" name="accion" value="producto">
                    <input type="hidden" name="producto" value="${r.idProducto}"><input type="hidden" name="cantidad" value="${r.cantidad}">
                    <button type="submit" class="btn btn-secundario btn-chico">Agregar</button>
                  </form>
                </c:otherwise>
              </c:choose>
            </td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
    </section>
    </c:if>
    </c:if>
  </div>

  <aside class="pos-resumen" aria-label="Resumen y pagos de la venta">
    <section class="tarjeta">
      <h2>Resumen</h2>
      <dl class="resumen-filas">
        <div class="resumen-fila"><dt>Subtotal</dt><dd><c:out value="${vs:soles(v.subtotal)}"/></dd></div>
        <div class="resumen-fila"><dt>Descuento<c:if test="${not empty v.autorizaDescuento}"> <small>(autorizó <c:out value="${v.autorizaDescuento}"/>)</small></c:if></dt>
          <dd>− <c:out value="${vs:soles(v.descuento)}"/></dd></div>
        <div class="resumen-fila resumen-total"><dt>Total</dt><dd class="total-grande"><c:out value="${vs:soles(v.total)}"/></dd></div>
        <div class="resumen-fila"><dt>Pagado</dt><dd><c:out value="${vs:soles(v.pagado)}"/></dd></div>
        <div class="resumen-fila"><dt>Saldo pendiente</dt>
          <dd class="${v.saldo().signum() == 0 ? 'saldo-cubierto' : 'saldo-pendiente'}"><c:out value="${vs:soles(v.saldo())}"/></dd></div>
      </dl>
    </section>

    <c:if test="${puedeEditar}">
    <section class="tarjeta">
      <h3>Descuento (RF-27)</h3>
      <form method="post" action="${ctx}/ventas/editar" class="formulario">
        <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
        <input type="hidden" name="id" value="${v.idVenta}"><input type="hidden" name="accion" value="descuento">
        <div class="campo"><label for="descuento">Monto del descuento (S/)</label>
          <input id="descuento" name="descuento" type="number" min="0" step="0.01" value="<c:out value='${v.descuento}'/>">
          <span class="ayuda">Hasta <c:out value="${vs:soles(limiteDescuento)}"/> (<c:out value="${pctDescuento}"/> %) lo aplica el cajero. Use 0 para retirarlo.</span></div>
        <fieldset class="autorizacion">
          <legend>Autorización del administrador (solo si supera el <c:out value="${pctDescuento}"/> %, RN-08)</legend>
          <div class="campo"><label for="usuarioAdmin">Usuario administrador</label>
            <input id="usuarioAdmin" name="usuarioAdmin" type="text" maxlength="40" autocomplete="off"></div>
          <div class="campo"><label for="claveAdmin">Contraseña del administrador</label>
            <input id="claveAdmin" name="claveAdmin" type="password" maxlength="128" autocomplete="off"></div>
        </fieldset>
        <button type="submit" class="btn btn-secundario">Aplicar descuento</button>
      </form>
    </section>
    </c:if>

    <section class="tarjeta">
      <h3>Pagos (RF-28)</h3>
      <c:choose>
        <c:when test="${empty c.pagos}"><p class="ayuda">Aún no hay pagos registrados.</p></c:when>
        <c:otherwise>
          <ul class="pagos-lista">
            <c:forEach var="pg" items="${c.pagos}">
              <li><span><strong><c:out value="${vs:etiqueta(pg.medio)}"/></strong>
                <c:if test="${not empty pg.referencia}"><br><small>Op. <c:out value="${pg.referencia}"/></small></c:if></span>
                <span><c:out value="${vs:soles(pg.monto)}"/>
                <c:if test="${puedeEditar}">
                  <form method="post" action="${ctx}/ventas/editar" class="en-linea">
                    <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
                    <input type="hidden" name="id" value="${v.idVenta}"><input type="hidden" name="accion" value="quitarPago">
                    <input type="hidden" name="pago" value="${pg.idPago}">
                    <button type="submit" class="btn btn-peligro btn-chico" aria-label="Quitar pago de ${vs:soles(pg.monto)}">×</button>
                  </form>
                </c:if></span></li>
            </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
      <c:if test="${puedeEditar and v.saldo().signum() > 0 and not empty c.lineas}">
        <form method="post" action="${ctx}/ventas/editar" class="formulario mt18">
          <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
          <input type="hidden" name="id" value="${v.idVenta}"><input type="hidden" name="accion" value="pago">
          <div class="campo"><label for="medio">Medio de pago</label>
            <select id="medio" name="medio" required>
              <c:forEach var="md" items="${medios}"><option value="${md}"><c:out value="${vs:etiqueta(md)}"/></option></c:forEach>
            </select></div>
          <div class="campo"><label for="monto">Monto recibido (S/)</label>
            <input id="monto" name="monto" type="number" min="0.01" step="0.01" required value="<c:out value='${v.saldo()}'/>">
            <span class="ayuda">En efectivo puede ser mayor al saldo: se registra el saldo y se indica el vuelto.</span></div>
          <div class="campo"><label for="referencia">N.° de operación</label>
            <input id="referencia" name="referencia" type="text" maxlength="40" placeholder="Obligatorio para tarjeta, Yape/Plin y transferencia"></div>
          <button type="submit" class="btn btn-secundario">Registrar pago</button>
        </form>
      </c:if>
    </section>

    <c:if test="${puedeEditar}">
    <section class="tarjeta">
      <form method="post" action="${ctx}/ventas/editar" data-confirmar="¿Confirmar la venta por ${vs:soles(v.total)}? Se emitirá el comprobante y se descontará el stock.">
        <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
        <input type="hidden" name="id" value="${v.idVenta}"><input type="hidden" name="accion" value="confirmar">
        <button type="submit" class="btn btn-primario btn-bloque" ${empty c.lineas or v.saldo().signum() != 0 ? 'disabled' : ''}>Confirmar y emitir comprobante</button>
      </form>
      <p class="ayuda">Se confirma solo cuando los pagos suman exactamente el total (RN-12). Una venta pendiente impide cerrar la caja.</p>
    </section>
    </c:if>

    <c:if test="${puedeDescartar}">
    <section class="tarjeta">
      <form method="post" action="${ctx}/ventas/descartar" data-confirmar="¿Descartar la venta n.° ${v.idVenta}? Se borrarán sus líneas y pagos; no se emitió comprobante ni se movió stock.">
        <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
        <input type="hidden" name="id" value="${v.idVenta}">
        <button type="submit" class="btn btn-peligro btn-bloque">Descartar venta</button>
      </form>
      <p class="ayuda">Úselo si el cliente desiste antes de pagar: la venta pendiente deja de bloquear el cierre de caja.</p>
    </section>
    </c:if>
  </aside>
</div>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
