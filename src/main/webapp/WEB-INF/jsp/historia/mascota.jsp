<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="${empty historia ? 'Historia clínica' : 'Historia clínica de '.concat(historia.mascota.nombre)}"/>
<c:set var="seccion" value="historia"/><c:set var="cssExtra" value="modulo-consulta"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P05 · Historia clínica completa en orden cronológico (RF-13)</p>

<c:choose>
  <c:when test="${empty historia}">
    <section class="tarjeta">
      <h2>No se encontró la historia clínica</h2>
      <p>La mascota solicitada no existe o el enlace está incompleto.</p>
      <a class="btn btn-secundario" href="${ctx}/historia">Buscar paciente</a>
    </section>
  </c:when>
  <c:otherwise>
    <c:set var="m" value="${historia.mascota}"/>
    <c:if test="${not empty m.alertas}">
      <div class="nota-clinica alerta-clinica" role="note"><strong>⚠ Alertas clínicas:</strong> <span><c:out value="${m.alertas}"/></span></div>
    </c:if>

    <div class="historia-rejilla">
      <section class="tarjeta">
        <div class="paciente-nombre">
          <h2><c:out value="${m.nombre}"/></h2>
          <span class="${vs:claseEstado(m.estado)}"><c:out value="${vs:etiqueta(m.estado)}"/></span>
        </div>
        <dl class="ficha ficha-compacta mt18">
          <dt>Especie / raza</dt><dd><c:out value="${m.especie}"/> · <c:out value="${m.raza}"/></dd>
          <dt>Sexo</dt><dd><c:out value="${m.sexoTexto()}"/>${m.esterilizado ? ' · esterilizado(a)' : ''}</dd>
          <dt>Edad</dt><dd><c:out value="${vs:edad(m.fechaNacimiento)}"/><c:if test="${not empty m.fechaNacimiento}"> <small>(<c:out value="${vs:fecha(m.fechaNacimiento)}"/>)</small></c:if></dd>
          <dt>Peso registrado</dt><dd><c:choose><c:when test="${empty m.pesoKg}">—</c:when><c:otherwise><c:out value="${vs:numero(m.pesoKg)}"/> kg</c:otherwise></c:choose></dd>
          <dt>Color</dt><dd><c:out value="${empty m.color ? '—' : m.color}"/></dd>
          <dt>Microchip</dt><dd><c:out value="${empty m.microchip ? '—' : m.microchip}"/></dd>
          <dt>Propietario</dt><dd><c:out value="${m.cliente}"/></dd>
          <dt>Documento</dt><dd><c:out value="${m.tipoDocumento}"/> <c:out value="${vs:enmascarar(m.numDocumento)}"/></dd>
          <dt>Teléfono</dt><dd><c:out value="${m.telefono}"/></dd>
          <dt>Recordatorios</dt><dd>${m.admiteRecordatorios() ? 'Acepta recordatorios por correo' : 'No recibe recordatorios por correo'}</dd>
        </dl>
        <div class="cifras">
          <div><strong>${fn:length(historia.consultas)}</strong><small>consultas</small></div>
          <div><strong>${historia.consultasAbiertas()}</strong><small>sin firmar</small></div>
        </div>
        <div class="acciones-paciente no-imprimir">
          <c:if test="${puedeAbrirConsulta and m.activa()}">
            <a class="btn btn-primario" href="${ctx}/consultas/nueva?mascota=${m.idMascota}">Nueva consulta</a>
          </c:if>
          <c:if test="${sesion.puede('CITA_GESTIONAR') and m.activa()}">
            <a class="btn btn-secundario" href="${ctx}/citas/nueva?mascota=${m.idMascota}">Programar cita</a>
          </c:if>
          <button type="button" class="btn btn-secundario" data-imprimir>Imprimir historia</button>
        </div>
      </section>

      <section class="tarjeta">
        <div class="cabecera-tarjeta">
          <h2>Línea de tiempo</h2>
          <nav class="pestanas no-imprimir" aria-label="Orden de la historia">
            <a class="${historia.recientesPrimero ? '' : 'activo'}" href="${ctx}/historia/mascota?id=${m.idMascota}">Cronológico</a>
            <a class="${historia.recientesPrimero ? 'activo' : ''}" href="${ctx}/historia/mascota?id=${m.idMascota}&amp;orden=desc">Recientes primero</a>
          </nav>
        </div>
        <c:choose>
          <c:when test="${empty historia.eventos}">
            <p class="vacio">Aún no hay atenciones registradas para esta mascota.</p>
          </c:when>
          <c:otherwise>
            <ol class="linea-tiempo historia">
              <c:forEach var="e" items="${historia.eventos}">
                <li class="tipo-${e.tipo}">
                  <div class="evento-cabecera">
                    <span class="evento-fecha"><c:out value="${e.tipo == 'CONTROL' ? vs:fecha(e.fechaHora) : vs:fechaHora(e.fechaHora)}"/></span>
                    <span class="badge badge-neutro"><c:out value="${tiposEvento[e.tipo]}"/></span>
                    <c:if test="${not empty e.estado}"><span class="${vs:claseEstado(e.estado)}"><c:out value="${vs:etiqueta(e.estado)}"/></span></c:if>
                  </div>
                  <h3 class="evento-titulo"><c:out value="${e.titulo}"/></h3>
                  <c:if test="${not empty e.detalle}"><p class="evento-detalle"><c:out value="${e.detalle}"/></p></c:if>
                  <c:if test="${not empty e.lineas}">
                    <ul class="evento-lineas">
                      <c:forEach var="l" items="${e.lineas}"><li><c:out value="${l}"/></li></c:forEach>
                    </ul>
                  </c:if>
                  <p class="evento-pie"><small>
                    <c:out value="${e.autor}"/>
                    <c:if test="${e.tipo == 'CONSULTA'}"> · <a href="${ctx}/consultas/ver?id=${e.idConsulta}">Abrir consulta N.° ${e.idConsulta}</a></c:if>
                    <c:if test="${e.tipo == 'RECETA'}"> · <a href="${ctx}/consultas/receta?id=${e.idConsulta}">Receta imprimible</a></c:if>
                  </small></p>
                </li>
              </c:forEach>
            </ol>
          </c:otherwise>
        </c:choose>
      </section>
    </div>
  </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
