package com.sanmiguelito.servicio;

import com.sanmiguelito.datos.Repositorio;
import com.sanmiguelito.modelo.ItemVenta;
import com.sanmiguelito.modelo.Movimiento;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.modelo.TipoMovimiento;
import com.sanmiguelito.modelo.Usuario;
import com.sanmiguelito.modelo.Venta;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Registro de ventas con validación de existencias (RF-009, RF-013). */
public class ServicioVentas {

    private final Repositorio repo;

    public ServicioVentas(Repositorio repo) { this.repo = repo; }

    /** Venta en construcción en la pantalla del vendedor. */
    public static class Carrito {
        private final List<ItemVenta> items = new ArrayList<>();

        public List<ItemVenta> items() { return items; }

        public long total() { return items.stream().mapToLong(ItemVenta::subtotal).sum(); }

        public boolean vacio() { return items.isEmpty(); }

        public void quitar(String codigo) { items.removeIf(i -> i.codigoProducto.equals(codigo)); }

        public void limpiar() { items.clear(); }

        /**
         * Agrega un producto. Si ya está en la venta, unifica las cantidades.
         * Valida que la cantidad total no supere las existencias (RF-009).
         */
        public void agregar(Producto p, int cantidad) {
            if (p == null) throw new ErrorNegocio("Seleccione un producto.");
            if (!p.activo) throw new ErrorNegocio("El producto «" + p.nombre + "» está desactivado.");
            if (cantidad <= 0) throw new ErrorNegocio("La cantidad debe ser mayor que cero.");
            ItemVenta existente = items.stream().filter(i -> i.codigoProducto.equals(p.codigo)).findFirst().orElse(null);
            int total = cantidad + (existente == null ? 0 : existente.cantidad);
            if (total > p.existencias)
                throw new ErrorNegocio("No hay unidades suficientes de «" + p.nombre + "». Disponibles: " + p.existencias
                        + (existente == null ? "" : " (ya hay " + existente.cantidad + " en esta venta)") + ".");
            if (existente == null) items.add(new ItemVenta(p.codigo, p.nombre, cantidad, p.precioVenta));
            else existente.cantidad = total;
        }
    }

    /**
     * Confirma la venta: vuelve a validar existencias contra los datos guardados, descuenta
     * el inventario, registra la venta y un movimiento de tipo VENTA por cada producto.
     */
    public synchronized Venta confirmar(Carrito carrito, Usuario vendedor) {
        if (carrito == null || carrito.vacio()) throw new ErrorNegocio("Agregue al menos un producto a la venta.");
        List<Producto> productos = repo.productos.leer();
        Map<String, Producto> porCodigo = productos.stream().collect(Collectors.toMap(p -> p.codigo, p -> p));
        for (ItemVenta i : carrito.items()) {
            Producto p = porCodigo.get(i.codigoProducto);
            if (p == null || !p.activo) throw new ErrorNegocio("El producto «" + i.nombreProducto + "» ya no está disponible.");
            if (i.cantidad > p.existencias)
                throw new ErrorNegocio("No hay unidades suficientes de «" + p.nombre + "». Disponibles: " + p.existencias + ".");
        }
        List<Venta> ventas = repo.ventas.leer();
        Venta v = new Venta();
        v.numero = siguienteNumero(ventas);
        v.fecha = Formato.ahoraIso();
        v.usuario = vendedor.usuario;
        v.nombreVendedor = vendedor.nombreCompleto;
        List<Movimiento> movs = repo.movimientos.leer();
        for (ItemVenta i : carrito.items()) {
            Producto p = porCodigo.get(i.codigoProducto);
            p.existencias -= i.cantidad;
            // Se guarda el precio vigente al confirmar la venta.
            v.items.add(new ItemVenta(p.codigo, p.nombre, i.cantidad, p.precioVenta));
            Movimiento m = ServicioInventario.nuevoMovimiento(movs, TipoMovimiento.VENTA, p, i.cantidad, "Venta " + v.numero, vendedor.usuario);
            m.fecha = v.fecha;
            movs.add(m);
        }
        ventas.add(v);
        repo.productos.guardar(productos);
        repo.ventas.guardar(ventas);
        repo.movimientos.guardar(movs);
        carrito.limpiar();
        return v;
    }

    static String siguienteNumero(List<Venta> ventas) {
        int max = ventas.stream().map(v -> v.numero).filter(n -> n != null && n.startsWith("V-"))
                .mapToInt(n -> Integer.parseInt(n.substring(2))).max().orElse(0);
        return String.format("V-%06d", max + 1);
    }

    public synchronized List<Venta> listar(LocalDate desde, LocalDate hasta) {
        return repo.ventas.leer().stream()
                .filter(v -> {
                    LocalDate f = Formato.leer(v.fecha).toLocalDate();
                    return (desde == null || !f.isBefore(desde)) && (hasta == null || !f.isAfter(hasta));
                })
                .sorted(Comparator.comparing((Venta v) -> v.numero).reversed())
                .collect(Collectors.toList());
    }
}
