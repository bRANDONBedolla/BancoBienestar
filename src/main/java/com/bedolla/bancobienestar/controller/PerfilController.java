package com.bedolla.bancobienestar.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.bedolla.bancobienestar.entity.CuentaEntity;
import com.bedolla.bancobienestar.entity.UsuarioEntity;
import com.bedolla.bancobienestar.repository.CuentaRepository;
import com.bedolla.bancobienestar.repository.UsuarioRepository;
import com.bedolla.bancobienestar.service.BancaService;

/**
 * Perfil propio del usuario que ha iniciado sesión. Al no estar bajo
 * /panel-ejecutivo/**, aplica tanto a clientes como a administradores:
 * cada quien ve y edita únicamente sus propios datos (nombre, profesión
 * y foto), identificados siempre a partir del Principal autenticado.
 */
@Controller
public class PerfilController {

    private final UsuarioRepository usuarioRepository;
    private final CuentaRepository cuentaRepository;
    private final BancaService bancaService;

    public PerfilController(UsuarioRepository usuarioRepository, CuentaRepository cuentaRepository,
                             BancaService bancaService) {
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
        this.bancaService = bancaService;
    }

    @GetMapping("/perfil")
    public String perfil(Model modelo, Principal principal) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        modelo.addAttribute("nombre", usuario.getNombre());
        modelo.addAttribute("username", usuario.getUsername());
        modelo.addAttribute("rol", usuario.getRol().name());
        modelo.addAttribute("profesion",
                usuario.getProfesion() != null ? usuario.getProfesion() : "No especificada");
        modelo.addAttribute("fotoBase64", usuario.getFotoBase64());

        List<CuentaEntity> cuentas = cuentaRepository.findByUsuario(usuario);
        if (cuentas != null && !cuentas.isEmpty()) {
            modelo.addAttribute("clabe", cuentas.get(0).getClabe());
            modelo.addAttribute("estadoCuenta", cuentas.get(0).getEstado());
        }

        return "perfil";
    }

    @GetMapping("/perfil/editar")
    public String editarForm(Model modelo, Principal principal) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        modelo.addAttribute("miPerfil", usuario);

        return "perfil-editar";
    }

    @PostMapping("/perfil/editar")
    public String editarGuardar(@RequestParam String nombre,
                                 @RequestParam(required = false) String profesion,
                                 @RequestParam(required = false) MultipartFile foto,
                                 Principal principal,
                                 RedirectAttributes redirectAttributes) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        try {
            bancaService.actualizarPerfilUsuario(usuario.getId(), nombre, profesion, foto);
            redirectAttributes.addFlashAttribute("exitoPerfil", "Tu perfil se actualizó correctamente.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorPerfil",
                    e.getMessage() != null ? e.getMessage() : "No se pudo actualizar el perfil.");
            return "redirect:/perfil/editar";
        }
        return "redirect:/perfil";
    }
}
