package com.bedolla.bancobienestar.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.bedolla.bancobienestar.entity.CuentaEntity;
import com.bedolla.bancobienestar.entity.MovimientosEntity;
import com.bedolla.bancobienestar.entity.UsuarioEntity;
import com.bedolla.bancobienestar.repository.CuentaRepository;
import com.bedolla.bancobienestar.repository.MovimientoCuentaRepository;
import com.bedolla.bancobienestar.repository.UsuarioRepository;

@Controller
public class MovimientoController {

    private final UsuarioRepository usuarioRepository;
    private final CuentaRepository cuentaRepository;
    private final MovimientoCuentaRepository movimientoRepository;

    public MovimientoController(UsuarioRepository usuarioRepository,
                                 CuentaRepository cuentaRepository,
                                 MovimientoCuentaRepository movimientoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
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
}