package com.sanmiguelito.servicio;

import com.sanmiguelito.datos.Repositorio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
            String nombre = "respaldo_" + LocalDateTime.now().format(NOMBRE) + (etiqueta == null ? "" : "_" + etiqueta) + ".zip";
            Path destino = repo.carpetaRespaldos().resolve(nombre);
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
        List<String> encontrados = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(respaldo))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) encontrados.add(e.getName());
        } catch (IOException ex) {
            throw new ErrorNegocio("El archivo seleccionado no es un respaldo válido.");
        }
        if (!encontrados.contains("productos.json") || !encontrados.contains("usuarios.json"))
            throw new ErrorNegocio("El archivo seleccionado no contiene los datos del sistema.");
        crearRespaldo("antes_de_restaurar");
        try {
            Files.createDirectories(repo.carpetaDatos());
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(respaldo))) {
                ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    if (!ARCHIVOS.contains(e.getName())) continue;   // solo se aceptan los archivos de datos conocidos
                    Path temporal = repo.carpetaDatos().resolve(e.getName() + ".restaurar");
                    try (OutputStream out = Files.newOutputStream(temporal)) {
                        copiar(zip, out);
                    }
                    Files.move(temporal, repo.carpetaDatos().resolve(e.getName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ex) {
            throw new ErrorNegocio("No fue posible restaurar el respaldo: " + ex.getMessage());
        }
    }

    private static void copiar(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
