package com.sanmiguelito.servicio;

/** Error de validación o de regla del negocio; su mensaje se muestra tal cual al usuario. */
public class ErrorNegocio extends RuntimeException {
    public ErrorNegocio(String mensaje) { super(mensaje); }
}
