package com.sanmiguelito.datos;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Almacén de una lista de registros en un archivo JSON (arquitectura de servidor de archivos).
 *
 * Para evitar la corrupción del archivo ante un cierre inesperado o un corte de energía
 * (riesgo identificado en las tablas 9.1 y 10.1), la escritura se hace primero en un archivo
 * temporal y después se reemplaza el original con un movimiento atómico.
 */
public class AlmacenJson<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Path archivo;
    private final JavaType tipoLista;

    public AlmacenJson(Path archivo, Class<T> tipo) {
        this.archivo = archivo;
        this.tipoLista = MAPPER.getTypeFactory().constructCollectionType(List.class, tipo);
    }

    public Path archivo() { return archivo; }

    /** Lee todos los registros. Si el archivo no existe, devuelve una lista vacía. */
    public synchronized List<T> leer() {
        try {
            if (!Files.exists(archivo) || Files.size(archivo) == 0) return new ArrayList<>();
            List<T> lista = MAPPER.readValue(archivo.toFile(), tipoLista);
            return lista == null ? new ArrayList<>() : new ArrayList<>(lista);
        } catch (IOException e) {
            throw new ErrorDatos("No fue posible leer el archivo de datos " + archivo.getFileName()
                    + ". Restaure una copia de seguridad desde el módulo Respaldos.", e);
        }
    }

    /** Guarda todos los registros de forma segura (archivo temporal + reemplazo atómico). */
    public synchronized void guardar(List<T> registros) {
        try {
            Files.createDirectories(archivo.getParent());
            Path temporal = archivo.resolveSibling(archivo.getFileName() + ".tmp");
            MAPPER.writeValue(temporal.toFile(), registros);
            try {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ErrorDatos("No fue posible guardar el archivo de datos " + archivo.getFileName() + ".", e);
        }
    }
}
