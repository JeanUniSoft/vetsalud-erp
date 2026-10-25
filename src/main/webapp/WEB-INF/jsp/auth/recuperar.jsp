<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Recuperar acceso · VetSalud ERP</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/app.css">
</head>
<body>
<div class="acceso">
  <section class="acceso-marca">
    <div>
      <img src="${pageContext.request.contextPath}/static/img/logo.svg" alt="" width="56" height="56">
      <h2>Recuperación controlada</h2>
      <p>Por seguridad, la contraseña no se envía por correo: el administrador verifica su identidad y le asigna una clave temporal que deberá cambiar al ingresar (RF-03).</p>
    </div>
    <small>VetSalud ERP</small>
  </section>
  <section class="acceso-form">
    <div class="caja">
      <p class="etiqueta-pantalla">P01 · Recuperación</p>
      <h1>Recuperar acceso</h1>
      <c:choose>
        <c:when test="${enviado}">
          <div class="aviso aviso-exito" role="status">Si los datos corresponden a una cuenta, el administrador recibió su solicitud y se comunicará con usted.</div>
        </c:when>
        <c:otherwise>
          <c:if test="${not empty error}"><div class="aviso aviso-error" role="alert"><c:out value="${error}"/></div></c:if>
          <form method="post" action="${pageContext.request.contextPath}/recuperar" class="formulario">
            <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
            <div class="campo">
              <label for="dato">Usuario o correo registrado</label>
              <input id="dato" name="dato" type="text" required maxlength="120">
            </div>
            <button type="submit" class="btn btn-primario">Solicitar restablecimiento</button>
          </form>
        </c:otherwise>
      </c:choose>
      <p><a href="${pageContext.request.contextPath}/login">Volver al inicio de sesión</a></p>
    </div>
  </section>
</div>
</body>
</html>
