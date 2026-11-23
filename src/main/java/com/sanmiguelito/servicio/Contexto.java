package com.sanmiguelito.servicio;

import com.sanmiguelito.datos.Repositorio;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.modelo.Rol;
import com.sanmiguelito.modelo.Usuario;

/** Punto de acceso a los servicios y a la sesión del usuario autenticado. */
public class Contexto {

    public final Repositorio repo;
    public final ServicioUsuarios usuarios;
    public final ServicioProductos productos;
    public final ServicioInventario inventario;
    public final ServicioVentas ventas;
    public final ServicioReportes reportes;
    public final ServicioRespaldos respaldos;
    private Usuario sesion;

    public Contexto(Repositorio repo) {
        this.repo = repo;
        usuarios = new ServicioUsuarios(repo);
        productos = new ServicioProductos(repo);
        inventario = new ServicioInventario(repo);
        ventas = new ServicioVentas(repo);
        reportes = new ServicioReportes(repo, productos, ventas);
        respaldos = new ServicioRespaldos(repo);
    }

    public Usuario sesion() { return sesion; }

    public void iniciarSesion(Usuario u) { sesion = u; }

    public void cerrarSesion() { sesion = null; }

    public boolean esAdministrador() { return sesion != null && sesion.rol == Rol.ADMINISTRADOR; }

    /** Exige el rol de administrador para operaciones restringidas (RF-012). */
    public void exigirAdministrador() {
        if (!esAdministrador()) throw new ErrorNegocio("Esta opción solo está disponible para el administrador.");
    }

    /**
     * Primer arranque: crea los usuarios iniciales y un catálogo de ejemplo con productos
     * típicos de la cacharrería, para que el establecimiento pueda probar el sistema.
     */
    public boolean prepararPrimerUso() {
        if (!usuarios.listar().isEmpty()) return false;
        usuarios.crear("admin", "Administrador San Miguelito", Rol.ADMINISTRADOR, "SanMiguelito2026");
        usuarios.crear("vendedor", "Vendedor San Miguelito", Rol.VENDEDOR, "Vendedor2026");
        if (productos.listar(true).isEmpty()) {
            Object[][] ejemplo = {
                    {"PAP-001", "Cuaderno cuadriculado 100 hojas", "Papelería", 4500L, 40, 10},
                    {"PAP-002", "Lapicero negro", "Papelería", 1200L, 120, 30},
                    {"PAP-003", "Resma papel carta", "Papelería", 22000L, 12, 4},
                    {"ESC-001", "Caja de colores x12", "Útiles escolares", 8500L, 18, 5},
                    {"ESC-002", "Tijeras escolares", "Útiles escolares", 3500L, 6, 6},
                    {"HOG-001", "Juego de vasos x6", "Hogar", 18000L, 8, 3},
                    {"HOG-002", "Balde plástico 12 L", "Hogar", 9500L, 10, 3},
                    {"ASE-001", "Escoba", "Aseo", 7000L, 15, 4},
                    {"ASE-002", "Detergente en polvo 1 kg", "Aseo", 9800L, 3, 5},
                    {"JUG-001", "Pelota de plástico", "Juguetería", 6000L, 9, 3},
                    {"PIN-001", "Bolsa de bombas x50", "Piñatería", 9000L, 14, 4},
                    {"PIN-002", "Velas de cumpleaños", "Piñatería", 2500L, 0, 5},
                    {"BIS-001", "Moños para el cabello x3", "Bisutería", 3000L, 25, 6},
            };
            for (Object[] e : ejemplo) {
                productos.crear(new Producto((String) e[0], (String) e[1], (String) e[2], (Long) e[3], (Integer) e[4], (Integer) e[5]), "admin");
            }
        }
        return true;
    }

    /** Productos activos aptos para la venta. */
    public java.util.List<Producto> productosVendibles(String texto) {
        return productos.buscar(texto, false);
    }
}
