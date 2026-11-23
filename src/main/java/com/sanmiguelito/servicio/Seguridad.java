package com.sanmiguelito.servicio;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Cifrado de contraseñas con PBKDF2 (HMAC-SHA256) y sal aleatoria (RNF-002).
 * Las contraseñas nunca se guardan en texto plano en los archivos JSON.
 */
public final class Seguridad {

    private static final int ITERACIONES = 120_000;
    private static final int LONGITUD_BITS = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Seguridad() { }

    public static String nuevaSal() {
        byte[] sal = new byte[16];
        ALEATORIO.nextBytes(sal);
        return Base64.getEncoder().encodeToString(sal);
    }

    public static String hash(String contrasena, String salBase64) {
        try {
            KeySpec spec = new PBEKeySpec(contrasena.toCharArray(), Base64.getDecoder().decode(salBase64), ITERACIONES, LONGITUD_BITS);
            byte[] h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(h);
        } catch (Exception e) {
            throw new IllegalStateException("No fue posible cifrar la contraseña", e);
        }
    }

    public static boolean verificar(String contrasena, String salBase64, String hashBase64) {
        if (contrasena == null || salBase64 == null || hashBase64 == null) return false;
        byte[] esperado = Base64.getDecoder().decode(hashBase64);
        byte[] calculado = Base64.getDecoder().decode(hash(contrasena, salBase64));
        return MessageDigest.isEqual(esperado, calculado);
    }

    /** Política mínima de contraseña: 8 caracteres, al menos una letra y un número. */
    public static void validarContrasena(String contrasena) {
        if (contrasena == null || contrasena.length() < 8)
            throw new ErrorNegocio("La contraseña debe tener al menos 8 caracteres.");
        if (!contrasena.matches(".*[A-Za-z].*") || !contrasena.matches(".*\\d.*"))
            throw new ErrorNegocio("La contraseña debe contener al menos una letra y un número.");
    }
}
