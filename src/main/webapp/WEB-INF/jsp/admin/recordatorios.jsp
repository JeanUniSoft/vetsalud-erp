<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Usuarios y auditoría"/><c:set var="seccion" value="admin"/><c:set var="pestana" value="recordatorios"/><c:set var="cssExtra" value="modulo-mascotas"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<%@ include file="/WEB-INF/jsp/layout/pestanas-admin.jspf" %>
<p class="etiqueta-pantalla">P12 · Recordatorios de citas y controles por correo con consentimiento (RF-37, RF-19)</p>

<div class="rejilla rejilla-4">
  <div class="kpi destacado">
    <div class="etiqueta">Pendientes por enviar</div>
    <div class="valor"><c:out value="${resumen.pendientesVencidos}"/></div>
    <div class="detalle">ya cumplieron su hora programada</div>
  </div>
  <div class="kpi">
    <div class="etiqueta">Programados a futuro</div>
    <div class="valor"><c:out value="${resumen.pendientesFuturos}"/></div>
    <div class="detalle">se enviarán en su fecha</div>
  </div>
  <div class="kpi">
    <div class="etiqueta">Enviados</div>
    <div class="valor"><c:out value="${resumen.enviados}"/></div>
    <div class="detalle">con fecha y destinatario registrados</div>
  </div>
  <div class="kpi">
    <div class="etiqueta">Fallidos / cancelados</div>
    <div class="valor"><c:out value="${resumen.fallidos}"/> / <c:out value="${resumen.cancelados}"/></div>
    <div class="detalle">sin consentimiento o correo · evento no vigente</div>
  </div>
</div>

<section class="tarjeta mt18">
  <h2>Cola de envío</h2>
  <div class="acciones-cola">
    <form method="post" action="${ctx}/admin/recordatorios"
          data-confirmar="¿Procesar la cola de recordatorios ahora?">
      <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
      <input type="hidden" name="accion" value="procesar">
      <button type="submit" class="btn btn-primario">Procesar cola ahora</button>
      <label class="casilla"><input type="checkbox" name="incluirFuturos"> Incluir también los programados a futuro (envío anticipado)</label>
    </form>
    <form method="post" action="${ctx}/admin/recordatorios">
      <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
      <input type="hidden" name="accion" value="programar">
      <button type="submit" class="btn btn-secundario">Programar recordatorios faltantes</button>
    </form>
  </div>
  <p class="ayuda">Se envía solo si el cliente autorizó recordatorios y tiene un correo válido; si no, queda «Fallido». Citas canceladas o ya pasadas
    y mascotas no activas se marcan «Cancelado». «Programar» crea el recordatorio de las próximas citas (24 h antes) y de los controles de consultas firmadas (día anterior, 09:00).
    El texto del correo solo incluye la mascota, la fecha y la hora: nunca motivo, diagnóstico ni receta. Canal de laboratorio: el envío se registra en el log NOTIFICACIONES; <c:choose><c:when test="${minutosAuto gt 0}">el sistema además procesa la cola vencida automáticamente cada <c:out value="${minutosAuto}"/> minuto(s).</c:when><c:otherwise>el envío automático está desactivado en este servidor.</c:otherwise></c:choose></p>
</section>

<section class="tarjeta">
  <form method="get" action="${ctx}/admin/recordatorios" class="barra-herramientas">
    <div class="campo"><label for="estado">Estado</label>
      <select id="estado" name="estado" data-autoenviar>
        <option value="">Todos</option>
        <c:forEach var="e" items="${['PENDIENTE','ENVIADO','FALLIDO','CANCELADO']}">
          <option value="${e}" ${estado == e ? 'selected' : ''}><c:out value="${vs:etiqueta(e)}"/></option>
        </c:forEach>
      </select></div>
    <noscript><button type="submit" class="btn btn-secundario">Filtrar</button></noscript>
  </form>
  <c:choose>
    <c:when test="${empty recordatorios}"><p class="vacio">No hay recordatorios<c:if test="${not empty estado}"> en estado «<c:out value="${vs:etiqueta(estado)}"/>»</c:if>.</p></c:when>
    <c:otherwise>
      <div class="tabla-scroll"><table class="tabla">
        <thead><tr><th class="num">#</th><th>Cliente</th><th>Destinatario</th><th>Consentimiento</th><th>Mascota</th><th>Origen</th><th>Programado para</th><th>Estado</th><th>Procesado el</th></tr></thead>
        <tbody>
        <c:forEach var="r" items="${recordatorios}">
          <tr>
            <td class="num"><c:out value="${r.idRecordatorio}"/></td>
            <td><a href="${ctx}/clientes/ver?id=${r.idCliente}"><c:out value="${r.cliente}"/></a><c:if test="${not r.clienteActivo}"> <span class="badge badge-error">Inactivo</span></c:if></td>
            <td><c:out value="${empty r.correo ? 'Sin correo' : r.destinatarioEnmascarado()}"/></td>
            <td><span class="${r.aceptaRecordatorios ? 'badge badge-ok' : 'badge badge-error'}">${r.aceptaRecordatorios ? 'Autorizado' : 'No autorizado'}</span></td>
            <td><c:out value="${r.mascota}"/><c:if test="${r.estadoMascota != 'ACTIVA'}"> <small>(<c:out value="${vs:etiqueta(r.estadoMascota)}"/>)</small></c:if></td>
            <td><c:out value="${r.origenTexto()}"/> del <c:out value="${r.esCita() ? vs:fechaHora(r.inicioCita) : vs:fecha(r.fechaControl)}"/>
              <c:if test="${r.esCita()}"><br><small><c:out value="${vs:etiqueta(r.estadoCita)}"/></small></c:if></td>
            <td><c:out value="${vs:fechaHora(r.programadoPara)}"/></td>
            <td><span class="${vs:claseEstado(r.estado)}"><c:out value="${vs:etiqueta(r.estado)}"/></span></td>
            <td><c:out value="${empty r.enviadoEn ? '—' : vs:fechaHora(r.enviadoEn)}"/></td>
          </tr>
        </c:forEach>
        </tbody>
      </table></div>
      <p class="ayuda">Se muestran los últimos 100 recordatorios. El destinatario aparece enmascarado (RNF-15).</p>
    </c:otherwise>
  </c:choose>
</section>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
