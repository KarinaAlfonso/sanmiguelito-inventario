package com.sanmiguelito;

import com.sanmiguelito.datos.Repositorio;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.modelo.Usuario;
import com.sanmiguelito.modelo.Venta;
import com.sanmiguelito.servicio.Contexto;
import com.sanmiguelito.servicio.ServicioVentas;
import com.sanmiguelito.ui.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

/**
 * Herramienta de documentación (no se distribuye): crea datos de demostración en una carpeta
 * temporal y guarda imágenes PNG de cada pantalla y de los PDF generados.
 * Uso: java ... com.sanmiguelito.Capturas carpetaDatos carpetaImagenes
 */
public class Capturas extends Application {

    static Path datos, salida;

    public static void main(String[] args) {
        datos = Paths.get(args[0]);
        salida = Paths.get(args[1]);
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Files.createDirectories(salida);
        Contexto ctx = new Contexto(new Repositorio(datos));
        ctx.prepararPrimerUso();
        Usuario admin = ctx.usuarios.autenticar("admin", "SanMiguelito2026");
        Usuario vend = ctx.usuarios.autenticar("vendedor", "Vendedor2026");

        // movimientos y ventas de demostración
        ctx.inventario.registrarEntrada("PAP-002", 50, "Compra a proveedor", "admin");
        ctx.inventario.registrarSalida("HOG-001", 1, "Producto dañado", "admin");
        Venta ultima = null;
        Object[][] ventas = {{"PAP-001", 2, "PAP-002", 3}, {"ASE-001", 1, "ASE-002", 1}, {"PIN-001", 2, "BIS-001", 2}, {"ESC-001", 1, "PAP-003", 1}};
        for (Object[] v : ventas) {
            ServicioVentas.Carrito c = new ServicioVentas.Carrito();
            c.agregar(ctx.productos.porCodigo((String) v[0]).orElseThrow(), (Integer) v[1]);
            c.agregar(ctx.productos.porCodigo((String) v[2]).orElseThrow(), (Integer) v[3]);
            ultima = ctx.ventas.confirmar(c, vend);
        }

        ctx.respaldos.crearRespaldo(null);
        Thread.sleep(1100);
        ctx.respaldos.crearRespaldo(null);
        ctx.iniciarSesion(admin);
        capturar(stage, "01_ingreso", () -> new VistaLogin(ctx, false, u -> { }).vista(), 1100, 680);
        capturar(stage, "02_inicio", () -> marco(ctx, "Inicio", new VistaInicio(ctx, () -> { }).vista()), 1366, 768);
        capturar(stage, "03_ventas", () -> {
            VistaVentas vv = new VistaVentas(ctx);
            javafx.scene.Node n = vv.vista();
            vv.agregarProducto(ctx.productos.porCodigo("PAP-001").orElseThrow(), 3);
            vv.agregarProducto(ctx.productos.porCodigo("PAP-002").orElseThrow(), 2);
            vv.agregarProducto(ctx.productos.porCodigo("ESC-001").orElseThrow(), 1);
            return marco(ctx, "Ventas", n);
        }, 1366, 900);
        capturar(stage, "04_productos", () -> marco(ctx, "Productos", new VistaProductos(ctx).vista()), 1366, 768);
        capturar(stage, "05_entradas_salidas", () -> marco(ctx, "Entradas y salidas", new VistaInventario(ctx).vista()), 1366, 768);
        capturar(stage, "06_movimientos", () -> marco(ctx, "Movimientos", new VistaMovimientos(ctx).vista()), 1366, 768);
        capturar(stage, "07_reportes", () -> marco(ctx, "Reportes", new VistaReportes(ctx).vista()), 1366, 768);
        capturar(stage, "08_usuarios", () -> marco(ctx, "Usuarios", new VistaUsuarios(ctx).vista()), 1366, 600);
        capturar(stage, "09_respaldos", () -> marco(ctx, "Respaldos", new VistaRespaldos(ctx).vista()), 1366, 600);
        capturar(stage, "10_contrasena", () -> marco(ctx, "Mi contraseña", new VistaContrasena(ctx).vista()), 1366, 600);

        VistaProductos vprod = new VistaProductos(ctx);
        stage.setScene(new Scene(marco(ctx, "Productos", vprod.vista()), 1366, 768));
        Ui.aplicarEstilos(stage.getScene());
        dialogo("16_formulario_producto_nuevo", vprod, "formulario", Producto.class, null);
        dialogo("17_formulario_producto_modificar", vprod, "formulario", Producto.class, ctx.productos.porCodigo("PAP-001").orElseThrow());
        VistaUsuarios vusu = new VistaUsuarios(ctx);
        stage.setScene(new Scene(marco(ctx, "Usuarios", vusu.vista()), 1366, 768));
        Ui.aplicarEstilos(stage.getScene());
        dialogo("18_formulario_usuario", vusu, "formulario", Usuario.class, null);

        ctx.iniciarSesion(vend);
        capturar(stage, "11_inicio_vendedor", () -> marco(ctx, "Inicio", new VistaInicio(ctx, () -> { }).vista()), 1366, 768);

        // salidas en PDF
        pdf(ctx.reportes.comprobante(ultima), "12_comprobante", 3f);
        pdf(ctx.reportes.reporteInventario(), "13_reporte_inventario", 1.6f);
        pdf(ctx.reportes.reportePocasExistencias(), "14_reporte_pocas_existencias", 1.6f);
        pdf(ctx.reportes.reporteVentas(null, null), "15_reporte_ventas", 1.6f);
        Platform.exit();
    }

