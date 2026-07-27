package com.bedolla.bancobienestar.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.bedolla.bancobienestar.entity.UsuarioEntity;
import com.bedolla.bancobienestar.repository.CuentaRepository;
import com.bedolla.bancobienestar.repository.UsuarioRepository;
import com.bedolla.bancobienestar.service.BancaService;

/**
 * Edición del perfil de los CLIENTES (nombre, profesión y foto) desde el
 * panel del ejecutivo. Todo bajo /panel-ejecutivo/** (protegido con
 * hasRole(ADMIN) en SecurityConfig) para que solo el rol ADMIN pueda
 * modificar los datos de otros usuarios.
 *
 * Para editar su propio perfil (incluido el del ejecutivo/admin), cada
 * usuario usa /perfil/editar en {@link PerfilController}, que identifica
 * siempre al usuario a partir de su sesión (Principal).
 */
@Controller
public class PerfilAdminController {

    private final UsuarioRepository usuarioRepository;
    private final CuentaRepository cuentaRepository;
    private final BancaService bancaService;

    public PerfilAdminController(UsuarioRepository usuarioRepository,
                                  CuentaRepository cuentaRepository,
                                  BancaService bancaService) {
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
        this.bancaService = bancaService;
    }

    @GetMapping("/panel-ejecutivo/clientes/{id}/editar")
    public String editarForm(@PathVariable Long id, Model modelo) {
        UsuarioEntity usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("El usuario no existe."));

        modelo.addAttribute("clienteEditar", usuario);

        var cuentas = cuentaRepository.findByUsuario(usuario);
        if (cuentas != null && !cuentas.isEmpty()) {
            modelo.addAttribute("clabeCliente", cuentas.get(0).getClabe());
        }

        return "editar-cliente";
    }

    @PostMapping("/panel-ejecutivo/clientes/{id}/editar")
    public String editarGuardar(@PathVariable Long id,
                                 @RequestParam String nombre,
                                 @RequestParam(required = false) String profesion,
                                 @RequestParam(required = false) MultipartFile foto,
                                 RedirectAttributes redirectAttributes) {
        try {
            bancaService.actualizarPerfilUsuario(id, nombre, profesion, foto);
            redirectAttributes.addFlashAttribute("exitoPerfil", "Perfil actualizado correctamente.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorPerfil",
                    e.getMessage() != null ? e.getMessage() : "No se pudo actualizar el perfil.");
            return "redirect:/panel-ejecutivo/clientes/" + id + "/editar";
        }
        return "redirect:/panel-ejecutivo";
    }
}
