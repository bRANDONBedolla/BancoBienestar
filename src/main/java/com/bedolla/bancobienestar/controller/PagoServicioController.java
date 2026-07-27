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
public class PagoServicioController {

    private static final Locale MX = new Locale("es", "MX");

    private final UsuarioRepository usuarioRepository;
    private final CuentaRepository cuentaRepository;
    private final MovimientoCuentaRepository movimientoRepository;

    public PagoServicioController(UsuarioRepository usuarioRepository,
                                   CuentaRepository cuentaRepository,
                                   MovimientoCuentaRepository movimientoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
    }

    @GetMapping("/pagar-servicio")
    public String pagarServicioForm(Model modelo, Principal principal) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        cargarDatosPropios(modelo, usuario);
        return "pagar-servicio";
    }

    @PostMapping("/pagar-servicio")
    public String pagarServicioEnviar(@RequestParam String servicio,
                                       @RequestParam String referencia,
                                       @RequestParam Double monto,
                                       Principal principal,
                                       Model modelo) {

        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        String error = null;
        String exito = null;

        List<CuentaEntity> cuentasPropias = usuario != null ? cuentaRepository.findByUsuario(usuario) : List.of();

        if (cuentasPropias.isEmpty()) {
            error = "No tienes una cuenta activa para pagar servicios.";
        } else if (monto == null || monto <= 0) {
            error = "El monto debe ser mayor a $0.00";
        } else {
            CuentaEntity cuenta = cuentasPropias.get(0);

            if (cuenta.getSaldo() < monto) {
                error = "Saldo insuficiente para realizar el pago.";
            } else {
                cuenta.setSaldo(cuenta.getSaldo() - monto);
                cuentaRepository.save(cuenta);

                MovimientosEntity movimiento = new MovimientosEntity();
                movimiento.setCuentaOrigen(cuenta.getClabe());
                movimiento.setCuentaDestino("SERVICIO:" + servicio);
                movimiento.setMonto(monto);
                movimiento.setTipo("PAGO_SERVICIO");
                movimiento.setCategoria(categoriaDeServicio(servicio));
                movimiento.setDescripcion(servicio + " - Ref: " + referencia);
                movimiento.setFecha(LocalDateTime.now());
                movimiento.setEstadoMovimiento("Completado");
                movimientoRepository.save(movimiento);

                exito = String.format(MX, "Pagaste $%,.2f de %s correctamente.", monto, servicio);
            }
        }

        cargarDatosPropios(modelo, usuario);
        modelo.addAttribute("error", error);
        modelo.addAttribute("exito", exito);
        return "pagar-servicio";
    }

    private String categoriaDeServicio(String servicio) {
        if (servicio == null) return "OCIO_OTROS";
        return switch (servicio) {
            case "Renta / Alquiler" -> "VIVIENDA";
            case "CFE (Luz)", "Agua potable", "Gas natural" -> "VIVIENDA";
            case "Supermercado / Alimentos" -> "ALIMENTACION";
            case "Telmex / Internet", "Telcel", "Tarjeta de crédito" -> "OCIO_OTROS";
            default -> "OCIO_OTROS";
        };
    }

    private void cargarDatosPropios(Model modelo, UsuarioEntity usuario) {
        if (usuario == null) return;

        List<CuentaEntity> cuentas = cuentaRepository.findByUsuario(usuario);
        double saldo = cuentas.isEmpty() ? 0.0 : cuentas.get(0).getSaldo();
        modelo.addAttribute("saldoPropio", String.format(MX, "%,.2f", saldo));

        if (!cuentas.isEmpty()) {
            String clabe = cuentas.get(0).getClabe();
            List<MovimientosEntity> pagos =
                    movimientoRepository.findByCuentaOrigenOrCuentaDestinoOrderByFechaDesc(clabe, clabe)
                            .stream().filter(m -> "PAGO_SERVICIO".equals(m.getTipo())).limit(8).toList();
            modelo.addAttribute("historialPagos", pagos);
        }
    }
}