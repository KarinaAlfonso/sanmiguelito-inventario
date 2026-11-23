package com.sanmiguelito.servicio;

import com.sanmiguelito.datos.Repositorio;
import com.sanmiguelito.modelo.Movimiento;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.modelo.Rol;
import com.sanmiguelito.modelo.TipoMovimiento;
import com.sanmiguelito.modelo.Usuario;
import com.sanmiguelito.modelo.Venta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pruebas de las reglas del negocio sobre una carpeta de datos temporal. */
class ServiciosTest {

    @TempDir
    Path dir;
    Contexto ctx;
    Usuario admin;

    @BeforeEach
    void preparar() {
        ctx = new Contexto(new Repositorio(dir));
        assertTrue(ctx.prepararPrimerUso());
        admin = ctx.usuarios.autenticar("admin", "SanMiguelito2026");
        ctx.iniciarSesion(admin);
    }

    // ------------------------------------------------------------ RF-001 / RNF-002

    @Test
    void lasContrasenasNoSeGuardanEnTextoPlano() throws Exception {
        String json = Files.readString(dir.resolve("datos/usuarios.json"));
        assertFalse(json.contains("SanMiguelito2026"));
        assertFalse(json.contains("Vendedor2026"));
    }

    @Test
    void credencialesIncorrectasSonRechazadas() {
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.autenticar("admin", "otra"));
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.autenticar("noexiste", "SanMiguelito2026"));
    }

    @Test
    void usuarioDesactivadoNoPuedeIngresar() {
        ctx.usuarios.modificar("vendedor", "Vendedor", Rol.VENDEDOR, false, "admin");
        ErrorNegocio e = assertThrows(ErrorNegocio.class, () -> ctx.usuarios.autenticar("vendedor", "Vendedor2026"));
        assertTrue(e.getMessage().contains("desactivado"));
    }

    @Test
    void siempreDebeExistirUnAdministradorActivo() {
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.modificar("admin", "Admin", Rol.VENDEDOR, true, "admin"));
    }

    @Test
    void contrasenaDebilEsRechazada() {
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.crear("caja2", "Caja dos", Rol.VENDEDOR, "corta1"));
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.crear("caja2", "Caja dos", Rol.VENDEDOR, "sinnumeros"));
    }

    // ------------------------------------------------------------ RF-002 a RF-006

    @Test
    void codigoDeProductoDuplicadoEsRechazado() {
        Producto p = new Producto("PAP-001", "Otro", "Papelería", 1000, 1, 1);
        assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(p, "admin"));
    }

    @Test
    void cantidadInicialQuedaComoMovimientoDeEntrada() {
        ctx.productos.crear(new Producto("NUE-001", "Producto nuevo", "Hogar", 5000, 7, 2), "admin");
        List<Movimiento> m = ctx.inventario.consultar(null, null, "NUE-001", TipoMovimiento.ENTRADA);
        assertEquals(1, m.size());
        assertEquals(7, m.get(0).cantidad);
    }

    @Test
    void laBusquedaIgnoraTildesYMayusculas() {
        assertFalse(ctx.productos.buscar("PAPELERIA", false).isEmpty());
        assertFalse(ctx.productos.buscar("cuaderno", false).isEmpty());
    }

    @Test
    void productoDesactivadoNoApareceEnLaVenta() {
        ctx.productos.cambiarEstado("PAP-001", false);
        assertTrue(ctx.productosVendibles("PAP-001").isEmpty());
        assertEquals(1, ctx.productos.buscar("PAP-001", true).size());
    }

    // ------------------------------------------------------------ RF-007 a RF-009

    @Test
    void entradaAumentaYSalidaDisminuyeExistencias() {
        ctx.inventario.registrarEntrada("PAP-002", 10, "Compra a proveedor", "admin");
        ctx.inventario.registrarSalida("PAP-002", 3, "Producto dañado", "admin");
        assertEquals(120 + 10 - 3, ctx.productos.porCodigo("PAP-002").orElseThrow().existencias);
    }

    @Test
    void salidaMayorALasExistenciasEsBloqueada() {
        ErrorNegocio e = assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarSalida("ASE-002", 99, "Pérdida", "admin"));
        assertTrue(e.getMessage().contains("No hay unidades suficientes"));
        assertEquals(3, ctx.productos.porCodigo("ASE-002").orElseThrow().existencias);
    }

    // ------------------------------------------------------------ RF-009, RF-013

    @Test
    void ventaUnificaProductosRepetidosYDescuentaInventario() {
        ServicioVentas.Carrito c = new ServicioVentas.Carrito();
        Producto cuaderno = ctx.productos.porCodigo("PAP-001").orElseThrow();
        c.agregar(cuaderno, 2);
        c.agregar(cuaderno, 3);
        c.agregar(ctx.productos.porCodigo("PAP-002").orElseThrow(), 4);
        assertEquals(2, c.items().size());
        Venta v = ctx.ventas.confirmar(c, admin);
        assertEquals("V-000001", v.numero);
        assertEquals(5 * 4500 + 4 * 1200, v.total());
        assertEquals(35, ctx.productos.porCodigo("PAP-001").orElseThrow().existencias);
        assertEquals(2, ctx.inventario.consultar(null, null, "", TipoMovimiento.VENTA).size());
        assertTrue(c.vacio());
    }

    @Test
    void noSePuedeVenderMasDeLoDisponible() {
        ServicioVentas.Carrito c = new ServicioVentas.Carrito();
        Producto tijeras = ctx.productos.porCodigo("ESC-002").orElseThrow(); // 6 unidades
        c.agregar(tijeras, 5);
        assertThrows(ErrorNegocio.class, () -> c.agregar(tijeras, 2));
        assertThrows(ErrorNegocio.class, () -> c.agregar(ctx.productos.porCodigo("PIN-002").orElseThrow(), 1)); // agotado
    }

    @Test
    void laVentaSeValidaDeNuevoAlConfirmar() {
        ServicioVentas.Carrito c = new ServicioVentas.Carrito();
        c.agregar(ctx.productos.porCodigo("ESC-002").orElseThrow(), 6);
        ctx.inventario.registrarSalida("ESC-002", 2, "Pérdida", "admin"); // otra operación deja 4 unidades
        assertThrows(ErrorNegocio.class, () -> ctx.ventas.confirmar(c, admin));
        assertEquals(4, ctx.productos.porCodigo("ESC-002").orElseThrow().existencias);
        assertTrue(ctx.ventas.listar(null, null).isEmpty());
    }

    // ------------------------------------------------------------ RF-014, RF-015

    @Test
    void seGeneranElComprobanteYLosReportesEnPdf() throws Exception {
        ServicioVentas.Carrito c = new ServicioVentas.Carrito();
        c.agregar(ctx.productos.porCodigo("HOG-001").orElseThrow(), 1);
        Venta v = ctx.ventas.confirmar(c, admin);
        for (Path pdf : List.of(ctx.reportes.comprobante(v), ctx.reportes.reporteInventario(),
                ctx.reportes.reportePocasExistencias(), ctx.reportes.reporteVentas(null, null))) {
            assertTrue(Files.size(pdf) > 500, pdf.toString());
            assertEquals("%PDF", new String(Files.readAllBytes(pdf), 0, 4));
        }
        assertEquals(1, ctx.reportes.resumenVentas(null, null).numeroVentas);
    }

    // ------------------------------------------------------------ RNF-005, RNF-006

    @Test
    void respaldoYRestauracionRecuperanLaInformacion() {
        Path respaldo = ctx.respaldos.crearRespaldo(null);
        ctx.inventario.registrarSalida("PAP-001", 40, "Pérdida", "admin");
        assertEquals(0, ctx.productos.porCodigo("PAP-001").orElseThrow().existencias);
        ctx.respaldos.restaurar(respaldo);
        assertEquals(40, ctx.productos.porCodigo("PAP-001").orElseThrow().existencias);
        assertTrue(ctx.respaldos.listar().stream().anyMatch(p -> p.getFileName().toString().contains("antes_de_restaurar")));
    }

    @Test
    void elGuardadoNoDejaArchivosTemporales() throws Exception {
        ctx.inventario.registrarEntrada("PAP-001", 1, "Compra", "admin");
        try (var s = Files.list(dir.resolve("datos"))) {
            assertTrue(s.noneMatch(p -> p.toString().endsWith(".tmp")));
        }
    }

    @Test
    void valoresNumericosInvalidosSonRechazados() {
        assertThrows(ErrorNegocio.class, () -> Formato.leerEntero("-3", "Cantidad"));
        assertThrows(ErrorNegocio.class, () -> Formato.leerEntero("2.5", "Cantidad"));
        assertThrows(ErrorNegocio.class, () -> Formato.leerPesos("abc", "Precio"));
        assertEquals(12500, Formato.leerPesos("$ 12.500", "Precio"));
    }
}
