package com.bedolla.bancobienestar.service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.bedolla.bancobienestar.entity.CuentaEntity;
import com.bedolla.bancobienestar.entity.MovimientosEntity;
import com.bedolla.bancobienestar.entity.SolicitudCreditoEntity;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/** Genera los documentos PDF descargables (detalle de crédito y comprobante de transferencia). */
@Service
public class PdfService {

    private static final Locale MX = new Locale("es", "MX");
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", MX);
    private static final DateTimeFormatter FMT_FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy", MX);

    private static final Font FUENTE_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new java.awt.Color(13, 110, 253));
    private static final Font FUENTE_SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 10, java.awt.Color.GRAY);
    private static final Font FUENTE_ETIQUETA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, java.awt.Color.DARK_GRAY);
    private static final Font FUENTE_VALOR = FontFactory.getFont(FontFactory.HELVETICA, 11, java.awt.Color.BLACK);
    private static final Font FUENTE_SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, java.awt.Color.BLACK);

    /** Genera el PDF con el detalle completo de una solicitud de crédito ya resuelta. */
    public byte[] generarPdfPrestamo(SolicitudCreditoEntity solicitud) {
        try {
            Document documento = new Document();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfWriter.getInstance(documento, salida);
            documento.open();

            documento.add(encabezado("Detalle de préstamo"));

            PdfPTable tabla = new PdfPTable(2);
            tabla.setWidthPercentage(100);
            tabla.setSpacingBefore(15);
            tabla.setWidths(new float[]{1.2f, 2f});

            String nombreCliente = solicitud.getUsuario() != null ? solicitud.getUsuario().getNombre() : "N/A";

            agregarFila(tabla, "Folio de solicitud", "#" + solicitud.getId());
            agregarFila(tabla, "Cliente", nombreCliente);
            agregarFila(tabla, "Monto solicitado",
                    "$" + String.format(MX, "%,.2f", solicitud.getMontoSolicitado()) + " MXN");
            agregarFila(tabla, "Fecha de solicitud",
                    solicitud.getFecha() != null ? solicitud.getFecha().format(FMT_FECHA) : "N/A");
            agregarFila(tabla, "Estado", solicitud.getEstado());
            agregarFila(tabla, "Ejecutivo que autorizó",
                    solicitud.getEjecutivoAutorizo() != null ? solicitud.getEjecutivoAutorizo() : "N/A");
            agregarFila(tabla, "Fecha y hora de aprobación",
                    solicitud.getFechaAprobacion() != null ? solicitud.getFechaAprobacion().format(FMT_FECHA) : "N/A");
            agregarFila(tabla, "Fecha del primer pago",
                    solicitud.getFechaPrimerPago() != null ? solicitud.getFechaPrimerPago().format(FMT_FECHA_CORTA) : "N/A");

            if ("APROBADA".equals(solicitud.getEstado()) || "PAGADA".equals(solicitud.getEstado())) {
                double pagado = solicitud.getMontoPagado() != null ? solicitud.getMontoPagado() : 0.0;
                agregarFila(tabla, "Monto pagado a la fecha",
                        "$" + String.format(MX, "%,.2f", pagado) + " MXN");
                agregarFila(tabla, "Saldo pendiente",
                        "$" + String.format(MX, "%,.2f", solicitud.getSaldoPendiente()) + " MXN");
                agregarFila(tabla, "Próximo pago",
                        solicitud.getFechaProximoPago() != null
                                ? solicitud.getFechaProximoPago().format(FMT_FECHA_CORTA)
                                : "Crédito liquidado");
            }

            agregarFila(tabla, "Observaciones del ejecutivo",
                    (solicitud.getObservaciones() != null && !solicitud.getObservaciones().isBlank())
                            ? solicitud.getObservaciones() : "Sin observaciones");

            documento.add(tabla);

            Paragraph tituloFirma = new Paragraph("Firma digital del cliente", FUENTE_SECCION);
            tituloFirma.setSpacingBefore(20);
            documento.add(tituloFirma);

            if (solicitud.getFirmaBase64() != null && !solicitud.getFirmaBase64().isBlank()) {
                Image imagenFirma = imagenDesdeBase64(solicitud.getFirmaBase64());
                if (imagenFirma != null) {
                    imagenFirma.scaleToFit(220, 110);
                    imagenFirma.setSpacingBefore(10);
                    imagenFirma.setBorder(Image.BOX);
                    imagenFirma.setBorderWidth(1);
                    imagenFirma.setBorderColor(java.awt.Color.LIGHT_GRAY);
                    documento.add(imagenFirma);
                } else {
                    documento.add(new Paragraph("No fue posible cargar la firma.", FUENTE_VALOR));
                }
            } else {
                documento.add(new Paragraph("Esta solicitud no cuenta con firma digital registrada.", FUENTE_VALOR));
            }

            piePagina(documento);
            documento.close();
            return salida.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el PDF del préstamo: " + e.getMessage(), e);
        }
    }

    /** Genera el comprobante en PDF de una transferencia ya completada. */
    public byte[] generarPdfComprobanteTransferencia(MovimientosEntity movimiento,
                                                       CuentaEntity cuentaOrigen,
                                                       CuentaEntity cuentaDestino) {
        try {
            Document documento = new Document();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfWriter.getInstance(documento, salida);
            documento.open();

            documento.add(encabezado("Comprobante de transferencia"));

            PdfPTable tabla = new PdfPTable(2);
            tabla.setWidthPercentage(100);
            tabla.setSpacingBefore(15);
            tabla.setWidths(new float[]{1.2f, 2f});

            String nombreOrigen = cuentaOrigen != null && cuentaOrigen.getUsuario() != null
                    ? cuentaOrigen.getUsuario().getNombre() : "N/A";
            String nombreDestino = cuentaDestino != null && cuentaDestino.getUsuario() != null
                    ? cuentaDestino.getUsuario().getNombre() : "N/A";

            agregarFila(tabla, "Folio de operación", "#" + movimiento.getId());
            agregarFila(tabla, "Fecha y hora", movimiento.getFecha() != null ? movimiento.getFecha().format(FMT_FECHA) : "N/A");
            agregarFila(tabla, "Monto transferido", "$" + String.format(MX, "%,.2f", movimiento.getMonto()) + " MXN");
            agregarFila(tabla, "Concepto", movimiento.getDescripcion() != null ? movimiento.getDescripcion() : "N/A");
            agregarFila(tabla, "Estado", movimiento.getEstadoMovimiento());
            agregarFila(tabla, "Cliente ordenante", nombreOrigen);
            agregarFila(tabla, "CLABE origen", movimiento.getCuentaOrigen());
            agregarFila(tabla, "Cliente beneficiario", nombreDestino);
            agregarFila(tabla, "CLABE destino", movimiento.getCuentaDestino());

            documento.add(tabla);

            if (movimiento.getFirmaBase64() != null && !movimiento.getFirmaBase64().isBlank()) {
                Paragraph tituloFirma = new Paragraph("Firma digital del ordenante", FUENTE_SECCION);
                tituloFirma.setSpacingBefore(20);
                documento.add(tituloFirma);

                Image imagenFirma = imagenDesdeBase64(movimiento.getFirmaBase64());
                if (imagenFirma != null) {
                    imagenFirma.scaleToFit(220, 110);
                    imagenFirma.setSpacingBefore(10);
                    imagenFirma.setBorder(Image.BOX);
                    imagenFirma.setBorderWidth(1);
                    imagenFirma.setBorderColor(java.awt.Color.LIGHT_GRAY);
                    documento.add(imagenFirma);
                }
            }

            piePagina(documento);
            documento.close();
            return salida.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el comprobante PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Genera el comprobante en PDF de una transferencia que fue CANCELADA por
     * el ejecutivo. Va dirigido al cliente que originalmente envió el dinero,
     * confirmándole que el monto ya fue devuelto a su cuenta.
     */
    public byte[] generarPdfCancelacionTransferencia(MovimientosEntity movimiento,
                                                       CuentaEntity cuentaOrigen,
                                                       CuentaEntity cuentaDestino) {
        try {
            Document documento = new Document();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfWriter.getInstance(documento, salida);
            documento.open();

            documento.add(encabezado("Comprobante de cancelación de transferencia"));

            Paragraph aviso = new Paragraph(
                    "Tu transferencia fue cancelada por un ejecutivo del banco. El monto ya fue devuelto a tu cuenta de origen.",
                    FUENTE_VALOR);
            aviso.setSpacingBefore(10);
            aviso.setSpacingAfter(10);
            documento.add(aviso);

            PdfPTable tabla = new PdfPTable(2);
            tabla.setWidthPercentage(100);
            tabla.setSpacingBefore(10);
            tabla.setWidths(new float[]{1.2f, 2f});

            String nombreOrigen = cuentaOrigen != null && cuentaOrigen.getUsuario() != null
                    ? cuentaOrigen.getUsuario().getNombre() : "N/A";
            String nombreDestino = cuentaDestino != null && cuentaDestino.getUsuario() != null
                    ? cuentaDestino.getUsuario().getNombre() : "N/A";

            agregarFila(tabla, "Folio de operación", "#" + movimiento.getId());
            agregarFila(tabla, "Monto devuelto", "$" + String.format(MX, "%,.2f", movimiento.getMonto()) + " MXN");
            agregarFila(tabla, "Concepto original", movimiento.getDescripcion() != null ? movimiento.getDescripcion() : "N/A");
            agregarFila(tabla, "Estado", "CANCELADA");
            agregarFila(tabla, "Cliente que recibe la devolución", nombreOrigen);
            agregarFila(tabla, "CLABE que recibe la devolución", movimiento.getCuentaOrigen());
            agregarFila(tabla, "Cliente al que se le retiró el monto (beneficiario original)", nombreDestino);
            agregarFila(tabla, "CLABE afectada por el retiro", movimiento.getCuentaDestino());
            agregarFila(tabla, "Cancelado por (ejecutivo)",
                    movimiento.getCanceladoPor() != null ? movimiento.getCanceladoPor() : "N/A");
            agregarFila(tabla, "Fecha y hora de cancelación",
                    movimiento.getFechaCancelacion() != null ? movimiento.getFechaCancelacion().format(FMT_FECHA) : "N/A");

            documento.add(tabla);
            piePagina(documento);
            documento.close();
            return salida.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el comprobante de cancelación: " + e.getMessage(), e);
        }
    }

    /**
     * Genera el comprobante en PDF de un abono (pago) hecho a un crédito:
     * muestra los datos del cliente y del crédito, cuánto se pagó en esta
     * operación, cuánto lleva pagado en total, cuánto le queda pendiente y
     * cuándo le toca el siguiente pago.
     */
    public byte[] generarPdfAbonoCredito(SolicitudCreditoEntity solicitud, MovimientosEntity abono) {
        try {
            Document documento = new Document();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfWriter.getInstance(documento, salida);
            documento.open();

            documento.add(encabezado("Comprobante de pago de crédito"));

            Paragraph aviso = new Paragraph(
                    "Tu pago fue aplicado correctamente y ya se descontó de tu crédito.", FUENTE_VALOR);
            aviso.setSpacingBefore(10);
            aviso.setSpacingAfter(10);
            documento.add(aviso);

            String nombreCliente = solicitud != null && solicitud.getUsuario() != null
                    ? solicitud.getUsuario().getNombre() : "N/A";

            PdfPTable tablaCliente = new PdfPTable(2);
            tablaCliente.setWidthPercentage(100);
            tablaCliente.setSpacingBefore(10);
            tablaCliente.setWidths(new float[]{1.2f, 2f});

            agregarFila(tablaCliente, "Folio de pago", "#" + abono.getId());
            agregarFila(tablaCliente, "Cliente", nombreCliente);
            agregarFila(tablaCliente, "Cuenta (CLABE)", abono.getCuentaOrigen());
            agregarFila(tablaCliente, "Fecha y hora del pago",
                    abono.getFecha() != null ? abono.getFecha().format(FMT_FECHA) : "N/A");
            agregarFila(tablaCliente, "Monto abonado en este pago",
                    "$" + String.format(MX, "%,.2f", abono.getMonto()) + " MXN");

            documento.add(tablaCliente);

            Paragraph tituloCredito = new Paragraph("Estado del crédito", FUENTE_SECCION);
            tituloCredito.setSpacingBefore(20);
            documento.add(tituloCredito);

            PdfPTable tablaCredito = new PdfPTable(2);
            tablaCredito.setWidthPercentage(100);
            tablaCredito.setSpacingBefore(10);
            tablaCredito.setWidths(new float[]{1.2f, 2f});

            if (solicitud != null) {
                double pagado = solicitud.getMontoPagado() != null ? solicitud.getMontoPagado() : 0.0;
                agregarFila(tablaCredito, "Folio de crédito", "#" + solicitud.getId());
                agregarFila(tablaCredito, "Monto original solicitado",
                        "$" + String.format(MX, "%,.2f", solicitud.getMontoSolicitado()) + " MXN");
                agregarFila(tablaCredito, "Total pagado a la fecha",
                        "$" + String.format(MX, "%,.2f", pagado) + " MXN");
                agregarFila(tablaCredito, "Saldo pendiente",
                        "$" + String.format(MX, "%,.2f", solicitud.getSaldoPendiente()) + " MXN");
                agregarFila(tablaCredito, "Estado del crédito", solicitud.getEstado());
                agregarFila(tablaCredito, "Próximo pago",
                        solicitud.getFechaProximoPago() != null
                                ? solicitud.getFechaProximoPago().format(FMT_FECHA_CORTA)
                                : "Crédito liquidado, ya no tienes más pagos pendientes.");
            } else {
                agregarFila(tablaCredito, "Crédito", "No fue posible recuperar el detalle del crédito.");
            }

            documento.add(tablaCredito);
            piePagina(documento);
            documento.close();
            return salida.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el comprobante de pago: " + e.getMessage(), e);
        }
    }

    /**
     * Genera un comprobante genérico para cualquier movimiento que no tenga
     * un PDF especializado propio (pago de servicio, vuelo, camión, hotel,
     * evento, crédito autorizado o cancelación de crédito). Sirve como
     * respaldo para que, desde la pantalla de "Movimientos", cualquier
     * renglón del historial tenga un comprobante descargable.
     */
    public byte[] generarPdfReciboMovimiento(MovimientosEntity movimiento, CuentaEntity cuentaCliente) {
        try {
            Document documento = new Document();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfWriter.getInstance(documento, salida);
            documento.open();

            documento.add(encabezado(tituloParaTipo(movimiento.getTipo())));

            PdfPTable tabla = new PdfPTable(2);
            tabla.setWidthPercentage(100);
            tabla.setSpacingBefore(15);
            tabla.setWidths(new float[]{1.2f, 2f});

            String nombreCliente = cuentaCliente != null && cuentaCliente.getUsuario() != null
                    ? cuentaCliente.getUsuario().getNombre() : "N/A";

            agregarFila(tabla, "Folio de operación", "#" + movimiento.getId());
            agregarFila(tabla, "Fecha y hora", movimiento.getFecha() != null ? movimiento.getFecha().format(FMT_FECHA) : "N/A");
            agregarFila(tabla, "Tipo de movimiento", movimiento.getTipo());
            agregarFila(tabla, "Concepto", movimiento.getDescripcion() != null ? movimiento.getDescripcion() : "N/A");
            agregarFila(tabla, "Monto", "$" + String.format(MX, "%,.2f", movimiento.getMonto()) + " MXN");
            agregarFila(tabla, "Estado", movimiento.getEstadoMovimiento());
            agregarFila(tabla, "Cliente", nombreCliente);
            agregarFila(tabla, "Cuenta (CLABE)", cuentaCliente != null ? cuentaCliente.getClabe() : "N/A");

            if ("Cancelada".equals(movimiento.getEstadoMovimiento())) {
                agregarFila(tabla, "Cancelado por (ejecutivo)",
                        movimiento.getCanceladoPor() != null ? movimiento.getCanceladoPor() : "N/A");
                agregarFila(tabla, "Fecha y hora de cancelación",
                        movimiento.getFechaCancelacion() != null ? movimiento.getFechaCancelacion().format(FMT_FECHA) : "N/A");
            }

            documento.add(tabla);
            piePagina(documento);
            documento.close();
            return salida.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar el comprobante PDF: " + e.getMessage(), e);
        }
    }

    private String tituloParaTipo(String tipo) {
        if (tipo == null) {
            return "Comprobante de movimiento";
        }
        return switch (tipo) {
            case "PAGO_SERVICIO" -> "Comprobante de pago de servicio";
            case "VUELO" -> "Comprobante de compra de vuelo";
            case "CAMION" -> "Comprobante de boleto de camión";
            case "HOTEL" -> "Comprobante de reservación de hotel";
            case "EVENTO" -> "Comprobante de boleto de evento";
            case "CREDITO" -> "Comprobante de crédito autorizado";
            case "CANCELACION_CREDITO" -> "Comprobante de cancelación de crédito";
            default -> "Comprobante de movimiento";
        };
    }

    // ---------------------------------------------------------------
    // Utilidades privadas
    // ---------------------------------------------------------------

    private Paragraph encabezado(String titulo) throws Exception {
        Paragraph banco = new Paragraph("Banco Bienestar", FUENTE_TITULO);
        Paragraph sub = new Paragraph(titulo, FUENTE_SUBTITULO);
        sub.setSpacingBefore(2);
        Paragraph contenedor = new Paragraph();
        contenedor.add(banco);
        contenedor.add(sub);
        return contenedor;
    }

    private void agregarFila(PdfPTable tabla, String etiqueta, String valor) {
        PdfPCell celdaEtiqueta = new PdfPCell(new Paragraph(etiqueta, FUENTE_ETIQUETA));
        celdaEtiqueta.setBorder(0);
        celdaEtiqueta.setPaddingBottom(8);

        PdfPCell celdaValor = new PdfPCell(new Paragraph(valor != null ? valor : "N/A", FUENTE_VALOR));
        celdaValor.setBorder(0);
        celdaValor.setPaddingBottom(8);
        celdaValor.setHorizontalAlignment(Element.ALIGN_LEFT);

        tabla.addCell(celdaEtiqueta);
        tabla.addCell(celdaValor);
    }

    private void piePagina(Document documento) throws Exception {
        Paragraph pie = new Paragraph(
                "Documento generado electrónicamente por Banco Bienestar. No requiere firma autógrafa.",
                FUENTE_SUBTITULO);
        pie.setSpacingBefore(30);
        documento.add(pie);
    }

    private Image imagenDesdeBase64(String base64) {
        try {
            String datos = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
            byte[] bytes = Base64.getDecoder().decode(datos);
            return Image.getInstance(bytes);
        } catch (Exception e) {
            return null;
        }
    }
}
