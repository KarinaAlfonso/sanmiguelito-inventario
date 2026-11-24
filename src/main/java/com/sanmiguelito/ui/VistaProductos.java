package com.sanmiguelito.ui;

import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.servicio.ErrorNegocio;
import com.sanmiguelito.servicio.Formato;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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
        Ui.limitar(buscar, 60);
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
        FormularioProducto.mostrar(ctx, p, p == null ? buscar.getText() : null, true).ifPresent(x -> {
            cargar();
            tabla.getItems().stream().filter(y -> y.codigo.equals(x.codigo)).findFirst()
                    .ifPresent(y -> { tabla.getSelectionModel().select(y); tabla.scrollTo(y); });
        });
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
