package com.bedolla.bancobienestar.controller;

import java.security.Principal;
import java.util.List;
import java.util.Set;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.bedolla.bancobienestar.entity.CuentaEntity;
import com.bedolla.bancobienestar.entity.MovimientosEntity;
import com.bedolla.bancobienestar.entity.Rol;
import com.bedolla.bancobienestar.entity.SolicitudCreditoEntity;
import com.bedolla.bancobienestar.entity.UsuarioEntity;
import com.bedolla.bancobienestar.repository.CuentaRepository;
import com.bedolla.bancobienestar.repository.MovimientoCuentaRepository;
import com.bedolla.bancobienestar.repository.SolicitudCreditoRepository;
import com.bedolla.bancobienestar.repository.UsuarioRepository;
import com.bedolla.bancobienestar.service.PdfService;

@Controller
public class MovimientoController {

    private static final Set<String> TIPOS_CARGO_SIMPLE =
            Set.of("PAGO_SERVICIO", "VUELO", "CAMION", "HOTEL", "EVENTO", "CREDITO", "CANCELACION_CREDITO");

    private final UsuarioRepository usuarioRepository;
    private final CuentaRepository cuentaRepository;
    private final MovimientoCuentaRepository movimientoRepository;
    private final SolicitudCreditoRepository solicitudCreditoRepository;
    private final PdfService pdfService;

    public MovimientoController(UsuarioRepository usuarioRepository,
                                 CuentaRepository cuentaRepository,
                                 MovimientoCuentaRepository movimientoRepository,
                                 SolicitudCreditoRepository solicitudCreditoRepository,
                                 PdfService pdfService) {
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.solicitudCreditoRepository = solicitudCreditoRepository;
        this.pdfService = pdfService;
    }

    @GetMapping("/movimientos")
    public String movimientos(Model modelo, Principal principal) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);

        String clabe = null;
        if (usuario != null) {
            List<CuentaEntity> cuentas = cuentaRepository.findByUsuario(usuario);
            if (!cuentas.isEmpty()) {
                clabe = cuentas.get(0).getClabe();
                modelo.addAttribute("cuentaClabePropia", clabe);
            }
        }

        List<MovimientosEntity> historial = clabe != null
                ? movimientoRepository.findByCuentaOrigenOrCuentaDestinoOrderByFechaDesc(clabe, clabe)
                : List.of();

        modelo.addAttribute("movimientos", historial);
        modelo.addAttribute("miClabe", clabe);

        return "movimientos";
    }

    /**
     * Punto de entrada único para ver el comprobante en PDF de cualquier
     * movimiento del historial (transferencia, abono a crédito, crédito
     * autorizado, cancelaciones, pago de servicio, vuelo, camión, hotel o
     * evento). Detecta el tipo y arma el PDF más adecuado.
     */
    @GetMapping("/movimientos/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdfMovimiento(@PathVariable Long id, Principal principal) {
        MovimientosEntity movimiento = movimientoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El movimiento no existe."));

        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        List<CuentaEntity> cuentasPropias = usuario != null ? cuentaRepository.findByUsuario(usuario) : List.of();
        boolean esParticipante = cuentasPropias.stream()
                .anyMatch(c -> c.getClabe().equals(movimiento.getCuentaOrigen())
                        || c.getClabe().equals(movimiento.getCuentaDestino()));
        boolean esAdmin = usuario != null && usuario.getRol() == Rol.ADMIN;

        if (!esParticipante && !esAdmin) {
            return ResponseEntity.status(403).build();
        }

        CuentaEntity cuentaOrigen = cuentaRepository.findByClabe(movimiento.getCuentaOrigen()).orElse(null);
        CuentaEntity cuentaDestino = cuentaRepository.findByClabe(movimiento.getCuentaDestino()).orElse(null);

        byte[] pdf;
        String tipo = movimiento.getTipo();

        if ("TRANSFERENCIA".equals(tipo)) {
            pdf = "Cancelada".equals(movimiento.getEstadoMovimiento())
                    ? pdfService.generarPdfCancelacionTransferencia(movimiento, cuentaOrigen, cuentaDestino)
                    : pdfService.generarPdfComprobanteTransferencia(movimiento, cuentaOrigen, cuentaDestino);
        } else if ("ABONO_CREDITO".equals(tipo)) {
            SolicitudCreditoEntity solicitud = movimiento.getSolicitudCreditoId() != null
                    ? solicitudCreditoRepository.findByIdConUsuario(movimiento.getSolicitudCreditoId()).orElse(null)
                    : null;
            pdf = pdfService.generarPdfAbonoCredito(solicitud, movimiento);
        } else if (TIPOS_CARGO_SIMPLE.contains(tipo)) {
            // Cuenta real del cliente: la que sí existe entre origen/destino
            // (la otra es un concepto virtual como "CREDITO-BANCO" o un servicio).
            CuentaEntity cuentaCliente = cuentaOrigen != null ? cuentaOrigen : cuentaDestino;
            pdf = pdfService.generarPdfReciboMovimiento(movimiento, cuentaCliente);
        } else {
            CuentaEntity cuentaCliente = cuentaOrigen != null ? cuentaOrigen : cuentaDestino;
            pdf = pdfService.generarPdfReciboMovimiento(movimiento, cuentaCliente);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename("comprobante-" + id + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}