"""Cliente HTTP mínimo (solo biblioteca estándar) con sesión, CSRF y rutas crudas sin normalizar."""
import http.client, json, re, subprocess, time, uuid
HOST, PUERTO = "localhost", 8090
CLAVE = "VetSalud2026!"

class Sesion:
    def __init__(self): self.cookies, self.ultimo = {}, None
    def pedir(self, metodo, ruta, datos=None, cabeceras=None, cuerpo=None):
        c = http.client.HTTPConnection(HOST, PUERTO, timeout=60)
        h = dict(cabeceras or {})
        if self.cookies: h["Cookie"] = "; ".join(f"{k}={v}" for k, v in self.cookies.items())
        if datos is not None:
            from urllib.parse import urlencode
            cuerpo = urlencode(datos); h["Content-Type"] = "application/x-www-form-urlencoded"
        t = time.perf_counter()
        c.putrequest(metodo, ruta, skip_accept_encoding=True)
        for k, v in h.items(): c.putheader(k, v)
        b = cuerpo.encode() if isinstance(cuerpo, str) else cuerpo
        if b is not None: c.putheader("Content-Length", str(len(b)))
        c.endheaders(b)
        r = c.getresponse(); texto = r.read()
        r.seg = time.perf_counter() - t; r.bytes = texto; r.texto = texto.decode("utf-8", "replace")
        r.cab = {k.lower(): v for k, v in r.getheaders()}
        r.set_cookie = [v for k, v in r.getheaders() if k.lower() == "set-cookie"]
        for v in r.set_cookie:
            k, val = v.split(";")[0].split("=", 1); self.cookies[k] = val
        c.close(); self.ultimo = r
        return r
    def get(self, ruta, **kw): return self.pedir("GET", ruta, **kw)
    def csrf(self, ruta="/login"):
        r = self.get(ruta)
        m = re.search(r'name="_csrf" value="([^"]+)"', r.texto)
        return m.group(1) if m else None
    def post(self, ruta, datos, form="/login", token=None):
        d = dict(datos); d["_csrf"] = token or self.csrf(form)
        return self.pedir("POST", ruta, datos=d)
    def login(self, usuario, clave=CLAVE):
        return self.post("/login", {"usuario": usuario, "clave": clave})

def entrar(usuario, clave=CLAVE):
    s = Sesion(); r = s.login(usuario, clave)
    assert r.status == 302 and "login" not in r.cab.get("location", ""), f"{usuario}: login falló {r.status}"
    return s

def sql(q, bd="vetsalud_s13", usuario=None, clave=None):
    import os
    env = dict(os.environ); cmd = ["psql", "-d", bd, "-At", "-c", q]
    if usuario: cmd += ["-U", usuario, "-h", "localhost"]; env["PGPASSWORD"] = clave
    p = subprocess.run(cmd, capture_output=True, text=True, env=env)
    return (p.stdout + p.stderr).strip()
