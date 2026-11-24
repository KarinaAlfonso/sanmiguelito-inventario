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
    /** Ventana principal; los mensajes y formularios se muestran encima de ella (también en pantalla completa). */
    public static javafx.stage.Stage ventana;
    /** Se consulta antes de salir del sistema (por ejemplo, si hay una venta sin confirmar). */
    public static java.util.function.BooleanSupplier puedeSalir = () -> true;

    /** Cierra el sistema después de confirmar. */
    public static void salir() {
        if (!puedeSalir.getAsBoolean()) return;
        if (ventana != null) ventana.close();
        javafx.application.Platform.exit();
    }

    /** Minimiza la ventana a la barra de tareas. */
    public static void minimizar() {
        if (ventana != null) ventana.setIconified(true);
    }

    /** Muestra el diálogo encima de la ventana principal. */
    public static void propietario(javafx.scene.control.Dialog<?> d) {
        if (ventana != null && ventana.isShowing() && d.getOwner() == null) d.initOwner(ventana);
    }

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
        propietario(a);
        return a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private static void alerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert a = new Alert(tipo, mensaje, ButtonType.OK);
        a.setTitle(titulo);
        a.setHeaderText(titulo);
        estilo(a);
        propietario(a);
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

    /**
     * Lista de requisitos de la contraseña que se marcan en verde a medida que se cumplen,
     * para que el usuario sepa exactamente qué le falta antes de guardar.
     */
    public static VBox requisitosContrasena(javafx.scene.control.PasswordField nueva, javafx.scene.control.PasswordField repetir) {
        return requisitosContrasena(nueva, repetir, true);
    }

    public static VBox requisitosContrasena(javafx.scene.control.PasswordField nueva, javafx.scene.control.PasswordField repetir,
                                            boolean distintaDeLaActual) {
        Label titulo = new Label("La contraseña debe cumplir:");
        titulo.setStyle("-fx-font-weight: bold; -fx-text-fill: #4A3020;");
        Label largo = new Label(), letra = new Label(), numero = new Label(), espacios = new Label(), iguales = new Label();
        Runnable revisar = () -> {
            String n = nueva.getText(), r = repetir.getText();
            marcar(largo, n.length() >= 8 && n.length() <= 64, "Entre 8 y 64 caracteres (lleva " + n.length() + ")");
            marcar(letra, n.matches(".*[A-Za-z].*"), "Al menos una letra (a-z, A-Z)");
            marcar(numero, n.matches(".*\\d.*"), "Al menos un número (0-9)");
            marcar(espacios, !n.isEmpty() && n.equals(n.trim()), "Sin espacios al inicio ni al final");
            marcar(iguales, !n.isEmpty() && n.equals(r), "Las dos contraseñas escritas son iguales");
        };
        nueva.textProperty().addListener((o, a, b) -> revisar.run());
        repetir.textProperty().addListener((o, a, b) -> revisar.run());
        revisar.run();
        Label nota = new Label((distintaDeLaActual ? "Además, debe ser diferente de la contraseña actual. " : "") + "Las mayúsculas y minúsculas cuentan: "
                + "«Tienda2026» y «tienda2026» son contraseñas distintas. Ejemplo válido: Miguelito2026");
        nota.getStyleClass().add("nota");
        nota.setWrapText(true);
        nota.setMaxWidth(400);
        VBox v = new VBox(4, titulo, largo, letra, numero, espacios, iguales, nota);
        v.setStyle("-fx-background-color: #FFF8EE; -fx-border-color: #E8D8C8; -fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 10 12 10 12;");
        return v;
    }

    private static void marcar(Label l, boolean ok, String texto) {
        l.setText((ok ? "✔  " : "○  ") + texto);
        l.setStyle(ok ? "-fx-text-fill: #388E3C; -fx-font-weight: bold;" : "-fx-text-fill: #6B5444;");
    }

    // ---------------------------------------------------------------- restricciones de entrada (RNF-009)

    /** El campo solo acepta dígitos (sin signos, letras, decimales ni espacios) y como máximo {@code max} cifras. */
    public static javafx.scene.control.TextField soloDigitos(javafx.scene.control.TextField campo, int max) {
        campo.setTextFormatter(new javafx.scene.control.TextFormatter<String>(c ->
                c.getControlNewText().matches("\\d{0," + max + "}") ? c : null));
        return campo;
    }

    /** Campo de precio: acepta dígitos y puntos de miles (12.500) hasta {@code max} cifras. */
    public static javafx.scene.control.TextField pesos(javafx.scene.control.TextField campo, int max) {
        campo.setTextFormatter(new javafx.scene.control.TextFormatter<String>(c -> {
            String t = c.getControlNewText();
            return t.matches("[\\d.]*") && t.replace(".", "").length() <= max ? c : null;
        }));
        return campo;
    }

    /** Limita la cantidad de caracteres que se pueden escribir en un campo de texto. */
    public static <C extends javafx.scene.control.TextInputControl> C limitar(C campo, int max) {
        campo.setTextFormatter(new javafx.scene.control.TextFormatter<String>(c ->
                c.getControlNewText().length() <= max ? c : null));
        return campo;
    }

    /**
     * Selector de fecha que solo se elige en el calendario (dd/MM/yyyy). Evita que se escriban
     * fechas inválidas a mano.
     */
    public static javafx.scene.control.DatePicker fecha(java.time.LocalDate valor) {
        javafx.scene.control.DatePicker d = new javafx.scene.control.DatePicker(valor);
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        d.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(java.time.LocalDate x) { return x == null ? "" : x.format(f); }
            @Override public java.time.LocalDate fromString(String s) {
                try { return s == null || s.isBlank() ? null : java.time.LocalDate.parse(s.trim(), f); }
                catch (java.time.format.DateTimeParseException e) { return d.getValue(); }
            }
        });
        d.setEditable(false);
        d.setPromptText("dd/mm/aaaa");
        d.setDayCellFactory(p -> new javafx.scene.control.DateCell() {
            @Override public void updateItem(java.time.LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(empty || item.isAfter(java.time.LocalDate.now()));   // no hay movimientos en el futuro
            }
        });
        return d;
    }

    public static <T> TableView<T> tabla(String vacio) {
        TableView<T> t = new TableView<>();
        t.setPlaceholder(new Label(vacio));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(t, Priority.ALWAYS);
        return t;
    }
}
