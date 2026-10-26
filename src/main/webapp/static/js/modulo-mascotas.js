// VetSalud ERP · formulario de mascota (RF-07): muestra solo las razas de la especie elegida.
// Mejora progresiva: sin JavaScript se ven todas las razas agrupadas y el servidor valida la coherencia.
(function () {
  var especie = document.getElementById('idEspecie');
  var raza = document.getElementById('idRaza');
  if (!especie || !raza) { return; }
  var grupos = Array.prototype.slice.call(raza.querySelectorAll('optgroup[data-especie]'));

  function filtrar() {
    var elegida = especie.value;
    grupos.forEach(function (g) {
      var visible = !elegida || g.getAttribute('data-especie') === elegida;
      g.hidden = !visible;
      g.disabled = !visible;
    });
    var opcion = raza.options[raza.selectedIndex];
    if (opcion && opcion.parentNode && opcion.parentNode.disabled) { raza.value = ''; }
  }

  especie.addEventListener('change', filtrar);
  filtrar();
})();
