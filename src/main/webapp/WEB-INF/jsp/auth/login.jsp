<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>
<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Ingresar · VetSalud ERP</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/app.css">
  <link rel="icon" href="${pageContext.request.contextPath}/static/img/logo.svg" type="image/svg+xml">
</head>
<body>
<div class="acceso">
  <section class="acceso-marca">
    <div>
      <img src="${pageContext.request.contextPath}/static/img/logo.svg" alt="" width="56" height="56">
      <h2>VetSalud ERP</h2>
      <p>Sistema integral de ventas y gestión de la Clínica Veterinaria VetSalud Integral S.A.C.</p>
      <ul>
        <li>Clientes, mascotas y agenda sin cruces de horario</li>
        <li>Historia clínica firmada y recetas trazables</li>
        <li>Farmacia por lotes con vencimientos (FEFO)</li>
        <li>Ventas, pagos mixtos y cierre de caja conciliado</li>
      </ul>
    </div>
    <small>Curso Integrador I: Sistemas – Software · UTP · 2026</small>
  </section>
  <section class="acceso-form">
    <div class="caja">
      <p class="etiqueta-pantalla">P01 · Acceso</p>
      <h1>Iniciar sesión</h1>
      <p class="suave">Ingrese con su usuario de la clínica. Por seguridad, la sesión se cierra tras 15 minutos sin actividad.</p>
      <c:if test="${param.salida == '1'}"><div class="aviso aviso-exito" role="status">Sesión cerrada correctamente.</div></c:if>
      <c:if test="${not empty error}"><div class="aviso aviso-error" role="alert"><c:out value="${error}"/></div></c:if>
      <form method="post" action="${pageContext.request.contextPath}/login" class="formulario" autocomplete="on">
        <input type="hidden" name="_csrf" value="${sessionScope.csrf}">
        <div class="campo">
          <label for="usuario">Usuario</label>
          <input id="usuario" name="usuario" type="text" required maxlength="40" autocomplete="username" autofocus value="<c:out value='${usuarioIngresado}'/>">
        </div>
        <div class="campo">
          <label for="clave">Contraseña</label>
          <input id="clave" name="clave" type="password" required maxlength="100" autocomplete="current-password">
        </div>
        <button type="submit" class="btn btn-primario">Ingresar</button>
        <a href="${pageContext.request.contextPath}/recuperar">¿Olvidó su contraseña?</a>
      </form>
    </div>
  </section>
</div>
</body>
</html>
