package com.sanmiguelito.ui;

import com.sanmiguelito.modelo.Movimiento;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.servicio.ErrorNegocio;
import com.sanmiguelito.servicio.Formato;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Registro de entradas (RF-007) y salidas (RF-008) con validación de existencias (RF-009). Proceso 5.0. */
public class VistaInventario {

    private final Contexto ctx;
    private final TableView<Producto> productos = Ui.tabla("No hay productos que coincidan con la búsqueda.");
    private final TextField buscar = new TextField();

    public VistaInventario(Contexto ctx) { this.ctx = ctx; }

    public Node vista() {
        buscar.setPromptText("Buscar por nombre, código o categoría");
        buscar.textProperty().addListener((o, a, n) -> cargar());
        productos.getColumns().add(Ui.columna("Código", 90, p -> p.codigo));
        productos.getColumns().add(Ui.columna("Producto", 260, p -> p.nombre));
        productos.getColumns().add(Ui.columna("Categoría", 140, p -> p.categoria));
        productos.getColumns().add(Ui.columnaNumero("Existencias", 100, p -> String.valueOf(p.existencias)));
        productos.getColumns().add(Ui.columnaNumero("Mínimo", 80, p -> String.valueOf(p.cantidadMinima)));
        productos.getColumns().add(VistaProductos.columnaEstado());
        VBox lista = Ui.tarjeta(seccion("1. Seleccione el producto"), buscar, productos);
        HBox.setHgrow(lista, Priority.ALWAYS);

        ToggleGroup tipo = new ToggleGroup();
        RadioButton entrada = new RadioButton("Entrada (mercancía recibida)");
        RadioButton salida = new RadioButton("Salida (daño, pérdida, uso interno)");
        entrada.setToggleGroup(tipo);
        salida.setToggleGroup(tipo);
        entrada.setSelected(true);
        Label seleccionado = new Label("Ningún producto seleccionado");
        seleccionado.setWrapText(true);
        seleccionado.setStyle("-fx-font-weight: bold; -fx-text-fill: #141414;");
        productos.getSelectionModel().selectedItemProperty().addListener((o, a, p) ->
                seleccionado.setText(p == null ? "Ningún producto seleccionado" : p.nombre + "  ·  Existencias: " + p.existencias));
        TextField cantidad = new TextField();
        cantidad.setPromptText("Unidades");
        ComboBox<String> motivo = new ComboBox<>();
        motivo.setEditable(true);
        motivo.setMaxWidth(Double.MAX_VALUE);
        entrada.selectedProperty().addListener((o, a, esEntrada) -> {
            motivo.getItems().setAll(esEntrada
                    ? java.util.List.of("Compra a proveedor", "Reposición de mercancía", "Devolución de cliente", "Ajuste por conteo físico")
                    : java.util.List.of("Producto dañado", "Pérdida", "Uso interno", "Devolución a proveedor", "Ajuste por conteo físico"));
            motivo.setValue("");
        });
        motivo.getItems().setAll("Compra a proveedor", "Reposición de mercancía", "Devolución de cliente", "Ajuste por conteo físico");

        Button registrar = Ui.primario("Registrar movimiento");
        registrar.setMaxWidth(Double.MAX_VALUE);
        registrar.setOnAction(e -> {
            Producto p = productos.getSelectionModel().getSelectedItem();
            final Movimiento[] m = new Movimiento[1];
            boolean ok = Ui.ejecutar("Registrar movimiento", () -> {
                if (p == null) throw new ErrorNegocio("Seleccione el producto en la lista.");
                int c = Formato.leerEntero(cantidad.getText(), "Cantidad");
                String mot = motivo.getEditor().getText();
                if (entrada.isSelected()) m[0] = ctx.inventario.registrarEntrada(p.codigo, c, mot, ctx.sesion().usuario);
                else m[0] = ctx.inventario.registrarSalida(p.codigo, c, mot, ctx.sesion().usuario);
            });
            if (ok) {
                Ui.info("Movimiento registrado", m[0].tipo + " de " + m[0].cantidad + " unidad(es) de «" + m[0].nombreProducto
                        + "».\nExistencias actualizadas: " + m[0].existenciasResultantes + ".");
                cantidad.clear();
                motivo.setValue("");
                String codigo = p.codigo;
                cargar();
                productos.getItems().stream().filter(x -> x.codigo.equals(codigo)).findFirst()
                        .ifPresent(x -> productos.getSelectionModel().select(x));
            }
        });
        VBox form = Ui.tarjeta(seccion("2. Registre el movimiento"), seleccionado, Ui.etiqueta("Tipo de movimiento"),
                entrada, salida, Ui.campo("Cantidad", cantidad), Ui.campo("Origen o motivo", motivo), registrar,
                nota("Las ventas se registran en el módulo Ventas; aquí solo se registran las entradas de mercancía y las salidas por otras causas."));
        form.setPrefWidth(380);
        form.setMinWidth(340);

        HBox cuerpo = new HBox(16, lista, form);
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        VBox v = new VBox(16, Ui.encabezado("Entradas y salidas de inventario",
                "Registre la mercancía recibida y las salidas por daño, pérdida o uso interno. Las existencias se actualizan automáticamente."), cuerpo);
        v.setPadding(new Insets(26));
        cargar();
        return v;
    }

    private Label seccion(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("seccion");
        return l;
    }

    private Label nota(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("nota");
        l.setWrapText(true);
        return l;
    }

    private void cargar() {
        Ui.ejecutar("Inventario", () -> productos.getItems().setAll(ctx.productos.buscar(buscar.getText(), false)));
    }
}
