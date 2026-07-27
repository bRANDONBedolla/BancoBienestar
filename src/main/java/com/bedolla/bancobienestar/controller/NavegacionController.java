package com.bedolla.bancobienestar.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.bedolla.bancobienestar.entity.CuentaEntity;
import com.bedolla.bancobienestar.entity.MovimientosEntity;
import com.bedolla.bancobienestar.entity.Rol;
import com.bedolla.bancobienestar.entity.SolicitudCreditoEntity;
import com.bedolla.bancobienestar.entity.UsuarioEntity;
import com.bedolla.bancobienestar.repository.CuentaRepository;
import com.bedolla.bancobienestar.repository.MovimientoCuentaRepository;
import com.bedolla.bancobienestar.repository.SolicitudCreditoRepository;
import com.bedolla.bancobienestar.repository.UsuarioRepository;
import com.bedolla.bancobienestar.service.BancaService;
import com.bedolla.bancobienestar.service.PdfService;

@Controller
public class NavegacionController {

    private final UsuarioRepository usuarioRepository;
    private final SolicitudCreditoRepository solicitudCreditoRepository;
    private final CuentaRepository cuentaRepository;
    private final MovimientoCuentaRepository movimientoCuentaRepository;
    private final BancaService bancaService;
    private final PdfService pdfService;

    private static final Locale MX_LOCALE = new Locale("es", "MX");
    private static final DateTimeFormatter FMT_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", MX_LOCALE);
    private static final DateTimeFormatter FMT_FECHA_DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy", MX_LOCALE);

    public NavegacionController(UsuarioRepository usuarioRepository,
                                 SolicitudCreditoRepository solicitudCreditoRepository,
                                 CuentaRepository cuentaRepository,
                                 MovimientoCuentaRepository movimientoCuentaRepository,
                                 BancaService bancaService,
                                 PdfService pdfService) {
        this.usuarioRepository = usuarioRepository;
        this.solicitudCreditoRepository = solicitudCreditoRepository;
        this.cuentaRepository = cuentaRepository;
        this.movimientoCuentaRepository = movimientoCuentaRepository;
        this.bancaService = bancaService;
        this.pdfService = pdfService;
    }

    // ===== Solicitar credito (cliente) =====

