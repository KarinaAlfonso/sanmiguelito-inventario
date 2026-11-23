package com.sanmiguelito.servicio;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sanmiguelito.datos.Repositorio;
import com.sanmiguelito.modelo.ItemVenta;
import com.sanmiguelito.modelo.Producto;
import com.sanmiguelito.modelo.Venta;

import java.awt.Color;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Comprobante de venta (RF-014) y reportes de inventario y ventas (RF-015) en PDF. */
public class ServicioReportes {

    public static final String EMPRESA = "Cacharrería San Miguelito";
    public static final String DIRECCION = "Carrera 11 #16-101, Centro Histórico · Tunja, Boyacá";
    public static final String NIT = "NIT 901.903.733-4";

    private static final Color ROJO = new Color(0xD2, 0x1E, 0x1E);
    private static final Color ROJO_OSCURO = new Color(0x96, 0x0A, 0x0A);
    private static final Color CREMA = new Color(0xFF, 0xF8, 0xEE);
    private static final Color BORDE = new Color(0xE8, 0xD8, 0xC8);
    private static final DateTimeFormatter ARCHIVO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Repositorio repo;
    private final ServicioProductos productos;
    private final ServicioVentas ventas;

    public ServicioReportes(Repositorio repo, ServicioProductos productos, ServicioVentas ventas) {
        this.repo = repo;
        this.productos = productos;
        this.ventas = ventas;
    }

    // ------------------------------------------------------------------ datos de los reportes

    /** Resumen de ventas de un período: número de ventas, unidades, total y productos vendidos. */
    public static class ResumenVentas {
        public int numeroVentas;
        public int unidades;
        public long total;
        public final Map<String, long[]> porProducto = new LinkedHashMap<>(); // nombre -> {unidades, valor}
        public List<Venta> ventas;
    }

