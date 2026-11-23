package com.sanmiguelito.modelo;

/** Línea de una venta. */
public class ItemVenta {
    public String codigoProducto;
    public String nombreProducto;
    public int cantidad;
    public long precioUnitario;

    public ItemVenta() { }

    public ItemVenta(String codigoProducto, String nombreProducto, int cantidad, long precioUnitario) {
        this.codigoProducto = codigoProducto;
        this.nombreProducto = nombreProducto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
    }

    public long subtotal() { return precioUnitario * cantidad; }
}
