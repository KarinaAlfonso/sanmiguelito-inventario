package com.sanmiguelito.ui;

import com.sanmiguelito.servicio.Contexto;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Ventana principal: barra lateral roja con los módulos según el rol (RF-012) y área de contenido. */
public class VistaPrincipal {

    private final Contexto ctx;
    private final Runnable alCerrarSesion;
    private final BorderPane raiz = new BorderPane();
    private final List<Button> botones = new ArrayList<>();

    public VistaPrincipal(Contexto ctx, Runnable alCerrarSesion) {
        this.ctx = ctx;
        this.alCerrarSesion = alCerrarSesion;
    }

    public Parent vista() {
        Label t1 = new Label("San Miguelito");
        t1.getStyleClass().add("marca-titulo");
        Label t2 = new Label("VENTAS E INVENTARIO");
        t2.getStyleClass().add("marca-sub");
        // El logo va sobre una placa blanca, como exige el manual de estilos.
        javafx.scene.layout.StackPane placa = new javafx.scene.layout.StackPane(Ui.logo(46));
        placa.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 8; -fx-padding: 3;");
        placa.setMaxSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox textos = new VBox(2, t1, t2);
        textos.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        javafx.scene.layout.HBox marca = new javafx.scene.layout.HBox(10, placa, textos);
        marca.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        marca.getStyleClass().add("marca");

        VBox menu = new VBox(2);
        menu.setPadding(new Insets(12, 0, 0, 0));
        Button inicio = opcion(menu, "Inicio", () -> new VistaInicio(ctx, this::navegarVentas).vista());
        opcion(menu, "Ventas", () -> new VistaVentas(ctx).vista());
        opcion(menu, "Productos", () -> new VistaProductos(ctx).vista());
        opcion(menu, "Entradas y salidas", () -> new VistaInventario(ctx).vista());
        opcion(menu, "Movimientos", () -> new VistaMovimientos(ctx).vista());
        if (ctx.esAdministrador()) {
            opcion(menu, "Reportes", () -> new VistaReportes(ctx).vista());
            opcion(menu, "Usuarios", () -> new VistaUsuarios(ctx).vista());
            opcion(menu, "Respaldos", () -> new VistaRespaldos(ctx).vista());
        }
        opcion(menu, "Mi contraseña", () -> new VistaContrasena(ctx).vista());

        Label usuario = new Label(ctx.sesion().nombreCompleto);
        usuario.getStyleClass().add("usuario-sesion");
        usuario.setWrapText(true);
        Label rol = new Label(ctx.sesion().rol.nombre().toUpperCase());
        rol.getStyleClass().add("rol-sesion");
        Button salir = new Button("Cerrar sesión");
        salir.getStyleClass().add("btn-secundario");
        salir.setMaxWidth(Double.MAX_VALUE);
        salir.setOnAction(e -> {
            if (Ui.confirmar("Cerrar sesión", "¿Desea cerrar la sesión de " + ctx.sesion().nombreCompleto + "?")) alCerrarSesion.run();
        });
        VBox pie = new VBox(4, usuario, rol, salir);
        pie.setPadding(new Insets(12, 16, 0, 16));

        Region relleno = new Region();
        VBox.setVgrow(relleno, Priority.ALWAYS);
        VBox lateral = new VBox(marca, menu, relleno, pie);
        lateral.getStyleClass().add("barra-lateral");
        lateral.setPrefWidth(250);
        lateral.setMinWidth(250);

        raiz.setLeft(lateral);
        inicio.fire();
        return raiz;
    }

    /** Muestra un contenido ya construido marcando su opción del menú (usado en la documentación). */
    public void mostrar(String opcionMenu, Node contenido) {
        botones.forEach(x -> x.getStyleClass().remove("menu-activo"));
        botones.stream().filter(b -> b.getText().equals(opcionMenu)).findFirst()
                .ifPresent(b -> b.getStyleClass().add("menu-activo"));
        ScrollPane sp = new ScrollPane(contenido);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color: #FAF5EE; -fx-background: #FAF5EE;");
        raiz.setCenter(sp);
    }

    private void navegarVentas() {
        botones.stream().filter(b -> b.getText().equals("Ventas")).findFirst().ifPresent(Button::fire);
    }

    private Button opcion(VBox menu, String texto, Supplier<Node> contenido) {
        Button b = new Button(texto);
        b.getStyleClass().add("menu-boton");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> {
            botones.forEach(x -> x.getStyleClass().remove("menu-activo"));
            b.getStyleClass().add("menu-activo");
            Node n;
            try {
                n = contenido.get();
            } catch (RuntimeException ex) {
                Ui.error("No fue posible abrir " + texto, ex.getMessage());
                return;
            }
            ScrollPane sp = new ScrollPane(n);
            sp.setFitToWidth(true);
            sp.setFitToHeight(true);
            sp.setStyle("-fx-background-color: #FAF5EE; -fx-background: #FAF5EE;");
            raiz.setCenter(sp);
        });
        botones.add(b);
        menu.getChildren().add(b);
        return b;
    }
}
