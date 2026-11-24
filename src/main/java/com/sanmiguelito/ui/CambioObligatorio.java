package com.sanmiguelito.ui;

import com.sanmiguelito.modelo.Usuario;
import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.servicio.ErrorNegocio;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Cambio obligatorio de contraseña en el primer ingreso o después de que el administrador la
 * restablece (RNF-002). Sin este cambio el usuario no puede entrar al sistema.
 */
public final class CambioObligatorio {

    private CambioObligatorio() { }

    /** @return el usuario actualizado si cambió la contraseña, o vacío si canceló */
    public static Optional<Usuario> pedir(Contexto ctx, Usuario u) {
        Dialog<ButtonType> d = new Dialog<>();
        Ui.propietario(d);
        d.setTitle("Cambio de contraseña");
        d.setHeaderText("Hola, " + u.nombreCompleto + ". Por seguridad, cree su propia contraseña");
        d.getDialogPane().getStylesheets().add(CambioObligatorio.class.getResource("/estilos.css").toExternalForm());
        ButtonType guardar = new ButtonType("Guardar y continuar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);
        PasswordField nueva = Ui.limitar(new PasswordField(), 64);
        PasswordField repetir = Ui.limitar(new PasswordField(), 64);
        nueva.setPromptText("Escriba su nueva contraseña");
        repetir.setPromptText("Escríbala otra vez");
        Label nota = new Label("Usted ingresó con una contraseña temporal: la que se asigna al crear el usuario o la que el "
                + "administrador le dio al restablecerla. Para proteger la información de la cacharrería, cree ahora una "
                + "contraseña personal que solo usted conozca. Desde este momento ingresará con la nueva contraseña "
                + "y la temporal dejará de funcionar.");
        nota.setWrapText(true);
        nota.setMaxWidth(400);
        nota.setStyle("-fx-text-fill: #4A3020;");
        d.getDialogPane().setContent(new VBox(10, nota, Ui.campo("Nueva contraseña", nueva),
                Ui.campo("Repetir nueva contraseña", repetir), Ui.requisitosContrasena(nueva, repetir)));
        Button b = (Button) d.getDialogPane().lookupButton(guardar);
        b.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            boolean ok = Ui.ejecutar(d.getTitle(), () -> {
                if (!nueva.getText().equals(repetir.getText()))
                    throw new ErrorNegocio("La nueva contraseña y su confirmación no coinciden.");
                ctx.usuarios.cambiarContrasena(u.usuario, nueva.getText());
            });
            if (!ok) ev.consume();
        });
        Platform.runLater(nueva::requestFocus);
        if (d.showAndWait().orElse(ButtonType.CANCEL) != guardar) return Optional.empty();
        Ui.info(d.getTitle(), "Su contraseña se cambió correctamente. Úsela la próxima vez que ingrese.");
        return ctx.usuarios.buscar(u.usuario);
    }
}
