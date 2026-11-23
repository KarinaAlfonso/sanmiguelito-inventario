package com.sanmiguelito.ui;

import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.servicio.ErrorNegocio;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

/** Cambio de la contraseña propia (cualquier rol). */
public class VistaContrasena {

    private final Contexto ctx;

    public VistaContrasena(Contexto ctx) { this.ctx = ctx; }

    public Node vista() {
        PasswordField actual = new PasswordField();
        PasswordField nueva = new PasswordField();
        PasswordField repetir = new PasswordField();
        Button guardar = Ui.primario("Cambiar contraseña");
        guardar.setOnAction(e -> {
            if (Ui.ejecutar("Cambiar contraseña", () -> {
                ctx.usuarios.autenticar(ctx.sesion().usuario, actual.getText());
                if (!nueva.getText().equals(repetir.getText())) throw new ErrorNegocio("La nueva contraseña y su confirmación no coinciden.");
                ctx.usuarios.cambiarContrasena(ctx.sesion().usuario, nueva.getText());
            })) {
                actual.clear(); nueva.clear(); repetir.clear();
                Ui.info("Cambiar contraseña", "Su contraseña se cambió correctamente.");
            }
        });
        VBox form = Ui.tarjeta(Ui.campo("Contraseña actual", actual), Ui.campo("Nueva contraseña", nueva),
                Ui.campo("Repetir nueva contraseña", repetir), Ui.subtitulo("Mínimo 8 caracteres, con al menos una letra y un número."), guardar);
        form.setMaxWidth(420);
        VBox v = new VBox(16, Ui.encabezado("Mi contraseña", "Usuario: " + ctx.sesion().usuario + " · " + ctx.sesion().rol.nombre()), form);
        v.setPadding(new Insets(26));
        return v;
    }
}
