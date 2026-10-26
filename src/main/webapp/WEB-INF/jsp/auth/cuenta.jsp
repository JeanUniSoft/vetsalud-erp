<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<c:set var="titulo" value="Mi cuenta"/><c:set var="seccion" value="cuenta"/>
<%@ include file="/WEB-INF/jsp/layout/inicio.jspf" %>
<c:if test="${param.obligatorio == '1'}">
  <div class="aviso aviso-aviso" role="alert">El administrador le asignó una contraseña temporal: debe cambiarla para continuar.</div>
</c:if>
<div class="rejilla rejilla-2">
  <section class="tarjeta">
    <h2>Cambiar contraseña</h2>
    <form method="post" action="${pageContext.request.contextPath}/cuenta" class="formulario">
      <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
      <div class="campo"><label for="actual">Contraseña actual <span class="req">*</span></label>
        <input id="actual" name="actual" type="password" required autocomplete="current-password"></div>
      <div class="campo"><label for="nueva">Nueva contraseña <span class="req">*</span></label>
        <input id="nueva" name="nueva" type="password" required minlength="8" autocomplete="new-password"></div>
      <div class="campo"><label for="confirmacion">Repita la nueva contraseña <span class="req">*</span></label>
        <input id="confirmacion" name="confirmacion" type="password" required minlength="8" autocomplete="new-password"></div>
      <div class="acciones-form"><button type="submit" class="btn btn-primario">Guardar contraseña</button></div>
    </form>
  </section>
  <section class="tarjeta">
    <h2>Política de contraseñas</h2>
    <ul>
      <li>Al menos 8 caracteres.</li>
      <li>Una mayúscula, una minúscula, un número y un símbolo.</li>
      <li>No puede contener su nombre de usuario ni repetir la actual.</li>
      <li>Se guarda con PBKDF2-SHA256 y sal aleatoria: nadie, ni el administrador, puede verla.</li>
    </ul>
    <dl class="ficha">
      <dt>Usuario</dt><dd><c:out value="${sesion.usuario}"/></dd>
      <dt>Rol</dt><dd><c:out value="${vs:etiqueta(sesion.rol)}"/></dd>
    </dl>
  </section>
</div>
<%@ include file="/WEB-INF/jsp/layout/fin.jspf" %>
