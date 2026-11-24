package com.sanmiguelito.servicio;

import com.sanmiguelito.modelo.Movimiento;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.modelo.TipoMovimiento;
import com.sanmiguelito.modelo.Venta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** RF-007 a RF-011 y RF-013 a RF-015: entradas, salidas, ventas, comprobante y reportes. */
@DisplayName("Entradas, salidas, ventas y reportes")
class InventarioYVentasTest extends BaseServicios {

    // ---------------------------------------------------------------- entradas y salidas

    @Test
    @DisplayName("Una entrada aumenta y una salida disminuye las existencias")
    void entradaYSalida() {
        Movimiento e = ctx.inventario.registrarEntrada("PAP-002", 10, "Compra a proveedor", "admin");
        assertEquals(130, e.existenciasResultantes);
        Movimiento s = ctx.inventario.registrarSalida("PAP-002", 3, "Producto dañado", "admin");
        assertEquals(127, s.existenciasResultantes);
        assertEquals(127, existencias("PAP-002"));
        assertEquals(TipoMovimiento.SALIDA, s.tipo);
        assertTrue(s.id > e.id, "los movimientos se numeran en orden");
    }

    @Test
    @DisplayName("Un producto nuevo registrado sin unidades puede recibir su primera entrada")
    void productoNuevoYEntrada() {
        Producto p = ctx.productos.crear(nuevo("Guirnalda dorada", "Navidad", 8000, 0, 2), "admin");
        assertEquals("NAV-003", p.codigo);
        assertEquals(0, existencias("NAV-003"));
        ctx.inventario.registrarEntrada(p.codigo, 25, "Compra a proveedor", "admin");
        assertEquals(25, existencias("NAV-003"));
        assertEquals(1, ctx.inventario.consultar(null, null, "guirnalda", TipoMovimiento.ENTRADA).size());
    }

