package com.sanmiguelito.ui;

import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.servicio.ErrorNegocio;
import com.sanmiguelito.servicio.Formato;
import com.sanmiguelito.servicio.ServicioProductos;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.Optional;

/**
 * Formulario de registro y modificación de productos (RF-002, RF-003), usado desde el módulo
 * Productos y desde Entradas y salidas.
 *
 * El código se asigna automáticamente según la categoría y el sistema avisa mientras se escribe
 * si el nombre ya existe (sin importar mayúsculas, minúsculas o tildes).
 */
public final class FormularioProducto {

    private FormularioProducto() { }

    /**
     * Muestra el formulario.
     *
     * @param existente      producto a modificar, o null para registrar uno nuevo
     * @param nombreSugerido texto con el que se prellena el nombre de un producto nuevo
     * @param cantidadInicial si se pide la cantidad inicial (en Entradas y salidas se registra después)
     * @return el producto guardado, o vacío si se canceló
     */
    public static Optional<Producto> mostrar(Contexto ctx, Producto existente, String nombreSugerido, boolean cantidadInicial) {
        boolean nuevo = existente == null;
        Dialog<ButtonType> d = new Dialog<>();
        Ui.propietario(d);
        d.setTitle(nuevo ? "Nuevo producto" : "Modificar producto");
        d.setHeaderText(nuevo ? "Registrar un producto nuevo" : "Modificar «" + existente.nombre + "»");
        d.getDialogPane().getStylesheets().add(FormularioProducto.class.getResource("/estilos.css").toExternalForm());
        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);

        ComboBox<String> categoria = new ComboBox<>();
        categoria.setEditable(true);
        categoria.getItems().setAll(ctx.productos.categorias());
        categoria.setPromptText("Seleccione o escriba una nueva");
        categoria.setValue(nuevo ? null : existente.categoria);
        categoria.setMaxWidth(Double.MAX_VALUE);
        categoria.setVisibleRowCount(12);
        Ui.limitar(categoria.getEditor(), 30);

        TextField codigo = new TextField(nuevo ? "" : existente.codigo);
        codigo.setEditable(false);
        codigo.setFocusTraversable(false);
        codigo.setPromptText("Elija la categoría");
        codigo.setStyle("-fx-background-color: #F3ECE3; -fx-font-weight: bold;");

        TextField nombre = Ui.limitar(new TextField(nuevo ? (nombreSugerido == null ? "" : nombreSugerido.trim()) : existente.nombre),
                ServicioProductos.LONGITUD_NOMBRE);
        nombre.setPromptText("Ej.: Escoba de cerdas suaves");
        nombre.setPrefWidth(440);
        Label aviso = new Label();
        aviso.getStyleClass().add("mensaje-error");
        aviso.setWrapText(true);
        aviso.setMaxWidth(440);
        aviso.managedProperty().bind(aviso.visibleProperty());
        aviso.setVisible(false);

        TextField precio = Ui.pesos(new TextField(nuevo ? "" : String.valueOf(existente.precioVenta)), 8);
        precio.setPromptText("Mínimo 50, en múltiplos de 50");
        TextField minimo = Ui.soloDigitos(new TextField(nuevo ? "5" : String.valueOf(existente.cantidadMinima)), 6);
        TextField existencias = Ui.soloDigitos(new TextField(nuevo ? "0" : String.valueOf(existente.existencias)), 6);
        existencias.setDisable(!nuevo);

        Label notaCodigo = new Label();
        notaCodigo.getStyleClass().add("nota");
        notaCodigo.setWrapText(true);
        notaCodigo.setMaxWidth(440);

        // Código automático según la categoría.
        Runnable actualizarCodigo = () -> {
            String cat = textoCategoria(categoria);
            if (nuevo) {
                String c = ctx.productos.siguienteCodigo(cat);
                codigo.setText(c);
                notaCodigo.setText(c.isEmpty()
                        ? "Elija una categoría de la lista o escriba una nueva para asignar el código automáticamente."
                        : "El código " + c + " se asigna automáticamente según la categoría.");
            } else {
                boolean otra = !cat.isBlank() && !mismaCategoria(cat, existente.categoria);
                String c = otra ? ctx.productos.siguienteCodigo(cat) : existente.codigo;
                codigo.setText(c.isEmpty() ? existente.codigo : c);
                notaCodigo.setText(otra && !c.isEmpty()
                        ? "Al cambiar la categoría, el código cambiará de " + existente.codigo + " a " + c
                          + ". El historial de movimientos y ventas se conserva con el nuevo código."
                        : "Las existencias solo cambian con entradas, salidas y ventas.");
            }
        };
        categoria.getEditor().textProperty().addListener((o, a, n) -> actualizarCodigo.run());
        categoria.valueProperty().addListener((o, a, n) -> actualizarCodigo.run());

