package com.sanmiguelito.servicio;

import com.sanmiguelito.datos.Repositorio;
import com.sanmiguelito.modelo.Rol;
import com.sanmiguelito.modelo.Usuario;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Autenticación (RF-001) y gestión de usuarios (RF-012). */
public class ServicioUsuarios {

    private final Repositorio repo;

    public ServicioUsuarios(Repositorio repo) { this.repo = repo; }

    public synchronized Usuario autenticar(String usuario, String contrasena) {
        if (usuario == null || usuario.isBlank() || contrasena == null || contrasena.isEmpty())
            throw new ErrorNegocio("Ingrese el usuario y la contraseña.");
        Optional<Usuario> u = buscar(usuario.trim());
        if (u.isEmpty() || !Seguridad.verificar(contrasena, u.get().sal, u.get().hashContrasena))
            throw new ErrorNegocio("El usuario o la contraseña son incorrectos.");
        if (!u.get().activo)
            throw new ErrorNegocio("El usuario está desactivado. Comuníquese con el administrador.");
        return u.get();
    }

    public synchronized List<Usuario> listar() {
        List<Usuario> lista = repo.usuarios.leer();
        lista.sort(Comparator.comparing(x -> x.usuario));
        return lista;
    }

    public synchronized Optional<Usuario> buscar(String usuario) {
        return repo.usuarios.leer().stream().filter(x -> x.usuario.equalsIgnoreCase(usuario)).findFirst();
    }

    public synchronized Usuario crear(String usuario, String nombreCompleto, Rol rol, String contrasena) {
        String login = usuario == null ? "" : usuario.trim().toLowerCase();
        if (!login.matches("[a-z0-9._]{3,20}"))
            throw new ErrorNegocio("El usuario debe tener entre 3 y 20 caracteres: letras minúsculas, números, punto o guion bajo.");
        if (nombreCompleto == null || nombreCompleto.isBlank())
            throw new ErrorNegocio("El nombre completo es obligatorio.");
        if (rol == null) throw new ErrorNegocio("Seleccione el rol del usuario.");
        Seguridad.validarContrasena(contrasena);
        List<Usuario> lista = repo.usuarios.leer();
        if (lista.stream().anyMatch(x -> x.usuario.equalsIgnoreCase(login)))
            throw new ErrorNegocio("Ya existe un usuario con el nombre «" + login + "».");
        Usuario u = new Usuario(login, nombreCompleto.trim(), rol);
        u.sal = Seguridad.nuevaSal();
        u.hashContrasena = Seguridad.hash(contrasena, u.sal);
        lista.add(u);
        repo.usuarios.guardar(lista);
        return u;
    }

    /** Modifica nombre, rol y estado. No permite dejar el sistema sin un administrador activo. */
    public synchronized void modificar(String usuario, String nombreCompleto, Rol rol, boolean activo, String usuarioEnSesion) {
        if (nombreCompleto == null || nombreCompleto.isBlank())
            throw new ErrorNegocio("El nombre completo es obligatorio.");
        List<Usuario> lista = repo.usuarios.leer();
        Usuario u = lista.stream().filter(x -> x.usuario.equals(usuario)).findFirst()
                .orElseThrow(() -> new ErrorNegocio("El usuario no existe."));
        if (usuario.equals(usuarioEnSesion) && (!activo || rol != Rol.ADMINISTRADOR))
            throw new ErrorNegocio("No puede desactivar ni quitar el rol de administrador a su propio usuario.");
        u.nombreCompleto = nombreCompleto.trim();
        u.rol = rol;
        u.activo = activo;
        long admins = lista.stream().filter(x -> x.activo && x.rol == Rol.ADMINISTRADOR).count();
        if (admins == 0) throw new ErrorNegocio("Debe existir al menos un administrador activo.");
        repo.usuarios.guardar(lista);
    }

    public synchronized void cambiarContrasena(String usuario, String nueva) {
        Seguridad.validarContrasena(nueva);
        List<Usuario> lista = repo.usuarios.leer();
        Usuario u = lista.stream().filter(x -> x.usuario.equals(usuario)).findFirst()
                .orElseThrow(() -> new ErrorNegocio("El usuario no existe."));
        u.sal = Seguridad.nuevaSal();
        u.hashContrasena = Seguridad.hash(nueva, u.sal);
        repo.usuarios.guardar(lista);
    }
}
