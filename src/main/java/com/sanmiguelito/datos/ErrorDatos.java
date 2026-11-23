package com.sanmiguelito.datos;

/** Error al leer o escribir los archivos de datos. */
public class ErrorDatos extends RuntimeException {
    public ErrorDatos(String mensaje, Throwable causa) { super(mensaje, causa); }
}
