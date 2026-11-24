package com.sanmiguelito.ui;

/**
 * Caracteres permitidos en cada tipo de campo. Se aplican mientras se escribe o se pega texto
 * (por ejemplo, en un campo de nombre no se pueden escribir símbolos ni emojis, y en una cantidad
 * no se pueden escribir letras). Las reglas completas se vuelven a validar al guardar.
 */
public final class Entrada {

    /** Nombre de usuario para ingresar: letras sin tilde, números, punto y guion bajo. */
    public static final String USUARIO = "a-zA-Z0-9._";
    /** Nombre de una persona: letras (con tildes y ñ), espacios, punto, apóstrofo y guion. */
    public static final String NOMBRE_PERSONA = "\\p{L} .'\\-";
    /** Nombre de un producto: letras, números, espacios y los signos usados en las descripciones. */
    public static final String NOMBRE_PRODUCTO = "\\p{L}\\p{N} .,()/%°+&'\"\\-";
    /** Categoría: solo letras y espacios. */
    public static final String CATEGORIA = "\\p{L} ";
    /** Origen o motivo de un movimiento. */
    public static final String MOTIVO = "\\p{L}\\p{N} .,;:()/#%°+&'\"\\-";
    /** Texto de búsqueda: letras, números, espacios y signos comunes (sin emojis ni caracteres de control). */
    public static final String BUSQUEDA = "\\p{L}\\p{N} .,;:()/#%°+&'\"_\\-";

    private Entrada() { }

    /**
     * Quita del texto que se escribe o se pega los caracteres que no pertenecen a la clase indicada.
     * Los tabuladores y saltos de línea se convierten en espacios.
     */
    public static String filtrar(String texto, String clasePermitida) {
        if (texto == null || texto.isEmpty()) return texto;
        String t = texto.replaceAll("[\\t\\r\\n]+", " ");
        StringBuilder sb = new StringBuilder();
        String patron = "[" + clasePermitida + "]";
        t.codePoints().forEach(cp -> {
            String c = new String(Character.toChars(cp));
            if (c.matches(patron)) sb.append(c);
        });
        return sb.toString();
    }

    /** Deja solo dígitos (cantidades). */
    public static String soloDigitos(String texto) {
        return texto == null ? null : texto.replaceAll("[^0-9]", "");
    }
}
