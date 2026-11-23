package com.sanmiguelito.servicio;

import com.sanmiguelito.datos.Repositorio;
import com.sanmiguelito.modelo.Movimiento;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.modelo.TipoMovimiento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Entradas y salidas de inventario, validación de existencias y consulta de movimientos (RF-007 a RF-011). */
public class ServicioInventario {

    private final Repositorio repo;

    public ServicioInventario(Repositorio repo) { this.repo = repo; }

    /** RF-007: registra las unidades recibidas y aumenta las existencias. */
    public synchronized Movimiento registrarEntrada(String codigo, int cantidad, String motivo, String usuario) {
        if (cantidad <= 0) throw new ErrorNegocio("La cantidad recibida debe ser mayor que cero.");
        if (motivo == null || motivo.isBlank()) throw new ErrorNegocio("Indique el origen de la entrada (por ejemplo, el proveedor o la compra).");
        return mover(codigo, cantidad, TipoMovimiento.ENTRADA, motivo.trim(), usuario);
    }

    /** RF-008 y RF-009: registra una salida distinta a la venta (daño, pérdida, uso interno) validando existencias. */
    public synchronized Movimiento registrarSalida(String codigo, int cantidad, String motivo, String usuario) {
        if (cantidad <= 0) throw new ErrorNegocio("La cantidad de la salida debe ser mayor que cero.");
        if (motivo == null || motivo.isBlank()) throw new ErrorNegocio("Indique el motivo de la salida (daño, pérdida, uso interno, etc.).");
        return mover(codigo, -cantidad, TipoMovimiento.SALIDA, motivo.trim(), usuario);
    }

    private Movimiento mover(String codigo, int delta, TipoMovimiento tipo, String motivo, String usuario) {
        List<Producto> productos = repo.productos.leer();
        Producto p = productos.stream().filter(x -> x.codigo.equals(codigo)).findFirst()
                .orElseThrow(() -> new ErrorNegocio("Seleccione un producto registrado."));
        if (!p.activo) throw new ErrorNegocio("El producto «" + p.nombre + "» está desactivado.");
        if (p.existencias + delta < 0)
            throw new ErrorNegocio("No hay unidades suficientes de «" + p.nombre + "». Existencias actuales: " + p.existencias + ".");
        p.existencias += delta;
        repo.productos.guardar(productos);
        List<Movimiento> movs = repo.movimientos.leer();
        Movimiento m = nuevoMovimiento(movs, tipo, p, Math.abs(delta), motivo, usuario);
        movs.add(m);
        repo.movimientos.guardar(movs);
        return m;
    }

    static Movimiento nuevoMovimiento(List<Movimiento> existentes, TipoMovimiento tipo, Producto p, int cantidad, String motivo, String usuario) {
        Movimiento m = new Movimiento();
        m.id = existentes.stream().mapToLong(x -> x.id).max().orElse(0) + 1;
        m.fecha = Formato.ahoraIso();
        m.tipo = tipo;
        m.codigoProducto = p.codigo;
        m.nombreProducto = p.nombre;
        m.cantidad = cantidad;
        m.existenciasResultantes = p.existencias;
        m.motivo = motivo;
        m.usuario = usuario;
        return m;
    }

    /** RF-011: consulta de movimientos con filtros opcionales (fechas inclusivas, producto y tipo). */
    public synchronized List<Movimiento> consultar(LocalDate desde, LocalDate hasta, String textoProducto, TipoMovimiento tipo) {
        String t = ServicioProductos.normalizar(textoProducto);
        return repo.movimientos.leer().stream()
                .filter(m -> tipo == null || m.tipo == tipo)
                .filter(m -> t.isEmpty() || ServicioProductos.normalizar(m.nombreProducto).contains(t)
                        || ServicioProductos.normalizar(m.codigoProducto).contains(t))
                .filter(m -> {
                    LocalDate f = Formato.leer(m.fecha).toLocalDate();
                    return (desde == null || !f.isBefore(desde)) && (hasta == null || !f.isAfter(hasta));
                })
                .sorted(Comparator.comparing((Movimiento m) -> Formato.leer(m.fecha)).reversed()
                        .thenComparing(m -> -m.id))
                .collect(Collectors.toList());
    }

    public synchronized long movimientosDelDia(LocalDateTime referencia) {
        LocalDate hoy = referencia.toLocalDate();
        return repo.movimientos.leer().stream().filter(m -> Formato.leer(m.fecha).toLocalDate().equals(hoy)).count();
    }
}