        // Aviso inmediato de producto repetido.
        Runnable revisarNombre = () -> {
            Optional<Producto> igual = ctx.productos.mismoNombre(nombre.getText(), nuevo ? null : existente.codigo);
            aviso.setVisible(igual.isPresent());
            igual.ifPresent(x -> aviso.setText("Ya existe «" + x.nombre + "» (" + x.codigo + ", " + x.categoria + ")"
                    + (x.activo ? "." : ", desactivado.") + " No se puede registrar dos veces."));
        };
        nombre.textProperty().addListener((o, a, n) -> revisarNombre.run());

        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(10);
        g.setPadding(new Insets(10));
        g.add(Ui.campo("Categoría", categoria), 0, 0);
        g.add(Ui.campo("Código (automático)", codigo), 1, 0);
        g.add(notaCodigo, 0, 1, 2, 1);
        g.add(Ui.campo("Nombre del producto", nombre), 0, 2, 2, 1);
        g.add(aviso, 0, 3, 2, 1);
        g.add(Ui.campo("Precio de venta ($)", precio), 0, 4);
        g.add(Ui.campo("Cantidad mínima", minimo), 1, 4);
        if (nuevo && cantidadInicial) g.add(Ui.campo("Cantidad inicial", existencias), 0, 5, 2, 1);
        if (!nuevo) g.add(Ui.campo("Existencias (se modifican con entradas, salidas y ventas)", existencias), 0, 5, 2, 1);
        Label nota = new Label("La cantidad mínima es el límite para la alerta de pocas existencias.");
        nota.getStyleClass().add("nota");
        g.add(nota, 0, 6, 2, 1);
        d.getDialogPane().setContent(g);
        actualizarCodigo.run();
        revisarNombre.run();

        final Producto[] guardado = new Producto[1];
        Button btn = (Button) d.getDialogPane().lookupButton(guardar);
        btn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            boolean ok = Ui.ejecutar(d.getTitle(), () -> {
                ctx.exigirAdministrador();
                Producto datos = new Producto();
                datos.codigo = nuevo ? null : existente.codigo;
                datos.categoria = textoCategoria(categoria);
                datos.nombre = nombre.getText();
                if (datos.categoria.isBlank()) throw new ErrorNegocio("Seleccione o escriba la categoría del producto.");
                if (datos.nombre == null || datos.nombre.isBlank()) throw new ErrorNegocio("Escriba el nombre del producto.");
                datos.precioVenta = Formato.leerPesos(precio.getText(), "Precio de venta");
                datos.cantidadMinima = Formato.leerEntero(minimo.getText(), "Cantidad mínima");
                if (nuevo) {
                    datos.existencias = cantidadInicial ? Formato.leerEntero(existencias.getText(), "Cantidad inicial") : 0;
                    guardado[0] = ctx.productos.crear(datos, ctx.sesion().usuario);
                } else {
                    String codigoFinal = ctx.productos.modificar(datos);
                    guardado[0] = ctx.productos.porCodigo(codigoFinal).orElse(null);
                }
            });
            if (!ok) ev.consume();
        });
        Platform.runLater(() -> (nuevo && categoria.getValue() == null ? categoria : nombre).requestFocus());
        if (d.showAndWait().orElse(ButtonType.CANCEL) == guardar && guardado[0] != null) {
            Producto p = guardado[0];
            Ui.info(d.getTitle(), nuevo
                    ? "El producto «" + p.nombre + "» se registró con el código " + p.codigo + "."
                    : "Los cambios de «" + p.nombre + "» (" + p.codigo + ") se guardaron correctamente.");
            return Optional.of(p);
        }
        return Optional.empty();
    }

    private static String textoCategoria(ComboBox<String> categoria) {
        String t = categoria.getEditor().getText();
        if (t != null && !t.isBlank()) return t;
        return categoria.getValue() == null ? "" : categoria.getValue();
    }

    private static boolean mismaCategoria(String a, String b) {
        return com.sanmiguelito.servicio.Categorias.iguales(a, b);
    }
}
