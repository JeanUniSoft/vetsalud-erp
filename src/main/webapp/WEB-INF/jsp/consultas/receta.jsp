<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="${empty receta ? 'Receta' : 'Receta N.° '.concat(receta.numero())}"/>
<c:set var="seccion" value="historia"/><c:set var="cssExtra" value="modulo-consulta"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla no-imprimir">P06 · Receta vinculada a la consulta, lista para imprimir (RF-17)</p>

<c:choose>
<c:when test="${empty consulta}">
  <section class="tarjeta"><h2>No se encontró la consulta</h2><a class="btn btn-secundario" href="${ctx}/historia">Ir a la historia clínica</a></section>
</c:when>
<c:otherwise>
  <div class="barra-herramientas no-imprimir">
    <a class="btn btn-secundario" href="${ctx}/consultas/ver?id=${consulta.idConsulta}#receta">‹ Volver a la consulta</a>
    <c:if test="${not empty receta}"><button type="button" class="btn btn-primario" data-imprimir>Imprimir receta</button></c:if>
  </div>
  <c:choose>
    <c:when test="${empty receta}">
      <section class="tarjeta"><h2>Sin receta</h2><p>La consulta N.° ${consulta.idConsulta} aún no tiene receta emitida.</p></section>
    </c:when>
    <c:otherwise>
      <article class="tarjeta receta-hoja" aria-label="Receta médica veterinaria">
        <header class="receta-cabecera">
          <img src="${ctx}/static/img/logo.svg" alt="" width="52" height="52">
          <div class="clinica">
            <strong>Clínica Veterinaria VetSalud Integral S.A.C.</strong>
            <small>Receta médica veterinaria</small>
          </div>
          <div class="receta-numero">
            <strong>N.° <c:out value="${receta.numero()}"/></strong>
            <small><c:out value="${vs:fecha(receta.fecha)}"/></small>
          </div>
        </header>

        <c:if test="${not consulta.firmada()}">
          <div class="aviso aviso-aviso" role="status"><strong>BORRADOR:</strong> la consulta aún no está firmada; la receta solo es válida
            con la firma del médico veterinario (RN-04).</div>
        </c:if>

        <div class="rejilla rejilla-2 receta-datos">
          <dl class="ficha ficha-compacta">
            <dt>Paciente</dt><dd><strong><c:out value="${mascota.nombre}"/></strong></dd>
            <dt>Especie / raza</dt><dd><c:out value="${mascota.especie}"/> · <c:out value="${mascota.raza}"/></dd>
            <dt>Sexo / edad</dt><dd><c:out value="${mascota.sexoTexto()}"/> · <c:out value="${vs:edad(mascota.fechaNacimiento)}"/></dd>
            <dt>Peso</dt><dd><c:choose><c:when test="${not empty consulta.pesoKg}"><c:out value="${vs:numero(consulta.pesoKg)}"/> kg</c:when><c:when test="${not empty mascota.pesoKg}"><c:out value="${vs:numero(mascota.pesoKg)}"/> kg</c:when><c:otherwise>—</c:otherwise></c:choose></dd>
          </dl>
          <dl class="ficha ficha-compacta">
            <dt>Propietario</dt><dd><c:out value="${mascota.cliente}"/></dd>
            <dt>Teléfono</dt><dd><c:out value="${mascota.telefono}"/></dd>
            <dt>Consulta</dt><dd>N.° ${consulta.idConsulta} · <c:out value="${vs:fecha(consulta.fechaHora)}"/></dd>
            <dt>Diagnóstico</dt><dd><c:out value="${empty principal ? '—' : principal.descripcion}"/></dd>
          </dl>
        </div>
        <c:if test="${not empty mascota.alertas}">
          <p class="nota-clinica" role="note"><strong>Alertas clínicas:</strong> <c:out value="${mascota.alertas}"/></p>
        </c:if>

        <h2 class="receta-rp">Rp.</h2>
        <c:choose>
          <c:when test="${empty receta.lineas}"><p class="suave">La receta aún no tiene medicamentos.</p></c:when>
          <c:otherwise>
            <table class="tabla">
              <thead><tr><th>#</th><th>Medicamento</th><th>Dosis</th><th>Frecuencia</th><th>Duración</th><th>Vía</th><th class="num">Cantidad</th></tr></thead>
              <tbody>
              <c:forEach var="l" items="${receta.lineas}" varStatus="i">
                <tr><td>${i.count}</td><td><strong><c:out value="${l.producto}"/></strong></td><td><c:out value="${l.dosis}"/></td>
                  <td><c:out value="${l.frecuencia}"/></td><td>${l.duracionDias} ${l.duracionDias == 1 ? 'día' : 'días'}</td>
                  <td><c:out value="${vias[l.via]}"/></td><td class="num"><c:out value="${vs:numero(l.cantidad)}"/> <c:out value="${l.unidad}"/></td></tr>
              </c:forEach>
              </tbody>
            </table>
          </c:otherwise>
        </c:choose>

        <c:if test="${not empty receta.indicaciones}">
          <section class="receta-indicaciones">
            <h3>Indicaciones</h3>
            <p><c:out value="${receta.indicaciones}"/></p>
          </section>
        </c:if>

        <footer class="receta-firma">
          <div class="firma-linea">
            M.V. <c:out value="${veterinario.nombreCompleto()}"/><br>
            CMVP N.° <c:out value="${empty veterinario.colegiatura ? '—' : veterinario.colegiatura}"/>
            <c:if test="${consulta.firmada()}"><br><small>Firmado el <c:out value="${vs:fechaHora(consulta.firmadaEn)}"/></small></c:if>
          </div>
        </footer>
        <p class="receta-pie">Documento generado por VetSalud ERP · Consulta N.° ${consulta.idConsulta}</p>
      </article>
    </c:otherwise>
  </c:choose>
</c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
