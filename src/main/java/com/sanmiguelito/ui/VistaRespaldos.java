package com.sanmiguelito.ui;

import com.sanmiguelito.servicio.Contexto;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** Copias de seguridad y recuperación de la información (RNF-006, proceso 8.0). Solo administrador. */
public class VistaRespaldos {

    private final Contexto ctx;
    private final ListView<Path> lista = new ListView<>();

    public VistaRespaldos(Contexto ctx) { this.ctx = ctx; }

    public Node vista() {
        ctx.exigirAdministrador();
        lista.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Path p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setText(null); return; }
                long kb = 0;
                try { kb = Files.size(p) / 1024 + 1; } catch (Exception ignored) { }
                setText(p.getFileName() + "   (" + kb + " KB)");
            }
        });
        lista.setPrefHeight(320);

        Button crear = Ui.primario("Crear copia de seguridad");
        crear.setOnAction(e -> {
            final Path[] p = new Path[1];
            if (Ui.ejecutar("Copia de seguridad", () -> p[0] = ctx.respaldos.crearRespaldo(null))) {
                cargar();
                if (Ui.confirmar("Copia de seguridad creada", "Se creó la copia " + p[0].getFileName()
                        + ".\n\n¿Desea guardar también una copia en una memoria USB u otra carpeta?")) copiarExterno(p[0]);
            }
        });
        Button copiar = Ui.secundario("Copiar a memoria USB");
        copiar.setOnAction(e -> {
            Path p = lista.getSelectionModel().getSelectedItem();
            if (p == null) { Ui.aviso("Copiar respaldo", "Seleccione un respaldo de la lista."); return; }
            copiarExterno(p);
        });
        Button restaurar = Ui.peligro("Restaurar respaldo seleccionado");
        restaurar.setOnAction(e -> restaurar(lista.getSelectionModel().getSelectedItem()));
        Button desdeArchivo = Ui.secundario("Restaurar desde un archivo…");
        desdeArchivo.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Seleccione el archivo de respaldo");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Respaldos de San Miguelito", "respaldo_*.zip", "*.zip"));
            File f = fc.showOpenDialog(lista.getScene().getWindow());
            if (f != null) restaurar(f.toPath());
        });

        Label nota = new Label("Las copias se guardan en " + ctx.repo.carpetaRespaldos()
                + ". Se recomienda crear una copia al final de cada día y guardarla también en la memoria USB del establecimiento.\n"
                + "Antes de restaurar, el sistema crea automáticamente una copia del estado actual.");
        nota.getStyleClass().add("nota");
        nota.setWrapText(true);
        VBox v = new VBox(16, Ui.encabezado("Copias de seguridad",
                        "Proteja la información de usuarios, productos, ventas y movimientos ante fallas del computador o eliminaciones accidentales."),
                Ui.tarjeta(Ui.fila(crear, copiar, Ui.espacio(), desdeArchivo, restaurar), lista, nota));
        v.setPadding(new Insets(26));
        cargar();
        return v;
    }

    private void cargar() { Ui.ejecutar("Respaldos", () -> lista.getItems().setAll(ctx.respaldos.listar())); }

    private void copiarExterno(Path respaldo) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Seleccione la memoria USB o la carpeta de destino");
        File dir = dc.showDialog(lista.getScene().getWindow());
        if (dir == null) return;
        final Path[] destino = new Path[1];
        if (Ui.ejecutar("Copiar respaldo", () -> destino[0] = ctx.respaldos.copiarA(respaldo, dir.toPath())))
            Ui.info("Copiar respaldo", "La copia se guardó en " + destino[0] + ".");
    }

    private void restaurar(Path respaldo) {
        if (respaldo == null) { Ui.aviso("Restaurar respaldo", "Seleccione un respaldo de la lista."); return; }
        if (!Ui.confirmar("Restaurar respaldo", "Se reemplazarán los datos actuales por los del respaldo " + respaldo.getFileName()
                + ".\nAntes de hacerlo se guardará una copia automática del estado actual.\n\n¿Desea continuar?")) return;
        if (Ui.ejecutar("Restaurar respaldo", () -> {
            ctx.exigirAdministrador();
            ctx.respaldos.restaurar(respaldo);
        })) {
            cargar();
            Ui.info("Restaurar respaldo", "La información se restauró correctamente. Los datos mostrados en las demás pantallas se actualizarán al abrirlas.");
        }
    }
}
