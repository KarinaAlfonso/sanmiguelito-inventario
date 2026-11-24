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

    public static final int LONGITUD_MOTIVO = 100;

    /** RF-007: registra las unidades recibidas y aumenta las existencias. */
    public synchronized Movimiento registrarEntrada(String codigo, int cantidad, String motivo, String usuario) {
        if (cantidad <= 0) throw new ErrorNegocio("La cantidad recibida debe ser mayor que cero.");
        if (cantidad > ServicioProductos.CANTIDAD_MAXIMA)
            throw new ErrorNegocio("La cantidad no puede superar " + ServicioProductos.CANTIDAD_MAXIMA + " unidades por movimiento.");
        String m = validarMotivo(motivo, "Indique el origen de la entrada (por ejemplo, el proveedor o la compra).");
        return mover(codigo, cantidad, TipoMovimiento.ENTRADA, m, usuario);
    }

    /** RF-008 y RF-009: registra una salida distinta a la venta (daño, pérdida, uso interno) validando existencias. */
    public synchronized Movimiento registrarSalida(String codigo, int cantidad, String motivo, String usuario) {
        if (cantidad <= 0) throw new ErrorNegocio("La cantidad de la salida debe ser mayor que cero.");
        if (cantidad > ServicioProductos.CANTIDAD_MAXIMA)
            throw new ErrorNegocio("La cantidad no puede superar " + ServicioProductos.CANTIDAD_MAXIMA + " unidades por movimiento.");
        String m = validarMotivo(motivo, "Indique el motivo de la salida (daño, pérdida, uso interno, etc.).");
        return mover(codigo, -cantidad, TipoMovimiento.SALIDA, m, usuario);
    }

    private static String validarMotivo(String motivo, String mensajeVacio) {
        String m = motivo == null ? "" : motivo.trim().replaceAll("\\s+", " ");
        if (m.isEmpty()) throw new ErrorNegocio(mensajeVacio);
        if (m.length() < 3) throw new ErrorNegocio("Describa el origen o motivo con al menos 3 caracteres.");
        if (m.length() > LONGITUD_MOTIVO) throw new ErrorNegocio("El origen o motivo no puede superar " + LONGITUD_MOTIVO + " caracteres.");
        if (!m.matches("[\\p{L}\\p{N} .,;:()/#%°+&'\"-]+"))
            throw new ErrorNegocio("El origen o motivo contiene caracteres no permitidos. Use letras, números y signos de puntuación comunes.");
        if (!m.matches(".*\\p{L}.*")) throw new ErrorNegocio("Describa el origen o motivo con palabras (por ejemplo, «Compra a proveedor»).");
        return Character.toUpperCase(m.charAt(0)) + m.substring(1);
    }

    private Movimiento mover(String codigo, int delta, TipoMovimiento tipo, String motivo, String usuario) {
        if (codigo == null || codigo.isBlank()) throw new ErrorNegocio("Seleccione el producto en la lista.");
        List<Producto> productos = repo.productos.leer();
        Producto p = productos.stream().filter(x -> x.codigo.equalsIgnoreCase(codigo.trim())).findFirst()
                .orElseThrow(() -> new ErrorNegocio("El producto seleccionado ya no existe. Actualice la lista."));
        if (!p.activo) throw new ErrorNegocio("El producto «" + p.nombre + "» está desactivado.");
        if (p.existencias + delta < 0)
            throw new ErrorNegocio(p.existencias == 0
                    ? "«" + p.nombre + "» está agotado: no se puede registrar la salida."
                    : "No hay unidades suficientes de «" + p.nombre + "». Existencias actuales: " + p.existencias + ".");
        if ((long) p.existencias + delta > ServicioProductos.CANTIDAD_MAXIMA)
            throw new ErrorNegocio("Con esta entrada las existencias de «" + p.nombre + "» superarían el máximo de "
                    + ServicioProductos.CANTIDAD_MAXIMA + " unidades. Verifique la cantidad.");
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
