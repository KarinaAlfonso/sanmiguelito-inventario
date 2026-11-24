package com.sanmiguelito.servicio;

import com.sanmiguelito.modelo.Producto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Categorías de productos de la cacharrería y prefijos de los códigos automáticos.
 *
 * Las categorías predefinidas tienen un prefijo fijo (Papelería → PAP-001, PAP-002…).
 * Si el administrador escribe una categoría nueva, el sistema le asigna un prefijo de tres
 * letras que no esté en uso. Las comparaciones no distinguen mayúsculas, tildes, espacios
 * repetidos ni la "s" final del plural (por ejemplo, «plasticos» es la categoría «Plástico»).
 */
public final class Categorias {

    /** Categorías predefinidas, en el orden en que se muestran, con su prefijo de código. */
    public static final Map<String, String> PREDEFINIDAS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Papelería", "PAP");
        m.put("Navidad", "NAV");
        m.put("Aseo", "ASE");
        m.put("Plástico", "PLA");
        m.put("Loza", "LOZ");
        m.put("Juguetería", "JUG");
        m.put("Fantasía", "FAN");
        m.put("Mascotas", "MAS");
        m.put("Otro", "OTR");
        PREDEFINIDAS = java.util.Collections.unmodifiableMap(m);
    }

    public static final int LONGITUD_MAXIMA = 30;

    private Categorias() { }

    /** Clave de comparación: minúsculas, sin tildes, espacios simples y sin la "s" final. */
    static String clave(String texto) {
        String c = ServicioProductos.normalizar(texto).replaceAll("\\s+", " ");
        if (c.length() > 3 && c.endsWith("s")) c = c.substring(0, c.length() - 1);
        return c;
    }

    /** true si la categoría existente empieza como el texto escrito, sin importar mayúsculas ni tildes. */
    public static boolean empiezaIgual(String existente, String escrito) {
        String e = ServicioProductos.normalizar(existente), t = ServicioProductos.normalizar(escrito).replaceAll("\\s+", " ");
        return !t.isEmpty() && e.startsWith(t) && !e.equals(t);
    }

    /** true si los dos textos son la misma categoría (sin importar mayúsculas, tildes ni plural). */
    public static boolean iguales(String a, String b) {
        return clave(a).equals(clave(b));
    }

    /**
     * Valida el texto escrito por el usuario y lo convierte en el nombre oficial de la categoría:
     * si coincide con una predefinida o con una ya usada, devuelve esa; si es nueva, la devuelve
     * con la primera letra en mayúscula.
     */
    public static String normalizar(String texto, Collection<String> existentes) {
        String t = texto == null ? "" : texto.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) throw new ErrorNegocio("Seleccione o escriba la categoría del producto.");
        if (t.length() < 3 || t.length() > LONGITUD_MAXIMA)
            throw new ErrorNegocio("La categoría debe tener entre 3 y " + LONGITUD_MAXIMA + " caracteres.");
        if (!t.matches("[\\p{L} ]+"))
            throw new ErrorNegocio("La categoría solo puede contener letras y espacios.");
        String k = clave(t);
        for (String p : PREDEFINIDAS.keySet()) if (clave(p).equals(k)) return p;
        if (existentes != null) for (String e : existentes) if (e != null && clave(e).equals(k)) return e;
        return Character.toUpperCase(t.charAt(0)) + t.substring(1).toLowerCase();
    }

    /** Lista para el selector: predefinidas (con «Otro» al final) y después las creadas por el usuario. */
    public static List<String> paraSelector(Collection<String> usadas) {
        List<String> lista = new ArrayList<>(PREDEFINIDAS.keySet());
        lista.remove("Otro");
        Set<String> claves = lista.stream().map(Categorias::clave).collect(Collectors.toSet());
        claves.add(clave("Otro"));
        TreeSet<String> nuevas = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String u : usadas) if (u != null && claves.add(clave(u))) nuevas.add(u);
        lista.addAll(nuevas);
        lista.add("Otro");
        return lista;
    }

    /** Prefijo del código para la categoría (ya normalizada) teniendo en cuenta los productos existentes. */
    static String prefijo(String categoria, List<Producto> productos) {
        String k = clave(categoria);
        for (Map.Entry<String, String> e : PREDEFINIDAS.entrySet()) if (clave(e.getKey()).equals(k)) return e.getValue();

        // Categoría creada por el usuario: se reutiliza el prefijo que ya tengan sus productos.
        Map<String, Integer> usos = new HashMap<>();
        for (Producto p : productos)
            if (p.categoria != null && clave(p.categoria).equals(k) && p.codigo != null && p.codigo.contains("-"))
                usos.merge(p.codigo.substring(0, p.codigo.indexOf('-')), 1, Integer::sum);
        if (!usos.isEmpty())
            return usos.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();

        // Prefijo nuevo que no esté en uso por otra categoría.
        Set<String> ocupados = new java.util.HashSet<>(PREDEFINIDAS.values());
        for (Producto p : productos)
            if (p.codigo != null && p.codigo.contains("-")) ocupados.add(p.codigo.substring(0, p.codigo.indexOf('-')));
        String letras = ServicioProductos.normalizar(categoria).replaceAll("[^a-z]", "").toUpperCase();
        while (letras.length() < 3) letras += "X";
        for (String candidato : candidatos(letras)) if (!ocupados.contains(candidato)) return candidato;
        for (char a = 'A'; a <= 'Z'; a++)
            for (char b = 'A'; b <= 'Z'; b++) {
                String c = "" + letras.charAt(0) + a + b;
                if (!ocupados.contains(c)) return c;
            }
        throw new ErrorNegocio("No fue posible asignar un código a la categoría «" + categoria + "».");
    }

    /** Combinaciones de tres letras que conservan el orden: MUEBLE → MUE, MUB, MUL, MEB… */
    private static List<String> candidatos(String letras) {
        List<String> c = new ArrayList<>();
        int n = Math.min(letras.length(), 12);
        for (int i = 1; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                String s = "" + letras.charAt(0) + letras.charAt(i) + letras.charAt(j);
                if (!c.contains(s)) c.add(s);
            }
        if (c.isEmpty()) c.add(letras.substring(0, 3));
        return c;
    }
}
