package com.sanmiguelito.modelo;

import java.util.ArrayList;
import java.util.List;

/** Venta confirmada (RF-013) y base del comprobante (RF-014). */
public class Venta {
    public String numero;           // V-000001
    public String fecha;            // ISO-8601 local
    public String usuario;
    public String nombreVendedor;
    public List<ItemVenta> items = new ArrayList<>();

    public Venta() { }

    public long total() {
        long t = 0;
        for (ItemVenta i : items) t += i.subtotal();
        return t;
    }

    public int unidades() {
        int u = 0;
        for (ItemVenta i : items) u += i.cantidad;
        return u;
    }
}
