package com.sanmiguelito.modelo;

/** Usuario del sistema. La contraseña nunca se guarda en texto plano (RNF-002). */
public class Usuario {
    public String usuario;          // nombre de inicio de sesión (único)
    public String nombreCompleto;
    public Rol rol;
    public String hashContrasena;   // PBKDF2 en Base64
    public String sal;              // sal aleatoria en Base64
    public boolean activo = true;

    public Usuario() { }

    public Usuario(String usuario, String nombreCompleto, Rol rol) {
        this.usuario = usuario;
        this.nombreCompleto = nombreCompleto;
        this.rol = rol;
    }

    public boolean esAdministrador() { return rol == Rol.ADMINISTRADOR; }
}
