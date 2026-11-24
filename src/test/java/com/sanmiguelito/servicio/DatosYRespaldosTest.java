package com.sanmiguelito.servicio;

import com.sanmiguelito.datos.ErrorDatos;
import com.sanmiguelito.datos.Repositorio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** RNF-005 y RNF-006: integridad de los archivos de datos, copias de seguridad y formatos de entrada. */
@DisplayName("Integridad de datos, respaldos y formatos")
class DatosYRespaldosTest extends BaseServicios {

    // ---------------------------------------------------------------- persistencia e integridad (RNF-005)

    @Test
    @DisplayName("La información se conserva al cerrar y volver a abrir el sistema")
    void persistencia() {
        ctx.inventario.registrarEntrada("PAP-001", 5, "Compra", "admin");
        Contexto otro = new Contexto(new Repositorio(dir));
        assertFalse(otro.prepararPrimerUso(), "no vuelve a crear los datos de ejemplo");
        assertEquals(45, otro.productos.porCodigo("PAP-001").orElseThrow().existencias);
        assertEquals(2, otro.usuarios.listar().size());
    }

    @Test
    @DisplayName("El guardado no deja archivos temporales")
    void sinTemporales() throws Exception {
        ctx.inventario.registrarEntrada("PAP-001", 1, "Compra", "admin");
        try (var s = Files.list(dir.resolve("datos"))) {
            assertTrue(s.noneMatch(p -> p.toString().endsWith(".tmp")));
        }
    }

    @Test
    @DisplayName("Un archivo de datos dañado produce un mensaje claro, no un cierre inesperado")
    void archivoDanado() throws Exception {
        Files.writeString(dir.resolve("datos/productos.json"), "{ esto no es json", StandardCharsets.UTF_8);
        ErrorDatos e = assertThrows(ErrorDatos.class, () -> ctx.productos.listar(true));
        assertTrue(e.getMessage().contains("Restaure una copia de seguridad"), e.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "productos.json | [{\"codigo\":\"PAP-001\",\"nombre\":null,\"categoria\":\"Papelería\",\"precioVenta\":100}]",
            "productos.json | [{\"codigo\":\"PAP-001\",\"nombre\":\"Cuaderno\",\"categoria\":\"Papelería\",\"precioVenta\":100,\"existencias\":-5}]",
            "productos.json | [null]",
            "productos.json | {\"no\":\"es una lista\"}",
            "movimientos.json | [{\"id\":1,\"fecha\":\"ayer\",\"tipo\":\"ENTRADA\",\"codigoProducto\":\"PAP-001\",\"cantidad\":1}]",
            "movimientos.json | [{\"id\":1,\"fecha\":\"2026-11-24T08:00:00\",\"tipo\":\"OTRO\",\"codigoProducto\":\"PAP-001\",\"cantidad\":1}]",
            "ventas.json | [{\"numero\":\"V-ABC\",\"fecha\":\"2026-11-24T08:00:00\",\"items\":[]}]",
            "ventas.json | [{\"numero\":\"V-000001\",\"fecha\":\"2026-11-24T08:00:00\",\"items\":[null]}]",
            "usuarios.json | [{\"usuario\":\"admin\",\"rol\":\"ADMINISTRADOR\"}]"})
    @DisplayName("Un archivo con registros incompletos o inválidos se informa como dañado, sin fallas inesperadas")
    void registrosInvalidos(String archivo, String contenido) throws Exception {
        Files.writeString(dir.resolve("datos/" + archivo), contenido, StandardCharsets.UTF_8);
        ErrorDatos e = assertThrows(ErrorDatos.class, () -> {
            ctx.productos.listar(true);
            ctx.inventario.consultar(null, null, "", null);
            ctx.ventas.listar(null, null);
            ctx.usuarios.listar();
        });
        assertTrue(e.getMessage().contains(archivo), e.getMessage());
    }

    @Test
    @DisplayName("Un archivo de datos vacío o inexistente se trata como lista vacía")
    void archivoVacio() throws Exception {
        Files.writeString(dir.resolve("datos/ventas.json"), "");
        assertTrue(ctx.ventas.listar(null, null).isEmpty());
        Files.delete(dir.resolve("datos/ventas.json"));
        assertTrue(ctx.ventas.listar(null, null).isEmpty());
    }

