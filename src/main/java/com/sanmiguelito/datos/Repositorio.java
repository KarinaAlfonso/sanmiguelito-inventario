package com.sanmiguelito.datos;

import com.sanmiguelito.modelo.Movimiento;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.modelo.Usuario;
import com.sanmiguelito.modelo.Venta;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Ubicación de los archivos de datos (almacenes D1, D2, D3, D4 y D5 de la Figura 8.1).
 *
 * Por defecto los datos se guardan en la carpeta del usuario de Windows:
 * (carpeta personal)/SanMiguelito/datos. Se puede cambiar con la propiedad
 * del sistema "sanmiguelito.dir" (se usa en las pruebas).
 */
public class Repositorio {

    private final Path base;
    public final AlmacenJson<Usuario> usuarios;       // D1
    public final AlmacenJson<Producto> productos;     // D2
    public final AlmacenJson<Movimiento> movimientos; // D3
    public final AlmacenJson<Venta> ventas;           // D5

    public Repositorio(Path base) {
        this.base = base;
        Path datos = base.resolve("datos");
        usuarios = new AlmacenJson<>(datos.resolve("usuarios.json"), Usuario.class,
                u -> texto(u.usuario) && u.rol != null && texto(u.sal) && texto(u.hashContrasena) && u.nombreCompleto != null);
        productos = new AlmacenJson<>(datos.resolve("productos.json"), Producto.class,
                p -> texto(p.codigo) && texto(p.nombre) && texto(p.categoria) && p.existencias >= 0
                        && p.precioVenta >= 0 && p.cantidadMinima >= 0);
        movimientos = new AlmacenJson<>(datos.resolve("movimientos.json"), Movimiento.class,
                m -> m.tipo != null && texto(m.codigoProducto) && fecha(m.fecha) && m.cantidad > 0);
        ventas = new AlmacenJson<>(datos.resolve("ventas.json"), Venta.class,
                v -> v.numero != null && v.numero.matches("V-\\d{1,9}") && fecha(v.fecha) && v.items != null
                        && v.items.stream().allMatch(i -> i != null && texto(i.codigoProducto) && i.cantidad > 0));
    }

    private static boolean texto(String s) { return s != null && !s.isBlank(); }

    private static boolean fecha(String iso) {
        try {
            java.time.LocalDateTime.parse(iso, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static Repositorio predeterminado() {
        String dir = System.getProperty("sanmiguelito.dir");
        Path base = dir != null ? Paths.get(dir) : Paths.get(System.getProperty("user.home"), "SanMiguelito");
        return new Repositorio(base);
    }

    public Path base() { return base; }

    public Path carpetaDatos() { return base.resolve("datos"); }

    /** D4: carpeta donde se guardan las copias de seguridad. */
    public Path carpetaRespaldos() { return base.resolve("respaldos"); }

    /** Carpeta donde se guardan los comprobantes y reportes en PDF. */
    public Path carpetaDocumentos() { return base.resolve("documentos"); }
}
