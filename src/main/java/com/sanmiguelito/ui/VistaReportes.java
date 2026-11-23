package com.sanmiguelito.ui;

import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.servicio.ErrorNegocio;
import com.sanmiguelito.servicio.Formato;
import com.sanmiguelito.servicio.ServicioReportes;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Supplier;

/** Generación de reportes de inventario y de ventas (RF-015, proceso 7.0). Solo administrador. */
public class VistaReportes {

    private final Contexto ctx;

    public VistaReportes(Contexto ctx) { this.ctx = ctx; }

    public Node vista() {
        ctx.exigirAdministrador();

        // --- Reporte de ventas por período, con vista previa
        DatePicker desde = new DatePicker(LocalDate.now().withDayOfMonth(1));
        DatePicker hasta = new DatePicker(LocalDate.now());
        Label resumen = new Label();
        resumen.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #960A0A;");
        TableView<Map.Entry<String, long[]>> ventas = Ui.tabla("No hay ventas en el período seleccionado.");
        ventas.getColumns().add(Ui.columna("Producto", 280, e -> e.getKey()));
        ventas.getColumns().add(Ui.columnaNumero("Unidades vendidas", 140, e -> String.valueOf(e.getValue()[0])));
        ventas.getColumns().add(Ui.columnaNumero("Valor vendido", 140, e -> Formato.pesos(e.getValue()[1])));
        ventas.setPrefHeight(260);
        Runnable vistaPrevia = () -> Ui.ejecutar("Reporte de ventas", () -> {
            validar(desde.getValue(), hasta.getValue());
            ServicioReportes.ResumenVentas r = ctx.reportes.resumenVentas(desde.getValue(), hasta.getValue());
            ventas.getItems().setAll(r.porProducto.entrySet());
            resumen.setText("Ventas: " + r.numeroVentas + "   ·   Unidades: " + r.unidades + "   ·   Total vendido: " + Formato.pesos(r.total));
        });
        Button consultar = Ui.secundario("Ver resumen");
        consultar.setOnAction(e -> vistaPrevia.run());
        Button pdfVentas = Ui.primario("Generar PDF de ventas");
        pdfVentas.setOnAction(e -> generar("Reporte de ventas", () -> {
            validar(desde.getValue(), hasta.getValue());
            return ctx.reportes.reporteVentas(desde.getValue(), hasta.getValue());
        }));
        desde.setPrefWidth(150);
        hasta.setPrefWidth(150);
        VBox tVentas = Ui.tarjeta(seccion("Ventas por período"),
                Ui.fila(Ui.campo("Desde", desde), Ui.campo("Hasta", hasta)), Ui.fila(consultar, pdfVentas), resumen, ventas);

        // --- Reportes de inventario
        TableView<Producto> bajos = Ui.tabla("Todos los productos tienen existencias suficientes.");
        bajos.getColumns().add(Ui.columna("Producto", 220, p -> p.nombre));
        bajos.getColumns().add(Ui.columnaNumero("Existencias", 90, p -> String.valueOf(p.existencias)));
        bajos.getColumns().add(Ui.columnaNumero("Mínimo", 70, p -> String.valueOf(p.cantidadMinima)));
        bajos.getColumns().add(VistaProductos.columnaEstado());
        bajos.getItems().setAll(ctx.productos.pocasExistencias());
        bajos.setPrefHeight(260);
        Button pdfInventario = Ui.primario("PDF de inventario disponible");
        pdfInventario.setOnAction(e -> generar("Reporte de inventario", ctx.reportes::reporteInventario));
        Button pdfBajos = Ui.primario("PDF de pocas existencias");
        pdfBajos.setOnAction(e -> generar("Reporte de pocas existencias", ctx.reportes::reportePocasExistencias));
        pdfInventario.setMaxWidth(Double.MAX_VALUE);
        pdfBajos.setMaxWidth(Double.MAX_VALUE);
        VBox tInv = Ui.tarjeta(seccion("Inventario"), new VBox(8, pdfInventario, pdfBajos),
                Ui.etiqueta("Productos con pocas existencias o agotados"), bajos);

        HBox cuerpo = new HBox(16, tVentas, tInv);
        HBox.setHgrow(tVentas, Priority.ALWAYS);
        HBox.setHgrow(tInv, Priority.ALWAYS);
        Label nota = new Label("Los reportes se guardan en la carpeta " + ctx.repo.carpetaDocumentos() + " y se abren automáticamente para imprimirlos.");
        nota.getStyleClass().add("nota");
        nota.setWrapText(true);
        VBox v = new VBox(16, Ui.encabezado("Reportes", "Genere reportes del inventario disponible, de los productos que requieren reposición y de las ventas de un período."),
                cuerpo, nota);
        v.setPadding(new Insets(26));
        vistaPrevia.run();
        return v;
    }

    private static void validar(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) throw new ErrorNegocio("Seleccione las fechas inicial y final del período.");
        if (desde.isAfter(hasta)) throw new ErrorNegocio("La fecha inicial no puede ser posterior a la fecha final.");
    }

    private void generar(String titulo, Supplier<Path> generador) {
        final Path[] pdf = new Path[1];
        if (Ui.ejecutar(titulo, () -> pdf[0] = generador.get())) Ui.abrir(pdf[0]);
    }

    private Label seccion(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("seccion");
        return l;
    }
}
