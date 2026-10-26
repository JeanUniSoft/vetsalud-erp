<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="${empty consulta ? 'Consulta' : 'Consulta N.° '.concat(consulta.idConsulta)}"/>
<c:set var="seccion" value="historia"/><c:set var="cssExtra" value="modulo-consulta"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<p class="etiqueta-pantalla">P05 / P06 · Atención clínica: datos, signos, diagnósticos, procedimientos, receta, firma y adendas (RF-14 a RF-19)</p>

<c:choose>
<c:when test="${empty consulta}">
  <section class="tarjeta">
    <h2>No se encontró la consulta</h2>
    <p>La consulta solicitada no existe o el enlace está incompleto.</p>
    <a class="btn btn-secundario" href="${ctx}/historia">Ir a la historia clínica</a>
  </section>
</c:when>
<c:otherwise>
<c:set var="c" value="${consulta}"/><c:set var="p" value="${permisos}"/><c:set var="m" value="${mascota}"/>

<%-- ===================== Cabecera: paciente, responsable y estado ===================== --%>
<section class="tarjeta consulta-cabecera">
  <div class="cabecera-tarjeta">
    <div>
      <h2><c:out value="${m.nombre}"/> · <c:out value="${c.motivo}"/></h2>
      <small>Consulta N.° ${c.idConsulta} · <c:out value="${vs:fechaHora(c.fechaHora)}"/> · M.V. <c:out value="${c.veterinario}"/><c:if test="${not empty c.colegiatura}"> (CMVP <c:out value="${c.colegiatura}"/>)</c:if>
        <c:if test="${not empty c.idCita}"> · <c:choose><c:when test="${sesion.accede('/citas')}"><a href="${ctx}/citas/ver?id=${c.idCita}">Cita N.° ${c.idCita}</a></c:when><c:otherwise>Cita N.° ${c.idCita}</c:otherwise></c:choose></c:if></small>
      <small><c:out value="${m.especie}"/>, <c:out value="${m.raza}"/> · <c:out value="${m.sexoTexto()}"/> · <c:out value="${vs:edad(m.fechaNacimiento)}"/> · Propietario: <c:out value="${m.cliente}"/> (<c:out value="${m.telefono}"/>)</small>
    </div>
    <div class="acciones-form">
      <span class="${vs:claseEstado(c.estado)}"><c:out value="${vs:etiqueta(c.estado)}"/></span>
      <a class="btn btn-secundario btn-chico" href="${ctx}/historia/mascota?id=${c.idMascota}">Historia clínica</a>
      <c:if test="${not empty receta}"><a class="btn btn-secundario btn-chico" href="${ctx}/consultas/receta?id=${c.idConsulta}">Receta imprimible</a></c:if>
    </div>
  </div>
  <c:if test="${not empty m.alertas}">
    <div class="nota-clinica alerta-clinica" role="note"><strong>⚠ Alertas clínicas:</strong> <span><c:out value="${m.alertas}"/></span></div>
  </c:if>
  <c:choose>
    <c:when test="${c.firmada()}">
      <div class="aviso aviso-exito" role="status"><strong>Consulta firmada</strong> el <c:out value="${vs:fechaHora(c.firmadaEn)}"/> por M.V. <c:out value="${c.veterinario}"/>.
        Es de solo lectura: para agregar información registre una adenda (RN-05).</div>
    </c:when>
    <c:when test="${not p.algunaEdicion()}">
      <div class="aviso aviso-aviso" role="status">Consulta abierta a cargo de M.V. <c:out value="${c.veterinario}"/>: solo el veterinario responsable
        la modifica (el técnico registra signos vitales y procedimientos).</div>
    </c:when>
  </c:choose>
</section>

