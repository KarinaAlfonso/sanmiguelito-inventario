package com.sanmiguelito.servicio;

import com.sanmiguelito.datos.Repositorio;
import com.sanmiguelito.modelo.ItemVenta;
import com.sanmiguelito.modelo.Movimiento;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.modelo.TipoMovimiento;
import com.sanmiguelito.modelo.Venta;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Registro, modificación, desactivación, visualización y búsqueda de productos (RF-002 a RF-006, RF-010). */
public class ServicioProductos {

    public static final int LONGITUD_NOMBRE = 60;
    public static final long PRECIO_MAXIMO = 99_999_950L;
    /** Moneda de menor valor en circulación en Colombia: los precios son múltiplos de 50 pesos. */
    public static final long PRECIO_MINIMO = 50L;
    public static final int CANTIDAD_MAXIMA = 999_999;

    private final Repositorio repo;

    public ServicioProductos(Repositorio repo) { this.repo = repo; }

    public synchronized List<Producto> listar(boolean incluirInactivos) {
        return repo.productos.leer().stream()
                .filter(p -> incluirInactivos || p.activo)
                .sorted(Comparator.comparing((Producto p) -> normalizar(p.nombre)))
                .collect(Collectors.toList());
    }

    /** Búsqueda por nombre, código o categoría, sin distinguir mayúsculas ni tildes (RF-006). */
    public synchronized List<Producto> buscar(String texto, boolean incluirInactivos) {
        String t = normalizar(texto).replaceAll("\\s+", " ");
        return listar(incluirInactivos).stream()
                .filter(p -> t.isEmpty() || normalizar(p.nombre).contains(t) || normalizar(p.codigo).contains(t)
                        || normalizar(p.categoria).contains(t))
                .collect(Collectors.toList());
    }

    public synchronized Optional<Producto> porCodigo(String codigo) {
        String c = codigo == null ? "" : codigo.trim();
        return repo.productos.leer().stream().filter(p -> p.codigo.equalsIgnoreCase(c)).findFirst();
    }

    /** Categorías para el formulario: las predefinidas y las que el administrador haya creado. */
    public synchronized List<String> categorias() {
        return Categorias.paraSelector(repo.productos.leer().stream().map(p -> p.categoria).collect(Collectors.toList()));
    }

    /** Código que recibirá el siguiente producto de la categoría, o vacío si la categoría no es válida. */
    public synchronized String siguienteCodigo(String categoria) {
        try {
            List<Producto> lista = repo.productos.leer();
            return siguienteCodigo(normalizarCategoria(categoria, lista), lista);
        } catch (ErrorNegocio e) {
            return "";
        }
    }

    /**
     * Busca un producto con el mismo nombre sin distinguir mayúsculas, tildes ni espacios repetidos.
     * Se usa para impedir duplicados («Escoba», «ESCOBA» y «escoba » son el mismo producto).
     */
    public synchronized Optional<Producto> mismoNombre(String nombre, String excluirCodigo) {
        return mismoNombre(repo.productos.leer(), nombre, excluirCodigo);
    }

