package com.bedolla.bancobienestar.controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
public class ViajesController {

    private static final Locale MX = new Locale("es", "MX");

    private final UsuarioRepository usuarioRepository;
    private final CuentaRepository cuentaRepository;
    private final MovimientoCuentaRepository movimientoRepository;

    public ViajesController(UsuarioRepository usuarioRepository,
                             CuentaRepository cuentaRepository,
                             MovimientoCuentaRepository movimientoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
    }

    // ================= CATÁLOGO =================

    private List<ItemCatalogo> catalogoVuelos() {
        List<ItemCatalogo> lista = new ArrayList<>();
        lista.add(new ItemCatalogo("vuelo-cdmx", "VUELO", "Ciudad de México", 1850.00));
        lista.add(new ItemCatalogo("vuelo-slp", "VUELO", "San Luis Potosí", 2100.00));
        lista.add(new ItemCatalogo("vuelo-chih", "VUELO", "Chihuahua", 3200.00));
        lista.add(new ItemCatalogo("vuelo-gro", "VUELO", "Acapulco, Guerrero", 2450.00));
        lista.add(new ItemCatalogo("vuelo-yuc", "VUELO", "Mérida, Yucatán", 3800.00));
        lista.add(new ItemCatalogo("vuelo-gdl", "VUELO", "Guadalajara", 1950.00));
        lista.add(new ItemCatalogo("vuelo-mty", "VUELO", "Monterrey", 2600.00));
        return lista;
    }

    private List<ItemCatalogo> catalogoCamionera() {
        List<ItemCatalogo> lista = new ArrayList<>();
        lista.add(new ItemCatalogo("bus-cdmx", "CAMION", "Ciudad de México", 650.00));
        lista.add(new ItemCatalogo("bus-slp", "CAMION", "San Luis Potosí", 450.00));
        lista.add(new ItemCatalogo("bus-chih", "CAMION", "Chihuahua", 1200.00));
        lista.add(new ItemCatalogo("bus-gro", "CAMION", "Acapulco/Chilpancingo, Guerrero", 580.00));
        lista.add(new ItemCatalogo("bus-yuc", "CAMION", "Mérida, Yucatán", 1650.00));
        lista.add(new ItemCatalogo("bus-gdl", "CAMION", "Guadalajara", 750.00));
        lista.add(new ItemCatalogo("bus-mty", "CAMION", "Monterrey", 980.00));
        return lista;
    }

    private List<ItemCatalogo> catalogoHoteles() {
        List<ItemCatalogo> lista = new ArrayList<>();
        lista.add(new ItemCatalogo("hotel-5", "HOTEL", "Hotel 5 estrellas (por noche)", 3500.00));
        lista.add(new ItemCatalogo("hotel-4", "HOTEL", "Hotel 4 estrellas (por noche)", 2200.00));
        lista.add(new ItemCatalogo("hotel-3", "HOTEL", "Hotel 3 estrellas (por noche)", 1300.00));
        lista.add(new ItemCatalogo("hotel-2", "HOTEL", "Hotel 2 estrellas (por noche)", 750.00));
        lista.add(new ItemCatalogo("hotel-1", "HOTEL", "Hotel 1 estrella (por noche)", 450.00));
        return lista;
    }

    private List<ItemCatalogo> catalogoEventos() {
        List<ItemCatalogo> lista = new ArrayList<>();
        lista.add(new ItemCatalogo("mundial-general", "EVENTO", "Mundial 2026 - Boleto General", 4500.00));
        lista.add(new ItemCatalogo("mundial-preferente", "EVENTO", "Mundial 2026 - Preferente", 8000.00));
        lista.add(new ItemCatalogo("mundial-vip", "EVENTO", "Mundial 2026 - VIP", 15000.00));
        lista.add(new ItemCatalogo("fenapo-general", "EVENTO", "FENAPO - Boleto General", 150.00));
        lista.add(new ItemCatalogo("fenapo-palco", "EVENTO", "FENAPO - Palco", 350.00));
        return lista;
    }

    private List<ItemCatalogo> catalogoCompleto() {
        List<ItemCatalogo> todo = new ArrayList<>();
        todo.addAll(catalogoVuelos());
        todo.addAll(catalogoCamionera());
        todo.addAll(catalogoHoteles());
        todo.addAll(catalogoEventos());
        return todo;
    }

    // ================= RUTAS =================

    @GetMapping("/viajes")
    public String viajes(Model modelo, Principal principal) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        cargarDatosPropios(modelo, usuario);

