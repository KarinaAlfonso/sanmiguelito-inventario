package com.sanmiguelito.modelo;

/** Roles de usuario definidos para la Cacharrería San Miguelito (RF-012). */
public enum Rol {
    ADMINISTRADOR("Administrador"),
    VENDEDOR("Vendedor");

    private final String nombre;

    Rol(String nombre) { this.nombre = nombre; }

    public String nombre() { return nombre; }

    @Override
    public String toString() { return nombre; }
}
