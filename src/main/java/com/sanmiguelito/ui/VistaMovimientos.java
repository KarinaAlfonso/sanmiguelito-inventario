package com.sanmiguelito.ui;

import com.sanmiguelito.modelo.Movimiento;
import com.sanmiguelito.modelo.TipoMovimiento;
import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.servicio.ErrorNegocio;
import com.sanmiguelito.servicio.Formato;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

/** Consulta de movimientos del inventario con filtros (RF-011, proceso 6.0). */
public class VistaMovimientos {

    private final Contexto ctx;
    private final TableView<Movimiento> tabla = Ui.tabla("No hay movimientos con los filtros seleccionados.");
    private final DatePicker desde = Ui.fecha(LocalDate.now().minusDays(30));
    private final DatePicker hasta = Ui.fecha(LocalDate.now());
    private final TextField producto = Ui.limitar(new TextField(), 60);
    private final ComboBox<String> tipo = new ComboBox<>();
    private final Label resumen = new Label();

    public VistaMovimientos(Contexto ctx) { this.ctx = ctx; }

    public Node vista() {
        producto.setPromptText("Nombre o código");
        tipo.getItems().setAll("Todos", "Entrada", "Salida", "Venta");
        tipo.setValue("Todos");
        Button filtrar = Ui.primario("Consultar");
        filtrar.setOnAction(e -> cargar());
        Button limpiar = Ui.secundario("Limpiar filtros");
        limpiar.setOnAction(e -> {
            desde.setValue(null);
            hasta.setValue(null);
            producto.clear();
            tipo.setValue("Todos");
            cargar();
        });
        tabla.getColumns().add(Ui.columna("Fecha", 150, m -> Formato.fechaHora(m.fecha)));
        tabla.getColumns().add(Ui.columna("Tipo", 80, m -> m.tipo.toString()));
        tabla.getColumns().add(Ui.columna("Código", 90, m -> m.codigoProducto));
        tabla.getColumns().add(Ui.columna("Producto", 230, m -> m.nombreProducto));
        tabla.getColumns().add(Ui.columnaNumero("Cantidad", 80, m -> (m.tipo == TipoMovimiento.ENTRADA ? "+" : "−") + m.cantidad));
        tabla.getColumns().add(Ui.columnaNumero("Existencias", 95, m -> String.valueOf(m.existenciasResultantes)));
        tabla.getColumns().add(Ui.columna("Origen o motivo", 200, m -> m.motivo));
        tabla.getColumns().add(Ui.columna("Usuario", 100, m -> m.usuario));
        resumen.getStyleClass().add("nota");

        VBox v = new VBox(16, Ui.encabezado("Movimientos del inventario",
                        "Consulte las entradas, salidas y ventas registradas, con la fecha, el producto, la cantidad y el usuario que las realizó."),
                Ui.tarjeta(Ui.fila(Ui.campo("Desde", desde), Ui.campo("Hasta", hasta), Ui.campo("Producto", producto),
                        Ui.campo("Tipo", tipo), filtrar, limpiar), resumen, tabla));
        v.setPadding(new Insets(26));
        cargar();
        return v;
    }

    private void cargar() {
        Ui.ejecutar("Movimientos", () -> {
            if (desde.getValue() != null && hasta.getValue() != null && desde.getValue().isAfter(hasta.getValue()))
                throw new ErrorNegocio("La fecha inicial no puede ser posterior a la fecha final.");
            TipoMovimiento t = switch (tipo.getValue()) {
                case "Entrada" -> TipoMovimiento.ENTRADA;
                case "Salida" -> TipoMovimiento.SALIDA;
                case "Venta" -> TipoMovimiento.VENTA;
                default -> null;
            };
            List<Movimiento> lista = ctx.inventario.consultar(desde.getValue(), hasta.getValue(), producto.getText(), t);
            tabla.getItems().setAll(lista);
            resumen.setText(lista.size() + " movimiento(s) encontrados.");
        });
    }
}
