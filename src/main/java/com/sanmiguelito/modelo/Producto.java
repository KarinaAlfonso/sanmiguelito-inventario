package com.sanmiguelito.modelo;

/** Producto del inventario de la cacharrería (RF-002 a RF-006). */
public class Producto {
    public String codigo;           // no se puede modificar una vez creado (RF-003)
    public String nombre;
    public String categoria;
    public long precioVenta;        // pesos colombianos, sin decimales
    public int existencias;
    public int cantidadMinima;      // umbral para "pocas existencias" (RF-010, RF-015)
    public boolean activo = true;   // los productos se desactivan, no se eliminan (RF-004)

    public Producto() { }

    public Producto(String codigo, String nombre, String categoria, long precioVenta, int existencias, int cantidadMinima) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.categoria = categoria;
        this.precioVenta = precioVenta;
        this.existencias = existencias;
        this.cantidadMinima = cantidadMinima;
    }

    /** Estado de disponibilidad mostrado al usuario. */
    public String estadoExistencias() {
        if (existencias <= 0) return "Agotado";
        if (existencias <= cantidadMinima) return "Pocas existencias";
        return "Disponible";
    }

    public Producto copia() {
        Producto p = new Producto(codigo, nombre, categoria, precioVenta, existencias, cantidadMinima);
        p.activo = activo;
        return p;
    }
}
