package com.sanmiguelito.ui;

import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.servicio.ErrorNegocio;
import com.sanmiguelito.servicio.Formato;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

/** Registro, modificación, desactivación, visualización y búsqueda de productos (RF-002 a RF-006, proceso 3.0). */
public class VistaProductos {

    private final Contexto ctx;
    private final TableView<Producto> tabla = Ui.tabla("No hay productos que coincidan con la búsqueda.");
    private final TextField buscar = new TextField();
    private final CheckBox inactivos = new CheckBox("Mostrar productos desactivados");

    public VistaProductos(Contexto ctx) { this.ctx = ctx; }

    public Node vista() {
        buscar.setPromptText("Buscar por nombre, código o categoría");
        buscar.setPrefWidth(360);
        buscar.textProperty().addListener((o, a, n) -> cargar());
        inactivos.selectedProperty().addListener((o, a, n) -> cargar());

        tabla.getColumns().add(Ui.columna("Código", 90, p -> p.codigo));
        tabla.getColumns().add(Ui.columna("Producto", 260, p -> p.nombre));
        tabla.getColumns().add(Ui.columna("Categoría", 140, p -> p.categoria));
        tabla.getColumns().add(Ui.columnaNumero("Precio de venta", 120, p -> Formato.pesos(p.precioVenta)));
        tabla.getColumns().add(Ui.columnaNumero("Existencias", 100, p -> String.valueOf(p.existencias)));
        tabla.getColumns().add(Ui.columnaNumero("Mínimo", 80, p -> String.valueOf(p.cantidadMinima)));
        tabla.getColumns().add(columnaEstado());
        tabla.getColumns().add(Ui.columna("Situación", 90, p -> p.activo ? "Activo" : "Desactivado"));
        tabla.setRowFactory(filasConEstado());

        VBox contenido = new VBox(14);
        String descripcion = ctx.esAdministrador()
                ? "Registre, modifique y consulte los productos. Los productos no se eliminan: se desactivan para conservar su historial."
                : "Consulte los productos, sus precios y la cantidad disponible.";
        contenido.getChildren().add(Ui.encabezado("Productos", descripcion));
        if (ctx.esAdministrador()) {
            Button nuevo = Ui.primario("Nuevo producto");
            nuevo.setOnAction(e -> formulario(null));
            Button editar = Ui.secundario("Modificar");
            editar.setOnAction(e -> { Producto p = seleccionado(); if (p != null) formulario(p); });
            Button estado = Ui.peligro("Desactivar / activar");
            estado.setOnAction(e -> cambiarEstado());
            tabla.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && seleccionado() != null) formulario(seleccionado()); });
            contenido.getChildren().add(Ui.tarjeta(Ui.fila(buscar, inactivos, Ui.espacio(), nuevo, editar, estado), tabla));
        } else {
            contenido.getChildren().add(Ui.tarjeta(Ui.fila(buscar), tabla));
        }
        contenido.setPadding(new Insets(26));
        cargar();
        return contenido;
    }

    private void cargar() {
        Ui.ejecutar("Productos", () -> tabla.getItems().setAll(ctx.productos.buscar(buscar.getText(), inactivos.isSelected())));
    }

    private Producto seleccionado() {
        Producto p = tabla.getSelectionModel().getSelectedItem();
        if (p == null) Ui.aviso("Productos", "Seleccione un producto de la lista.");
        return p;
    }

    private void cambiarEstado() {
        Producto p = seleccionado();
        if (p == null) return;
        String accion = p.activo ? "desactivar" : "activar";
        if (!Ui.confirmar("Productos", "¿Desea " + accion + " el producto «" + p.nombre + "»?"
                + (p.activo ? "\nNo aparecerá en las ventas, pero se conservará su historial." : ""))) return;
        Ui.ejecutar("Productos", () -> {
            ctx.exigirAdministrador();
            ctx.productos.cambiarEstado(p.codigo, !p.activo);
        });
        cargar();
    }

    /** Formulario de registro (p == null) o modificación de un producto. */
    private void formulario(Producto p) {
        boolean nuevo = p == null;
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(nuevo ? "Nuevo producto" : "Modificar producto");
        d.setHeaderText(nuevo ? "Registrar un producto nuevo" : "Modificar «" + p.nombre + "»");
        d.getDialogPane().getStylesheets().add(getClass().getResource("/estilos.css").toExternalForm());
        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);

        TextField codigo = new TextField(nuevo ? "" : p.codigo);
        codigo.setDisable(!nuevo);
        codigo.setPromptText("Ej.: PAP-010");
        TextField nombre = new TextField(nuevo ? "" : p.nombre);
        ComboBox<String> categoria = new ComboBox<>();
        categoria.setEditable(true);
        categoria.getItems().setAll(ctx.productos.categorias());
        categoria.setValue(nuevo ? "" : p.categoria);
        categoria.setMaxWidth(Double.MAX_VALUE);
        TextField precio = new TextField(nuevo ? "" : String.valueOf(p.precioVenta));
        precio.setPromptText("Ej.: 4500");
        TextField existencias = new TextField(nuevo ? "0" : String.valueOf(p.existencias));
        existencias.setDisable(!nuevo);
        TextField minimo = new TextField(nuevo ? "5" : String.valueOf(p.cantidadMinima));

        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(10);
        g.setPadding(new Insets(10));
        g.add(Ui.campo("Código", codigo), 0, 0);
        g.add(Ui.campo("Categoría", categoria), 1, 0);
        g.add(Ui.campo("Nombre del producto", nombre), 0, 1, 2, 1);
        g.add(Ui.campo("Precio de venta ($)", precio), 0, 2);
        g.add(Ui.campo("Cantidad mínima", minimo), 1, 2);
        g.add(Ui.campo(nuevo ? "Cantidad inicial" : "Existencias (se modifican con entradas y salidas)", existencias), 0, 3, 2, 1);
        Label nota = new Label(nuevo ? "El código no se podrá cambiar después de guardar el producto."
                : "El código y las existencias no se modifican desde este formulario.");
        nota.getStyleClass().add("nota");
        g.add(nota, 0, 4, 2, 1);
        nombre.setPrefWidth(420);
        d.getDialogPane().setContent(g);

        Button btn = (Button) d.getDialogPane().lookupButton(guardar);
        btn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            boolean ok = Ui.ejecutar(d.getTitle(), () -> {
                ctx.exigirAdministrador();
                Producto datos = new Producto();
                datos.codigo = nuevo ? codigo.getText() : p.codigo;
                datos.nombre = nombre.getText();
                String cat = categoria.getEditor().getText();
                datos.categoria = cat == null || cat.isBlank() ? categoria.getValue() : cat;
                datos.precioVenta = Formato.leerPesos(precio.getText(), "Precio de venta");
                datos.cantidadMinima = Formato.leerEntero(minimo.getText(), "Cantidad mínima");
                if (nuevo) {
                    datos.existencias = Formato.leerEntero(existencias.getText(), "Cantidad inicial");
                    ctx.productos.crear(datos, ctx.sesion().usuario);
                } else {
                    ctx.productos.modificar(datos);
                }
            });
            if (!ok) ev.consume();
        });
        if (d.showAndWait().orElse(ButtonType.CANCEL) == guardar) {
            cargar();
            Ui.info(d.getTitle(), nuevo ? "El producto se registró correctamente." : "Los cambios se guardaron correctamente.");
        }
    }

    // ---------------------------------------------------------------- compartido con otras vistas

    static TableColumn<Producto, String> columnaEstado() {
        TableColumn<Producto, String> c = Ui.columna("Estado", 150, Producto::estadoExistencias);
        c.setMinWidth(150);
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                Label l = new Label(item);
                l.getStyleClass().add("estado");
                l.getStyleClass().add(switch (item) {
                    case "Agotado" -> "estado-agotado";
                    case "Pocas existencias" -> "estado-pocas";
                    default -> "estado-ok";
                });
                setGraphic(l);
                setText(null);
            }
        });
        return c;
    }

    static Callback<TableView<Producto>, TableRow<Producto>> filasConEstado() {
        return tv -> new TableRow<>() {
            @Override
            protected void updateItem(Producto p, boolean empty) {
                super.updateItem(p, empty);
                getStyleClass().removeAll("fila-inactivo");
                if (!empty && p != null && !p.activo) getStyleClass().add("fila-inactivo");
            }
        };
    }

    static void exigir(boolean condicion, String mensaje) {
        if (!condicion) throw new ErrorNegocio(mensaje);
    }
}
