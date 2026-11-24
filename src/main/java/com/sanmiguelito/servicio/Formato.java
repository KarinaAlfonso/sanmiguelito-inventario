package com.sanmiguelito.servicio;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Utilidades de formato para pesos colombianos y fechas. */
public final class Formato {

    public static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    public static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");
    public static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DecimalFormat PESOS;

    static {
        DecimalFormatSymbols s = new DecimalFormatSymbols();
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        PESOS = new DecimalFormat("#,##0", s);
    }

    private Formato() { }

    public static String pesos(long valor) { return "$ " + PESOS.format(valor); }

    public static String ahoraIso() { return LocalDateTime.now().withNano(0).format(ISO); }

    public static LocalDateTime leer(String iso) { return LocalDateTime.parse(iso, ISO); }

    public static String fechaHora(String iso) {
        try { return leer(iso).format(FECHA_HORA); } catch (Exception e) { return iso; }
    }

    /** Convierte un texto ingresado por el usuario en un valor en pesos (acepta puntos de miles). */
    public static long leerPesos(String texto, String campo) {
        String t = texto == null ? "" : texto.replace("$", "").replace(" ", "").replace(" ", "").trim();
        if (t.isEmpty()) throw new ErrorNegocio("El campo " + campo + " es obligatorio.");
        if (t.contains(",")) throw new ErrorNegocio("El campo " + campo + " no lleva decimales: los pesos colombianos se escriben sin centavos (ej.: 4500 o 4.500).");
        // Se aceptan 4500 o 4.500 (puntos de miles bien ubicados); «4.5» o «1.2.3» no son precios válidos.
        if (!t.matches("\\d{1,10}") && !t.matches("\\d{1,3}(\\.\\d{3}){1,3}"))
            throw new ErrorNegocio("El campo " + campo + " debe ser un valor en pesos, sin letras, signos ni decimales (ej.: 4500 o 4.500).");
        String limpio = t.replace(".", "");
        if (limpio.length() > 10) throw new ErrorNegocio("El valor del campo " + campo + " es demasiado grande.");
        return Long.parseLong(limpio);
    }

    /** Convierte un texto en un entero no negativo. */
    public static int leerEntero(String texto, String campo) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.isEmpty()) throw new ErrorNegocio("El campo " + campo + " es obligatorio.");
        if (!limpio.matches("\\d{1,7}")) throw new ErrorNegocio("El campo " + campo + " debe ser un número entero sin signos ni decimales.");
        return Integer.parseInt(limpio);
    }
}
