package com.sanmiguelito.servicio;

import com.sanmiguelito.datos.Repositorio;
import com.sanmiguelito.modelo.Rol;
import com.sanmiguelito.modelo.Usuario;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Autenticación (RF-001) y gestión de usuarios (RF-012). */
public class ServicioUsuarios {

    /** Intentos fallidos permitidos antes de bloquear temporalmente el usuario. */
    public static final int INTENTOS_MAXIMOS = 5;
    public static final Duration TIEMPO_BLOQUEO = Duration.ofMinutes(2);
    public static final int LONGITUD_NOMBRE = 60;

    private final Repositorio repo;
    private final Clock reloj;
    private final Map<String, Integer> fallidos = new HashMap<>();
    private final Map<String, Instant> bloqueadoHasta = new HashMap<>();

    public ServicioUsuarios(Repositorio repo) { this(repo, Clock.systemDefaultZone()); }

    public ServicioUsuarios(Repositorio repo, Clock reloj) {
        this.repo = repo;
        this.reloj = reloj;
    }

    /**
     * Valida usuario y contraseña. Después de {@value #INTENTOS_MAXIMOS} intentos fallidos el usuario
     * queda bloqueado durante dos minutos, para impedir que se adivine la contraseña.
     */
    public synchronized Usuario autenticar(String usuario, String contrasena) {
        if (usuario == null || usuario.isBlank() || contrasena == null || contrasena.isEmpty())
            throw new ErrorNegocio("Ingrese el usuario y la contraseña.");
        String clave = usuario.trim().toLowerCase();
        Instant hasta = bloqueadoHasta.get(clave);
        Instant ahora = reloj.instant();
        if (hasta != null && ahora.isBefore(hasta)) {
            long seg = Math.max(1, Duration.between(ahora, hasta).getSeconds());
            throw new ErrorNegocio("Por seguridad, el usuario está bloqueado por demasiados intentos fallidos. Intente de nuevo en "
                    + (seg >= 60 ? (seg + 59) / 60 + " minuto(s)." : seg + " segundos."));
        }
        if (hasta != null) { bloqueadoHasta.remove(clave); fallidos.remove(clave); }

        Optional<Usuario> u = buscar(clave);
        if (u.isEmpty() || !Seguridad.verificar(contrasena, u.get().sal, u.get().hashContrasena)) {
            int n = fallidos.merge(clave, 1, Integer::sum);
            if (n >= INTENTOS_MAXIMOS) {
                bloqueadoHasta.put(clave, ahora.plus(TIEMPO_BLOQUEO));
                throw new ErrorNegocio("El usuario o la contraseña son incorrectos. Por seguridad, el usuario quedó bloqueado durante "
                        + TIEMPO_BLOQUEO.toMinutes() + " minutos.");
            }
            int quedan = INTENTOS_MAXIMOS - n;
            throw new ErrorNegocio("El usuario o la contraseña son incorrectos."
                    + (quedan <= 3 ? " Le quedan " + quedan + " intento(s) antes del bloqueo temporal." : ""));
        }
        if (!u.get().activo)
            throw new ErrorNegocio("El usuario está desactivado. Comuníquese con el administrador.");
        fallidos.remove(clave);
        return u.get();
    }

    public synchronized List<Usuario> listar() {
        List<Usuario> lista = repo.usuarios.leer();
        lista.sort(Comparator.comparing(x -> x.usuario));
        return lista;
    }

    public synchronized Optional<Usuario> buscar(String usuario) {
        String u = usuario == null ? "" : usuario.trim();
        return repo.usuarios.leer().stream().filter(x -> x.usuario.equalsIgnoreCase(u)).findFirst();
    }

    /** Registra un usuario. Deberá cambiar la contraseña asignada en su primer ingreso. */
    public synchronized Usuario crear(String usuario, String nombreCompleto, Rol rol, String contrasena) {
        String login = usuario == null ? "" : usuario.trim().toLowerCase();
        if (login.isEmpty()) throw new ErrorNegocio("El nombre de usuario es obligatorio.");
        if (!login.matches("[a-z0-9._]{3,20}"))
            throw new ErrorNegocio("El usuario debe tener entre 3 y 20 caracteres, sin espacios ni tildes: letras minúsculas, números, punto o guion bajo.");
        String nombre = validarNombre(nombreCompleto);
        if (rol == null) throw new ErrorNegocio("Seleccione el rol del usuario.");
        Seguridad.validarContrasena(contrasena);
        List<Usuario> lista = repo.usuarios.leer();
        if (lista.stream().anyMatch(x -> x.usuario.equalsIgnoreCase(login)))
            throw new ErrorNegocio("Ya existe un usuario con el nombre «" + login + "».");
        Usuario u = new Usuario(login, nombre, rol);
        u.sal = Seguridad.nuevaSal();
        u.hashContrasena = Seguridad.hash(contrasena, u.sal);
        u.debeCambiarContrasena = true;
        lista.add(u);
        repo.usuarios.guardar(lista);
        return u;
    }

