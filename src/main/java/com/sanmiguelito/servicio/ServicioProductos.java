package com.sanmiguelito.servicio;

import com.sanmiguelito.datos.Repositorio;
import com.sanmiguelito.modelo.Movimiento;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.modelo.TipoMovimiento;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Registro, modificación, desactivación, visualización y búsqueda de productos (RF-002 a RF-006, RF-010). */
public class ServicioProductos {

    private final Repositorio repo;

    public ServicioProductos(Repositorio repo) { this.repo = repo; }

    public synchronized List<Producto> listar(boolean incluirInactivos) {
        return repo.productos.leer().stream()
                .filter(p -> incluirInactivos || p.activo)
                .sorted(Comparator.comparing((Producto p) -> p.nombre.toLowerCase()))
                .collect(Collectors.toList());
    }

    /** Búsqueda por nombre, código o categoría, sin distinguir mayúsculas ni tildes (RF-006). */
    public synchronized List<Producto> buscar(String texto, boolean incluirInactivos) {
        String t = normalizar(texto);
        return listar(incluirInactivos).stream()
                .filter(p -> t.isEmpty() || normalizar(p.nombre).contains(t) || normalizar(p.codigo).contains(t)
                        || normalizar(p.categoria).contains(t))
                .collect(Collectors.toList());
    }

    public synchronized Optional<Producto> porCodigo(String codigo) {
        return repo.productos.leer().stream().filter(p -> p.codigo.equalsIgnoreCase(codigo == null ? "" : codigo.trim())).findFirst();
    }

    public synchronized List<String> categorias() {
        TreeSet<String> c = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        repo.productos.leer().forEach(p -> c.add(p.categoria));
        return List.copyOf(c);
    }

    /** Registra un producto nuevo. La cantidad inicial queda como movimiento de entrada (RF-002, RF-011). */
    public synchronized Producto crear(Producto nuevo, String usuario) {
        validar(nuevo);
        String codigo = nuevo.codigo == null ? "" : nuevo.codigo.trim().toUpperCase();
        if (!codigo.matches("[A-Z0-9-]{2,15}"))
            throw new ErrorNegocio("El código debe tener entre 2 y 15 caracteres: letras, números o guion.");
        List<Producto> lista = repo.productos.leer();
        if (lista.stream().anyMatch(p -> p.codigo.equalsIgnoreCase(codigo)))
            throw new ErrorNegocio("Ya existe un producto con el código " + codigo + ".");
        if (nuevo.existencias < 0) throw new ErrorNegocio("La cantidad inicial no puede ser negativa.");
        Producto p = nuevo.copia();
        p.codigo = codigo;
        p.nombre = p.nombre.trim();
        p.categoria = p.categoria.trim();
        p.activo = true;
        lista.add(p);
        repo.productos.guardar(lista);
        if (p.existencias > 0) {
            List<Movimiento> movs = repo.movimientos.leer();
            movs.add(ServicioInventario.nuevoMovimiento(movs, TipoMovimiento.ENTRADA, p, p.existencias, "Inventario inicial", usuario));
            repo.movimientos.guardar(movs);
        }
        return p;
    }

    /** Modifica los datos descriptivos. El código y las existencias no se cambian aquí (RF-003). */
    public synchronized void modificar(Producto cambios) {
        validar(cambios);
        List<Producto> lista = repo.productos.leer();
        Producto p = lista.stream().filter(x -> x.codigo.equals(cambios.codigo)).findFirst()
                .orElseThrow(() -> new ErrorNegocio("El producto no existe."));
        p.nombre = cambios.nombre.trim();
        p.categoria = cambios.categoria.trim();
        p.precioVenta = cambios.precioVenta;
        p.cantidadMinima = cambios.cantidadMinima;
        repo.productos.guardar(lista);
    }

    /** Activa o desactiva un producto sin eliminarlo, para conservar su historial (RF-004). */
    public synchronized void cambiarEstado(String codigo, boolean activo) {
        List<Producto> lista = repo.productos.leer();
        Producto p = lista.stream().filter(x -> x.codigo.equals(codigo)).findFirst()
                .orElseThrow(() -> new ErrorNegocio("El producto no existe."));
        p.activo = activo;
        repo.productos.guardar(lista);
    }

    public synchronized List<Producto> pocasExistencias() {
        return listar(false).stream().filter(p -> p.existencias <= p.cantidadMinima)
                .sorted(Comparator.comparingInt((Producto p) -> p.existencias)).collect(Collectors.toList());
    }

    private static void validar(Producto p) {
        if (p == null) throw new ErrorNegocio("Faltan los datos del producto.");
        if (p.nombre == null || p.nombre.isBlank()) throw new ErrorNegocio("El nombre del producto es obligatorio.");
        if (p.nombre.trim().length() > 60) throw new ErrorNegocio("El nombre no puede superar 60 caracteres.");
        if (p.categoria == null || p.categoria.isBlank()) throw new ErrorNegocio("La categoría es obligatoria.");
        if (p.precioVenta <= 0) throw new ErrorNegocio("El precio de venta debe ser mayor que cero.");
        if (p.cantidadMinima < 0) throw new ErrorNegocio("La cantidad mínima no puede ser negativa.");
    }

    static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.toLowerCase().trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
