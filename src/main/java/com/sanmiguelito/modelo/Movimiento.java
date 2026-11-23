package com.sanmiguelito.modelo;

/** Registro de un movimiento del inventario. */
public class Movimiento {
    public long id;
    public String fecha;            // ISO-8601 local: yyyy-MM-ddTHH:mm:ss
    public TipoMovimiento tipo;
    public String codigoProducto;
    public String nombreProducto;
    public int cantidad;
    public int existenciasResultantes;
    public String motivo;           // proveedor, daño, pérdida, número de venta, etc.
    public String usuario;

    public Movimiento() { }
}
