package com.sanmiguelito.ui;

import com.sanmiguelito.modelo.ItemVenta;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.modelo.Venta;
import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.servicio.Formato;
import com.sanmiguelito.servicio.ServicioVentas;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.time.LocalDate;

/** Registro de ventas (RF-013), validación de existencias (RF-009) y comprobante (RF-014). Proceso 4.0. */
public class VistaVentas {

    private final Contexto ctx;
    private final ServicioVentas.Carrito carrito = new ServicioVentas.Carrito();
    private final TableView<Producto> productos = Ui.tabla("No hay productos que coincidan con la búsqueda.");
    private final TableView<ItemVenta> detalle = Ui.tabla("Agregue productos a la venta.");
    private final TableView<Venta> recientes = Ui.tabla("Todavía no hay ventas hoy.");
    private final Label total = new Label(Formato.pesos(0));
    private final TextField buscar = new TextField();
    private final TextField cantidad = Ui.soloDigitos(new TextField("1"), 6);

    public VistaVentas(Contexto ctx) { this.ctx = ctx; }

    public Node vista() {
        // ---------------- catálogo
        buscar.setPromptText("Buscar por nombre, código o categoría");
        Ui.filtrar(buscar, Entrada.BUSQUEDA, 60);
        buscar.textProperty().addListener((o, a, n) -> cargarProductos());
        productos.getColumns().add(Ui.columna("Código", 80, p -> p.codigo));
        productos.getColumns().add(Ui.columna("Producto", 220, p -> p.nombre));
        productos.getColumns().add(Ui.columnaNumero("Precio", 90, p -> Formato.pesos(p.precioVenta)));
        productos.getColumns().add(Ui.columnaNumero("Disponibles", 90, p -> String.valueOf(p.existencias)));
        productos.getColumns().add(VistaProductos.columnaEstado());
        productos.setRowFactory(VistaProductos.filasConEstado());
        productos.setOnMouseClicked(e -> { if (e.getClickCount() == 2) agregar(); });
        cantidad.setPrefWidth(80);
        Button agregar = Ui.primario("Agregar a la venta");
        agregar.setOnAction(e -> agregar());
        cantidad.setOnAction(e -> agregar());
        VBox catalogo = Ui.tarjeta(seccion("1. Seleccione los productos"), buscar, productos,
                Ui.fila(Ui.campo("Cantidad", cantidad), agregar));
        HBox.setHgrow(catalogo, Priority.ALWAYS);

        // ---------------- venta en curso
        detalle.getColumns().add(Ui.columna("Producto", 200, i -> i.nombreProducto));
        detalle.getColumns().add(Ui.columnaNumero("Cant.", 60, i -> String.valueOf(i.cantidad)));
        detalle.getColumns().add(Ui.columnaNumero("Precio", 90, i -> Formato.pesos(i.precioUnitario)));
        detalle.getColumns().add(Ui.columnaNumero("Subtotal", 100, i -> Formato.pesos(i.subtotal())));
        Button quitar = Ui.secundario("Quitar producto");
        quitar.setOnAction(e -> {
            ItemVenta i = detalle.getSelectionModel().getSelectedItem();
            if (i == null) { Ui.aviso("Quitar producto", "Seleccione el producto que desea quitar de la venta."); return; }
            carrito.quitar(i.codigoProducto);
            refrescarDetalle();
        });
        Button cancelar = Ui.secundario("Cancelar venta");
        cancelar.setOnAction(e -> {
            if (!carrito.vacio() && Ui.confirmar("Cancelar venta", "¿Desea descartar los productos de esta venta?")) {
                carrito.limpiar();
                refrescarDetalle();
            }
        });
        Button confirmar = Ui.exito("Confirmar venta");
        confirmar.setPrefHeight(46);
        confirmar.setMaxWidth(Double.MAX_VALUE);
        confirmar.setOnAction(e -> confirmar());
        total.getStyleClass().add("total-venta");
        Label lt = Ui.etiqueta("Total a pagar");
        VBox venta = Ui.tarjeta(seccion("2. Venta en curso"), detalle, Ui.fila(quitar, cancelar),
                new VBox(2, lt, total), confirmar);
        venta.setPrefWidth(470);
        venta.setMinWidth(430);

        // ---------------- ventas de hoy
        recientes.getColumns().add(Ui.columna("Número", 90, v -> v.numero));
        recientes.getColumns().add(Ui.columna("Hora", 90, v -> Formato.fechaHora(v.fecha).substring(11)));
        recientes.getColumns().add(Ui.columna("Vendedor", 180, v -> v.nombreVendedor));
        recientes.getColumns().add(Ui.columnaNumero("Unidades", 80, v -> String.valueOf(v.unidades())));
        recientes.getColumns().add(Ui.columnaNumero("Total", 110, v -> Formato.pesos(v.total())));
        recientes.setPrefHeight(190);
        Button verComprobante = Ui.secundario("Ver comprobante");
        verComprobante.setOnAction(e -> {
            Venta v = recientes.getSelectionModel().getSelectedItem();
            if (v == null) { Ui.aviso("Comprobante", "Seleccione una venta de la lista."); return; }
            Ui.ejecutar("Comprobante", () -> Ui.abrir(ctx.reportes.comprobante(v)));
        });

        HBox arriba = new HBox(16, catalogo, venta);
        VBox.setVgrow(arriba, Priority.ALWAYS);
        VBox v = new VBox(16, Ui.encabezado("Ventas", "Registre los productos que lleva el cliente. El sistema valida las existencias y genera el comprobante."),
                arriba, Ui.tarjeta(seccion("Ventas de hoy"), recientes, Ui.fila(verComprobante)));
        v.setPadding(new Insets(26));
        cargarProductos();
        refrescarDetalle();
        cargarRecientes();
        return v;
    }