    /**
     * Registra un producto nuevo. El código se asigna automáticamente según la categoría y la
     * cantidad inicial queda como movimiento de entrada (RF-002, RF-011).
     */
    public synchronized Producto crear(Producto nuevo, String usuario) {
        List<Producto> lista = repo.productos.leer();
        Producto p = validar(nuevo, lista, null);
        if (nuevo.existencias < 0) throw new ErrorNegocio("La cantidad inicial no puede ser negativa.");
        if (nuevo.existencias > CANTIDAD_MAXIMA)
            throw new ErrorNegocio("La cantidad inicial no puede superar " + CANTIDAD_MAXIMA + " unidades.");
        String codigo = nuevo.codigo == null ? "" : nuevo.codigo.trim().toUpperCase();
        if (codigo.isEmpty()) {
            codigo = siguienteCodigo(p.categoria, lista);
        } else {
            if (!codigo.matches("[A-Z0-9-]{2,15}"))
                throw new ErrorNegocio("El código debe tener entre 2 y 15 caracteres: letras, números o guion.");
            String c = codigo;
            if (lista.stream().anyMatch(x -> x.codigo.equalsIgnoreCase(c)))
                throw new ErrorNegocio("Ya existe un producto con el código " + codigo + ".");
        }
        p.codigo = codigo;
        p.existencias = nuevo.existencias;
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

    /**
     * Modifica nombre, categoría, precio y cantidad mínima (RF-003). Las existencias solo cambian con
     * entradas, salidas y ventas. Si cambia la categoría, el producto recibe el código de la nueva
     * categoría y su historial de movimientos y ventas se actualiza con el nuevo código.
     *
     * @return el código del producto después de la modificación
     */
    public synchronized String modificar(Producto cambios) {
        if (cambios == null || cambios.codigo == null) throw new ErrorNegocio("Seleccione el producto que desea modificar.");
        List<Producto> lista = repo.productos.leer();
        Producto p = lista.stream().filter(x -> x.codigo.equalsIgnoreCase(cambios.codigo.trim())).findFirst()
                .orElseThrow(() -> new ErrorNegocio("El producto no existe."));
        Producto datos = validar(cambios, lista, p.codigo);
        String anterior = p.codigo;
        boolean cambiaCategoria = !Categorias.clave(datos.categoria).equals(Categorias.clave(p.categoria));
        if (cambiaCategoria) {
            List<Producto> otros = lista.stream().filter(x -> x != p).collect(Collectors.toList());
            p.codigo = siguienteCodigo(datos.categoria, otros);
        }
        p.nombre = datos.nombre;
        p.categoria = datos.categoria;
        p.precioVenta = datos.precioVenta;
        p.cantidadMinima = datos.cantidadMinima;
        repo.productos.guardar(lista);
        if (cambiaCategoria && !p.codigo.equals(anterior)) actualizarHistorial(anterior, p.codigo);
        return p.codigo;
    }

    /** Activa o desactiva un producto sin eliminarlo, para conservar su historial (RF-004). */
    public synchronized void cambiarEstado(String codigo, boolean activo) {
        List<Producto> lista = repo.productos.leer();
        String c = codigo == null ? "" : codigo.trim();
        Producto p = lista.stream().filter(x -> x.codigo.equalsIgnoreCase(c)).findFirst()
                .orElseThrow(() -> new ErrorNegocio("El producto no existe."));
        if (activo) {
            mismoNombre(lista, p.nombre, p.codigo).filter(x -> x.activo).ifPresent(x -> {
                throw new ErrorNegocio("No se puede activar: ya hay un producto activo llamado «" + x.nombre + "» (" + x.codigo + ").");
            });
        }
        p.activo = activo;
        repo.productos.guardar(lista);
    }

    public synchronized List<Producto> pocasExistencias() {
        return listar(false).stream().filter(p -> p.existencias <= p.cantidadMinima)
                .sorted(Comparator.comparingInt((Producto p) -> p.existencias)).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- reglas internas

    /** Valida los datos y devuelve una copia con el nombre y la categoría normalizados. */
    private static Producto validar(Producto p, List<Producto> lista, String codigoPropio) {
        if (p == null) throw new ErrorNegocio("Faltan los datos del producto.");
        Producto r = p.copia();
        r.categoria = normalizarCategoria(p.categoria, lista);
        r.nombre = normalizarNombre(p.nombre);
        mismoNombre(lista, r.nombre, codigoPropio).ifPresent(x -> {
            throw new ErrorNegocio("Ya existe un producto llamado «" + x.nombre + "» con el código " + x.codigo
                    + (x.activo ? "." : ", pero está desactivado. Actívelo desde el módulo Productos.")
                    + "\nNo se permiten productos repetidos, aunque se escriban con mayúsculas, minúsculas o tildes diferentes.");
        });
        if (p.precioVenta < PRECIO_MINIMO)
            throw new ErrorNegocio("El precio de venta mínimo es " + Formato.pesos(PRECIO_MINIMO)
                    + ", la moneda de menor valor en Colombia.");
        if (p.precioVenta % PRECIO_MINIMO != 0)
            throw new ErrorNegocio("El precio debe ser múltiplo de $ 50, porque en Colombia no circulan monedas de menor valor. "
                    + "Por ejemplo: " + Formato.pesos(p.precioVenta / PRECIO_MINIMO * PRECIO_MINIMO) + " o "
                    + Formato.pesos((p.precioVenta / PRECIO_MINIMO + 1) * PRECIO_MINIMO) + ".");
        if (p.precioVenta > PRECIO_MAXIMO) throw new ErrorNegocio("El precio de venta no puede superar " + Formato.pesos(PRECIO_MAXIMO) + ".");
        if (p.cantidadMinima < 0) throw new ErrorNegocio("La cantidad mínima no puede ser negativa.");
        if (p.cantidadMinima > CANTIDAD_MAXIMA) throw new ErrorNegocio("La cantidad mínima no puede superar " + CANTIDAD_MAXIMA + " unidades.");
        return r;
    }

    /** Nombre sin espacios repetidos y con la primera letra en mayúscula. */
    static String normalizarNombre(String nombre) {
        String n = nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) throw new ErrorNegocio("El nombre del producto es obligatorio.");
        if (n.length() < 3) throw new ErrorNegocio("El nombre del producto debe tener al menos 3 caracteres.");
        if (n.length() > LONGITUD_NOMBRE) throw new ErrorNegocio("El nombre no puede superar " + LONGITUD_NOMBRE + " caracteres.");
        if (!n.matches(".*\\p{L}.*")) throw new ErrorNegocio("El nombre del producto debe contener letras.");
        // Un nombre escrito todo en mayúsculas («ESCOBA DE MADERA») se guarda como «Escoba de madera».
        if (n.equals(n.toUpperCase()) && n.chars().filter(Character::isLetter).count() > 3) n = n.toLowerCase();
        if (!n.matches("[\\p{L}\\p{N} .,()/%°+&'\"-]+"))
            throw new ErrorNegocio("El nombre contiene caracteres no permitidos. Use letras, números, espacios y . , ( ) / % - +");
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static String normalizarCategoria(String categoria, List<Producto> lista) {
        return Categorias.normalizar(categoria, lista.stream().map(x -> x.categoria).collect(Collectors.toList()));
    }

    private static Optional<Producto> mismoNombre(List<Producto> lista, String nombre, String excluirCodigo) {
        String k = clave(nombre);
        if (k.isEmpty()) return Optional.empty();
        return lista.stream()
                .filter(x -> excluirCodigo == null || !x.codigo.equalsIgnoreCase(excluirCodigo))
                .filter(x -> clave(x.nombre).equals(k))
                .findFirst();
    }

    private static String clave(String nombre) {
        return normalizar(nombre).replaceAll("\\s+", " ");
    }

    private static String siguienteCodigo(String categoria, List<Producto> lista) {
        String prefijo = Categorias.prefijo(categoria, lista);
        int max = 0;
        for (Producto x : lista) {
            if (x.codigo == null || !x.codigo.startsWith(prefijo + "-")) continue;
            String n = x.codigo.substring(prefijo.length() + 1);
            if (n.matches("\\d{1,9}")) max = Math.max(max, Integer.parseInt(n));
        }
        return String.format("%s-%03d", prefijo, max + 1);
    }

    /** Reemplaza el código anterior por el nuevo en los movimientos y en las ventas registradas. */
    private void actualizarHistorial(String anterior, String nuevo) {
        List<Movimiento> movs = repo.movimientos.leer();
        boolean cambio = false;
        for (Movimiento m : movs) if (anterior.equals(m.codigoProducto)) { m.codigoProducto = nuevo; cambio = true; }
        if (cambio) repo.movimientos.guardar(movs);
        List<Venta> ventas = repo.ventas.leer();
        cambio = false;
        for (Venta v : ventas) for (ItemVenta i : v.items) if (anterior.equals(i.codigoProducto)) { i.codigoProducto = nuevo; cambio = true; }
        if (cambio) repo.ventas.guardar(ventas);
    }

    static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.toLowerCase().trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
