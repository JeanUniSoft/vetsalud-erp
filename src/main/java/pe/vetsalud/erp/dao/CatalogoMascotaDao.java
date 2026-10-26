package pe.vetsalud.erp.dao;

import java.sql.SQLException;
import java.util.List;

import pe.vetsalud.erp.modelo.Especie;
import pe.vetsalud.erp.modelo.Raza;

/** Catálogos de especies y razas (RF-07). Cambian poco: el servicio los guarda en caché (Guava). */
public interface CatalogoMascotaDao {

    List<Especie> especies() throws SQLException;

    /** Todas las razas con su especie, ordenadas por especie y nombre. */
    List<Raza> razas() throws SQLException;
}