    @GetMapping("/solicitar-credito")
    public String solicitarCreditoForm(Model modelo, Principal principal) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        if (usuario != null) {
            List<SolicitudCreditoEntity> misSolicitudes =
                    solicitudCreditoRepository.findByUsuarioOrderByFechaDesc(usuario);

            List<SolicitudClienteVista> misSolicitudesVista = misSolicitudes.stream()
                    .map(this::construirSolicitudClienteVista)
                    .toList();

            modelo.addAttribute("misSolicitudes", misSolicitudesVista);
        }
        return "solicitar-credito";
    }

    /** Arma la vista enriquecida de una solicitud: montos, próximo pago e historial de abonos. */
    private SolicitudClienteVista construirSolicitudClienteVista(SolicitudCreditoEntity s) {
        SolicitudClienteVista v = new SolicitudClienteVista();
        v.id = s.getId();
        v.montoSolicitado = s.getMontoSolicitado();
        v.montoTexto = formatearMonto(s.getMontoSolicitado());
        v.fecha = s.getFecha();
        v.firmaBase64 = s.getFirmaBase64();
        v.estado = s.getEstado();
        v.ejecutivoAutorizo = s.getEjecutivoAutorizo();
        v.fechaPrimerPago = s.getFechaPrimerPago();
        v.observaciones = s.getObservaciones();
        v.canceladoPor = s.getCanceladoPor();

        double pagado = s.getMontoPagado() != null ? s.getMontoPagado() : 0.0;
        v.montoPagadoTexto = formatearMonto(pagado);
        v.saldoPendienteTexto = formatearMonto(s.getSaldoPendiente());
        v.fechaProximoPagoTexto = s.getFechaProximoPago() != null ? s.getFechaProximoPago().format(FMT_FECHA_DIA) : null;
        v.puedePagar = "APROBADA".equals(s.getEstado()) && s.getSaldoPendiente() > 0.0;

        if ("APROBADA".equals(s.getEstado()) || "PAGADA".equals(s.getEstado())) {
            List<MovimientosEntity> abonos = movimientoCuentaRepository.findBySolicitudCreditoIdOrderByFechaDesc(s.getId());
            v.historialPagos = abonos.stream().map(m -> {
                PagoVista p = new PagoVista();
                p.id = m.getId();
                p.fechaTexto = m.getFecha() != null ? m.getFecha().format(FMT_FECHA_HORA) : "N/A";
                p.montoTexto = formatearMonto(m.getMonto());
                return p;
            }).toList();
        } else {
            v.historialPagos = List.of();
        }

        return v;
    }

    private String formatearMonto(double valor) {
        return String.format(MX_LOCALE, "%,.2f", valor);
    }

    // ===== Pagar / abonar a un crédito ya aprobado (cliente) =====

    @PostMapping("/solicitar-credito/{id}/pagar")
    public String pagarCredito(@PathVariable Long id,
                                @RequestParam Double montoPago,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        try {
            MovimientosEntity abono = bancaService.abonarCredito(principal.getName(), id, montoPago);
            redirectAttributes.addFlashAttribute("pagoRealizado", abono.getId());
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/solicitar-credito";
    }

    @GetMapping("/solicitar-credito/pago/{movimientoId}/pdf")
    public ResponseEntity<byte[]> descargarPdfAbonoCredito(@PathVariable Long movimientoId, Principal principal) {
        MovimientosEntity abono = movimientoCuentaRepository.findById(movimientoId)
                .orElseThrow(() -> new IllegalArgumentException("El comprobante no existe."));

        SolicitudCreditoEntity solicitud = abono.getSolicitudCreditoId() != null
                ? solicitudCreditoRepository.findByIdConUsuario(abono.getSolicitudCreditoId()).orElse(null)
                : null;

        boolean esDueño = solicitud != null && solicitud.getUsuario() != null
                && solicitud.getUsuario().getUsername().equalsIgnoreCase(principal.getName());
        boolean esAdmin = usuarioRepository.findByUsername(principal.getName())
                .map(u -> u.getRol() == Rol.ADMIN).orElse(false);

        if (!esDueño && !esAdmin) {
            return ResponseEntity.status(403).build();
        }

        byte[] pdf = pdfService.generarPdfAbonoCredito(solicitud, abono);
        return construirRespuestaPdf(pdf, "abono-credito-" + movimientoId + ".pdf");
    }

    @PostMapping("/solicitar-credito")
    public String solicitarCreditoEnviar(@RequestParam Double montoSolicitado,
                                          @RequestParam(required = false) String firmaBase64,
                                          Principal principal,
                                          RedirectAttributes redirectAttributes) {
        try {
            bancaService.guardarSolicitudCredito(principal.getName(), montoSolicitado, firmaBase64);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/solicitar-credito";
        }

        return "redirect:/solicitar-credito?enviada";
    }

    @GetMapping("/solicitar-credito/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdfSolicitud(@PathVariable Long id, Principal principal) {
        SolicitudCreditoEntity solicitud = solicitudCreditoRepository.findByIdConUsuario(id)
                .orElseThrow(() -> new IllegalArgumentException("La solicitud no existe."));

        boolean esDueño = solicitud.getUsuario() != null
                && solicitud.getUsuario().getUsername().equalsIgnoreCase(principal.getName());
        boolean esAdmin = usuarioRepository.findByUsername(principal.getName())
                .map(u -> u.getRol() == Rol.ADMIN).orElse(false);

        if (!esDueño && !esAdmin) {
            return ResponseEntity.status(403).build();
        }

        byte[] pdf = pdfService.generarPdfPrestamo(solicitud);
        return construirRespuestaPdf(pdf, "prestamo-" + id + ".pdf");
    }

    // ===== Panel ejecutivo (solo ADMIN, protegido en SecurityConfig) =====

    @GetMapping("/panel-ejecutivo")
    public String panelEjecutivo(Model modelo, Principal principal) {
        UsuarioEntity admin = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        modelo.addAttribute("nombreAdmin", admin != null ? admin.getNombre() : principal.getName());
        modelo.addAttribute("totalUsuarios", usuarioRepository.count());

        List<SolicitudCreditoEntity> solicitudes = solicitudCreditoRepository.findAllByOrderByFechaDesc();

        DateTimeFormatter formateadorFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es", "MX"));
        List<SolicitudVista> solicitudesVista = solicitudes.stream().map(s -> {
            SolicitudVista v = new SolicitudVista();
            v.id = s.getId();
            v.nombreCliente = s.getUsuario() != null ? s.getUsuario().getNombre() : "N/A";
            v.montoTexto = String.format(new Locale("es", "MX"), "%,.2f", s.getMontoSolicitado());
            v.fechaTexto = s.getFecha() != null ? s.getFecha().format(formateadorFecha) : "N/A";
            v.estado = s.getEstado();
            v.firma = s.getFirmaBase64();
            v.ejecutivoAutorizo = s.getEjecutivoAutorizo();
            v.fechaAprobacionTexto = s.getFechaAprobacion() != null
                    ? s.getFechaAprobacion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "MX")))
                    : null;
            v.fechaPrimerPagoTexto = s.getFechaPrimerPago() != null
                    ? s.getFechaPrimerPago().format(formateadorFecha)
                    : null;
            v.observaciones = s.getObservaciones();
            v.canceladoPor = s.getCanceladoPor();
            v.fechaCancelacionTexto = s.getFechaCancelacion() != null
                    ? s.getFechaCancelacion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "MX")))
                    : null;

            if ("APROBADA".equals(s.getEstado()) || "PAGADA".equals(s.getEstado())) {
                double pagado = s.getMontoPagado() != null ? s.getMontoPagado() : 0.0;
                v.montoPagadoTexto = formatearMonto(pagado);
                v.saldoPendienteTexto = formatearMonto(s.getSaldoPendiente());
                v.fechaProximoPagoTexto = s.getFechaProximoPago() != null
                        ? s.getFechaProximoPago().format(formateadorFecha)
                        : null;
            }
            return v;
        }).toList();

        long pendientes = solicitudes.stream().filter(s -> "PENDIENTE".equals(s.getEstado())).count();
        long aprobadas = solicitudes.stream().filter(s -> "APROBADA".equals(s.getEstado())).count();
        long rechazadas = solicitudes.stream().filter(s -> "RECHAZADA".equals(s.getEstado())).count();

        modelo.addAttribute("solicitudes", solicitudesVista);
        modelo.addAttribute("totalSolicitudes", solicitudes.size());
        modelo.addAttribute("solicitudesPendientes", pendientes);
        modelo.addAttribute("solicitudesAprobadas", aprobadas);
        modelo.addAttribute("solicitudesRechazadas", rechazadas);

        // ==== Clientes registrados (para que el ejecutivo vea las altas y sus saldos) ====
        DateTimeFormatter formateadorFechaHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("es", "MX"));

        List<ClienteRegistradoVista> clientesRegistrados = usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() == Rol.CLIENTE)
                .sorted((a, b) -> {
                    if (a.getFechaRegistro() == null || b.getFechaRegistro() == null) return 0;
                    return b.getFechaRegistro().compareTo(a.getFechaRegistro());
                })
                .map(u -> {
                    ClienteRegistradoVista v = new ClienteRegistradoVista();
                    v.id = u.getId();
                    v.nombre = u.getNombre();
                    v.username = u.getUsername();
                    v.profesion = u.getProfesion();
                    v.fotoBase64 = u.getFotoBase64();
                    v.fechaRegistro = u.getFechaRegistro() != null
                            ? u.getFechaRegistro().format(formateadorFechaHora)
                            : "N/A";

                    List<CuentaEntity> cuentas = cuentaRepository.findByUsuario(u);
                    v.clabe = (cuentas != null && !cuentas.isEmpty()) ? cuentas.get(0).getClabe() : "N/A";
                    v.saldoTexto = (cuentas != null && !cuentas.isEmpty())
                            ? String.format(new Locale("es", "MX"), "%,.2f", cuentas.get(0).getSaldo())
                            : "0.00";
                    return v;
                })
                .toList();

        modelo.addAttribute("clientesRegistrados", clientesRegistrados);
        modelo.addAttribute("totalClientesRegistrados", clientesRegistrados.size());

        return "panel-ejecutivo";
    }

    // ===== Alta de clientes (solo el ejecutivo puede crear cuentas) =====

    @PostMapping("/panel-ejecutivo/clientes")
    public String crearCliente(@RequestParam String nombre,
                                @RequestParam String username,
                                @RequestParam String password,
                                @RequestParam(required = false) Double saldoInicial,
                                RedirectAttributes redirectAttributes) {
        try {
            bancaService.crearClienteConCuenta(nombre, username, password, saldoInicial);
            redirectAttributes.addFlashAttribute("exitoCliente",
                    "Cliente \"" + nombre + "\" registrado y cuenta aperturada correctamente.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorCliente", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    // ===== Aceptar / rechazar solicitudes de crédito (abona o no el dinero real) =====

    @PostMapping("/panel-ejecutivo/solicitudes/{id}/aprobar")
    public String aprobarSolicitud(@PathVariable Long id,
                                    @RequestParam(required = false) String observaciones,
                                    Principal principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            String ejecutivo = usuarioRepository.findByUsername(principal.getName())
                    .map(UsuarioEntity::getNombre).orElse(principal.getName());
            bancaService.resolverSolicitudCredito(id, true, ejecutivo, observaciones);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorCliente", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/panel-ejecutivo/solicitudes/{id}/rechazar")
    public String rechazarSolicitud(@PathVariable Long id,
                                     @RequestParam(required = false) String observaciones,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
        try {
            String ejecutivo = usuarioRepository.findByUsername(principal.getName())
                    .map(UsuarioEntity::getNombre).orElse(principal.getName());
            bancaService.resolverSolicitudCredito(id, false, ejecutivo, observaciones);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorCliente", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/panel-ejecutivo/solicitudes/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdfSolicitudAdmin(@PathVariable Long id) {
        SolicitudCreditoEntity solicitud = solicitudCreditoRepository.findByIdConUsuario(id)
                .orElseThrow(() -> new IllegalArgumentException("La solicitud no existe."));
        byte[] pdf = pdfService.generarPdfPrestamo(solicitud);
        return construirRespuestaPdf(pdf, "prestamo-" + id + ".pdf");
    }

    @PostMapping("/panel-ejecutivo/solicitudes/{id}/cancelar")
    public String cancelarSolicitud(@PathVariable Long id,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
        try {
            String ejecutivo = usuarioRepository.findByUsername(principal.getName())
                    .map(UsuarioEntity::getNombre).orElse(principal.getName());
            bancaService.cancelarSolicitudCredito(id, ejecutivo);
            redirectAttributes.addFlashAttribute("exitoCliente", "El crédito fue cancelado y el monto retirado con éxito.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorCliente",
                    e.getMessage() != null ? e.getMessage() : "No se pudo cancelar el crédito.");
        }
        return "redirect:/dashboard";
    }

    private ResponseEntity<byte[]> construirRespuestaPdf(byte[] pdf, String nombreArchivo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename(nombreArchivo).build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    public static class ClienteRegistradoVista {
        public Long id;
        public String nombre;
        public String username;
        public String profesion;
        public String fotoBase64;
        public String clabe;
        public String fechaRegistro;
        public String saldoTexto;

        public Long getId() { return id; }
        public String getNombre() { return nombre; }
        public String getUsername() { return username; }
        public String getProfesion() { return profesion; }
        public String getFotoBase64() { return fotoBase64; }
        public String getClabe() { return clabe; }
        public String getFechaRegistro() { return fechaRegistro; }
        public String getSaldoTexto() { return saldoTexto; }
    }

    public static class SolicitudVista {
        public Long id;
        public String nombreCliente;
        public String montoTexto;
        public String fechaTexto;
        public String estado;
        public String firma;
        public String ejecutivoAutorizo;
        public String fechaAprobacionTexto;
        public String fechaPrimerPagoTexto;
        public String observaciones;
        public String canceladoPor;
        public String fechaCancelacionTexto;
        public String montoPagadoTexto;
        public String saldoPendienteTexto;
        public String fechaProximoPagoTexto;

        public Long getId() { return id; }
        public String getNombreCliente() { return nombreCliente; }
        public String getMontoTexto() { return montoTexto; }
        public String getFechaTexto() { return fechaTexto; }
        public String getEstado() { return estado; }
        public String getFirma() { return firma; }
        public String getEjecutivoAutorizo() { return ejecutivoAutorizo; }
        public String getFechaAprobacionTexto() { return fechaAprobacionTexto; }
        public String getFechaPrimerPagoTexto() { return fechaPrimerPagoTexto; }
        public String getObservaciones() { return observaciones; }
        public String getCanceladoPor() { return canceladoPor; }
        public String getFechaCancelacionTexto() { return fechaCancelacionTexto; }
        public String getMontoPagadoTexto() { return montoPagadoTexto; }
        public String getSaldoPendienteTexto() { return saldoPendienteTexto; }
        public String getFechaProximoPagoTexto() { return fechaProximoPagoTexto; }
    }

    /** Vista de "mis solicitudes" en /solicitar-credito, con montos, próximo pago e historial de abonos. */
    public static class SolicitudClienteVista {
        public Long id;
        public Double montoSolicitado;
        public String montoTexto;
        public LocalDateTime fecha;
        public String firmaBase64;
        public String estado;
        public String ejecutivoAutorizo;
        public LocalDate fechaPrimerPago;
        public String observaciones;
        public String canceladoPor;

        public String montoPagadoTexto;
        public String saldoPendienteTexto;
        public String fechaProximoPagoTexto;
        public boolean puedePagar;
        public List<PagoVista> historialPagos;

        public Long getId() { return id; }
        public Double getMontoSolicitado() { return montoSolicitado; }
        public String getMontoTexto() { return montoTexto; }
        public LocalDateTime getFecha() { return fecha; }
        public String getFirmaBase64() { return firmaBase64; }
        public String getEstado() { return estado; }
        public String getEjecutivoAutorizo() { return ejecutivoAutorizo; }
        public LocalDate getFechaPrimerPago() { return fechaPrimerPago; }
        public String getObservaciones() { return observaciones; }
        public String getCanceladoPor() { return canceladoPor; }
        public String getMontoPagadoTexto() { return montoPagadoTexto; }
        public String getSaldoPendienteTexto() { return saldoPendienteTexto; }
        public String getFechaProximoPagoTexto() { return fechaProximoPagoTexto; }
        public boolean isPuedePagar() { return puedePagar; }
        public List<PagoVista> getHistorialPagos() { return historialPagos; }
    }

    /** Una fila de la tablita de abonos de un crédito. */
    public static class PagoVista {
        public Long id;
        public String fechaTexto;
        public String montoTexto;

        public Long getId() { return id; }
        public String getFechaTexto() { return fechaTexto; }
        public String getMontoTexto() { return montoTexto; }
    }
}