    /** Envuelve una vista en la ventana principal para que se vea con la barra lateral. */
    private Parent marco(Contexto ctx, String menu, javafx.scene.Node contenido) {
        VistaPrincipal vp = new VistaPrincipal(ctx, () -> { });
        Parent principal = vp.vista();
        vp.mostrar(menu, contenido);
        return principal;
    }

    /** Abre un formulario modal privado de una vista y guarda la imagen del diálogo. */
    private void dialogo(String nombre, Object vista, String metodo, Class<?> tipoArg, Object arg) throws Exception {
        Platform.runLater(() -> Platform.runLater(() -> {
            for (javafx.stage.Window w : new java.util.ArrayList<>(javafx.stage.Window.getWindows())) {
                if (w instanceof Stage s && s.getModality() != javafx.stage.Modality.NONE && s.isShowing()) {
                    try {
                        s.getScene().getRoot().applyCss();
                        s.getScene().getRoot().layout();
                        ImageIO.write(SwingFXUtils.fromFXImage(s.getScene().snapshot(null), null), "png",
                                salida.resolve(nombre + ".png").toFile());
                        System.out.println("dialogo " + nombre);
                    } catch (Exception e) { e.printStackTrace(); }
                    s.close();
                }
            }
        }));
        java.lang.reflect.Method m = vista.getClass().getDeclaredMethod(metodo, tipoArg);
        m.setAccessible(true);
        m.invoke(vista, arg);
    }

    private void capturar(Stage stage, String nombre, Supplier<Parent> vista, int ancho, int alto) throws Exception {
        Scene s = new Scene(vista.get(), ancho, alto);
        Ui.aplicarEstilos(s);
        stage.setScene(s);
        stage.show();
        s.getRoot().applyCss();
        s.getRoot().layout();
        WritableImage img = s.snapshot(null);
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", salida.resolve(nombre + ".png").toFile());
        System.out.println("captura " + nombre);
    }

    private void pdf(Path archivo, String nombre, float escala) throws Exception {
        try (PDDocument d = Loader.loadPDF(archivo.toFile())) {
            BufferedImage img = new PDFRenderer(d).renderImage(0, escala);
            ImageIO.write(img, "png", new File(salida.toFile(), nombre + ".png"));
        }
        System.out.println("pdf " + nombre);
    }
}