        modelo.addAttribute("vuelos", catalogoVuelos());
        modelo.addAttribute("camiones", catalogoCamionera());
        modelo.addAttribute("hoteles", catalogoHoteles());
        modelo.addAttribute("eventos", catalogoEventos());

        return "viajes";
    }
    @PostMapping("/viajes/comprar")
    public String comprar(@RequestParam String itemId, Principal principal, Model modelo) {

        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName()).orElse(null);
        String error = null;
        String exito = null;

        ItemCatalogo item = catalogoCompleto().stream()
                .filter(i -> i.id.equals(itemId))
                .findFirst()
                .orElse(null);

        List<CuentaEntity> cuentasPropias = usuario != null ? cuentaRepository.findByUsuario(usuario) : List.of();

        if (item == null) {
            error = "El producto seleccionado ya no está disponible.";
        } else if (cuentasPropias.isEmpty()) {
            error = "No tienes una cuenta activa para realizar el pago.";
        } else {
            CuentaEntity cuenta = cuentasPropias.get(0);

            if (cuenta.getSaldo() < item.precio) {
                error = "Saldo insuficiente para comprar: " + item.detalle;
            } else {
                cuenta.setSaldo(cuenta.getSaldo() - item.precio);
                cuentaRepository.save(cuenta);

                MovimientosEntity movimiento = new MovimientosEntity();
                movimiento.setCuentaOrigen(cuenta.getClabe());
                movimiento.setCuentaDestino(item.tipo + ":" + item.detalle);
                movimiento.setMonto(item.precio);
                movimiento.setTipo(item.tipo);
                movimiento.setCategoria(
                        item.tipo.equals("VUELO") || item.tipo.equals("CAMION") ? "TRANSPORTE" : "OCIO_OTROS");
                movimiento.setDescripcion(nombreAmigable(item.tipo) + " - " + item.detalle);
                movimiento.setFecha(LocalDateTime.now());
                movimiento.setEstadoMovimiento("Completado");
                movimientoRepository.save(movimiento);

                exito = String.format(MX, "Compraste \"%s\" por $%,.2f correctamente.", item.detalle, item.precio);
            }
        }

        cargarDatosPropios(modelo, usuario);
        modelo.addAttribute("vuelos", catalogoVuelos());
        modelo.addAttribute("camiones", catalogoCamionera());
        modelo.addAttribute("hoteles", catalogoHoteles());
        modelo.addAttribute("eventos", catalogoEventos());
        modelo.addAttribute("error", error);
        modelo.addAttribute("exito", exito);

        return "viajes";
    }

    private String nombreAmigable(String tipo) {
        return switch (tipo) {
            case "VUELO" -> "Vuelo";
            case "CAMION" -> "Autobús";
            case "HOTEL" -> "Hospedaje";
            case "EVENTO" -> "Evento";
            default -> tipo;
        };
    }

    private static final java.util.Set<String> TIPOS_VIAJES = java.util.Set.of("VUELO", "CAMION", "HOTEL", "EVENTO");

    private void cargarDatosPropios(Model modelo, UsuarioEntity usuario) {
        if (usuario == null) return;

        List<CuentaEntity> cuentas = cuentaRepository.findByUsuario(usuario);
        double saldo = cuentas.isEmpty() ? 0.0 : cuentas.get(0).getSaldo();
        modelo.addAttribute("saldoPropio", String.format(MX, "%,.2f", saldo));

        if (!cuentas.isEmpty()) {
            String clabe = cuentas.get(0).getClabe();
            List<MovimientosEntity> compras =
                    movimientoRepository.findByCuentaOrigenOrCuentaDestinoOrderByFechaDesc(clabe, clabe)
                            .stream().filter(m -> TIPOS_VIAJES.contains(m.getTipo())).limit(10).toList();
            modelo.addAttribute("historialViajes", compras);
        }
    }

    /** Clase simple para representar un producto del catálogo (vuelo, camión, hotel o evento). */
    public static class ItemCatalogo {
        public String id;
        public String tipo;
        public String detalle;
        public Double precio;
        public String precioTexto;

        public ItemCatalogo(String id, String tipo, String detalle, Double precio) {
            this.id = id;
            this.tipo = tipo;
            this.detalle = detalle;
            this.precio = precio;
            this.precioTexto = String.format(MX, "%,.2f", precio);
        }

        public String getId() { return id; }
        public String getTipo() { return tipo; }
        public String getDetalle() { return detalle; }
        public Double getPrecio() { return precio; }
        public String getPrecioTexto() { return precioTexto; }
    }
}
