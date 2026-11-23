package com.sanmiguelito.modelo;

/** Tipos de movimiento del inventario (RF-007, RF-008, RF-011, RF-013). */
public enum TipoMovimiento {
    ENTRADA("Entrada"),
    SALIDA("Salida"),
    VENTA("Venta");

    private final String nombre;

    TipoMovimiento(String nombre) { this.nombre = nombre; }

    @Override
    public String toString() { return nombre; }
}
