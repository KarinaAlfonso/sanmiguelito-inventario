package com.sanmiguelito.servicio;

import com.sanmiguelito.datos.Repositorio;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.modelo.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base de las pruebas: cada prueba trabaja en una carpeta temporal con los datos de ejemplo
 * del primer uso (14 productos con códigos PAP-001, NAV-001, ASE-001, PLA-001, LOZ-001…).
 */
abstract class BaseServicios {

    @TempDir
    Path dir;
    Contexto ctx;
    Usuario admin;

    @BeforeEach
    void preparar() {
        ctx = new Contexto(new Repositorio(dir));
        assertTrue(ctx.prepararPrimerUso());
        admin = ctx.usuarios.autenticar("admin", "SanMiguelito2026");
        ctx.iniciarSesion(admin);
    }

    Producto producto(String codigo) {
        return ctx.productos.porCodigo(codigo).orElseThrow();
    }

    int existencias(String codigo) {
        return producto(codigo).existencias;
    }

    static Producto nuevo(String nombre, String categoria, long precio, int cantidad, int minimo) {
        return new Producto(null, nombre, categoria, precio, cantidad, minimo);
    }
}
