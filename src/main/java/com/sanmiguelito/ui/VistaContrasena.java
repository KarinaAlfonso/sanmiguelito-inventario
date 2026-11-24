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
        PasswordField actual = Ui.limitar(new PasswordField(), 64);
        PasswordField nueva = Ui.limitar(new PasswordField(), 64);
        PasswordField repetir = Ui.limitar(new PasswordField(), 64);
        Button guardar = Ui.primario("Cambiar contraseña");
        guardar.setOnAction(e -> {
            if (Ui.ejecutar("Cambiar contraseña", () -> {
                if (actual.getText().isEmpty()) throw new ErrorNegocio("Escriba su contraseña actual.");
                var yo = ctx.usuarios.buscar(ctx.sesion().usuario).orElseThrow(() -> new ErrorNegocio("El usuario ya no existe."));
                if (!com.sanmiguelito.servicio.Seguridad.verificar(actual.getText(), yo.sal, yo.hashContrasena))
                    throw new ErrorNegocio("La contraseña actual no es correcta.");
                if (!nueva.getText().equals(repetir.getText())) throw new ErrorNegocio("La nueva contraseña y su confirmación no coinciden.");
                ctx.usuarios.cambiarContrasena(ctx.sesion().usuario, nueva.getText());
                ctx.usuarios.buscar(ctx.sesion().usuario).ifPresent(ctx::iniciarSesion);
            })) {
                actual.clear(); nueva.clear(); repetir.clear();
                Ui.info("Cambiar contraseña", "Su contraseña se cambió correctamente.");
            }
        });
        VBox form = Ui.tarjeta(Ui.campo("Contraseña actual", actual), Ui.campo("Nueva contraseña", nueva),
                Ui.campo("Repetir nueva contraseña", repetir), Ui.requisitosContrasena(nueva, repetir), guardar);
        form.setMaxWidth(460);
        VBox v = new VBox(16, Ui.encabezado("Mi contraseña", "Usuario: " + ctx.sesion().usuario + " · " + ctx.sesion().rol.nombre()), form);
        v.setPadding(new Insets(26));
        return v;
    }
}
