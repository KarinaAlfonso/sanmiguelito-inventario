package com.sanmiguelito.ui;

import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.modelo.Venta;
import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.servicio.Formato;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Panel de inicio con indicadores del día y alertas de pocas existencias. */
public class VistaInicio {

    private final Contexto ctx;
    private final Runnable irAVentas;

    public VistaInicio(Contexto ctx, Runnable irAVentas) {
        this.ctx = ctx;
        this.irAVentas = irAVentas;
    }

    public Node vista() {
        LocalDate hoy = LocalDate.now();
        List<Venta> ventasHoy = ctx.ventas.listar(hoy, hoy);
        long totalHoy = ventasHoy.stream().mapToLong(Venta::total).sum();
        List<Producto> bajos = ctx.productos.pocasExistencias();
        int activos = ctx.productos.listar(false).size();

        HBox indicadores = new HBox(14,
                indicador(String.valueOf(ventasHoy.size()), "Ventas de hoy", "ind-rojo"),
                indicador(Formato.pesos(totalHoy), "Total vendido hoy", "ind-oscuro"),
                indicador(String.valueOf(activos), "Productos activos", "ind-azul"),
                indicador(String.valueOf(bajos.size()), "Con pocas existencias o agotados", "ind-naranja"));

        Label tAlertas = new Label("Productos que requieren reposición");
        tAlertas.getStyleClass().add("seccion");
        TableView<Producto> tabla = Ui.tabla("Todos los productos tienen existencias suficientes.");
        tabla.getColumns().add(Ui.columna("Código", 90, p -> p.codigo));
        tabla.getColumns().add(Ui.columna("Producto", 260, p -> p.nombre));
        tabla.getColumns().add(Ui.columna("Categoría", 140, p -> p.categoria));
        tabla.getColumns().add(Ui.columnaNumero("Existencias", 100, p -> String.valueOf(p.existencias)));
        tabla.getColumns().add(Ui.columnaNumero("Mínimo", 90, p -> String.valueOf(p.cantidadMinima)));
        tabla.getColumns().add(VistaProductos.columnaEstado());
        tabla.getItems().setAll(bajos);
        tabla.setPrefHeight(320);

        Button nuevaVenta = Ui.primario("Registrar una venta");
        nuevaVenta.setOnAction(e -> irAVentas.run());
        String saludo = "Bienvenido(a), " + ctx.sesion().nombreCompleto + " · " + LocalDateTime.now().format(Formato.FECHA_HORA);

        VBox v = new VBox(18, Ui.encabezado("Inicio", saludo), indicadores, Ui.fila(nuevaVenta), Ui.tarjeta(tAlertas, tabla));
        v.setPadding(new Insets(26));
        return v;
    }

    private Node indicador(String valor, String texto, String clase) {
        Label v = new Label(valor);
        v.getStyleClass().add("indicador-valor");
        Label t = new Label(texto);
        t.getStyleClass().add("indicador-texto");
        t.setWrapText(true);
        VBox b = new VBox(4, v, t);
        b.getStyleClass().addAll("indicador", clase);
        b.setPrefWidth(230);
        HBox.setHgrow(b, Priority.ALWAYS);
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
    }
}
