package com.sanmiguelito.ui;

import com.sanmiguelito.modelo.Movimiento;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.servicio.ErrorNegocio;
import com.sanmiguelito.servicio.Formato;
import com.sanmiguelito.servicio.ServicioInventario;
import javafx.application.Platform;
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

import java.util.List;

/**
 * Registro de entradas (RF-007) y salidas (RF-008) con validación de existencias (RF-009). Proceso 5.0.
 * Si la mercancía que llega es un producto nuevo, el administrador lo registra desde aquí mismo
 * y a continuación registra la entrada.
 */
public class VistaInventario {

    private static final List<String> MOTIVOS_ENTRADA = List.of("Compra a proveedor", "Reposición de mercancía",
            "Devolución de cliente", "Ajuste por conteo físico");
    private static final List<String> MOTIVOS_SALIDA = List.of("Producto dañado", "Pérdida", "Uso interno",
            "Devolución a proveedor", "Ajuste por conteo físico");

    private final Contexto ctx;
    private final TableView<Producto> productos = Ui.tabla("No hay productos que coincidan con la búsqueda.");
    private final TextField buscar = Ui.limitar(new TextField(), 60);
    private final Label vacio = new Label();
    private final RadioButton entrada = new RadioButton("Entrada (mercancía recibida)");
    private final RadioButton salida = new RadioButton("Salida (daño, pérdida, uso interno)");
    private final TextField cantidad = Ui.soloDigitos(new TextField(), 6);
    private final ComboBox<String> motivo = new ComboBox<>();

    public VistaInventario(Contexto ctx) { this.ctx = ctx; }

