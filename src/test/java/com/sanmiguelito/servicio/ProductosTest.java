package com.sanmiguelito.servicio;

import com.sanmiguelito.modelo.Movimiento;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.modelo.TipoMovimiento;
import com.sanmiguelito.modelo.Venta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** RF-002 a RF-006 y RF-010: productos, categorías y código automático. */
@DisplayName("Productos, categorías y código automático")
class ProductosTest extends BaseServicios {

    // ---------------------------------------------------------------- código automático

    @Test
    @DisplayName("Los productos de ejemplo reciben el código de su categoría")
    void codigosDeEjemplo() {
        assertEquals("Cuaderno cuadriculado 100 hojas", producto("PAP-001").nombre);
        assertEquals("Navidad", producto("NAV-001").categoria);
        assertEquals("Escoba", producto("ASE-001").nombre);
        assertEquals("Plástico", producto("PLA-001").categoria);
        assertEquals("Loza", producto("LOZ-001").categoria);
        assertEquals("Juguetería", producto("JUG-001").categoria);
        assertEquals("Fantasía", producto("FAN-001").categoria);
        assertEquals("Mascotas", producto("MAS-001").categoria);
    }

    @ParameterizedTest(name = "«{0}» → {1}")
    @CsvSource({"Papelería, PAP-004", "papeleria, PAP-004", "PAPELERÍA, PAP-004", "Navidad, NAV-003", "aseo, ASE-003",
            "plastico, PLA-003", "Plásticos, PLA-003", "loza, LOZ-003", "jugueteria, JUG-002", "fantasia, FAN-002",
            "mascota, MAS-002", "Otro, OTR-001", "otros, OTR-001"})
    @DisplayName("El siguiente código depende de la categoría, sin importar mayúsculas, tildes ni plural")
    void siguienteCodigoPorCategoria(String categoria, String esperado) {
        assertEquals(esperado, ctx.productos.siguienteCodigo(categoria));
    }

    @Test
    @DisplayName("Al crear un producto se asigna el código consecutivo de la categoría")
    void crearAsignaCodigo() {
        Producto p = ctx.productos.crear(nuevo("Recogedor", "aseo", 5000, 4, 2), "admin");
        assertEquals("ASE-003", p.codigo);
        assertEquals("Aseo", p.categoria, "la categoría se guarda con su nombre oficial");
        assertEquals("ASE-004", ctx.productos.crear(nuevo("Trapero", "Aseo", 9000, 0, 2), "admin").codigo);
    }

    @Test
    @DisplayName("Una categoría nueva recibe un prefijo propio de tres letras")
    void categoriaNueva() {
        Producto p = ctx.productos.crear(nuevo("Pila AA x2", "ferretería", 3000, 10, 2), "admin");
        assertEquals("FER-001", p.codigo);
        assertEquals("Ferretería", p.categoria);
        assertEquals("FER-002", ctx.productos.siguienteCodigo("FERRETERIA"));
        assertTrue(ctx.productos.categorias().contains("Ferretería"));
        assertEquals("Otro", ctx.productos.categorias().get(ctx.productos.categorias().size() - 1), "«Otro» va al final");
    }

    @Test
    @DisplayName("Si el prefijo de una categoría nueva ya existe, se usa otro")
    void prefijoNoSeRepite() {
        Producto p = ctx.productos.crear(nuevo("Bombillo LED", "Pantallas", 8000, 3, 1), "admin");
        assertEquals("PAN-001", p.codigo);
        Producto q = ctx.productos.crear(nuevo("Bolsa de regalo", "Papeles de regalo", 1500, 3, 1), "admin");
        assertFalse(q.codigo.startsWith("PAP-"), "no puede usar el prefijo de Papelería: " + q.codigo);
        assertNotEquals(p.codigo.substring(0, 3), q.codigo.substring(0, 3));
    }

    @Test
    @DisplayName("Las categorías predefinidas están disponibles en el formulario")
    void categoriasPredefinidas() {
        List<String> c = ctx.productos.categorias();
        assertTrue(c.containsAll(List.of("Papelería", "Navidad", "Aseo", "Plástico", "Loza", "Juguetería", "Fantasía", "Mascotas", "Otro")));
        assertEquals(c.size(), c.stream().map(Categorias::clave).distinct().count(), "sin categorías repetidas");
    }

