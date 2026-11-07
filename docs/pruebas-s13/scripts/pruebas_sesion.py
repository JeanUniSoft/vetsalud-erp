"""Expiración real de sesión: con MINUTOS_SESION=1 una sesión inactiva 65 s debe caducar (RNF-05). Ejecutar tras reiniciar."""
import json, sys, time
from cliente import *
sql("update vetsalud.parametro set valor='1' where clave='MINUTOS_SESION'")
s=entrar("dramos"); a=s.get("/panel").status; time.sleep(65); r=s.get("/panel")
sql("update vetsalud.parametro set valor='15' where clave='MINUTOS_SESION'")
ok=r.status==302 and "login" in r.cab.get("location","")
print(f"{'PASA ' if ok else 'FALLA'} SEG-41 sesión activa HTTP {a}; tras 65 s inactiva HTTP {r.status} → {r.cab.get('location')}")
json.dump([dict(id="SEG-41",cat="A07",nombre="La sesión caduca por inactividad (parámetro MINUTOS_SESION=1, espera de 65 s)",ok=ok,obs=f"activa HTTP {a}; tras 65 s HTTP {r.status} → {r.cab.get('location')}",sev="—")],open(sys.argv[1],"w"),ensure_ascii=False)
