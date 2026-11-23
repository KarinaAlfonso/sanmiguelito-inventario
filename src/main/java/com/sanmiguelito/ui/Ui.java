package com.sanmiguelito.ui;

import com.sanmiguelito.datos.ErrorDatos;
import com.sanmiguelito.servicio.ErrorNegocio;
import javafx.application.HostServices;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.function.Function;

/** Utilidades de interfaz con el estilo del manual corporativo (rojo #D21E1E, crema #FAF5EE). */
public final class Ui {

    public static HostServices hostServices;

    private Ui() { }

    public static void aplicarEstilos(Scene s) {
        s.getStylesheets().add(Ui.class.getResource("/estilos.css").toExternalForm());
    }

    // ---------------------------------------------------------------- mensajes

    public static void error(String titulo, String mensaje) { alerta(Alert.AlertType.ERROR, titulo, mensaje); }

    public static void info(String titulo, String mensaje) { alerta(Alert.AlertType.INFORMATION, titulo, mensaje); }

    public static void aviso(String titulo, String mensaje) { alerta(Alert.AlertType.WARNING, titulo, mensaje); }

    public static boolean confirmar(String titulo, String mensaje) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, mensaje, ButtonType.YES, ButtonType.NO);
        a.setTitle(titulo);
        a.setHeaderText(titulo);
        estilo(a);
        return a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private static void alerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert a = new Alert(tipo, mensaje, ButtonType.OK);
        a.setTitle(titulo);
        a.setHeaderText(titulo);
        estilo(a);
        a.showAndWait();
    }

    private static void estilo(Alert a) {
        a.getDialogPane().getStylesheets().add(Ui.class.getResource("/estilos.css").toExternalForm());
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
    }

    /** Ejecuta una acción mostrando los errores de negocio y de datos como mensajes claros (RNF-009). */
    public static boolean ejecutar(String titulo, Runnable accion) {
        try {
            accion.run();
            return true;
        } catch (ErrorNegocio e) {
            aviso(titulo, e.getMessage());
        } catch (ErrorDatos e) {
            error(titulo, e.getMessage());
        } catch (RuntimeException e) {
            error(titulo, "Ocurrió un error inesperado: " + e.getMessage());
        }
        return false;
    }

    public static void abrir(Path archivo) {
        if (hostServices != null) hostServices.showDocument(archivo.toUri().toString());
    }

    // ---------------------------------------------------------------- componentes

    public static Button primario(String texto) { return boton(texto, "btn-primario"); }

    public static Button secundario(String texto) { return boton(texto, "btn-secundario"); }

    public static Button peligro(String texto) { return boton(texto, "btn-peligro"); }

    public static Button exito(String texto) { return boton(texto, "btn-exito"); }

    private static Button boton(String texto, String clase) {
        Button b = new Button(texto);
        b.getStyleClass().add(clase);
        b.setMinWidth(Region.USE_PREF_SIZE);   // evita que el texto del botón se corte
        return b;
    }

    /** Logo de la empresa. Según el manual de estilos, siempre sobre fondo blanco o crema. */
    public static javafx.scene.image.ImageView logo(double alto) {
        var in = Ui.class.getResourceAsStream("/logo.png");
        javafx.scene.image.ImageView v = new javafx.scene.image.ImageView(in == null ? null : new javafx.scene.image.Image(in));
        v.setFitHeight(alto);
        v.setPreserveRatio(true);
        v.setSmooth(true);
        return v;
    }

    public static Label titulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("titulo-modulo");
        return l;
    }

    public static Label subtitulo(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("subtitulo");
        l.setWrapText(true);
        return l;
    }

    public static Label etiqueta(String texto) {
        Label l = new Label(texto.toUpperCase());
        l.getStyleClass().add("etiqueta");
        return l;
    }

    public static VBox campo(String etiqueta, Node control) {
        VBox v = new VBox(4, etiqueta(etiqueta), control);
        return v;
    }

    public static Region espacio() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static VBox tarjeta(Node... hijos) {
        VBox v = new VBox(12, hijos);
        v.getStyleClass().add("tarjeta");
        v.setPadding(new Insets(18));
        return v;
    }

    public static VBox encabezado(String titulo, String descripcion) {
        VBox v = new VBox(4, titulo(titulo), subtitulo(descripcion));
        v.setPadding(new Insets(0, 0, 6, 0));
        return v;
    }

    public static HBox fila(Node... hijos) {
        HBox h = new HBox(10, hijos);
        h.setAlignment(Pos.BOTTOM_LEFT);
        return h;
    }

    public static <T> TableColumn<T, String> columna(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new SimpleStringProperty(valor.apply(d.getValue())));
        c.setPrefWidth(ancho);
        return c;
    }

    public static <T> TableColumn<T, String> columnaNumero(String titulo, double ancho, Function<T, String> valor) {
        TableColumn<T, String> c = columna(titulo, ancho, valor);
        c.setStyle("-fx-alignment: CENTER-RIGHT;");
        return c;
    }

    public static <T> TableView<T> tabla(String vacio) {
        TableView<T> t = new TableView<>();
        t.setPlaceholder(new Label(vacio));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(t, Priority.ALWAYS);
        return t;
    }
}
