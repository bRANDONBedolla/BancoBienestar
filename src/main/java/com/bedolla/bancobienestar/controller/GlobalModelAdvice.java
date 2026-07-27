package com.bedolla.bancobienestar.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.bedolla.bancobienestar.entity.UsuarioEntity;
import com.bedolla.bancobienestar.repository.UsuarioRepository;

@ControllerAdvice
public class GlobalModelAdvice {

    private final UsuarioRepository usuarioRepository;

    public GlobalModelAdvice(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @ModelAttribute("usuarioLogeado")
    public UsuarioEntity usuarioLogeado(Principal principal) {

        if (principal == null) return null;

        return usuarioRepository.findByUsername(principal.getName())
                .orElse(null);
    }

    @ModelAttribute("nombreUsuario")
    public String nombreUsuario(@ModelAttribute("usuarioLogeado") UsuarioEntity u) {
        return (u != null) ? u.getNombre() : "Invitado";
    }

    // El layout (sidebar/topbar) usa esta clave; la mantenemos igual al nombre real.
    @ModelAttribute("nombreCliente")
    public String nombreCliente(@ModelAttribute("usuarioLogeado") UsuarioEntity u) {
        return (u != null) ? u.getNombre() : "Invitado";
    }

    @ModelAttribute("rolUsuario")
    public String rolUsuario(@ModelAttribute("usuarioLogeado") UsuarioEntity u) {
        return (u != null) ? u.getRol().toString() : "SIN ROL";
    }

    @ModelAttribute("fechaHoy")
    public String fechaHoy() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd 'de' MMMM yyyy", new Locale("es", "MX"));
        return LocalDate.now().format(formatter);
    }
}
