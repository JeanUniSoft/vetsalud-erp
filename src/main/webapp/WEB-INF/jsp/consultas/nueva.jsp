<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Nueva consulta"/><c:set var="seccion" value="historia"/><c:set var="cssExtra" value="modulo-consulta"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P05 · Apertura de la consulta por el médico veterinario (RF-14, RN-04)</p>

<c:if test="${not puedeAbrir}">
  <div class="aviso aviso-aviso" role="status">Solo un médico veterinario abre consultas (RN-04). El técnico registra signos vitales y
    procedimientos dentro de una consulta ya abierta.</div>
</c:if>

<c:choose>
  <c:when test="${empty mascota}">
    <section class="tarjeta">
      <h2>Elija al paciente</h2>
      <p>La consulta se abre desde una cita «En atención» de la agenda o desde la historia clínica de la mascota (atención sin cita).</p>
      <div class="acciones-form">
        <a class="btn btn-primario" href="${ctx}/historia">Buscar paciente</a>
        <a class="btn btn-secundario" href="${ctx}/citas">Ir a la agenda</a>
      </div>
    </section>
  </c:when>
  <c:otherwise>
    <c:if test="${not empty mascota.alertas}">
      <div class="nota-clinica alerta-clinica" role="note"><strong>⚠ Alertas clínicas:</strong> <span><c:out value="${mascota.alertas}"/></span></div>
    </c:if>
    <section class="tarjeta">
      <h2><c:out value="${mascota.nombre}"/> <small>· <c:out value="${mascota.especie}"/>, <c:out value="${mascota.raza}"/></small></h2>
      <dl class="ficha">
        <dt>Propietario</dt><dd><c:out value="${mascota.cliente}"/> · <c:out value="${mascota.telefono}"/></dd>
        <dt>Sexo y edad</dt><dd><c:out value="${mascota.sexoTexto()}"/> · <c:out value="${vs:edad(mascota.fechaNacimiento)}"/></dd>
        <c:if test="${not empty cita}">
          <dt>Cita</dt><dd>N.° ${cita.idCita} · <c:out value="${vs:fecha(cita.inicio)}"/> <c:out value="${vs:hora(cita.inicio)}"/> ·
            <c:out value="${cita.servicio}"/> · <span class="${vs:claseEstado(cita.estado)}"><c:out value="${vs:etiqueta(cita.estado)}"/></span></dd>
        </c:if>
        <c:if test="${not empty responsable}">
          <dt>Responsable</dt><dd>M.V. <c:out value="${responsable.nombreCompleto()}"/><c:if test="${not empty responsable.colegiatura}"> · CMVP <c:out value="${responsable.colegiatura}"/></c:if></dd>
        </c:if>
      </dl>

      <c:choose>
        <c:when test="${not empty consultaExistente}">
          <div class="aviso aviso-aviso mt18" role="status">Esta cita ya tiene la consulta N.° ${consultaExistente.idConsulta}.
            <a href="${ctx}/consultas/ver?id=${consultaExistente.idConsulta}">Abrir la consulta</a></div>
        </c:when>
        <c:when test="${not empty cita and cita.estado != 'EN_ATENCION'}">
          <div class="aviso aviso-aviso mt18" role="status">Para iniciar la consulta, la cita debe estar «En atención» (RF-12).
            <a href="${ctx}/citas/ver?id=${cita.idCita}">Ir a la cita</a></div>
        </c:when>
        <c:when test="${not mascota.activa()}">
          <div class="aviso aviso-error mt18" role="alert">La mascota no está activa: no se le pueden abrir consultas.</div>
        </c:when>
        <c:when test="${puedeAbrir}">
          <form method="post" action="${ctx}/consultas/nueva" class="formulario mt18">
            <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
            <c:choose>
              <c:when test="${not empty cita}"><input type="hidden" name="cita" value="${cita.idCita}"></c:when>
              <c:otherwise><input type="hidden" name="mascota" value="${mascota.idMascota}"></c:otherwise>
            </c:choose>
            <div class="campos">
              <div class="campo ancho"><label for="motivo">Motivo de consulta <span class="req">*</span></label>
                <input id="motivo" name="motivo" type="text" required maxlength="300" value="<c:out value='${cita.motivo}'/>"
                       placeholder="Ej.: vómitos desde ayer" autofocus></div>
            </div>
            <p class="ayuda">Usted queda como veterinario responsable: solo usted podrá diagnosticar, recetar y firmar esta consulta.</p>
            <div class="acciones-form">
              <button type="submit" class="btn btn-primario">Abrir consulta</button>
              <a class="btn btn-secundario" href="${ctx}/historia/mascota?id=${mascota.idMascota}">Cancelar</a>
            </div>
          </form>
        </c:when>
      </c:choose>
    </section>
  </c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