    @Test
    @DisplayName("No se puede sacar más de lo que hay ni sacar de un producto agotado")
    void salidaSinExistencias() {
        ErrorNegocio e = assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarSalida("ASE-002", 4, "Pérdida", "admin"));
        assertTrue(e.getMessage().contains("No hay unidades suficientes"), e.getMessage());
        assertEquals(3, existencias("ASE-002"), "las existencias no cambian");
        ErrorNegocio agotado = assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarSalida("NAV-002", 1, "Pérdida", "admin"));
        assertTrue(agotado.getMessage().contains("agotado"), agotado.getMessage());
        ctx.inventario.registrarSalida("ASE-002", 3, "Pérdida", "admin");
        assertEquals(0, existencias("ASE-002"), "se puede sacar exactamente lo que hay");
    }

    @ParameterizedTest(name = "cantidad {0}")
    @ValueSource(ints = {0, -1, -100, 1_000_000})
    @DisplayName("Las cantidades cero, negativas o excesivas son rechazadas")
    void cantidadInvalida(int cantidad) {
        assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarEntrada("PAP-001", cantidad, "Compra", "admin"));
        assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarSalida("PAP-001", cantidad, "Pérdida", "admin"));
        assertEquals(40, existencias("PAP-001"));
    }

    @Test
    @DisplayName("Las existencias no pueden superar el máximo permitido")
    void maximoDeExistencias() {
        ctx.inventario.registrarEntrada("PAP-001", ServicioProductos.CANTIDAD_MAXIMA - 40, "Compra", "admin");
        assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarEntrada("PAP-001", 1, "Compra", "admin"));
    }

    @ParameterizedTest(name = "motivo «{0}»")
    @ValueSource(strings = {"", "   ", "ab"})
    @DisplayName("El origen o motivo es obligatorio")
    void motivoObligatorio(String motivo) {
        assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarEntrada("PAP-001", 1, motivo, "admin"));
        assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarSalida("PAP-001", 1, motivo, "admin"));
        assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarEntrada("PAP-001", 1, null, "admin"));
        assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarEntrada("PAP-001", 1, "x".repeat(101), "admin"));
    }

    @Test
    @DisplayName("No se registran movimientos de productos inexistentes o desactivados")
    void productoInvalido() {
        assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarEntrada("NO-EXISTE", 1, "Compra", "admin"));
        assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarEntrada(null, 1, "Compra", "admin"));
        assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarEntrada("  ", 1, "Compra", "admin"));
        ctx.productos.cambiarEstado("PAP-001", false);
        assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarEntrada("PAP-001", 1, "Compra", "admin"));
    }

    @Test
    @DisplayName("El motivo se guarda sin espacios sobrantes y con mayúscula inicial")
    void motivoLimpio() {
        Movimiento m = ctx.inventario.registrarEntrada("PAP-001", 1, "   compra    a proveedor  ", "admin");
        assertEquals("Compra a proveedor", m.motivo);
        assertEquals("Factura #123 de Distribuidora López", ctx.inventario.registrarEntrada("PAP-001", 1,
                "Factura #123 de Distribuidora López", "admin").motivo);
    }

    @ParameterizedTest(name = "motivo inválido «{0}»")
    @ValueSource(strings = {"12345", "<script>", "Compra {}", "Pérdida ~ robo", "@@@"})
    @DisplayName("Los motivos sin palabras o con caracteres extraños son rechazados")
    void motivoInvalido(String motivo) {
        assertThrows(ErrorNegocio.class, () -> ctx.inventario.registrarSalida("PAP-001", 1, motivo, "admin"));
        assertEquals(40, existencias("PAP-001"));
    }

    // ---------------------------------------------------------------- consulta de movimientos (RF-011)

    @Test
    @DisplayName("Los movimientos se filtran por fecha, producto y tipo")
    void consultaDeMovimientos() {
        ctx.inventario.registrarEntrada("PAP-001", 5, "Compra", "admin");
        ctx.inventario.registrarSalida("PAP-001", 2, "Pérdida", "admin");
        LocalDate hoy = LocalDate.now();
        assertEquals(1, ctx.inventario.consultar(hoy, hoy, "PAP-001", TipoMovimiento.SALIDA).size());
        assertEquals(3, ctx.inventario.consultar(hoy, hoy, "cuaderno", null).size(), "inicial + entrada + salida");
        assertTrue(ctx.inventario.consultar(hoy.plusDays(1), null, "", null).isEmpty());
        assertTrue(ctx.inventario.consultar(null, hoy.minusDays(1), "", null).isEmpty());
        List<Movimiento> todos = ctx.inventario.consultar(null, null, "", null);
        assertEquals(TipoMovimiento.SALIDA, todos.get(0).tipo, "el más reciente primero");
        assertEquals(todos.size(), ctx.inventario.movimientosDelDia(java.time.LocalDateTime.now()));
    }

    // ---------------------------------------------------------------- ventas (RF-009, RF-013)

    @Test
    @DisplayName("La venta unifica productos repetidos, descuenta el inventario y numera en orden")
    void venta() {
        ServicioVentas.Carrito c = new ServicioVentas.Carrito();
        c.agregar(producto("PAP-001"), 2);
        c.agregar(producto("PAP-001"), 3);
        c.agregar(producto("PAP-002"), 4);
        assertEquals(2, c.items().size());
        assertEquals(5 * 4500 + 4 * 1200, c.total());
        Venta v = ctx.ventas.confirmar(c, admin);
        assertEquals("V-000001", v.numero);
        assertEquals(5 * 4500 + 4 * 1200, v.total());
        assertEquals(9, v.unidades());
        assertEquals(35, existencias("PAP-001"));
        assertEquals(116, existencias("PAP-002"));
        assertEquals(2, ctx.inventario.consultar(null, null, "", TipoMovimiento.VENTA).size());
        assertTrue(c.vacio(), "la venta en curso queda vacía");

        ServicioVentas.Carrito c2 = new ServicioVentas.Carrito();
        c2.agregar(producto("FAN-001"), 1);
        assertEquals("V-000002", ctx.ventas.confirmar(c2, admin).numero);
    }

    @Test
    @DisplayName("No se puede vender más de lo disponible, ni productos agotados o desactivados")
    void noSeVendeMasDeLoDisponible() {
        ServicioVentas.Carrito c = new ServicioVentas.Carrito();
        Producto pocillo = producto("LOZ-002");   // 6 unidades
        c.agregar(pocillo, 5);
        ErrorNegocio e = assertThrows(ErrorNegocio.class, () -> c.agregar(pocillo, 2));
        assertTrue(e.getMessage().contains("ya hay 5"), e.getMessage());
        assertThrows(ErrorNegocio.class, () -> c.agregar(producto("NAV-002"), 1));
        assertThrows(ErrorNegocio.class, () -> c.agregar(producto("PAP-001"), 0));
        assertThrows(ErrorNegocio.class, () -> c.agregar(producto("PAP-001"), -2));
        assertThrows(ErrorNegocio.class, () -> c.agregar(null, 1));
        ctx.productos.cambiarEstado("PAP-003", false);
        assertThrows(ErrorNegocio.class, () -> c.agregar(producto("PAP-003"), 1));
    }

    @Test
    @DisplayName("Quitar productos y cancelar la venta en curso")
    void quitarYCancelar() {
        ServicioVentas.Carrito c = new ServicioVentas.Carrito();
        c.agregar(producto("PAP-001"), 1);
        c.agregar(producto("PAP-002"), 1);
        c.quitar("PAP-001");
        assertEquals(1, c.items().size());
        assertEquals(1200, c.total());
        c.limpiar();
        assertTrue(c.vacio());
        assertThrows(ErrorNegocio.class, () -> ctx.ventas.confirmar(c, admin), "venta vacía");
        assertThrows(ErrorNegocio.class, () -> ctx.ventas.confirmar(null, admin));
    }

    @Test
    @DisplayName("Al confirmar se valida de nuevo: si otra operación agotó el producto, la venta no se registra")
    void validacionAlConfirmar() {
        ServicioVentas.Carrito c = new ServicioVentas.Carrito();
        c.agregar(producto("LOZ-002"), 6);
        ctx.inventario.registrarSalida("LOZ-002", 2, "Pérdida", "admin");
        assertThrows(ErrorNegocio.class, () -> ctx.ventas.confirmar(c, admin));
        assertEquals(4, existencias("LOZ-002"));
        assertTrue(ctx.ventas.listar(null, null).isEmpty());
        assertFalse(c.vacio(), "la venta en curso se conserva para corregirla");
    }

    @Test
    @DisplayName("Si un producto se desactiva antes de confirmar, la venta no se registra")
    void productoDesactivadoAntesDeConfirmar() {
        ServicioVentas.Carrito c = new ServicioVentas.Carrito();
        c.agregar(producto("PAP-001"), 1);
        ctx.productos.cambiarEstado("PAP-001", false);
        assertThrows(ErrorNegocio.class, () -> ctx.ventas.confirmar(c, admin));
        assertThrows(ErrorNegocio.class, () -> ctx.ventas.confirmar(c, null));
    }

    @Test
    @DisplayName("Si el precio cambió mientras se armaba la venta, se avisa y se actualiza el total")
    void cambioDePrecio() {
        ServicioVentas.Carrito c = new ServicioVentas.Carrito();
        c.agregar(producto("PAP-001"), 2);
        Producto p = producto("PAP-001");
        p.precioVenta = 5000;
        ctx.productos.modificar(p);
        ErrorNegocio e = assertThrows(ErrorNegocio.class, () -> ctx.ventas.confirmar(c, admin));
        assertTrue(e.getMessage().contains("cambió"), e.getMessage());
        assertEquals(10000, c.total(), "el total ya tiene el precio nuevo");
        assertEquals(40, existencias("PAP-001"), "todavía no se descontó nada");
        assertEquals(10000, ctx.ventas.confirmar(c, admin).total(), "la segunda confirmación registra la venta");
    }

    @Test
    @DisplayName("Las ventas se consultan por rango de fechas")
    void listarVentas() {
        ServicioVentas.Carrito c = new ServicioVentas.Carrito();
        c.agregar(producto("PAP-001"), 1);
        ctx.ventas.confirmar(c, admin);
        LocalDate hoy = LocalDate.now();
        assertEquals(1, ctx.ventas.listar(hoy, hoy).size());
        assertTrue(ctx.ventas.listar(hoy.plusDays(1), null).isEmpty());
        assertTrue(ctx.ventas.listar(null, hoy.minusDays(1)).isEmpty());
    }

    // ---------------------------------------------------------------- comprobante y reportes (RF-014, RF-015)

    @Test
    @DisplayName("Se generan el comprobante y los tres reportes en PDF")
    void pdfs() throws Exception {
        ServicioVentas.Carrito c = new ServicioVentas.Carrito();
        c.agregar(producto("PLA-001"), 1);
        c.agregar(producto("MAS-001"), 2);
        Venta v = ctx.ventas.confirmar(c, admin);
        LocalDate hoy = LocalDate.now();
        for (Path pdf : List.of(ctx.reportes.comprobante(v), ctx.reportes.reporteInventario(),
                ctx.reportes.reportePocasExistencias(), ctx.reportes.reporteVentas(null, null), ctx.reportes.reporteVentas(hoy, hoy))) {
            assertTrue(Files.size(pdf) > 500, pdf.toString());
            assertEquals("%PDF", new String(Files.readAllBytes(pdf), 0, 4));
        }
        ServicioReportes.ResumenVentas r = ctx.reportes.resumenVentas(null, null);
        assertEquals(1, r.numeroVentas);
    }

    @Test
    @DisplayName("Los reportes se generan aunque no haya ventas en el período")
    void reportesSinVentas() throws Exception {
        LocalDate manana = LocalDate.now().plusDays(1);
        Path pdf = ctx.reportes.reporteVentas(manana, manana);
        assertTrue(Files.size(pdf) > 500);
        assertEquals(0, ctx.reportes.resumenVentas(manana, manana).numeroVentas);
    }
}
