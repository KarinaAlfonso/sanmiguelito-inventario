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
        String limpio = texto == null ? "" : texto.replace("$", "").replace(".", "").replace(" ", "").trim();
        if (limpio.isEmpty()) throw new ErrorNegocio("El campo " + campo + " es obligatorio.");
        if (!limpio.matches("\\d{1,10}")) throw new ErrorNegocio("El campo " + campo + " debe ser un valor numérico en pesos, sin decimales.");
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
