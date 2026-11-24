package com.sanmiguelito.servicio;

import com.sanmiguelito.modelo.Rol;
import com.sanmiguelito.modelo.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** RF-001, RF-012 y RNF-002: ingreso, usuarios, roles y seguridad de las contraseñas. */
@DisplayName("Usuarios, roles y seguridad")
class UsuariosYSeguridadTest extends BaseServicios {

    /** Reloj que se puede adelantar para probar el bloqueo temporal. */
    static final class RelojPrueba extends Clock {
        Instant ahora = Instant.parse("2026-11-24T08:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("America/Bogota"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    // ---------------------------------------------------------------- ingreso (RF-001)

    @Test
    @DisplayName("Las contraseñas se guardan cifradas, nunca en texto plano")
    void contrasenasCifradas() throws Exception {
        String json = Files.readString(dir.resolve("datos/usuarios.json"));
        assertFalse(json.contains("SanMiguelito2026"));
        assertFalse(json.contains("Vendedor2026"));
        Usuario a = ctx.usuarios.buscar("admin").orElseThrow();
        Usuario v = ctx.usuarios.buscar("vendedor").orElseThrow();
        assertNotEquals(a.sal, v.sal, "cada usuario tiene su propia sal");
    }

    @Test
    @DisplayName("Credenciales incorrectas o vacías son rechazadas")
    void credencialesIncorrectas() {
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.autenticar("admin", "otra"));
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.autenticar("noexiste", "SanMiguelito2026"));
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.autenticar("admin", "sanmiguelito2026"), "distingue mayúsculas");
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.autenticar("", "x"));
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.autenticar("admin", ""));
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.autenticar(null, null));
    }

    @Test
    @DisplayName("El nombre de usuario no distingue mayúsculas ni espacios alrededor")
    void usuarioSinDistinguirMayusculas() {
        assertEquals("admin", ctx.usuarios.autenticar("  ADMIN ", "SanMiguelito2026").usuario);
    }

    @Test
    @DisplayName("Después de 5 intentos fallidos el usuario queda bloqueado 2 minutos")
    void bloqueoPorIntentos() {
        RelojPrueba reloj = new RelojPrueba();
        ServicioUsuarios s = new ServicioUsuarios(ctx.repo, reloj);
        for (int i = 1; i < ServicioUsuarios.INTENTOS_MAXIMOS; i++)
            assertThrows(ErrorNegocio.class, () -> s.autenticar("vendedor", "mala"));
        ErrorNegocio ultimo = assertThrows(ErrorNegocio.class, () -> s.autenticar("vendedor", "mala"));
        assertTrue(ultimo.getMessage().contains("bloqueado"), ultimo.getMessage());
        ErrorNegocio bloqueado = assertThrows(ErrorNegocio.class, () -> s.autenticar("vendedor", "Vendedor2026"));
        assertTrue(bloqueado.getMessage().contains("bloqueado"), "ni con la contraseña correcta mientras dura el bloqueo");
        assertEquals("admin", s.autenticar("admin", "SanMiguelito2026").usuario, "los demás usuarios no se bloquean");
        reloj.ahora = reloj.ahora.plus(ServicioUsuarios.TIEMPO_BLOQUEO).plus(Duration.ofSeconds(1));
        assertEquals("vendedor", s.autenticar("vendedor", "Vendedor2026").usuario);
    }

    @Test
    @DisplayName("Un ingreso correcto reinicia el conteo de intentos fallidos")
    void ingresoCorrectoReiniciaIntentos() {
        ServicioUsuarios s = new ServicioUsuarios(ctx.repo, new RelojPrueba());
        for (int i = 0; i < 4; i++) assertThrows(ErrorNegocio.class, () -> s.autenticar("vendedor", "mala"));
        s.autenticar("vendedor", "Vendedor2026");
        for (int i = 0; i < 4; i++) assertThrows(ErrorNegocio.class, () -> s.autenticar("vendedor", "mala"));
        assertEquals("vendedor", s.autenticar("vendedor", "Vendedor2026").usuario);
    }

    @Test
    @DisplayName("Un usuario desactivado no puede ingresar")
    void usuarioDesactivado() {
        ctx.usuarios.modificar("vendedor", "Vendedor", Rol.VENDEDOR, false, "admin");
        ErrorNegocio e = assertThrows(ErrorNegocio.class, () -> ctx.usuarios.autenticar("vendedor", "Vendedor2026"));
        assertTrue(e.getMessage().contains("desactivado"));
    }

    // ---------------------------------------------------------------- cambio de contraseña

    @Test
    @DisplayName("Los usuarios iniciales deben cambiar la contraseña en su primer ingreso")
    void cambioObligatorio() {
        assertTrue(admin.debeCambiarContrasena);
        ctx.usuarios.cambiarContrasena("admin", "NuevaClave2026");
        Usuario a = ctx.usuarios.autenticar("admin", "NuevaClave2026");
        assertFalse(a.debeCambiarContrasena);
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.autenticar("admin", "SanMiguelito2026"));
    }

    @Test
    @DisplayName("La nueva contraseña debe ser diferente de la actual")
    void contrasenaDiferente() {
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.cambiarContrasena("admin", "SanMiguelito2026"));
    }

    @Test
    @DisplayName("Si el administrador restablece una contraseña, el usuario debe cambiarla al ingresar")
    void restablecer() {
        ctx.usuarios.cambiarContrasena("vendedor", "MiClave2026");
        assertFalse(ctx.usuarios.buscar("vendedor").orElseThrow().debeCambiarContrasena);
        ctx.usuarios.restablecerContrasena("vendedor", "Temporal2026");
        assertTrue(ctx.usuarios.autenticar("vendedor", "Temporal2026").debeCambiarContrasena);
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.restablecerContrasena("noexiste", "Temporal2026"));
    }

    @ParameterizedTest(name = "contraseña débil «{0}»")
    @ValueSource(strings = {"", "corta1", "sinnumeros", "12345678", " Espacio123", "Espacio123 "})
    @DisplayName("Las contraseñas débiles son rechazadas")
    void contrasenaDebil(String clave) {
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.crear("caja2", "Caja dos", Rol.VENDEDOR, clave));
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.cambiarContrasena("vendedor", clave));
    }

    @Test
    @DisplayName("Las contraseñas demasiado largas son rechazadas")
    void contrasenaLarga() {
        assertThrows(ErrorNegocio.class, () -> Seguridad.validarContrasena("a1".repeat(33)));
        assertThrows(ErrorNegocio.class, () -> Seguridad.validarContrasena(null));
        assertFalse(Seguridad.verificar(null, "x", "y"));
    }

    // ---------------------------------------------------------------- gestión de usuarios (RF-012)

    @Test
    @DisplayName("Se crea un usuario nuevo, que debe cambiar su contraseña al ingresar")
    void crearUsuario() {
        Usuario u = ctx.usuarios.crear("  Maria.Lopez ", "  María   López ", Rol.VENDEDOR, "Temporal2026");
        assertEquals("maria.lopez", u.usuario);
        assertEquals("María López", u.nombreCompleto);
        assertTrue(u.debeCambiarContrasena);
        assertEquals("maria.lopez", ctx.usuarios.autenticar("MARIA.LOPEZ", "Temporal2026").usuario);
        assertEquals(3, ctx.usuarios.listar().size());
    }

    @ParameterizedTest(name = "«{0}» → «{1}»")
    @org.junit.jupiter.params.provider.CsvSource({"MARÍA DE LOS ÁNGELES PÉREZ, María de los Ángeles Pérez",
            "juan esteban garcía, Juan Esteban García", "'  ana   del  río ', Ana del Río", "O'NEIL SMITH, O'neil Smith"})
    @DisplayName("El nombre completo se guarda como nombre propio, sin importar cómo se escriba")
    void nombrePropio(String escrito, String guardado) {
        assertEquals(guardado, ctx.usuarios.crear("caja2", escrito, Rol.VENDEDOR, "Temporal2026").nombreCompleto);
    }

    @Test
    @DisplayName("Modificar, restablecer y cambiar contraseña no distinguen mayúsculas en el usuario")
    void usuarioSinMayusculasEnGestion() {
        ctx.usuarios.modificar("VENDEDOR", "vendedor principal", Rol.VENDEDOR, true, "ADMIN");
        assertEquals("Vendedor Principal", ctx.usuarios.buscar("vendedor").orElseThrow().nombreCompleto);
        ctx.usuarios.restablecerContrasena(" Vendedor ", "Temporal2026");
        assertEquals("vendedor", ctx.usuarios.autenticar("vendedor", "Temporal2026").usuario);
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.modificar("ADMIN", "Admin", Rol.VENDEDOR, true, "admin"),
                "sigue protegiendo al usuario en sesión aunque cambien las mayúsculas");
    }

    @ParameterizedTest(name = "usuario inválido «{0}»")
    @ValueSource(strings = {"", "ab", "maria lopez", "maría", "usuario-con-guion", "unnombredeusuariodemasiadolargo"})
    @DisplayName("Los nombres de usuario inválidos son rechazados")
    void usuarioInvalido(String login) {
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.crear(login, "Nombre Apellido", Rol.VENDEDOR, "Temporal2026"));
    }

    @Test
    @DisplayName("No se permiten usuarios repetidos, nombres inválidos ni usuarios sin rol")
    void validacionesDeUsuario() {
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.crear("ADMIN", "Otro admin", Rol.ADMINISTRADOR, "Temporal2026"));
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.crear("caja2", "  ", Rol.VENDEDOR, "Temporal2026"));
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.crear("caja2", "Caja 2", Rol.VENDEDOR, "Temporal2026"), "sin números en el nombre");
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.crear("caja2", "Caja dos", null, "Temporal2026"));
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.modificar("vendedor", "Vendedor", null, true, "admin"));
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.modificar("noexiste", "Nombre", Rol.VENDEDOR, true, "admin"));
    }

    @Test
    @DisplayName("Siempre debe existir un administrador activo y nadie se quita su propio rol")
    void administradorActivo() {
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.modificar("admin", "Admin", Rol.VENDEDOR, true, "admin"));
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.modificar("admin", "Admin", Rol.ADMINISTRADOR, false, "admin"));
        assertThrows(ErrorNegocio.class, () -> ctx.usuarios.modificar("admin", "Admin", Rol.VENDEDOR, true, "otro"),
                "aunque lo haga otra persona, no puede quedar sin administradores");
        ctx.usuarios.modificar("vendedor", "Vendedor Principal", Rol.ADMINISTRADOR, true, "admin");
        ctx.usuarios.modificar("admin", "Admin", Rol.VENDEDOR, true, "vendedor");
        assertEquals(Rol.VENDEDOR, ctx.usuarios.buscar("admin").orElseThrow().rol);
    }

    @Test
    @DisplayName("Solo el administrador accede a las opciones restringidas")
    void permisosPorRol() {
        assertTrue(ctx.esAdministrador());
        ctx.exigirAdministrador();
        ctx.iniciarSesion(ctx.usuarios.autenticar("vendedor", "Vendedor2026"));
        assertFalse(ctx.esAdministrador());
        assertThrows(ErrorNegocio.class, ctx::exigirAdministrador);
        ctx.cerrarSesion();
        assertNull(ctx.sesion());
        assertThrows(ErrorNegocio.class, ctx::exigirAdministrador);
    }
}
