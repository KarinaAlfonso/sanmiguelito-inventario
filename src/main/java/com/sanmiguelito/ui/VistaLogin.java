package com.sanmiguelito.ui;

import com.sanmiguelito.modelo.Usuario;
import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.servicio.ErrorNegocio;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/** Pantalla de ingreso a la aplicación (RF-001, proceso 1.0). */
public class VistaLogin {

    private final Contexto ctx;
    private final boolean primerUso;
    private final Consumer<Usuario> alIngresar;

    public VistaLogin(Contexto ctx, boolean primerUso, Consumer<Usuario> alIngresar) {
        this.ctx = ctx;
        this.primerUso = primerUso;
        this.alIngresar = alIngresar;
    }

    public Parent vista() {
        // Panel de marca
        Label nombre = new Label("San Miguelito");
        nombre.setStyle("-fx-text-fill: white; -fx-font-size: 38px; -fx-font-weight: bold;");
        Label tipo = new Label("CACHARRERÍA");
        tipo.setStyle("-fx-text-fill: #FFC800; -fx-font-size: 18px; -fx-font-weight: bold;");
        Region franja = new Region();
        franja.getStyleClass().add("franja");
        franja.setPrefSize(80, 6);
        franja.setMaxWidth(80);
        VBox marca = new VBox(10, tipo, nombre, franja);
        marca.setAlignment(Pos.CENTER_LEFT);
        marca.setPadding(new Insets(40));

        // Formulario
        Label titulo = new Label("Iniciar sesión");
        titulo.getStyleClass().add("login-titulo");
        Label sub = new Label("Ingrese su usuario y contraseña");
        sub.getStyleClass().add("subtitulo");
        TextField usuario = Ui.limitar(new TextField(), 20);
        usuario.setPromptText("Usuario");
        usuario.setPrefHeight(40);
        PasswordField clave = Ui.limitar(new PasswordField(), 64);
        clave.setPromptText("Contraseña");
        clave.setPrefHeight(40);
        Label mensaje = new Label();
        mensaje.getStyleClass().add("mensaje-error");
        mensaje.setWrapText(true);
        Button ingresar = Ui.primario("Ingresar");
        ingresar.setMaxWidth(Double.MAX_VALUE);
        ingresar.setPrefHeight(42);
        ingresar.setDefaultButton(true);
        ingresar.setOnAction(e -> {
            mensaje.setText("");
            try {
                Usuario u = ctx.usuarios.autenticar(usuario.getText(), clave.getText());
                alIngresar.accept(u);
            } catch (ErrorNegocio ex) {
                mensaje.setText(ex.getMessage());
                clave.clear();
                clave.requestFocus();
            } catch (RuntimeException ex) {
                mensaje.setText("No fue posible validar el usuario: " + ex.getMessage());
            }
        });

        javafx.scene.image.ImageView logo = Ui.logo(110);
        HBox cabeza = new HBox(logo);
        cabeza.setAlignment(Pos.CENTER);
        VBox form = new VBox(12, cabeza, titulo, sub, Ui.campo("Usuario", usuario), Ui.campo("Contraseña", clave), mensaje, ingresar);
        if (primerUso) {
            Label nota = new Label("Primer uso del sistema. Usuarios iniciales:\n"
                    + "· admin / SanMiguelito2026 (administrador)\n"
                    + "· vendedor / Vendedor2026 (vendedor)\n"
                    + "Al ingresar por primera vez, el sistema le pedirá cambiar la contraseña.");
            nota.getStyleClass().add("nota");
            nota.setWrapText(true);
            form.getChildren().add(nota);
        }
        form.getStyleClass().add("login-panel");
        form.setMaxWidth(380);
        form.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane derecha = new StackPane(form);
        derecha.setPadding(new Insets(30));
        HBox raiz = new HBox(marca, derecha);
        HBox.setHgrow(marca, Priority.ALWAYS);
        HBox.setHgrow(derecha, Priority.ALWAYS);
        raiz.getStyleClass().add("login-fondo");
        raiz.setAlignment(Pos.CENTER);
        javafx.application.Platform.runLater(usuario::requestFocus);
        return raiz;
    }
}