    private Label seccion(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("seccion");
        return l;
    }

    private void cargarProductos() {
        Ui.ejecutar("Productos", () -> productos.getItems().setAll(ctx.productosVendibles(buscar.getText())));
    }

    private void cargarRecientes() {
        Ui.ejecutar("Ventas", () -> recientes.getItems().setAll(ctx.ventas.listar(LocalDate.now(), LocalDate.now())));
    }

    private void refrescarDetalle() {
        detalle.getItems().setAll(carrito.items());
        detalle.refresh();
        total.setText(Formato.pesos(carrito.total()));
    }

    /** Agrega un producto a la venta en curso (también usado para la documentación). */
    public void agregarProducto(Producto p, int c) {
        carrito.agregar(p, c);
        refrescarDetalle();
    }

    /** true si hay productos en la venta en curso (se usa para advertir antes de cerrar sesión). */
    public boolean hayVentaEnCurso() { return !carrito.vacio(); }

    /** Actualiza la lista de productos, la venta en curso y las ventas del día al volver al módulo. */
    public void refrescar() {
        cargarProductos();
        refrescarDetalle();
        cargarRecientes();
    }

    private void agregar() {
        Producto seleccionado = productos.getSelectionModel().getSelectedItem();
        Ui.ejecutar("Agregar producto", () -> {
            if (seleccionado == null) throw new com.sanmiguelito.servicio.ErrorNegocio("Seleccione un producto de la lista.");
            int c = Formato.leerEntero(cantidad.getText(), "Cantidad");
            // Se consultan las existencias actuales, no las que se cargaron al abrir la pantalla.
            Producto p = ctx.productos.porCodigo(seleccionado.codigo)
                    .orElseThrow(() -> new com.sanmiguelito.servicio.ErrorNegocio("El producto ya no existe. Actualice la lista."));
            carrito.agregar(p, c);
            cantidad.setText("1");
            refrescarDetalle();
        });
    }

    private void confirmar() {
        if (carrito.vacio()) { Ui.aviso("Confirmar venta", "Agregue al menos un producto a la venta."); return; }
        if (!Ui.confirmar("Confirmar venta", "Total a pagar: " + Formato.pesos(carrito.total()) + "\n¿Desea confirmar la venta?")) return;
        final Venta[] hecha = new Venta[1];
        boolean ok = Ui.ejecutar("Confirmar venta", () -> hecha[0] = ctx.ventas.confirmar(carrito, ctx.sesion()));
        if (!ok) { cargarProductos(); refrescarDetalle(); return; }
        refrescarDetalle();
        cargarProductos();
        cargarRecientes();
        Venta v = hecha[0];
        Path pdf = null;
        try { pdf = ctx.reportes.comprobante(v); } catch (RuntimeException ex) { Ui.aviso("Comprobante", ex.getMessage()); }
        if (pdf != null && Ui.confirmar("Venta " + v.numero + " registrada",
                "La venta se registró correctamente por " + Formato.pesos(v.total()) + " y el inventario se actualizó.\n\n"
                        + "¿Desea abrir el comprobante para imprimirlo o guardarlo?")) {
            Ui.abrir(pdf);
        }
    }
}