<div class="rejilla rejilla-2">
  <div>
    <%-- ===================== RF-14 / RF-19: datos clínicos y control ===================== --%>
    <section class="tarjeta" id="datos">
      <h2>Datos clínicos</h2>
      <c:choose>
        <c:when test="${p.datos}">
          <form method="post" action="${ctx}/consultas/datos" class="formulario">
            <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
            <input type="hidden" name="id" value="${c.idConsulta}">
            <div class="campo"><label for="motivo">Motivo de consulta <span class="req">*</span></label>
              <input id="motivo" name="motivo" type="text" required maxlength="300" value="<c:out value='${c.motivo}'/>"></div>
            <div class="campo"><label for="anamnesis">Anamnesis</label>
              <textarea id="anamnesis" name="anamnesis" maxlength="4000" placeholder="Historia de la enfermedad actual, contada por el propietario"><c:out value="${c.anamnesis}"/></textarea></div>
            <div class="campo"><label for="examenClinico">Examen clínico</label>
              <textarea id="examenClinico" name="examenClinico" maxlength="4000"><c:out value="${c.examenClinico}"/></textarea></div>
            <div class="campo"><label for="indicaciones">Indicaciones</label>
              <textarea id="indicaciones" name="indicaciones" maxlength="4000" placeholder="Dieta, reposo, cuidados en casa"><c:out value="${c.indicaciones}"/></textarea></div>
            <div class="campo"><label for="fechaControl">Fecha de control</label>
              <input id="fechaControl" name="fechaControl" type="date" min="${c.fechaHora.toLocalDate().plusDays(1)}" value="<c:out value='${c.fechaControl}'/>">
              <span class="ayuda">Debe ser posterior a la consulta. Al firmar se programa un recordatorio al propietario el día anterior a las 09:00 (RF-19).</span></div>
            <div class="acciones-form"><button type="submit" class="btn btn-primario">Guardar datos clínicos</button></div>
          </form>
        </c:when>
        <c:otherwise>
          <dl class="ficha">
            <dt>Motivo</dt><dd><c:out value="${c.motivo}"/></dd>
            <dt>Anamnesis</dt><dd class="texto-clinico"><c:out value="${empty c.anamnesis ? '—' : c.anamnesis}"/></dd>
            <dt>Examen clínico</dt><dd class="texto-clinico"><c:out value="${empty c.examenClinico ? '—' : c.examenClinico}"/></dd>
            <dt>Indicaciones</dt><dd class="texto-clinico"><c:out value="${empty c.indicaciones ? '—' : c.indicaciones}"/></dd>
            <dt>Control</dt><dd><c:out value="${empty c.fechaControl ? 'Sin control programado' : vs:fecha(c.fechaControl)}"/></dd>
          </dl>
        </c:otherwise>
      </c:choose>
    </section>

    <%-- ===================== RF-14: signos vitales ===================== --%>
    <section class="tarjeta" id="signos">
      <h2>Signos vitales</h2>
      <div class="signos">
        <div class="signo"><div class="etiqueta">Temperatura</div><div class="valor"><c:out value="${empty c.temperaturaC ? '—' : vs:numero(c.temperaturaC)}"/></div><small>°C</small></div>
        <div class="signo"><div class="etiqueta">Frec. cardiaca</div><div class="valor"><c:out value="${empty c.frecCardiaca ? '—' : c.frecCardiaca}"/></div><small>lpm</small></div>
        <div class="signo"><div class="etiqueta">Frec. respiratoria</div><div class="valor"><c:out value="${empty c.frecRespiratoria ? '—' : c.frecRespiratoria}"/></div><small>rpm</small></div>
        <div class="signo"><div class="etiqueta">Peso</div><div class="valor"><c:out value="${empty c.pesoKg ? '—' : vs:numero(c.pesoKg)}"/></div><small>kg</small></div>
      </div>
      <c:if test="${p.signos}">
        <form method="post" action="${ctx}/consultas/signos" class="formulario form-en-tarjeta">
          <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
          <input type="hidden" name="id" value="${c.idConsulta}">
          <div class="campos">
            <div class="campo"><label for="temperatura">Temperatura (°C)</label>
              <input id="temperatura" name="temperatura" type="number" min="30" max="45" step="0.1" value="<c:out value='${c.temperaturaC}'/>"></div>
            <div class="campo"><label for="frecuenciaCardiaca">Frecuencia cardiaca (lpm)</label>
              <input id="frecuenciaCardiaca" name="frecuenciaCardiaca" type="number" min="1" max="400" step="1" value="<c:out value='${c.frecCardiaca}'/>"></div>
            <div class="campo"><label for="frecuenciaRespiratoria">Frecuencia respiratoria (rpm)</label>
              <input id="frecuenciaRespiratoria" name="frecuenciaRespiratoria" type="number" min="1" max="200" step="1" value="<c:out value='${c.frecRespiratoria}'/>"></div>
            <div class="campo"><label for="peso">Peso (kg)</label>
              <input id="peso" name="peso" type="number" min="0.01" max="999.99" step="0.01" value="<c:out value='${c.pesoKg}'/>"></div>
          </div>
          <p class="ayuda">Rangos admitidos: temperatura 30–45 °C, FC 1–400 lpm, FR 1–200 rpm, peso mayor que 0.</p>
          <div class="acciones-form"><button type="submit" class="btn btn-primario">Registrar signos vitales</button></div>
        </form>
      </c:if>
    </section>
  </div>

  <div>
    <%-- ===================== RF-15: diagnósticos ===================== --%>
    <section class="tarjeta" id="diagnosticos">
      <h2>Diagnósticos</h2>
      <c:choose>
        <c:when test="${empty diagnosticos}"><p class="suave">Sin diagnósticos. Para firmar se necesita uno principal (RN-04).</p></c:when>
        <c:otherwise>
          <ul class="lista-diagnosticos">
            <c:forEach var="d" items="${diagnosticos}">
              <li class="${d.esPrincipal ? 'principal' : ''}">
                <span class="${d.esPrincipal ? 'badge badge-ok' : 'badge badge-neutro'}">${d.esPrincipal ? 'Principal' : 'Secundario'}</span>
                <span><c:out value="${d.descripcion}"/></span>
              </li>
            </c:forEach>
          </ul>
        </c:otherwise>
      </c:choose>
      <c:if test="${p.diagnosticar}">
        <form method="post" action="${ctx}/consultas/diagnostico" class="formulario form-en-tarjeta">
          <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
          <input type="hidden" name="id" value="${c.idConsulta}">
          <div class="campo"><label for="descripcion">Nuevo diagnóstico <span class="req">*</span></label>
            <input id="descripcion" name="descripcion" type="text" required maxlength="200" placeholder="Ej.: gastroenteritis aguda"></div>
          <c:choose>
            <c:when test="${tienePrincipal}"><p class="ayuda">Ya hay un diagnóstico principal: los siguientes se registran como secundarios (RF-15).</p></c:when>
            <c:otherwise><label class="casilla"><input type="checkbox" name="principal" checked> Es el diagnóstico principal</label></c:otherwise>
          </c:choose>
          <div class="acciones-form"><button type="submit" class="btn btn-primario">Agregar diagnóstico</button></div>
          <p class="ayuda">Los diagnósticos no se eliminan: quedan en la historia clínica (trazabilidad).</p>
        </form>
      </c:if>
    </section>

    <%-- ===================== RF-16: procedimientos ===================== --%>
    <section class="tarjeta" id="procedimientos">
      <h2>Procedimientos</h2>
      <c:choose>
        <c:when test="${empty procedimientos}"><p class="suave">Sin procedimientos registrados.</p></c:when>
        <c:otherwise>
          <div class="tabla-scroll"><table class="tabla">
            <thead><tr><th>Procedimiento</th><th>Responsable</th><th class="num">Cant.</th><th class="num">P. unit.</th><th class="num">Importe</th></tr></thead>
            <tbody>
            <c:forEach var="x" items="${procedimientos}">
              <tr>
                <td><c:out value="${x.servicio}"/><c:if test="${not empty x.resultado}"><br><small><c:out value="${x.resultado}"/></small></c:if></td>
                <td><c:out value="${x.responsable}"/></td>
                <td class="num">${x.cantidad}</td>
                <td class="num"><c:out value="${vs:soles(x.precioUnitario)}"/></td>
                <td class="num"><c:out value="${vs:soles(x.importe())}"/></td>
              </tr>
            </c:forEach>
            </tbody>
            <tfoot><tr><td colspan="4" class="derecha">Total de procedimientos</td><td class="num"><c:out value="${vs:soles(totalProcedimientos)}"/></td></tr></tfoot>
          </table></div>
        </c:otherwise>
      </c:choose>
      <c:if test="${p.procedimientos}">
        <form method="post" action="${ctx}/consultas/procedimiento" class="formulario form-en-tarjeta">
          <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
          <input type="hidden" name="id" value="${c.idConsulta}">
          <div class="campos">
            <div class="campo"><label for="servicioProc">Procedimiento <span class="req">*</span></label>
              <select id="servicioProc" name="servicio" required>
                <option value="">Seleccione…</option>
                <c:forEach var="s" items="${serviciosProcedimiento}">
                  <option value="${s.idServicio}"><c:out value="${s.nombre}"/> · <c:out value="${vs:soles(s.tarifa)}"/></option>
                </c:forEach>
              </select></div>
            <div class="campo"><label for="responsable">Responsable <span class="req">*</span></label>
              <select id="responsable" name="responsable" required>
                <c:forEach var="r" items="${personal}">
                  <option value="${r.idUsuario}" ${r.idUsuario == sesion.idUsuario ? 'selected' : ''}><c:out value="${r.etiqueta()}"/></option>
                </c:forEach>
              </select></div>
            <div class="campo"><label for="cantidad">Cantidad <span class="req">*</span></label>
              <input id="cantidad" name="cantidad" type="number" required min="1" max="99" step="1" value="1"></div>
            <div class="campo ancho"><label for="resultado">Resultado</label>
              <textarea id="resultado" name="resultado" maxlength="2000" placeholder="Hallazgos o resultado del procedimiento"></textarea></div>
          </div>
          <p class="ayuda">El importe usa la tarifa vigente del servicio, que se copia al registrar (RF-16).</p>
          <div class="acciones-form"><button type="submit" class="btn btn-primario">Registrar procedimiento</button></div>
        </form>
      </c:if>
    </section>

    <%-- ===================== RF-17: receta ===================== --%>
    <section class="tarjeta" id="receta">
      <div class="cabecera-tarjeta">
        <h2>Receta</h2>
        <c:if test="${not empty receta}"><a class="btn btn-secundario btn-chico" href="${ctx}/consultas/receta?id=${c.idConsulta}">Ver e imprimir</a></c:if>
      </div>
      <c:choose>
        <c:when test="${empty receta}"><p class="suave">Aún no se emitió receta para esta consulta.</p></c:when>
        <c:otherwise>
          <p><small>Receta N.° <c:out value="${receta.numero()}"/> · <c:out value="${vs:fechaHora(receta.fecha)}"/></small></p>
          <c:choose>
            <c:when test="${empty receta.lineas}"><p class="suave">La receta aún no tiene medicamentos.</p></c:when>
            <c:otherwise>
              <div class="tabla-scroll"><table class="tabla">
                <thead><tr><th>Medicamento</th><th>Dosis</th><th>Frecuencia</th><th>Duración</th><th>Vía</th><th class="num">Cant.</th></tr></thead>
                <tbody>
                <c:forEach var="l" items="${receta.lineas}">
                  <tr><td><c:out value="${l.producto}"/></td><td><c:out value="${l.dosis}"/></td><td><c:out value="${l.frecuencia}"/></td>
                    <td>${l.duracionDias} ${l.duracionDias == 1 ? 'día' : 'días'}</td><td><c:out value="${vias[l.via]}"/></td>
                    <td class="num"><c:out value="${vs:numero(l.cantidad)}"/> <small><c:out value="${l.unidad}"/></small></td></tr>
                </c:forEach>
                </tbody>
              </table></div>
            </c:otherwise>
          </c:choose>
          <c:if test="${not empty receta.indicaciones and not p.recetar}"><p class="texto-clinico"><strong>Indicaciones:</strong> <c:out value="${receta.indicaciones}"/></p></c:if>
        </c:otherwise>
      </c:choose>
      <c:if test="${p.recetar}">
        <form method="post" action="${ctx}/consultas/medicamento" class="formulario form-en-tarjeta">
          <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
          <input type="hidden" name="id" value="${c.idConsulta}">
          <h3>Agregar medicamento</h3>
          <div class="campos">
            <div class="campo ancho"><label for="producto">Producto <span class="req">*</span></label>
              <select id="producto" name="producto" required>
                <option value="">Seleccione…</option>
                <c:forEach var="pr" items="${productos}">
                  <option value="${pr.idProducto}"><c:out value="${pr.nombre}"/> (<c:out value="${pr.unidad}"/>)${pr.requiereReceta ? ' · requiere receta' : ''}</option>
                </c:forEach>
              </select></div>
            <div class="campo"><label for="dosis">Dosis <span class="req">*</span></label>
              <input id="dosis" name="dosis" type="text" required maxlength="60" placeholder="Ej.: 1 tableta (250 mg)"></div>
            <div class="campo"><label for="frecuencia">Frecuencia <span class="req">*</span></label>
              <input id="frecuencia" name="frecuencia" type="text" required maxlength="60" placeholder="Ej.: cada 12 horas"></div>
            <div class="campo"><label for="duracion">Duración (días) <span class="req">*</span></label>
              <input id="duracion" name="duracion" type="number" required min="1" max="365" step="1"></div>
            <div class="campo"><label for="via">Vía <span class="req">*</span></label>
              <select id="via" name="via" required>
                <c:forEach var="v" items="${vias}"><option value="${v.key}"><c:out value="${v.value}"/></option></c:forEach>
              </select></div>
            <div class="campo"><label for="cantidadMed">Cantidad a dispensar <span class="req">*</span></label>
              <input id="cantidadMed" name="cantidad" type="number" required min="0.01" max="9999" step="0.01" value="1"></div>
          </div>
          <div class="acciones-form"><button type="submit" class="btn btn-primario">Agregar a la receta</button></div>
        </form>
        <form method="post" action="${ctx}/consultas/receta" class="formulario form-en-tarjeta">
          <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
          <input type="hidden" name="id" value="${c.idConsulta}">
          <div class="campo"><label for="indicacionesReceta">Indicaciones generales de la receta</label>
            <textarea id="indicacionesReceta" name="indicaciones" maxlength="2000" placeholder="Ej.: administrar con comida; volver si hay fiebre"><c:out value="${receta.indicaciones}"/></textarea></div>
          <div class="acciones-form"><button type="submit" class="btn btn-secundario">${empty receta ? 'Emitir receta' : 'Guardar indicaciones'}</button></div>
        </form>
      </c:if>
    </section>
  </div>
