package com.bedolla.bancobienestar.controller;

import java.security.Principal;
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

import com.bedolla.bancobienestar.entity.CuentaEntity;
import com.bedolla.bancobienestar.entity.MovimientosEntity;
import com.bedolla.bancobienestar.entity.UsuarioEntity;
import com.bedolla.bancobienestar.exception.FondosInsuficientesException;
import com.bedolla.bancobienestar.repository.CuentaRepository;
import com.bedolla.bancobienestar.repository.MovimientoCuentaRepository;
import com.bedolla.bancobienestar.repository.UsuarioRepository;
import com.bedolla.bancobienestar.service.BancaService;
import com.bedolla.bancobienestar.service.PdfService;

@Controller
public class TransferenciaController {

    private static final Locale MX = new Locale("es", "MX");
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", MX);

    private final UsuarioRepository usuarioRepository;
    private final CuentaRepository cuentaRepository;
    private final MovimientoCuentaRepository movimientoRepository;
    private final BancaService bancaService;
    private final PdfService pdfService;

    public TransferenciaController(UsuarioRepository usuarioRepository,
                                    CuentaRepository cuentaRepository,
                                    MovimientoCuentaRepository movimientoRepository,
                                    BancaService bancaService,
                                    PdfService pdfService) {
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.bancaService = bancaService;
        this.pdfService = pdfService;
    }

    @GetMapping("/transferencias")
    public String transferencias(Model modelo, Principal principal) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        cargarDatosPropios(modelo, usuario);
        return "transferencias";
    }

    @PostMapping("/transferencias")
    public String procesarTransferencia(@RequestParam String clabeDestino,
                                         @RequestParam Double monto,
                                         @RequestParam(required = false) String concepto,
                                         @RequestParam(required = false) String firmaBase64,
                                         Principal principal,
                                         Model modelo) {

        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        String error = null;
        String exito = null;

        List<CuentaEntity> cuentasPropias = usuario != null ? cuentaRepository.findByUsuario(usuario) : List.of();

        if (cuentasPropias.isEmpty()) {
            error = "No tienes una cuenta activa para transferir.";
        } else if (monto == null || monto <= 0) {
            error = "El monto debe ser mayor a $0.00";
        } else if (firmaBase64 == null || firmaBase64.isBlank()) {
            error = "Debes firmar para autorizar la transferencia.";
        } else {
            CuentaEntity cuentaOrigen = cuentasPropias.get(0);

            if (clabeDestino == null || clabeDestino.equals(cuentaOrigen.getClabe())) {
                error = "No puedes transferir a tu propia cuenta.";
            } else if (cuentaRepository.findByClabe(clabeDestino).isEmpty()) {
                error = "No existe ninguna cuenta con esa CLABE.";
            } else {
                try {
                    bancaService.transferirDesdeUsuario(principal.getName(), clabeDestino, monto, concepto, firmaBase64);

                    String nombreDestino = cuentaRepository.findByClabe(clabeDestino)
                            .map(c -> c.getUsuario().getNombre())
                            .orElse("la cuenta destino");
                    exito = String.format(MX, "Transferiste $%,.2f a %s correctamente.", monto, nombreDestino);
                } catch (FondosInsuficientesException e) {
                    error = "Saldo insuficiente para realizar la transferencia.";
                } catch (RuntimeException e) {
                    error = e.getMessage() != null ? e.getMessage() : "No se pudo completar la transferencia.";
                }
            }
        }

        cargarDatosPropios(modelo, usuario);
        modelo.addAttribute("error", error);
        modelo.addAttribute("exito", exito);
        return "transferencias";
    }

    @GetMapping("/transferencias/comprobante/{id}/pdf")
    public ResponseEntity<byte[]> descargarComprobante(@PathVariable Long id, Principal principal) {
        MovimientosEntity movimiento = movimientoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El movimiento no existe."));

        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        List<CuentaEntity> cuentasPropias = usuario != null ? cuentaRepository.findByUsuario(usuario) : List.of();
        boolean esParticipante = cuentasPropias.stream()
                .anyMatch(c -> c.getClabe().equals(movimiento.getCuentaOrigen())
                        || c.getClabe().equals(movimiento.getCuentaDestino()));
        boolean esAdmin = usuario != null && usuario.getRol() == com.bedolla.bancobienestar.entity.Rol.ADMIN;

        if (!esParticipante && !esAdmin) {
            return ResponseEntity.status(403).build();
        }

        CuentaEntity cuentaOrigen = cuentaRepository.findByClabe(movimiento.getCuentaOrigen()).orElse(null);
        CuentaEntity cuentaDestino = cuentaRepository.findByClabe(movimiento.getCuentaDestino()).orElse(null);

        byte[] pdf = "Cancelada".equals(movimiento.getEstadoMovimiento())
                ? pdfService.generarPdfCancelacionTransferencia(movimiento, cuentaOrigen, cuentaDestino)
                : pdfService.generarPdfComprobanteTransferencia(movimiento, cuentaOrigen, cuentaDestino);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename("comprobante-" + id + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    private void cargarDatosPropios(Model modelo, UsuarioEntity usuario) {
        if (usuario == null) return;

        List<CuentaEntity> cuentas = cuentaRepository.findByUsuario(usuario);
        String clabe = "N/A";
        double saldo = 0.0;

        if (!cuentas.isEmpty()) {
            clabe = cuentas.get(0).getClabe();
            saldo = cuentas.get(0).getSaldo();
        }

        modelo.addAttribute("cuentaClabePropia", clabe);
        modelo.addAttribute("saldoPropio", String.format(MX, "%,.2f", saldo));

        if (!"N/A".equals(clabe)) {
            List<MovimientosEntity> historial =
                    movimientoRepository.findByCuentaOrigenOrCuentaDestinoOrderByFechaDesc(clabe, clabe)
                            .stream().filter(m -> "TRANSFERENCIA".equals(m.getTipo())).limit(8).toList();
            modelo.addAttribute("historialTransferencias", historial);
            modelo.addAttribute("miClabeParaVista", clabe);
        }
    }
}