    @ParameterizedTest(name = "categoría inválida «{0}»")
    @ValueSource(strings = {"", "   ", "ab", "Aseo123", "Aseo!", "Una categoría con un nombre demasiado largo para el sistema"})
    @DisplayName("Las categorías inválidas son rechazadas")
    void categoriaInvalida(String categoria) {
        assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(nuevo("Producto X", categoria, 1000, 1, 1), "admin"));
        assertEquals("", ctx.productos.siguienteCodigo(categoria));
    }

    // ---------------------------------------------------------------- nombres repetidos

    @ParameterizedTest(name = "«{0}» ya existe")
    @ValueSource(strings = {"Escoba", "escoba", "ESCOBA", "  escoba  ", "EsCoBa", "Éscoba", "plato  hondo de LOZA"})
    @DisplayName("No se permiten productos repetidos aunque cambien mayúsculas, tildes o espacios")
    void nombreRepetido(String nombre) {
        ErrorNegocio e = assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(nuevo(nombre, "Aseo", 7000, 1, 1), "admin"));
        assertTrue(e.getMessage().contains("Ya existe un producto"), e.getMessage());
    }

    @Test
    @DisplayName("El nombre repetido se detecta también en otra categoría y en productos desactivados")
    void repetidoEnOtraCategoriaODesactivado() {
        assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(nuevo("Escoba", "Plástico", 7000, 1, 1), "admin"));
        ctx.productos.cambiarEstado("ASE-001", false);
        ErrorNegocio e = assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(nuevo("escoba", "Aseo", 7000, 1, 1), "admin"));
        assertTrue(e.getMessage().contains("desactivado"), e.getMessage());
    }

    @Test
    @DisplayName("Al modificar no se puede usar el nombre de otro producto, pero sí conservar el propio")
    void modificarNombreRepetido() {
        Producto p = producto("ASE-002");
        p.nombre = "ESCOBA";
        assertThrows(ErrorNegocio.class, () -> ctx.productos.modificar(p));
        Producto q = producto("ASE-001");
        q.nombre = "escoba";
        q.precioVenta = 7500;
        ctx.productos.modificar(q);
        assertEquals(7500, producto("ASE-001").precioVenta);
    }

    @Test
    @DisplayName("El sistema avisa en el formulario si el nombre ya existe")
    void avisoMismoNombre() {
        assertEquals("ASE-001", ctx.productos.mismoNombre("ESCOBA", null).orElseThrow().codigo);
        assertTrue(ctx.productos.mismoNombre("escoba", "ASE-001").isEmpty(), "no se compara consigo mismo");
        assertTrue(ctx.productos.mismoNombre("Escoba nueva", null).isEmpty());
    }

    @Test
    @DisplayName("El nombre se guarda sin espacios repetidos y con mayúscula inicial")
    void nombreNormalizado() {
        Producto p = ctx.productos.crear(nuevo("  trapero   de   algodón ", "Aseo", 9000, 0, 1), "admin");
        assertEquals("Trapero de algodón", p.nombre);
    }

    @ParameterizedTest(name = "nombre inválido «{0}»")
    @ValueSource(strings = {"", "  ", "ab", "12345", "Escoba<script>", "Producto con un nombre larguísimo que supera los sesenta caracteres"})
    @DisplayName("Los nombres vacíos, sin letras, con símbolos o muy largos son rechazados")
    void nombreInvalido(String nombre) {
        assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(nuevo(nombre, "Aseo", 1000, 1, 1), "admin"));
    }

    // ---------------------------------------------------------------- precio, cantidades y otros datos

    @ParameterizedTest(name = "precio {0}")
    @ValueSource(longs = {0, -1, -50, 1, 10, 49, 75, 4525, 4999, 100_000_000})
    @DisplayName("El precio mínimo es $ 50 y debe ser múltiplo de $ 50")
    void precioInvalido(long precio) {
        assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(nuevo("Producto nuevo", "Otro", precio, 1, 1), "admin"));
    }

    @ParameterizedTest(name = "precio {0}")
    @ValueSource(longs = {50, 100, 150, 4550, 99_999_950})
    @DisplayName("Los precios de $ 50 en adelante, en múltiplos de $ 50, son aceptados")
    void precioValido(long precio) {
        assertEquals(precio, ctx.productos.crear(nuevo("Producto nuevo", "Otro", precio, 1, 1), "admin").precioVenta);
    }

    @Test
    @DisplayName("El mensaje de un precio no múltiplo de 50 sugiere los precios cercanos")
    void sugerenciaDePrecio() {
        ErrorNegocio e = assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(nuevo("Producto nuevo", "Otro", 4525, 1, 1), "admin"));
        assertTrue(e.getMessage().contains("$ 4.500") && e.getMessage().contains("$ 4.550"), e.getMessage());
        Producto p = producto("PAP-001");
        p.precioVenta = 30;
        assertThrows(ErrorNegocio.class, () -> ctx.productos.modificar(p), "también al modificar");
    }

    // ---------------------------------------------------------------- mayúsculas y minúsculas

    @Test
    @DisplayName("Los códigos se encuentran sin importar mayúsculas o espacios")
    void codigosSinMayusculas() {
        assertEquals("PAP-001", ctx.productos.porCodigo(" pap-001 ").orElseThrow().codigo);
        ctx.inventario.registrarEntrada("pap-001", 1, "Compra", "admin");
        assertEquals(41, existencias("PAP-001"));
        ctx.productos.cambiarEstado("pap-001", false);
        assertFalse(producto("PAP-001").activo);
        Producto p = producto("PAP-002");
        p.codigo = "pap-002";
        p.precioVenta = 1300;
        assertEquals("PAP-002", ctx.productos.modificar(p));
    }

    @ParameterizedTest(name = "«{0}» → «{1}»")
    @CsvSource({"ESCOBA DE MADERA, Escoba de madera", "trapero grande, Trapero grande", "Bombillo LED 9W, Bombillo LED 9W",
            "'  vaso   de VIDRIO ', Vaso de VIDRIO"})
    @DisplayName("Los nombres en mayúsculas sostenidas o minúsculas quedan con presentación uniforme")
    void presentacionDelNombre(String escrito, String guardado) {
        assertEquals(guardado, ctx.productos.crear(nuevo(escrito, "Otro", 1000, 0, 1), "admin").nombre);
    }

    @ParameterizedTest(name = "categoría «{0}» → «{1}»")
    @CsvSource({"FERRETERÍA, Ferretería", "ferreteria, Ferreteria", "  ARTÍCULOS   DE  COCINA , Artículos de cocina"})
    @DisplayName("Las categorías nuevas quedan con mayúscula inicial y sin espacios sobrantes")
    void presentacionDeCategoria(String escrita, String guardada) {
        assertEquals(guardada, ctx.productos.crear(nuevo("Producto de prueba", escrita, 1000, 0, 1), "admin").categoria);
    }

    @Test
    @DisplayName("La cantidad inicial y la mínima no pueden ser negativas ni excesivas")
    void cantidadesInvalidas() {
        assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(nuevo("Producto a", "Otro", 1000, -1, 1), "admin"));
        assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(nuevo("Producto b", "Otro", 1000, 1, -1), "admin"));
        assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(nuevo("Producto c", "Otro", 1000, 1_000_000, 1), "admin"));
        assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(nuevo("Producto d", "Otro", 1000, 1, 1_000_000), "admin"));
        assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(null, "admin"));
    }

    @Test
    @DisplayName("Un código escrito a mano repetido o inválido es rechazado")
    void codigoManual() {
        assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(new Producto("pap-001", "Otro cuaderno", "Papelería", 1000, 1, 1), "admin"));
        assertThrows(ErrorNegocio.class, () -> ctx.productos.crear(new Producto("PAP 9!", "Otro cuaderno", "Papelería", 1000, 1, 1), "admin"));
        assertEquals("ESP-1", ctx.productos.crear(new Producto("esp-1", "Producto especial", "Otro", 1000, 1, 1), "admin").codigo);
    }

    @Test
    @DisplayName("La cantidad inicial queda registrada como movimiento de entrada")
    void cantidadInicialComoEntrada() {
        Producto p = ctx.productos.crear(nuevo("Producto nuevo", "Hogar", 5000, 7, 2), "admin");
        List<Movimiento> m = ctx.inventario.consultar(null, null, p.codigo, TipoMovimiento.ENTRADA);
        assertEquals(1, m.size());
        assertEquals(7, m.get(0).cantidad);
        assertEquals("Inventario inicial", m.get(0).motivo);
        Producto sin = ctx.productos.crear(nuevo("Producto sin unidades", "Hogar", 5000, 0, 2), "admin");
        assertTrue(ctx.inventario.consultar(null, null, sin.codigo, null).isEmpty());
    }

    @Test
    @DisplayName("Modificar no cambia las existencias y valida el producto")
    void modificar() {
        Producto p = producto("PAP-001");
        p.precioVenta = 5000;
        p.cantidadMinima = 12;
        p.existencias = 999;
        assertEquals("PAP-001", ctx.productos.modificar(p));
        Producto r = producto("PAP-001");
        assertEquals(5000, r.precioVenta);
        assertEquals(12, r.cantidadMinima);
        assertEquals(40, r.existencias);
        Producto x = new Producto("NO-EXISTE", "Algo", "Otro", 1000, 1, 1);
        assertThrows(ErrorNegocio.class, () -> ctx.productos.modificar(x));
        assertThrows(ErrorNegocio.class, () -> ctx.productos.modificar(null));
    }

    @Test
    @DisplayName("Al cambiar la categoría, el producto recibe un código nuevo y su historial lo conserva")
    void cambioDeCategoria() {
        ServicioVentas.Carrito c = new ServicioVentas.Carrito();
        c.agregar(producto("PLA-002"), 2);
        Venta v = ctx.ventas.confirmar(c, admin);
        Producto p = producto("PLA-002");
        p.categoria = "Loza";
        String nuevoCodigo = ctx.productos.modificar(p);
        assertEquals("LOZ-003", nuevoCodigo);
        assertTrue(ctx.productos.porCodigo("PLA-002").isEmpty());
        assertEquals("Loza", producto("LOZ-003").categoria);
        assertTrue(ctx.inventario.consultar(null, null, "LOZ-003", null).size() >= 2, "entradas y venta con el código nuevo");
        assertEquals("LOZ-003", ctx.ventas.listar(null, null).stream().filter(x -> x.numero.equals(v.numero))
                .findFirst().orElseThrow().items.get(0).codigoProducto);
    }

    // ---------------------------------------------------------------- búsqueda, estado y disponibilidad

    @Test
    @DisplayName("La búsqueda ignora tildes, mayúsculas y espacios repetidos")
    void busqueda() {
        assertFalse(ctx.productos.buscar("PAPELERIA", false).isEmpty());
        assertFalse(ctx.productos.buscar("cuaderno", false).isEmpty());
        assertFalse(ctx.productos.buscar("plato   hondo", false).isEmpty());
        assertEquals(1, ctx.productos.buscar("nav-002", false).size());
        assertEquals(14, ctx.productos.buscar("", false).size());
        assertTrue(ctx.productos.buscar("no existe nada así", false).isEmpty());
    }

    @Test
    @DisplayName("Un producto desactivado no aparece en la venta pero conserva su historial")
    void desactivar() {
        ctx.productos.cambiarEstado("PAP-001", false);
        assertTrue(ctx.productosVendibles("PAP-001").isEmpty());
        assertEquals(1, ctx.productos.buscar("PAP-001", true).size());
        ctx.productos.cambiarEstado("PAP-001", true);
        assertEquals(1, ctx.productosVendibles("PAP-001").size());
        assertThrows(ErrorNegocio.class, () -> ctx.productos.cambiarEstado("NO-EXISTE", false));
    }

    @Test
    @DisplayName("Estado de existencias: disponible, pocas existencias y agotado")
    void estadoExistencias() {
        assertEquals("Agotado", producto("NAV-002").estadoExistencias());
        assertEquals("Pocas existencias", producto("ASE-002").estadoExistencias());
        assertEquals("Pocas existencias", producto("LOZ-002").estadoExistencias(), "igual al mínimo");
        assertEquals("Disponible", producto("PAP-001").estadoExistencias());
        List<Producto> pocas = ctx.productos.pocasExistencias();
        assertEquals("NAV-002", pocas.get(0).codigo, "primero los agotados");
        assertTrue(pocas.stream().anyMatch(p -> p.codigo.equals("LOZ-002")));
        assertTrue(pocas.stream().noneMatch(p -> p.codigo.equals("PAP-001")));
    }
}
