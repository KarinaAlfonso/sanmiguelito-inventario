package com.sanmiguelito.servicio;

import com.sanmiguelito.datos.ErrorDatos;
import com.sanmiguelito.datos.Repositorio;
import com.sanmiguelito.modelo.Rol;
import com.sanmiguelito.modelo.Usuario;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Copias de seguridad y recuperación de los archivos de datos (RNF-006, proceso 8.0). */
public class ServicioRespaldos {

    private static final Set<String> ARCHIVOS = Set.of("usuarios.json", "productos.json", "movimientos.json", "ventas.json");
    private static final DateTimeFormatter NOMBRE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Repositorio repo;

    public ServicioRespaldos(Repositorio repo) { this.repo = repo; }

    /** Crea un archivo ZIP con los cuatro archivos de datos en la carpeta de respaldos. */
    public synchronized Path crearRespaldo(String etiqueta) {
        try {
            Files.createDirectories(repo.carpetaRespaldos());
            String base = "respaldo_" + LocalDateTime.now().format(NOMBRE) + (etiqueta == null ? "" : "_" + etiqueta);
            Path destino = repo.carpetaRespaldos().resolve(base + ".zip");
            for (int i = 2; Files.exists(destino); i++) destino = repo.carpetaRespaldos().resolve(base + "_" + i + ".zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(destino))) {
                for (String a : ARCHIVOS) {
                    Path origen = repo.carpetaDatos().resolve(a);
                    if (!Files.exists(origen)) continue;
                    zip.putNextEntry(new ZipEntry(a));
                    Files.copy(origen, zip);
                    zip.closeEntry();
                }
            }
            return destino;
        } catch (IOException e) {
            throw new ErrorNegocio("No fue posible crear la copia de seguridad: " + e.getMessage());
        }
    }

    /** Copia un respaldo a otra ubicación, por ejemplo una memoria USB. */
    public Path copiarA(Path respaldo, Path carpetaDestino) {
        if (respaldo == null || !Files.isRegularFile(respaldo)) throw new ErrorNegocio("Seleccione el respaldo que desea copiar.");
        if (carpetaDestino == null || !Files.isDirectory(carpetaDestino)) throw new ErrorNegocio("Seleccione una carpeta de destino válida.");
        try {
            Path destino = carpetaDestino.resolve(respaldo.getFileName());
            Files.copy(respaldo, destino, StandardCopyOption.REPLACE_EXISTING);
            return destino;
        } catch (IOException e) {
            throw new ErrorNegocio("No fue posible copiar el respaldo: " + e.getMessage());
        }
    }

    public synchronized List<Path> listar() {
        List<Path> lista = new ArrayList<>();
        if (!Files.isDirectory(repo.carpetaRespaldos())) return lista;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(repo.carpetaRespaldos(), "respaldo_*.zip")) {
            ds.forEach(lista::add);
        } catch (IOException e) {
            throw new ErrorNegocio("No fue posible leer la carpeta de respaldos.");
        }
        lista.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        return lista;
    }

    /**
     * Restaura los datos desde un respaldo. Antes de reemplazar los archivos crea un respaldo
     * automático del estado actual, para poder deshacer la restauración.
     */
    public synchronized void restaurar(Path respaldo) {
        if (respaldo == null || !Files.isRegularFile(respaldo)) throw new ErrorNegocio("Seleccione un archivo de respaldo válido.");
        if (!respaldo.getFileName().toString().toLowerCase().endsWith(".zip"))
            throw new ErrorNegocio("El respaldo debe ser un archivo .zip creado por el sistema.");

        // 1. Se lee y se valida todo el contenido antes de tocar los datos actuales.
        Map<String, byte[]> contenido = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(respaldo))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (!ARCHIVOS.contains(e.getName())) continue;   // solo se aceptan los archivos de datos conocidos
                contenido.put(e.getName(), leerLimitado(zip));
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new ErrorNegocio("El archivo seleccionado no es un respaldo válido.");
        }
        if (!contenido.containsKey("productos.json") || !contenido.containsKey("usuarios.json"))
            throw new ErrorNegocio("El archivo seleccionado no contiene los datos del sistema.");
        try {
            List<Usuario> usuarios = repo.usuarios.interpretar(contenido.get("usuarios.json"));
            repo.productos.interpretar(contenido.get("productos.json"));
            if (contenido.containsKey("movimientos.json")) repo.movimientos.interpretar(contenido.get("movimientos.json"));
            if (contenido.containsKey("ventas.json")) repo.ventas.interpretar(contenido.get("ventas.json"));
            if (usuarios.stream().noneMatch(u -> u.activo && u.rol == Rol.ADMINISTRADOR && u.usuario != null && u.hashContrasena != null))
                throw new ErrorNegocio("El respaldo no tiene un administrador activo; si se restaurara nadie podría administrar el sistema.");
        } catch (ErrorDatos ex) {
            throw new ErrorNegocio("El respaldo está dañado y no se restauró. Los datos actuales no se modificaron.\nDetalle: " + ex.getMessage());
        }

        // 2. Copia del estado actual para poder deshacer, y reemplazo de los archivos.
        crearRespaldo("antes_de_restaurar");
        try {
            Files.createDirectories(repo.carpetaDatos());
            for (Map.Entry<String, byte[]> e : contenido.entrySet()) {
                Path temporal = repo.carpetaDatos().resolve(e.getKey() + ".restaurar");
                Files.write(temporal, e.getValue());
                Files.move(temporal, repo.carpetaDatos().resolve(e.getKey()), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new ErrorNegocio("No fue posible restaurar el respaldo: " + ex.getMessage());
        }
    }

    /**
     * Crea un respaldo automático si todavía no hay uno del día y conserva solo los
     * {@value #AUTOMATICOS_CONSERVADOS} respaldos automáticos más recientes.
     *
     * @return el respaldo creado, o null si ya existía uno del día
     */
    public synchronized Path respaldoAutomaticoDelDia() {
        String hoy = "respaldo_" + LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        if (listar().stream().anyMatch(p -> p.getFileName().toString().startsWith(hoy) && p.getFileName().toString().contains("_automatico")))
            return null;
        Path creado = crearRespaldo("automatico");
        List<Path> automaticos = listar().stream().filter(p -> p.getFileName().toString().contains("_automatico")).toList();
        for (int i = AUTOMATICOS_CONSERVADOS; i < automaticos.size(); i++) {
            try { Files.deleteIfExists(automaticos.get(i)); } catch (IOException ignorado) { /* se intenta de nuevo otro día */ }
        }
        return creado;
    }

    public static final int AUTOMATICOS_CONSERVADOS = 15;
    private static final int TAMANO_MAXIMO = 50 * 1024 * 1024;

    private static byte[] leerLimitado(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > TAMANO_MAXIMO) throw new IOException("Archivo demasiado grande");
        }
        return out.toByteArray();
    }
}
