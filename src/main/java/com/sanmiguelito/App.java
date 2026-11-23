package com.sanmiguelito;

import com.sanmiguelito.datos.Repositorio;
import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.ui.Ui;
import com.sanmiguelito.ui.VistaLogin;
import com.sanmiguelito.ui.VistaPrincipal;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/** Aplicación de escritorio del Sistema de Gestión de Ventas e Inventario de la Cacharrería San Miguelito. */
public class App extends Application {

    private Contexto ctx;
    private Stage ventana;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        ventana = stage;
        Ui.hostServices = getHostServices();
        ctx = new Contexto(Repositorio.predeterminado());
        boolean primerUso;
        try {
            primerUso = ctx.prepararPrimerUso();
        } catch (RuntimeException e) {
            Ui.error("No fue posible iniciar el sistema", e.getMessage());
            return;
        }
        var icono = App.class.getResourceAsStream("/icono.png");
        if (icono != null) stage.getIcons().add(new Image(icono));
        stage.setTitle("San Miguelito · Ventas e Inventario");
        mostrarLogin(primerUso);
        stage.show();
    }

    private void mostrarLogin(boolean primerUso) {
        VistaLogin login = new VistaLogin(ctx, primerUso, usuario -> {
            ctx.iniciarSesion(usuario);
            mostrarPrincipal();
        });
        Scene s = new Scene(login.vista(), 960, 620);
        Ui.aplicarEstilos(s);
        ventana.setScene(s);
        ventana.setMinWidth(900);
        ventana.setMinHeight(600);
        ventana.setMaximized(false);
        ventana.centerOnScreen();
    }

    private void mostrarPrincipal() {
        VistaPrincipal principal = new VistaPrincipal(ctx, () -> {
            ctx.cerrarSesion();
            mostrarLogin(false);
        });
        Scene s = new Scene(principal.vista(), 1280, 760);
        Ui.aplicarEstilos(s);
        ventana.setScene(s);
        ventana.setMaximized(true);
    }
}
