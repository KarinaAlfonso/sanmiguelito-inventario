package com.sanmiguelito;

import com.sanmiguelito.datos.Repositorio;
import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.ui.CambioObligatorio;
import com.sanmiguelito.ui.Ui;
import com.sanmiguelito.ui.VistaLogin;
import com.sanmiguelito.ui.VistaPrincipal;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Aplicación de escritorio del Sistema de Gestión de Ventas e Inventario de la Cacharrería San Miguelito. */
public class App extends Application {

    private Contexto ctx;
    private Stage ventana;
    private VistaPrincipal principal;
    private FileChannel canalBloqueo;
    private FileLock bloqueo;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        ventana = stage;
        Ui.hostServices = getHostServices();
        Ui.ventana = stage;
        Repositorio repo = Repositorio.predeterminado();
        // Cualquier error no previsto se registra y se informa con un mensaje, sin cerrar el programa.
        Thread.setDefaultUncaughtExceptionHandler((hilo, error) -> errorInesperado(repo.base(), error));
        if (!bloquearInstancia(repo.base())) {
            Ui.aviso("San Miguelito ya está abierto",
                    "El sistema ya se está ejecutando en este computador. Use la ventana que está abierta "
                            + "(búsquela en la barra de tareas). Abrir dos ventanas podría hacer que una sobrescriba los datos de la otra.");
            Platform.exit();
            return;
        }
        ctx = new Contexto(repo);
        Boolean primerUso = iniciarDatos();
        if (primerUso == null) { Platform.exit(); return; }
        try { ctx.respaldos.respaldoAutomaticoDelDia(); } catch (RuntimeException ignorado) { /* no impide trabajar */ }

        var icono = App.class.getResourceAsStream("/icono.png");
        if (icono != null) stage.getIcons().add(new Image(icono));
        stage.setTitle("San Miguelito");
        Ui.puedeSalir = () -> Ui.confirmar("Salir del sistema", principal != null && principal.hayVentaEnCurso()
                ? "Hay una venta en curso sin confirmar. Si sale, se descartará.\n¿Desea salir del sistema?"
                : "¿Desea salir del sistema?");
        stage.setOnCloseRequest(e -> { if (!Ui.puedeSalir.getAsBoolean()) e.consume(); });
        mostrarLogin(primerUso);
        stage.show();
    }

    @Override
    public void stop() {
        try {
            if (bloqueo != null) bloqueo.release();
            if (canalBloqueo != null) canalBloqueo.close();
        } catch (IOException ignorado) { /* se libera al terminar el proceso */ }
    }

    private static void errorInesperado(Path base, Throwable error) {
        try {
            Files.createDirectories(base);
            java.io.StringWriter traza = new java.io.StringWriter();
            error.printStackTrace(new java.io.PrintWriter(traza));
            Files.writeString(base.resolve("errores.log"), java.time.LocalDateTime.now() + "\n" + traza + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignorado) { /* el mensaje se muestra de todas formas */ }
        Runnable mostrar = () -> {
            try {
                Ui.error("Ocurrió un error inesperado",
                        "La operación no se completó, pero la información guardada está protegida y puede seguir trabajando.\n"
                                + "Si el problema se repite, informe al soporte técnico (el detalle quedó en el archivo errores.log).\n\n"
                                + "Detalle: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
            } catch (RuntimeException ignorado) { /* no se puede mostrar el mensaje */ }
        };
        if (Platform.isFxApplicationThread()) mostrar.run(); else Platform.runLater(mostrar);
    }

    /** Impide abrir dos veces el sistema sobre la misma carpeta de datos. */
    private boolean bloquearInstancia(Path base) {
        try {
            Files.createDirectories(base);
            canalBloqueo = FileChannel.open(base.resolve(".en_uso"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            bloqueo = canalBloqueo.tryLock();
            return bloqueo != null;
        } catch (IOException | java.nio.channels.OverlappingFileLockException e) {
            return false;
        }
    }

    /**
     * Prepara los datos. Si un archivo está dañado, ofrece restaurar la copia de seguridad más reciente.
     *
     * @return true en el primer uso, false si ya había datos, null si no fue posible iniciar
     */
    private Boolean iniciarDatos() {
        try {
            ctx.productos.listar(true);
            ctx.inventario.consultar(null, null, "", null);
            ctx.ventas.listar(null, null);
            return ctx.prepararPrimerUso();
        } catch (RuntimeException e) {
            List<Path> respaldos;
            try { respaldos = ctx.respaldos.listar(); } catch (RuntimeException ex) { respaldos = List.of(); }
            if (respaldos.isEmpty()) {
                Ui.error("No fue posible iniciar el sistema", e.getMessage()
                        + "\n\nNo hay copias de seguridad en " + ctx.repo.carpetaRespaldos()
                        + ".\nComuníquese con el soporte técnico.");
                return null;
            }
            for (Path r : respaldos) {
                if (r.getFileName().toString().contains("antes_de_restaurar")) continue;
                if (!Ui.confirmar("Datos dañados", e.getMessage() + "\n\n¿Desea restaurar la copia de seguridad «"
                        + r.getFileName() + "»?\nSe conservará una copia de los archivos actuales.")) return null;
                try {
                    ctx.respaldos.restaurar(r);
                    Ui.info("Datos recuperados", "La información se restauró desde «" + r.getFileName()
                            + "». Revise las últimas ventas y movimientos registrados después de esa copia.");
                    return ctx.prepararPrimerUso();
                } catch (RuntimeException ex) {
                    Ui.aviso("Datos dañados", "La copia «" + r.getFileName() + "» tampoco se pudo usar: " + ex.getMessage()
                            + "\nSe intentará con la copia anterior.");
                }
            }
            Ui.error("No fue posible iniciar el sistema", "Ninguna copia de seguridad se pudo restaurar. Comuníquese con el soporte técnico.");
            return null;
        }
    }

    private void mostrarLogin(boolean primerUso) {
        principal = null;
        VistaLogin login = new VistaLogin(ctx, primerUso, usuario -> {
            var u = usuario;
            if (u.debeCambiarContrasena) {
                var cambiado = CambioObligatorio.pedir(ctx, u);
                if (cambiado.isEmpty()) return;   // sin cambiar la contraseña no se ingresa
                u = cambiado.get();
            }
            ctx.iniciarSesion(u);
            mostrarPrincipal();
        });
        Scene s = new Scene(login.vista(), 960, 620);
        Ui.aplicarEstilos(s);
        ventana.setScene(s);
        ventana.setMinWidth(900);
        ventana.setMinHeight(600);
        ventana.setMaximized(true);
    }

    private void mostrarPrincipal() {
        principal = new VistaPrincipal(ctx, () -> {
            ctx.cerrarSesion();
            mostrarLogin(false);
        });
        Scene s = new Scene(principal.vista(), 1280, 760);
        Ui.aplicarEstilos(s);
        ventana.setScene(s);
        ventana.setMaximized(true);
    }
}
