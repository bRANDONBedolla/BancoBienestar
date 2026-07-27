package com.bedolla.bancobienestar.controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.bedolla.bancobienestar.entity.CuentaEntity;
import com.bedolla.bancobienestar.entity.MovimientosEntity;
import com.bedolla.bancobienestar.entity.UsuarioEntity;
import com.bedolla.bancobienestar.repository.CuentaRepository;
import com.bedolla.bancobienestar.repository.MovimientoCuentaRepository;
import com.bedolla.bancobienestar.repository.UsuarioRepository;

@Controller
public class RecargaController {

    private static final Locale MX = new Locale("es", "MX");
    private static final double MONTO_MAXIMO = 10000.0;

    private final UsuarioRepository usuarioRepository;
    private final CuentaRepository cuentaRepository;
    private final MovimientoCuentaRepository movimientoRepository;

    public RecargaController(UsuarioRepository usuarioRepository,
                              CuentaRepository cuentaRepository,
                              MovimientoCuentaRepository movimientoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
    }

    @GetMapping("/recargar")
    public String recargar(Model modelo, Principal principal) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        cargarDatosPropios(modelo, usuario);
        return "recargar";
    }

    @PostMapping("/recargar")
    public String procesarRecarga(@RequestParam Double monto,
                                   @RequestParam String metodo,
                                   @RequestParam(required = false) String referencia,
                                   Principal principal,
                                   Model modelo) {

        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        String error = null;
        String exito = null;

        List<CuentaEntity> cuentasPropias = usuario != null ? cuentaRepository.findByUsuario(usuario) : List.of();

        if (cuentasPropias.isEmpty()) {
            error = "No tienes una cuenta activa para recargar.";
        } else if (monto == null || monto <= 0) {
            error = "El monto debe ser mayor a $0.00";
        } else if (monto > MONTO_MAXIMO) {
            error = String.format(MX, "El monto máximo por recarga es de $%,.2f MXN.", MONTO_MAXIMO);
        } else if (metodo == null || metodo.isBlank()) {
            error = "Selecciona un método de recarga.";
        } else {
            CuentaEntity cuenta = cuentasPropias.get(0);

            cuenta.setSaldo(cuenta.getSaldo() + monto);
            cuentaRepository.save(cuenta);

            MovimientosEntity movimiento = new MovimientosEntity();
            movimiento.setCuentaOrigen(nombreMetodo(metodo));
            movimiento.setCuentaDestino(cuenta.getClabe());
            movimiento.setMonto(monto);
            movimiento.setTipo("RECARGA");
            movimiento.setCategoria("RECARGA");
            movimiento.setDescripcion(
                    "Recarga vía " + nombreMetodo(metodo)
                            + ((referencia == null || referencia.isBlank()) ? "" : " (Ref. " + referencia + ")"));
            movimiento.setFecha(LocalDateTime.now());
            movimiento.setEstadoMovimiento("Completado");
            movimientoRepository.save(movimiento);

            exito = String.format(MX, "Recargaste $%,.2f a tu cuenta correctamente.", monto);
        }

        cargarDatosPropios(modelo, usuario);
        modelo.addAttribute("error", error);
        modelo.addAttribute("exito", exito);
        return "recargar";
    }

    private String nombreMetodo(String metodo) {
        return switch (metodo) {
            case "TARJETA" -> "Tarjeta de débito/crédito";
            case "OXXO" -> "Efectivo en OXXO";
            case "SUCURSAL" -> "Efectivo en sucursal";
            case "SPEI" -> "Transferencia SPEI";
            default -> metodo;
        };
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
                            .stream().filter(m -> "RECARGA".equals(m.getTipo())).limit(8).toList();
            modelo.addAttribute("historialRecargas", historial);
        }
    }
}