    @Test
    @DisplayName("La carpeta de datos se puede configurar y por defecto está en la carpeta del usuario")
    void carpetaDeDatos() {
        String anterior = System.getProperty("sanmiguelito.dir");
        try {
            System.clearProperty("sanmiguelito.dir");
            assertEquals(Path.of(System.getProperty("user.home"), "SanMiguelito"), Repositorio.predeterminado().base());
            System.setProperty("sanmiguelito.dir", dir.toString());
            Repositorio r = Repositorio.predeterminado();
            assertEquals(dir, r.base());
            assertEquals(dir.resolve("documentos"), r.carpetaDocumentos());
            assertEquals(dir.resolve("datos/productos.json"), r.productos.archivo());
        } finally {
            if (anterior == null) System.clearProperty("sanmiguelito.dir"); else System.setProperty("sanmiguelito.dir", anterior);
        }
    }

    @Test
    @DisplayName("Se detecta el contenido dañado de un archivo antes de usarlo")
    void interpretarContenido() {
        assertTrue(ctx.repo.productos.interpretar(new byte[0]).isEmpty());
        assertTrue(ctx.repo.productos.interpretar(null).isEmpty());
        assertTrue(ctx.repo.productos.interpretar("null".getBytes()).isEmpty());
        assertEquals(1, ctx.repo.productos.interpretar(("[{\"codigo\":\"X-1\",\"nombre\":\"Algo\",\"categoria\":\"Otro\","
                + "\"precioVenta\":100,\"existencias\":1,\"cantidadMinima\":0}]").getBytes()).size());
        assertThrows(ErrorDatos.class, () -> ctx.repo.productos.interpretar("[{\"codigo\":\"X-1\"}]".getBytes()), "registro incompleto");
        assertThrows(ErrorDatos.class, () -> ctx.repo.productos.interpretar("[null]".getBytes()));
        assertThrows(ErrorDatos.class, () -> ctx.repo.productos.interpretar("{\"no\":\"lista\"}".getBytes()));
    }

    // ---------------------------------------------------------------- respaldos (RNF-006)

    @Test
    @DisplayName("Respaldar y restaurar recupera la información y guarda una copia previa")
    void respaldoYRestauracion() {
        Path respaldo = ctx.respaldos.crearRespaldo(null);
        ctx.inventario.registrarSalida("PAP-001", 40, "Pérdida", "admin");
        assertEquals(0, existencias("PAP-001"));
        ctx.respaldos.restaurar(respaldo);
        assertEquals(40, existencias("PAP-001"));
        assertTrue(ctx.respaldos.listar().stream().anyMatch(p -> p.getFileName().toString().contains("antes_de_restaurar")));
    }

    @Test
    @DisplayName("Dos respaldos en el mismo segundo no se sobrescriben")
    void respaldosConNombreUnico() {
        Path a = ctx.respaldos.crearRespaldo(null);
        Path b = ctx.respaldos.crearRespaldo(null);
        assertNotEquals(a, b);
        assertTrue(Files.exists(a) && Files.exists(b));
    }

    @Test
    @DisplayName("El respaldo automático se crea una sola vez al día")
    void respaldoAutomatico() {
        assertNotNull(ctx.respaldos.respaldoAutomaticoDelDia());
        assertNull(ctx.respaldos.respaldoAutomaticoDelDia());
        assertEquals(1, ctx.respaldos.listar().stream().filter(p -> p.toString().contains("_automatico")).count());
    }

    @Test
    @DisplayName("Se copia un respaldo a otra carpeta (memoria USB)")
    void copiarAUsb() throws Exception {
        Path respaldo = ctx.respaldos.crearRespaldo(null);
        Path usb = Files.createDirectories(dir.resolve("usb"));
        Path copia = ctx.respaldos.copiarA(respaldo, usb);
        assertEquals(Files.size(respaldo), Files.size(copia));
        assertThrows(ErrorNegocio.class, () -> ctx.respaldos.copiarA(respaldo, dir.resolve("no-existe")));
        assertThrows(ErrorNegocio.class, () -> ctx.respaldos.copiarA(null, usb));
    }