</div>

<%-- ===================== RF-18 / RF-19: cierre y firma ===================== --%>
<c:if test="${c.abierta()}">
  <section class="tarjeta" id="firma">
    <h2>Cierre y firma (RF-18)</h2>
    <ul class="lista-verificacion">
      <li><span class="${tienePrincipal ? 'marca-ok' : 'marca-falta'}" aria-hidden="true">${tienePrincipal ? '✓' : '✗'}</span>
        <span>Diagnóstico principal ${tienePrincipal ? 'registrado' : 'pendiente: es obligatorio para firmar (RN-04)'}</span></li>
      <li><span class="${c.conSignos() ? 'marca-ok' : 'marca-falta'}" aria-hidden="true">${c.conSignos() ? '✓' : '!'}</span>
        <span>Signos vitales ${c.conSignos() ? 'registrados' : 'sin registrar (recomendado)'}</span></li>
      <li><span class="marca-ok" aria-hidden="true">${empty c.fechaControl ? '–' : '✓'}</span>
        <span>
          <c:choose>
            <c:when test="${empty c.fechaControl}">Sin control programado.</c:when>
            <c:when test="${m.admiteRecordatorios()}">Control el <c:out value="${vs:fecha(c.fechaControl)}"/>: al firmar se programa el recordatorio para el <c:out value="${vs:fechaHora(recordatorioPara)}"/> (RF-19).</c:when>
            <c:otherwise>Control el <c:out value="${vs:fecha(c.fechaControl)}"/>: el propietario no acepta recordatorios por correo; avísele por teléfono.</c:otherwise>
          </c:choose>
        </span></li>
    </ul>
    <c:choose>
      <c:when test="${p.firmar}">
        <form method="post" action="${ctx}/consultas/firmar" data-confirmar="Al firmar, la consulta ya no podrá modificarse (solo adendas). ¿Firmar ahora?">
          <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
          <input type="hidden" name="id" value="${c.idConsulta}">
          <button type="submit" class="btn btn-primario">Firmar y cerrar la consulta</button>
          <span class="ayuda">Si la consulta viene de una cita, la cita pasará a «Atendida».</span>
        </form>
      </c:when>
      <c:otherwise><p class="suave">Solo el veterinario responsable (M.V. <c:out value="${c.veterinario}"/>) firma la consulta, con el permiso CONSULTA_FIRMAR.</p></c:otherwise>
    </c:choose>
  </section>
