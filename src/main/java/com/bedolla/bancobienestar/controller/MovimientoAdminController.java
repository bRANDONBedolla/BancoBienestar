package com.bedolla.bancobienestar.controller;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.bedolla.bancobienestar.entity.CuentaEntity;
import com.bedolla.bancobienestar.entity.MovimientosEntity;
import com.bedolla.bancobienestar.entity.UsuarioEntity;
import com.bedolla.bancobienestar.repository.CuentaRepository;
import com.bedolla.bancobienestar.repository.MovimientoCuentaRepository;
import com.bedolla.bancobienestar.repository.UsuarioRepository;
import com.bedolla.bancobienestar.service.BancaService;

/**
 * Panel de movimientos del ejecutivo: ver todo el historial de la banca,
 * consultar el "mini perfil" (foto, nombre y rol) de origen/destino,
 * cancelar transferencias (revierte el dinero) y eliminar registros del
 * historial. Todo bajo /panel-ejecutivo/** (protegido con hasRole(ADMIN)
 * en SecurityConfig).
 */
@Controller
public class MovimientoAdminController {

    private static final Locale MX = new Locale("es", "MX");
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", MX);

    private final UsuarioRepository usuarioRepository;
    private final CuentaRepository cuentaRepository;
    private final MovimientoCuentaRepository movimientoRepository;
    private final BancaService bancaService;

    public MovimientoAdminController(UsuarioRepository usuarioRepository,
                                      CuentaRepository cuentaRepository,
                                      MovimientoCuentaRepository movimientoRepository,
                                      BancaService bancaService) {
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.bancaService = bancaService;
    }

    @GetMapping("/panel-ejecutivo/movimientos")
    public String movimientosAdmin(Model modelo) {
        // Mapa clabe -> usuario dueño de la cuenta (para nombre, rol y foto)
        Map<String, UsuarioEntity> usuarioPorClabe = new HashMap<>();
        for (UsuarioEntity u : usuarioRepository.findAll()) {
            List<CuentaEntity> cuentas = cuentaRepository.findByUsuario(u);
            if (cuentas != null) {
                for (CuentaEntity c : cuentas) {
                    if (c.getClabe() != null) {
                        usuarioPorClabe.put(c.getClabe(), u);
                    }
                }
            }
        }

        List<MovimientosEntity> todos = movimientoRepository.findAll();
        todos.sort((a, b) -> {
            if (a.getFecha() == null || b.getFecha() == null) return 0;
            return b.getFecha().compareTo(a.getFecha());
        });

        List<MovimientoAdminVista> vista = new ArrayList<>();
        for (MovimientosEntity m : todos) {
            MovimientoAdminVista v = new MovimientoAdminVista();
            v.id = m.getId();
            v.fechaTexto = m.getFecha() != null ? m.getFecha().format(FMT_FECHA) : "N/A";
            v.tipo = m.getTipo();
            v.descripcion = m.getDescripcion();
            v.montoTexto = String.format(MX, "%,.2f", m.getMonto());
            v.estadoMovimiento = m.getEstadoMovimiento();
            v.canceladoPor = m.getCanceladoPor();
            v.fechaCancelacionTexto = m.getFechaCancelacion() != null
                    ? m.getFechaCancelacion().format(FMT_FECHA) : null;

            v.clabeOrigen = m.getCuentaOrigen();
            v.clabeDestino = m.getCuentaDestino();

            UsuarioEntity uOrigen = usuarioPorClabe.get(m.getCuentaOrigen());
            v.nombreOrigen = uOrigen != null ? uOrigen.getNombre() : perfilEspecial(m.getCuentaOrigen());
            v.rolOrigen = uOrigen != null ? uOrigen.getRol().name() : "BANCO";
            v.fotoOrigen = uOrigen != null ? uOrigen.getFotoBase64() : null;

            UsuarioEntity uDestino = usuarioPorClabe.get(m.getCuentaDestino());
            v.nombreDestino = uDestino != null ? uDestino.getNombre() : perfilEspecial(m.getCuentaDestino());
            v.rolDestino = uDestino != null ? uDestino.getRol().name() : "BANCO";
            v.fotoDestino = uDestino != null ? uDestino.getFotoBase64() : null;

            boolean esCancelable = java.util.Set.of("TRANSFERENCIA", "PAGO_SERVICIO", "VUELO", "CAMION", "HOTEL", "EVENTO")
                    .contains(m.getTipo());
            v.puedeCancelar = esCancelable && !"Cancelada".equals(m.getEstadoMovimiento());

            vista.add(v);
        }

        modelo.addAttribute("movimientosAdmin", vista);
        modelo.addAttribute("totalMovimientosAdmin", vista.size());
        return "movimientos-admin";
    }

    private String perfilEspecial(String clabe) {
        return "CREDITO-BANCO".equals(clabe) ? "Banco Bienestar" : (clabe != null ? clabe : "N/A");
    }

    @PostMapping("/panel-ejecutivo/movimientos/{id}/cancelar")
    public String cancelar(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        try {
            String ejecutivo = usuarioRepository.findByUsername(principal.getName())
                    .map(UsuarioEntity::getNombre).orElse(principal.getName());
            bancaService.cancelarMovimiento(id, ejecutivo);
            redirectAttributes.addFlashAttribute("exitoMovimiento", "Ha sido cancelado con éxito.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMovimiento",
                    e.getMessage() != null ? e.getMessage() : "No se pudo cancelar el movimiento.");
        }
        return "redirect:/panel-ejecutivo/movimientos";
    }

    @PostMapping("/panel-ejecutivo/movimientos/{id}/eliminar")
    public String eliminar(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bancaService.eliminarMovimiento(id);
            redirectAttributes.addFlashAttribute("exitoMovimiento", "Movimiento eliminado.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMovimiento",
                    e.getMessage() != null ? e.getMessage() : "No se pudo eliminar el movimiento.");
        }
        return "redirect:/panel-ejecutivo/movimientos";
    }

    // ================= CLASE VISTA =================
    public static class MovimientoAdminVista {
        public Long id;
        public String fechaTexto;
        public String tipo;
        public String descripcion;
        public String montoTexto;
        public String estadoMovimiento;
        public String canceladoPor;
        public String fechaCancelacionTexto;

        public String clabeOrigen;
        public String clabeDestino;

        public String nombreOrigen;
        public String rolOrigen;
        public String fotoOrigen;

        public String nombreDestino;
        public String rolDestino;
        public String fotoDestino;

        public boolean puedeCancelar;

        public Long getId() { return id; }
        public String getFechaTexto() { return fechaTexto; }
        public String getTipo() { return tipo; }
        public String getDescripcion() { return descripcion; }
        public String getMontoTexto() { return montoTexto; }
        public String getEstadoMovimiento() { return estadoMovimiento; }
        public String getCanceladoPor() { return canceladoPor; }
        public String getFechaCancelacionTexto() { return fechaCancelacionTexto; }
        public String getClabeOrigen() { return clabeOrigen; }
        public String getClabeDestino() { return clabeDestino; }
        public String getNombreOrigen() { return nombreOrigen; }
        public String getRolOrigen() { return rolOrigen; }
        public String getFotoOrigen() { return fotoOrigen; }
        public String getNombreDestino() { return nombreDestino; }
        public String getRolDestino() { return rolDestino; }
        public String getFotoDestino() { return fotoDestino; }
        public boolean isPuedeCancelar() { return puedeCancelar; }
    }
}
