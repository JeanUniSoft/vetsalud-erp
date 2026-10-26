// VetSalud ERP · comportamiento mínimo (sin scripts en línea: lo exige la CSP)
document.addEventListener('submit', function (e) {
  var form = e.target;
  var mensaje = form.getAttribute('data-confirmar');
  if (mensaje && !window.confirm(mensaje)) {
    e.preventDefault();
    return;
  }
  // Evita el doble envío (dos ventas o dos pagos por un doble clic)
  var botones = form.querySelectorAll('button[type=submit]');
  setTimeout(function () { botones.forEach(function (b) { b.disabled = true; }); }, 0);
});
document.addEventListener('click', function (e) {
  var boton = e.target.closest('[data-imprimir]');
  if (boton) { window.print(); }
});
document.addEventListener('change', function (e) {
  var el = e.target;
  if (el.matches('[data-autoenviar]')) { el.form.submit(); }
});