    public Node vista() {
        buscar.setPromptText("Buscar por nombre, código o categoría");
        buscar.textProperty().addListener((o, a, n) -> cargar());
        HBox.setHgrow(buscar, Priority.ALWAYS);
        productos.getColumns().add(Ui.columna("Código", 90, p -> p.codigo));
        productos.getColumns().add(Ui.columna("Producto", 260, p -> p.nombre));
        productos.getColumns().add(Ui.columna("Categoría", 140, p -> p.categoria));
        productos.getColumns().add(Ui.columnaNumero("Existencias", 100, p -> String.valueOf(p.existencias)));
        productos.getColumns().add(Ui.columnaNumero("Mínimo", 80, p -> String.valueOf(p.cantidadMinima)));
        productos.getColumns().add(VistaProductos.columnaEstado());
        vacio.setWrapText(true);
        vacio.setMaxWidth(420);
        vacio.setStyle("-fx-text-alignment: center;");
        productos.setPlaceholder(vacio);

        Node filaBusqueda;
        if (ctx.esAdministrador()) {
            Button nuevo = Ui.secundario("+ Producto nuevo");
            nuevo.setOnAction(e -> productoNuevo());
            filaBusqueda = Ui.fila(buscar, nuevo);
        } else {
            filaBusqueda = Ui.fila(buscar);
        }
        VBox lista = Ui.tarjeta(seccion("1. Seleccione el producto"), filaBusqueda, productos,
                nota(ctx.esAdministrador()
                        ? "¿Llegó un producto que no está en la lista? Pulse «+ Producto nuevo», regístrelo y luego registre la entrada."
                        : "Si llegó un producto que no está en la lista, solicite al administrador que lo registre."));
        HBox.setHgrow(lista, Priority.ALWAYS);

        ToggleGroup tipo = new ToggleGroup();
        entrada.setToggleGroup(tipo);
        salida.setToggleGroup(tipo);
        entrada.setSelected(true);
        Label seleccionado = new Label("Ningún producto seleccionado");
        seleccionado.setWrapText(true);
        seleccionado.setStyle("-fx-font-weight: bold; -fx-text-fill: #141414;");
        productos.getSelectionModel().selectedItemProperty().addListener((o, a, p) ->
                seleccionado.setText(p == null ? "Ningún producto seleccionado"
                        : p.nombre + " (" + p.codigo + ")\nExistencias actuales: " + p.existencias));
        cantidad.setPromptText("Unidades");
        motivo.setEditable(true);
        motivo.setMaxWidth(Double.MAX_VALUE);
        motivo.setPromptText("Seleccione o escriba");
        Ui.limitar(motivo.getEditor(), ServicioInventario.LONGITUD_MOTIVO);
        motivo.getItems().setAll(MOTIVOS_ENTRADA);
        entrada.selectedProperty().addListener((o, a, esEntrada) -> {
            motivo.getItems().setAll(esEntrada ? MOTIVOS_ENTRADA : MOTIVOS_SALIDA);
            motivo.getEditor().clear();
            motivo.setValue(null);
        });

        Button registrar = Ui.primario("Registrar movimiento");
        registrar.setMaxWidth(Double.MAX_VALUE);
        registrar.setOnAction(e -> registrar());
        cantidad.setOnAction(e -> registrar());
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

    private void registrar() {
        Producto p = productos.getSelectionModel().getSelectedItem();
        boolean esEntrada = entrada.isSelected();
        final int[] c = new int[1];
        final String[] mot = new String[1];
        boolean valido = Ui.ejecutar("Registrar movimiento", () -> {
            if (p == null) throw new ErrorNegocio("Seleccione el producto en la lista de la izquierda.");
            c[0] = Formato.leerEntero(cantidad.getText(), "Cantidad");
            if (c[0] <= 0) throw new ErrorNegocio("La cantidad debe ser mayor que cero.");
            mot[0] = motivo.getEditor().getText();
            if (mot[0] == null || mot[0].isBlank())
                throw new ErrorNegocio(esEntrada ? "Indique el origen de la entrada (por ejemplo, «Compra a proveedor»)."
                        : "Indique el motivo de la salida (por ejemplo, «Producto dañado»).");
            if (!esEntrada && c[0] > p.existencias)
                throw new ErrorNegocio(p.existencias == 0 ? "«" + p.nombre + "» está agotado: no se puede registrar la salida."
                        : "No hay unidades suficientes de «" + p.nombre + "». Existencias actuales: " + p.existencias + ".");
        });
        if (!valido) return;
        int resultado = esEntrada ? p.existencias + c[0] : p.existencias - c[0];
        if (!Ui.confirmar("Confirmar " + (esEntrada ? "entrada" : "salida"),
                (esEntrada ? "Entrada" : "Salida") + " de " + c[0] + " unidad(es) de «" + p.nombre + "»\n"
                        + (esEntrada ? "Origen: " : "Motivo: ") + mot[0].trim() + "\n\n"
                        + "Existencias: " + p.existencias + " → " + resultado + "\n\n¿Desea registrar el movimiento?")) return;
        final Movimiento[] m = new Movimiento[1];
        boolean ok = Ui.ejecutar("Registrar movimiento", () -> m[0] = esEntrada
                ? ctx.inventario.registrarEntrada(p.codigo, c[0], mot[0], ctx.sesion().usuario)
                : ctx.inventario.registrarSalida(p.codigo, c[0], mot[0], ctx.sesion().usuario));
        String codigo = p.codigo;
        cargar();
        seleccionar(codigo);
        if (ok) {
            Ui.info("Movimiento registrado", (esEntrada ? "Entrada" : "Salida") + " de " + m[0].cantidad + " unidad(es) de «"
                    + m[0].nombreProducto + "».\nExistencias actualizadas: " + m[0].existenciasResultantes + ".");
            cantidad.clear();
            motivo.getEditor().clear();
            motivo.setValue(null);
        }
    }

    /** Registra un producto nuevo y lo deja seleccionado para registrar la entrada de inmediato. */
    private void productoNuevo() {
        FormularioProducto.mostrar(ctx, null, buscar.getText(), false).ifPresent(p -> {
            buscar.clear();
            cargar();
            seleccionar(p.codigo);
            entrada.setSelected(true);
            motivo.setValue(null);
            motivo.getEditor().setText("Compra a proveedor");
            cantidad.clear();
            Platform.runLater(cantidad::requestFocus);
            Ui.info("Registre la entrada", "Ahora escriba la cantidad recibida de «" + p.nombre + "» y pulse «Registrar movimiento».");
        });
    }

    private void seleccionar(String codigo) {
        productos.getItems().stream().filter(x -> x.codigo.equals(codigo)).findFirst()
                .ifPresent(x -> { productos.getSelectionModel().select(x); productos.scrollTo(x); });
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
        String t = buscar.getText() == null ? "" : buscar.getText().trim();
        vacio.setText(t.isEmpty() ? "No hay productos activos registrados."
                : "No se encontró «" + t + "»." + (ctx.esAdministrador()
                        ? "\nSi es un producto nuevo, pulse «+ Producto nuevo» para registrarlo." : ""));
        Ui.ejecutar("Inventario", () -> productos.getItems().setAll(ctx.productos.buscar(t, false)));
    }
}
