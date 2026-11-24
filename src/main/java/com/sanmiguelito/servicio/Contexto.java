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
            // El código se asigna automáticamente según la categoría (PAP-001, NAV-001, ASE-001…).
            Object[][] ejemplo = {
                    {"Cuaderno cuadriculado 100 hojas", "Papelería", 4500L, 40, 10},
                    {"Lapicero negro", "Papelería", 1200L, 120, 30},
                    {"Resma papel carta", "Papelería", 22000L, 12, 4},
                    {"Luces navideñas 100 bombillos", "Navidad", 25000L, 10, 3},
                    {"Bolas de Navidad x6", "Navidad", 12000L, 0, 4},
                    {"Escoba", "Aseo", 7000L, 15, 4},
                    {"Detergente en polvo 1 kg", "Aseo", 9800L, 3, 5},
                    {"Balde plástico 12 L", "Plástico", 9500L, 10, 3},
                    {"Juego de vasos plásticos x6", "Plástico", 6000L, 8, 3},
                    {"Plato hondo de loza", "Loza", 5500L, 24, 6},
                    {"Pocillo de loza", "Loza", 4000L, 6, 6},
                    {"Pelota de caucho", "Juguetería", 6000L, 9, 3},
                    {"Moños para el cabello x3", "Fantasía", 3000L, 25, 6},
                    {"Plato para mascota", "Mascotas", 8000L, 7, 2},
            };
            for (Object[] e : ejemplo) {
                productos.crear(new Producto(null, (String) e[0], (String) e[1], (Long) e[2], (Integer) e[3], (Integer) e[4]), "admin");
            }
        }
        return true;
    }

    /** Productos activos aptos para la venta. */
    public java.util.List<Producto> productosVendibles(String texto) {
        return productos.buscar(texto, false);
    }
}