    /** Modifica nombre, rol y estado. No permite dejar el sistema sin un administrador activo. */
    public synchronized void modificar(String usuario, String nombreCompleto, Rol rol, boolean activo, String usuarioEnSesion) {
        String nombre = validarNombre(nombreCompleto);
        if (rol == null) throw new ErrorNegocio("Seleccione el rol del usuario.");
        List<Usuario> lista = repo.usuarios.leer();
        String login = usuario == null ? "" : usuario.trim();
        Usuario u = lista.stream().filter(x -> x.usuario.equalsIgnoreCase(login)).findFirst()
                .orElseThrow(() -> new ErrorNegocio("El usuario no existe."));
        if (u.usuario.equalsIgnoreCase(usuarioEnSesion == null ? "" : usuarioEnSesion.trim()) && (!activo || rol != Rol.ADMINISTRADOR))
            throw new ErrorNegocio("No puede desactivar ni quitar el rol de administrador a su propio usuario.");
        u.nombreCompleto = nombre;
        u.rol = rol;
        u.activo = activo;
        long admins = lista.stream().filter(x -> x.activo && x.rol == Rol.ADMINISTRADOR).count();
        if (admins == 0) throw new ErrorNegocio("Debe existir al menos un administrador activo.");
        repo.usuarios.guardar(lista);
    }

    /** El propio usuario cambia su contraseña. La nueva debe ser distinta de la actual. */
    public synchronized void cambiarContrasena(String usuario, String nueva) {
        asignarContrasena(usuario, nueva, false);
    }

    /** El administrador asigna una contraseña temporal; el usuario deberá cambiarla al ingresar. */
    public synchronized void restablecerContrasena(String usuario, String nueva) {
        asignarContrasena(usuario, nueva, true);
        fallidos.remove(usuario.trim().toLowerCase());
        bloqueadoHasta.remove(usuario.trim().toLowerCase());
    }

    private void asignarContrasena(String usuario, String nueva, boolean temporal) {
        Seguridad.validarContrasena(nueva);
        List<Usuario> lista = repo.usuarios.leer();
        String login = usuario == null ? "" : usuario.trim();
        Usuario u = lista.stream().filter(x -> x.usuario.equalsIgnoreCase(login)).findFirst()
                .orElseThrow(() -> new ErrorNegocio("El usuario no existe."));
        if (Seguridad.verificar(nueva, u.sal, u.hashContrasena))
            throw new ErrorNegocio("La nueva contraseña debe ser diferente de la actual.");
        u.sal = Seguridad.nuevaSal();
        u.hashContrasena = Seguridad.hash(nueva, u.sal);
        u.debeCambiarContrasena = temporal;
        repo.usuarios.guardar(lista);
    }

    private static String validarNombre(String nombreCompleto) {
        String n = nombreCompleto == null ? "" : nombreCompleto.trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) throw new ErrorNegocio("El nombre completo es obligatorio.");
        if (n.length() < 3 || n.length() > LONGITUD_NOMBRE)
            throw new ErrorNegocio("El nombre completo debe tener entre 3 y " + LONGITUD_NOMBRE + " caracteres.");
        if (!n.matches("[\\p{L} .'-]+")) throw new ErrorNegocio("El nombre completo solo puede contener letras y espacios.");
        if (n.chars().filter(Character::isLetter).count() < 3) throw new ErrorNegocio("Escriba el nombre completo de la persona.");
        return nombrePropio(n);
    }

    /** «MARÍA DE LOS ÁNGELES pérez» → «María de los Ángeles Pérez». */
    static String nombrePropio(String n) {
        java.util.Set<String> menores = java.util.Set.of("de", "del", "la", "las", "los", "y", "e");
        String[] partes = n.toLowerCase().split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            String w = partes[i];
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(i > 0 && menores.contains(w) ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return sb.toString();
    }
}
