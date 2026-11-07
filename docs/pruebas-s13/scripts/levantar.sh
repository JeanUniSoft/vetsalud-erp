#!/bin/zsh
# Compila la copia de trabajo y la levanta en :8090 contra la base clonada vetsalud_s13 (no toca vetsalud_erp).
# Uso: levantar.sh [--reset]   (--reset vuelve a clonar la base desde vetsalud_erp)
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home
cd "$(dirname "$0")/../../.."
pkill -f "puerto=8090" 2>/dev/null; sleep 1
if [[ "$1" == "--reset" ]]; then
  psql -q -d postgres -c "DROP DATABASE IF EXISTS vetsalud_s13" -c "CREATE DATABASE vetsalud_s13 TEMPLATE vetsalud_erp" \
    -c "ALTER DATABASE vetsalud_s13 SET search_path TO vetsalud, public" -c "ALTER DATABASE vetsalud_s13 SET timezone TO 'America/Lima'"
  rm -rf /tmp/vetsalud_s13_logs
fi
mvn -q -o compile dependency:build-classpath -DincludeScope=compile -Dmdep.outputFile=target/cp.txt >/dev/null || exit 1
sed -i '' 's#/vetsalud_erp?#/vetsalud_s13?#' target/classes/vetsalud.properties
nohup $JAVA_HOME/bin/java -Dpuerto=8090 -Dvetsalud.logdir=/tmp/vetsalud_s13_logs -cp target/classes:$(cat target/cp.txt) pe.vetsalud.erp.Servidor > /tmp/vetsalud_s13_server.out 2>&1 &
for i in {1..40}; do curl -s -o /dev/null http://localhost:8090/login && { echo "arriba en :8090"; exit 0; }; sleep 0.5; done
echo "no levantó"; tail -20 /tmp/vetsalud_s13_server.out; exit 1
