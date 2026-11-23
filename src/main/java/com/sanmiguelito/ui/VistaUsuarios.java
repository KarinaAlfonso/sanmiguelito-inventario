package com.sanmiguelito.ui;

import com.sanmiguelito.modelo.Rol;
import com.sanmiguelito.modelo.Usuario;
import com.sanmiguelito.servicio.Contexto;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Gestión de usuarios y roles (RF-012, proceso 2.0). Solo administrador. */
public class VistaUsuarios {

    private final Contexto ctx;
    private final TableView<Usuario> tabla = Ui.tabla("No hay usuarios registrados.");

    public VistaUsuarios(Contexto ctx) { this.ctx = ctx; }

    public Node vista() {
        ctx.exigirAdministrador();
        tabla.getColumns().add(Ui.columna("Usuario", 140, u -> u.usuario));
        tabla.getColumns().add(Ui.columna("Nombre completo", 280, u -> u.nombreCompleto));
        tabla.getColumns().add(Ui.columna("Rol", 140, u -> u.rol.nombre()));
        tabla.getColumns().add(Ui.columna("Estado", 110, u -> u.activo ? "Activo" : "Desactivado"));
        Button nuevo = Ui.primario("Nuevo usuario");
        nuevo.setOnAction(e -> formulario(null));
        Button editar = Ui.secundario("Modificar");
        editar.setOnAction(e -> { Usuario u = seleccionado(); if (u != null) formulario(u); });
        Button clave = Ui.secundario("Restablecer contraseña");
        clave.setOnAction(e -> restablecer());
        VBox v = new VBox(16, Ui.encabezado("Usuarios",
                        "Registre, modifique o desactive los usuarios y asigne su rol. El vendedor solo accede a ventas, productos, entradas y salidas y movimientos."),
                Ui.tarjeta(Ui.fila(nuevo, editar, clave), tabla));
        v.setPadding(new Insets(26));
        cargar();
        return v;
    }

    private void cargar() { Ui.ejecutar("Usuarios", () -> tabla.getItems().setAll(ctx.usuarios.listar())); }

    private Usuario seleccionado() {
        Usuario u = tabla.getSelectionModel().getSelectedItem();
        if (u == null) Ui.aviso("Usuarios", "Seleccione un usuario de la lista.");
        return u;
    }

    private Dialog<ButtonType> dialogo(String titulo, String encabezado, ButtonType ok) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(titulo);
        d.setHeaderText(encabezado);
        d.getDialogPane().getStylesheets().add(getClass().getResource("/estilos.css").toExternalForm());
        d.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        return d;
    }

    private void formulario(Usuario u) {
        boolean nuevo = u == null;
        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> d = dialogo(nuevo ? "Nuevo usuario" : "Modificar usuario",
                nuevo ? "Registrar un usuario" : "Modificar el usuario «" + u.usuario + "»", guardar);
        TextField login = new TextField(nuevo ? "" : u.usuario);
        login.setDisable(!nuevo);
        TextField nombre = new TextField(nuevo ? "" : u.nombreCompleto);
        nombre.setPrefWidth(320);
        ComboBox<Rol> rol = new ComboBox<>();
        rol.getItems().setAll(Rol.values());
        rol.setValue(nuevo ? Rol.VENDEDOR : u.rol);
        PasswordField clave = new PasswordField();
        PasswordField repetir = new PasswordField();
        CheckBox activo = new CheckBox("Usuario activo");
        activo.setSelected(nuevo || u.activo);
        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(10);
        g.setPadding(new Insets(10));
        g.add(Ui.campo("Usuario", login), 0, 0);
        g.add(Ui.campo("Rol", rol), 1, 0);
        g.add(Ui.campo("Nombre completo", nombre), 0, 1, 2, 1);
        if (nuevo) {
            g.add(Ui.campo("Contraseña", clave), 0, 2);
            g.add(Ui.campo("Repetir contraseña", repetir), 1, 2);
            javafx.scene.control.Label n = new javafx.scene.control.Label("Mínimo 8 caracteres, con al menos una letra y un número.");
            n.getStyleClass().add("nota");
            g.add(n, 0, 3, 2, 1);
        } else {
            g.add(activo, 0, 2, 2, 1);
        }
        d.getDialogPane().setContent(g);
        Button b = (Button) d.getDialogPane().lookupButton(guardar);
        b.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            boolean ok = Ui.ejecutar(d.getTitle(), () -> {
                ctx.exigirAdministrador();
                if (nuevo) {
                    VistaProductos.exigir(clave.getText().equals(repetir.getText()), "Las contraseñas no coinciden.");
                    ctx.usuarios.crear(login.getText(), nombre.getText(), rol.getValue(), clave.getText());
                } else {
                    ctx.usuarios.modificar(u.usuario, nombre.getText(), rol.getValue(), activo.isSelected(), ctx.sesion().usuario);
                }
            });
            if (!ok) ev.consume();
        });
        if (d.showAndWait().orElse(ButtonType.CANCEL) == guardar) {
            cargar();
            Ui.info(d.getTitle(), "Los datos del usuario se guardaron correctamente.");
        }
    }

    private void restablecer() {
        Usuario u = seleccionado();
        if (u == null) return;
        ButtonType guardar = new ButtonType("Restablecer", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> d = dialogo("Restablecer contraseña", "Nueva contraseña para «" + u.usuario + "»", guardar);
        PasswordField clave = new PasswordField();
        PasswordField repetir = new PasswordField();
        d.getDialogPane().setContent(new VBox(10, Ui.campo("Nueva contraseña", clave), Ui.campo("Repetir contraseña", repetir)));
        Button b = (Button) d.getDialogPane().lookupButton(guardar);
        b.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            boolean ok = Ui.ejecutar(d.getTitle(), () -> {
                ctx.exigirAdministrador();
                VistaProductos.exigir(clave.getText().equals(repetir.getText()), "Las contraseñas no coinciden.");
                ctx.usuarios.cambiarContrasena(u.usuario, clave.getText());
            });
            if (!ok) ev.consume();
        });
        if (d.showAndWait().orElse(ButtonType.CANCEL) == guardar) Ui.info(d.getTitle(), "La contraseña se cambió correctamente.");
    }
}