    public ResumenVentas resumenVentas(LocalDate desde, LocalDate hasta) {
        ResumenVentas r = new ResumenVentas();
        r.ventas = ventas.listar(desde, hasta);
        r.numeroVentas = r.ventas.size();
        for (Venta v : r.ventas) {
            r.total += v.total();
            r.unidades += v.unidades();
            for (ItemVenta i : v.items) {
                long[] acc = r.porProducto.computeIfAbsent(i.nombreProducto, k -> new long[2]);
                acc[0] += i.cantidad;
                acc[1] += i.subtotal();
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ PDF

    public Path comprobante(Venta v) {
        Path destino = archivo("comprobante_" + v.numero);
        float alto = 290 + 26f * v.items.size();                              // alto según el número de productos
        Document doc = new Document(new Rectangle(226, alto), 14, 14, 14, 14); // formato tipo tirilla (8 cm)
        try {
            FileOutputStream out = new FileOutputStream(destino.toFile()); // lo cierra doc.close()
            PdfWriter.getInstance(doc, out);
            doc.open();
            agregarLogo(doc, 52, Element.ALIGN_CENTER);
            centrado(doc, EMPRESA, new Font(Font.HELVETICA, 11, Font.BOLD, ROJO));
            centrado(doc, NIT, new Font(Font.HELVETICA, 7));
            centrado(doc, DIRECCION, new Font(Font.HELVETICA, 7));
            centrado(doc, "COMPROBANTE DE VENTA " + v.numero, new Font(Font.HELVETICA, 9, Font.BOLD));
            Font f7 = new Font(Font.HELVETICA, 7);
            doc.add(new Paragraph("Fecha: " + Formato.fechaHora(v.fecha), f7));
            doc.add(new Paragraph("Vendedor: " + v.nombreVendedor, f7));
            doc.add(new Paragraph(" ", f7));
            PdfPTable t = new PdfPTable(new float[]{3.2f, 0.8f, 1.6f});
            t.setWidthPercentage(100);
            encabezado(t, new String[]{"Producto", "Cant.", "Subtotal"}, 7);
            for (ItemVenta i : v.items) {
                celda(t, i.nombreProducto + "\n" + Formato.pesos(i.precioUnitario) + " c/u", 7, Element.ALIGN_LEFT, null);
                celda(t, String.valueOf(i.cantidad), 7, Element.ALIGN_CENTER, null);
                celda(t, Formato.pesos(i.subtotal()), 7, Element.ALIGN_RIGHT, null);
            }
            doc.add(t);
            Paragraph total = new Paragraph("TOTAL: " + Formato.pesos(v.total()), new Font(Font.HELVETICA, 10, Font.BOLD, ROJO_OSCURO));
            total.setAlignment(Element.ALIGN_RIGHT);
            doc.add(total);
            doc.add(new Paragraph(" ", f7));
            centrado(doc, "Soporte interno de la venta. No corresponde a una factura electrónica.", new Font(Font.HELVETICA, 6, Font.ITALIC));
            centrado(doc, "¡Gracias por su compra!", new Font(Font.HELVETICA, 8, Font.BOLD));
        } catch (Exception e) {
            throw new ErrorNegocio("No fue posible generar el comprobante: " + e.getMessage());
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return destino;
    }

    public Path reporteInventario() {
        List<Producto> lista = productos.listar(false);
        Path destino = archivo("reporte_inventario");
        String[] col = {"Código", "Producto", "Categoría", "Precio", "Existencias", "Mínimo", "Estado"};
        float[] anchos = {1.2f, 3.2f, 1.6f, 1.3f, 1.1f, 0.9f, 1.6f};
        escribirReporte(destino, "Reporte de inventario disponible", "Productos activos: " + lista.size(), col, anchos, t -> {
            int fila = 0;
            for (Producto p : lista) {
                Color fondo = fila++ % 2 == 0 ? Color.WHITE : CREMA;
                celda(t, p.codigo, 8, Element.ALIGN_LEFT, fondo);
                celda(t, p.nombre, 8, Element.ALIGN_LEFT, fondo);
                celda(t, p.categoria, 8, Element.ALIGN_LEFT, fondo);
                celda(t, Formato.pesos(p.precioVenta), 8, Element.ALIGN_RIGHT, fondo);
                celda(t, String.valueOf(p.existencias), 8, Element.ALIGN_CENTER, fondo);
                celda(t, String.valueOf(p.cantidadMinima), 8, Element.ALIGN_CENTER, fondo);
                celda(t, p.estadoExistencias(), 8, Element.ALIGN_LEFT, fondo);
            }
        }, null);
        return destino;
    }

    public Path reportePocasExistencias() {
        List<Producto> lista = productos.pocasExistencias();
        Path destino = archivo("reporte_pocas_existencias");
        String[] col = {"Código", "Producto", "Categoría", "Existencias", "Mínimo", "Estado"};
        float[] anchos = {1.2f, 3.4f, 1.8f, 1.1f, 0.9f, 1.6f};
        escribirReporte(destino, "Productos con pocas existencias o agotados",
                "Productos que requieren reposición: " + lista.size(), col, anchos, t -> {
                    int fila = 0;
                    for (Producto p : lista) {
                        Color fondo = fila++ % 2 == 0 ? Color.WHITE : CREMA;
                        celda(t, p.codigo, 8, Element.ALIGN_LEFT, fondo);
                        celda(t, p.nombre, 8, Element.ALIGN_LEFT, fondo);
                        celda(t, p.categoria, 8, Element.ALIGN_LEFT, fondo);
                        celda(t, String.valueOf(p.existencias), 8, Element.ALIGN_CENTER, fondo);
                        celda(t, String.valueOf(p.cantidadMinima), 8, Element.ALIGN_CENTER, fondo);
                        celda(t, p.estadoExistencias(), 8, Element.ALIGN_LEFT, fondo);
                    }
                }, null);
        return destino;
    }

    public Path reporteVentas(LocalDate desde, LocalDate hasta) {
        ResumenVentas r = resumenVentas(desde, hasta);
        Path destino = archivo("reporte_ventas");
        String periodo = "Período: " + (desde == null ? "inicio" : desde.format(Formato.FECHA)) + " a "
                + (hasta == null ? "hoy" : hasta.format(Formato.FECHA))
                + "   ·   Ventas: " + r.numeroVentas + "   ·   Unidades: " + r.unidades + "   ·   Total: " + Formato.pesos(r.total);
        String[] col = {"Producto", "Unidades vendidas", "Valor vendido"};
        float[] anchos = {4f, 1.5f, 1.8f};
        escribirReporte(destino, "Reporte de ventas por período", periodo, col, anchos, t -> {
            int fila = 0;
            for (Map.Entry<String, long[]> e : r.porProducto.entrySet()) {
                Color fondo = fila++ % 2 == 0 ? Color.WHITE : CREMA;
                celda(t, e.getKey(), 8, Element.ALIGN_LEFT, fondo);
                celda(t, String.valueOf(e.getValue()[0]), 8, Element.ALIGN_CENTER, fondo);
                celda(t, Formato.pesos(e.getValue()[1]), 8, Element.ALIGN_RIGHT, fondo);
            }
        }, doc -> {
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Detalle de ventas", new Font(Font.HELVETICA, 11, Font.BOLD, ROJO)));
            PdfPTable d = new PdfPTable(new float[]{1.3f, 2.2f, 2.6f, 1.1f, 1.6f});
            d.setWidthPercentage(100);
            encabezado(d, new String[]{"Número", "Fecha", "Vendedor", "Unidades", "Total"}, 8);
            int fila = 0;
            for (Venta v : r.ventas) {
                Color fondo = fila++ % 2 == 0 ? Color.WHITE : CREMA;
                celda(d, v.numero, 8, Element.ALIGN_LEFT, fondo);
                celda(d, Formato.fechaHora(v.fecha), 8, Element.ALIGN_LEFT, fondo);
                celda(d, v.nombreVendedor, 8, Element.ALIGN_LEFT, fondo);
                celda(d, String.valueOf(v.unidades()), 8, Element.ALIGN_CENTER, fondo);
                celda(d, Formato.pesos(v.total()), 8, Element.ALIGN_RIGHT, fondo);
            }
            doc.add(d);
        });
        return destino;
    }

    // ------------------------------------------------------------------ utilidades PDF

    private interface Filas { void llenar(PdfPTable t) throws Exception; }
    private interface Extra { void agregar(Document d) throws Exception; }

    private void escribirReporte(Path destino, String titulo, String subtitulo, String[] columnas, float[] anchos, Filas filas, Extra extra) {
        Document doc = new Document(PageSize.LETTER, 40, 40, 40, 40);
        try {
            FileOutputStream out = new FileOutputStream(destino.toFile()); // lo cierra doc.close()
            PdfWriter.getInstance(doc, out);
            doc.open();
            agregarLogo(doc, 60, Element.ALIGN_RIGHT);
            doc.add(new Paragraph(EMPRESA, new Font(Font.HELVETICA, 16, Font.BOLD, ROJO)));
            doc.add(new Paragraph(NIT + " · " + DIRECCION, new Font(Font.HELVETICA, 8)));
            doc.add(new Paragraph(titulo, new Font(Font.HELVETICA, 13, Font.BOLD, ROJO_OSCURO)));
            doc.add(new Paragraph("Generado: " + LocalDateTime.now().format(Formato.FECHA_HORA), new Font(Font.HELVETICA, 8)));
            doc.add(new Paragraph(subtitulo, new Font(Font.HELVETICA, 9)));
            doc.add(new Paragraph(" "));
            PdfPTable t = new PdfPTable(anchos);
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            encabezado(t, columnas, 8);
            filas.llenar(t);
            if (t.getRows().size() == 1) {
                PdfPCell vacio = new PdfPCell(new Phrase("No hay registros para mostrar.", new Font(Font.HELVETICA, 8, Font.ITALIC)));
                vacio.setColspan(columnas.length);
                vacio.setBorderColor(BORDE);
                vacio.setPadding(5);
                t.addCell(vacio);
            }
            doc.add(t);
            if (extra != null) extra.agregar(doc);
        } catch (Exception e) {
            throw new ErrorNegocio("No fue posible generar el reporte: " + e.getMessage());
        } finally {
            if (doc.isOpen()) doc.close();
        }
    }

    /** Agrega el logo de la empresa (si está disponible) en la parte superior del documento. */
    private static void agregarLogo(Document doc, float alto, int alineacion) {
        try (var in = ServicioReportes.class.getResourceAsStream("/logo.png")) {
            if (in == null) return;
            com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(in.readAllBytes());
            img.scaleToFit(alto * 1.1f, alto);
            img.setAlignment(alineacion);
            if (alineacion == Element.ALIGN_RIGHT) {
                img.setAbsolutePosition(doc.getPageSize().getWidth() - doc.rightMargin() - img.getScaledWidth(),
                        doc.getPageSize().getHeight() - doc.topMargin() - img.getScaledHeight() + 10);
            }
            doc.add(img);
        } catch (Exception e) {
            // el logo es decorativo: si no se puede cargar, el documento se genera sin él
        }
    }

    private static void centrado(Document doc, String texto, Font f) throws Exception {
        Paragraph p = new Paragraph(texto, f);
        p.setAlignment(Element.ALIGN_CENTER);
        doc.add(p);
    }

    private static void encabezado(PdfPTable t, String[] columnas, int tam) {
        for (String c : columnas) {
            PdfPCell cell = new PdfPCell(new Phrase(c, new Font(Font.HELVETICA, tam, Font.BOLD, Color.WHITE)));
            cell.setBackgroundColor(ROJO_OSCURO);
            cell.setBorderColor(BORDE);
            cell.setPadding(4);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            t.addCell(cell);
        }
    }

    private static void celda(PdfPTable t, String texto, int tam, int alineacion, Color fondo) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, new Font(Font.HELVETICA, tam)));
        cell.setHorizontalAlignment(alineacion);
        cell.setBorderColor(BORDE);
        cell.setPadding(3);
        if (fondo != null) cell.setBackgroundColor(fondo);
        t.addCell(cell);
    }

    private Path archivo(String nombre) {
        try {
            Files.createDirectories(repo.carpetaDocumentos());
        } catch (IOException e) {
            throw new ErrorNegocio("No fue posible crear la carpeta de documentos.");
        }
        return repo.carpetaDocumentos().resolve(nombre + "_" + LocalDateTime.now().format(ARCHIVO) + ".pdf");
    }
}