</c:if>

<%-- ===================== RN-05: adendas ===================== --%>
<section class="tarjeta" id="adendas">
  <h2>Adendas</h2>
  <c:choose>
    <c:when test="${empty adendas}"><p class="suave">${c.firmada() ? 'Sin adendas.' : 'Las adendas se agregan después de firmar la consulta (RN-05).'}</p></c:when>
    <c:otherwise>
      <c:forEach var="a" items="${adendas}">
        <article class="adenda">
          <small><strong><c:out value="${a.autor}"/></strong> · <c:out value="${vs:fechaHora(a.fechaHora)}"/></small>
          <p><c:out value="${a.texto}"/></p>
        </article>
      </c:forEach>
    </c:otherwise>
  </c:choose>
  <c:if test="${p.adenda}">
    <form method="post" action="${ctx}/consultas/adenda" class="formulario form-en-tarjeta">
      <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
      <input type="hidden" name="id" value="${c.idConsulta}">
      <div class="campo"><label for="textoAdenda">Nueva adenda <span class="req">*</span></label>
        <textarea id="textoAdenda" name="texto" required maxlength="2000" placeholder="Información posterior a la firma (resultados de laboratorio, evolución…)"></textarea></div>
      <div class="acciones-form"><button type="submit" class="btn btn-primario">Agregar adenda</button></div>
    </form>
  </c:if>
</section>
</c:otherwise>
</c:choose>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