    @Test
    @DisplayName("Restaurar un archivo que no es un respaldo es rechazado sin tocar los datos")
    void restaurarArchivoInvalido() throws Exception {
        Path txt = Files.writeString(dir.resolve("notas.txt"), "hola");
        assertThrows(ErrorNegocio.class, () -> ctx.respaldos.restaurar(txt));
        Path zipFalso = Files.writeString(dir.resolve("falso.zip"), "no es un zip");
        assertThrows(ErrorNegocio.class, () -> ctx.respaldos.restaurar(zipFalso));
        assertThrows(ErrorNegocio.class, () -> ctx.respaldos.restaurar(null));
        assertThrows(ErrorNegocio.class, () -> ctx.respaldos.restaurar(dir.resolve("no-existe.zip")));
        Path otroZip = zip("otro.zip", Map.of("foto.jpg", "x"));
        assertThrows(ErrorNegocio.class, () -> ctx.respaldos.restaurar(otroZip));
        assertEquals(40, existencias("PAP-001"));
    }

    @Test
    @DisplayName("Un respaldo con datos dañados no se restaura y los datos actuales quedan intactos")
    void restaurarRespaldoDanado() throws Exception {
        String usuarios = Files.readString(dir.resolve("datos/usuarios.json"));
        Path danado = zip("respaldo_danado.zip", Map.of("usuarios.json", usuarios, "productos.json", "[ {\"codigo\": "));
        ErrorNegocio e = assertThrows(ErrorNegocio.class, () -> ctx.respaldos.restaurar(danado));
        assertTrue(e.getMessage().contains("no se restauró"), e.getMessage());
        assertEquals(14, ctx.productos.listar(true).size());
        assertTrue(ctx.respaldos.listar().stream().noneMatch(p -> p.toString().contains("antes_de_restaurar")),
                "no se alcanzó a modificar nada");
    }

    @Test
    @DisplayName("No se restaura un respaldo sin administrador activo")
    void restaurarSinAdministrador() throws Exception {
        Path sinAdmin = zip("respaldo_sin_admin.zip", Map.of("usuarios.json", "[]", "productos.json", "[]"));
        ErrorNegocio e = assertThrows(ErrorNegocio.class, () -> ctx.respaldos.restaurar(sinAdmin));
        assertTrue(e.getMessage().contains("administrador"), e.getMessage());
    }

    private Path zip(String nombre, Map<String, String> archivos) throws Exception {
        Path z = dir.resolve(nombre);
        try (OutputStream out = Files.newOutputStream(z); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : archivos.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return z;
    }

    // ---------------------------------------------------------------- formatos de entrada

    @ParameterizedTest(name = "«{0}» → {1}")
    @CsvSource({"12500, 12500", "12.500, 12500", "$ 12.500, 12500", "' 800 ', 800", "1.250.000, 1250000", "$50, 50"})
    @DisplayName("Los precios se aceptan con o sin puntos de miles y signo de pesos")
    void preciosValidos(String texto, long esperado) {
        assertEquals(esperado, Formato.leerPesos(texto, "Precio"));
    }

    @ParameterizedTest(name = "precio inválido «{0}»")
    @ValueSource(strings = {"", "  ", "$", "abc", "-500", "12,5", "4.500,00", "4.5", "1.2.3", "12.50", ".500", "500.", "1e5", "12345678901", "5O0"})
    @DisplayName("Los precios con letras, signos o decimales son rechazados")
    void preciosInvalidos(String texto) {
        assertThrows(ErrorNegocio.class, () -> Formato.leerPesos(texto, "Precio"));
    }

    @ParameterizedTest(name = "cantidad inválida «{0}»")
    @ValueSource(strings = {"", " ", "-3", "2.5", "2,5", "tres", "12345678"})
    @DisplayName("Las cantidades con letras, signos o decimales son rechazadas")
    void cantidadesInvalidas(String texto) {
        assertThrows(ErrorNegocio.class, () -> Formato.leerEntero(texto, "Cantidad"));
        assertThrows(ErrorNegocio.class, () -> Formato.leerEntero(null, "Cantidad"));
    }

    @Test
    @DisplayName("Formato de pesos y fechas")
    void formatos() {
        assertEquals("$ 12.500", Formato.pesos(12500));
        assertEquals("$ 0", Formato.pesos(0));
        assertEquals(7, Formato.leerEntero(" 7 ", "Cantidad"));
        assertTrue(Formato.fechaHora("2026-11-24T08:30:00").startsWith("24/11/2026 08:30"));
        assertEquals("texto", Formato.fechaHora("texto"), "una fecha inválida se muestra tal cual");
    }
}
