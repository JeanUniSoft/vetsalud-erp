package pe.vetsalud.erp.web;

import java.beans.FeatureDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;

/**
 * Permite usar records de Java en las vistas JSP: ${cliente.nombres} invoca cliente.nombres().
 * El EL 3.0 de Tomcat 9 solo entiende getters JavaBean; este resolver se registra al iniciar la aplicación.
 */
public class RecordELResolver extends ELResolver {

    @Override
    public Object getValue(ELContext ctx, Object base, Object propiedad) {
        if (base == null || propiedad == null || !base.getClass().isRecord()) {
            return null;
        }
        String nombre = propiedad.toString();
        for (RecordComponent rc : base.getClass().getRecordComponents()) {
            if (rc.getName().equals(nombre)) {
                try {
                    Method acceso = rc.getAccessor();
                    acceso.setAccessible(true);
                    Object valor = acceso.invoke(base);
                    ctx.setPropertyResolved(base, propiedad);
                    return valor;
                } catch (ReflectiveOperationException e) {
                    throw new ELException("No se pudo leer " + nombre, e);
                }
            }
        }
        return null; // no es componente: lo resuelve el BeanELResolver (p. ej. getIniciales())
    }

    @Override
    public Class<?> getType(ELContext ctx, Object base, Object propiedad) {
        return null;
    }

    @Override
    public void setValue(ELContext ctx, Object base, Object propiedad, Object valor) {
        // los records son inmutables
    }

    @Override
    public boolean isReadOnly(ELContext ctx, Object base, Object propiedad) {
        return true;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext ctx, Object base) {
        return null;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext ctx, Object base) {
        return base != null && base.getClass().isRecord() ? Object.class : null;
    }
}
