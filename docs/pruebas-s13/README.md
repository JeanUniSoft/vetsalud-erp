# Pruebas de software y de seguridad (S13)

Scripts y resultados del avance de la semana 13. Cada corrida se hace sobre una base aislada, `vetsalud_s13`, clonada de la base de demostración `vetsalud_erp`.

```bash
docs/pruebas-s13/scripts/levantar.sh --reset      # recrea la base aislada y levanta el ERP en :8090
cd docs/pruebas-s13/scripts
python3 pruebas_seguridad.py seguridad.json       # batería OWASP (42 casos)
python3 pruebas_sesion.py sesion.json             # expiración de sesión (SEG-41, espera 65 s)
python3 pruebas_funcionales.py funcionales.json   # regresión (17 casos)
python3 prueba_carga.py carga.json 50 80          # 50 usuarios, 80 operaciones cada uno
```

`evidencias/` guarda los resultados de antes (`*_antes`, v0.4.0) y de después de las correcciones. Requiere JDK 25, Maven, PostgreSQL 18 y Python 3; `JAVA_HOME` está fijado en `levantar.sh` para macOS y debe ajustarse en otros equipos. Las claves de los scripts son las de laboratorio ya documentadas en el README del proyecto.
