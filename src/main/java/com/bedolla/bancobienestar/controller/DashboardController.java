package com.bedolla.bancobienestar.controller;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.bedolla.bancobienestar.entity.CuentaEntity;
import com.bedolla.bancobienestar.entity.MovimientosEntity;
import com.bedolla.bancobienestar.entity.Rol;
import com.bedolla.bancobienestar.entity.SolicitudCreditoEntity;
import com.bedolla.bancobienestar.entity.UsuarioEntity;
import com.bedolla.bancobienestar.repository.CuentaRepository;
import com.bedolla.bancobienestar.repository.MovimientoCuentaRepository;
import com.bedolla.bancobienestar.repository.SolicitudCreditoRepository;
import com.bedolla.bancobienestar.repository.UsuarioRepository;

@Controller
public class DashboardController {

    private final UsuarioRepository usuarioRepository;
    private final CuentaRepository cuentaRepository;
    private final MovimientoCuentaRepository movimientoRepository;
    private final SolicitudCreditoRepository solicitudCreditoRepository;

    private static final Locale MX = new Locale("es", "MX");
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", MX);
    private static final DateTimeFormatter FMT_FECHA_CORTA = DateTimeFormatter.ofPattern("dd/MM/yyyy", MX);

    public DashboardController(UsuarioRepository usuarioRepository,
                                CuentaRepository cuentaRepository,
                                MovimientoCuentaRepository movimientoRepository,
                                SolicitudCreditoRepository solicitudCreditoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.solicitudCreditoRepository = solicitudCreditoRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model modelo, Principal principal) {

        UsuarioEntity usuario = usuarioRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        modelo.addAttribute("nombreUsuario", usuario.getNombre());
        modelo.addAttribute("rolUsuario", usuario.getRol().name());

        // Todos los roles manejan su propia cuenta igual (cliente o ejecutivo).
        cargarDashboardPersonal(modelo, usuario);

        if (usuario.getRol() == Rol.ADMIN) {
            cargarDashboardEjecutivo(modelo);
        }

        return "dashboard";
    }

    // ================= DASHBOARD PERSONAL (cliente o ejecutivo) =================
    private void cargarDashboardPersonal(Model modelo, UsuarioEntity usuario) {
        List<CuentaEntity> cuentas = cuentaRepository.findByUsuario(usuario);

        double saldo = 0.0;
        String clabe = "N/A";

        if (cuentas != null && !cuentas.isEmpty()) {
            saldo = cuentas.get(0).getSaldo();
            clabe = cuentas.get(0).getClabe();
        }

        modelo.addAttribute("saldoTotal", formatear(saldo));
        modelo.addAttribute("cuentaClabe", clabe);

        List<MovimientoVista> movimientos = new ArrayList<>();
        double ingresos = 0;
        double gastos = 0;

        double gastoAlimentacion = 0;
        double gastoVivienda = 0;
        double gastoTransporte = 0;
        double gastoOcioOtros = 0;

        if (!"N/A".equals(clabe)) {
            List<MovimientosEntity> historial =
                    movimientoRepository.findByCuentaOrigenOrCuentaDestinoOrderByFechaDesc(clabe, clabe);

            for (MovimientosEntity m : historial) {
                boolean esIngreso = clabe.equals(m.getCuentaDestino()) && !clabe.equals(m.getCuentaOrigen());
                boolean esEgreso = clabe.equals(m.getCuentaOrigen());
                double montoMostrado = esIngreso ? m.getMonto() : -m.getMonto();

                if (esIngreso) {
                    ingresos += m.getMonto();
                }

                if (esEgreso) {
                    gastos += m.getMonto();
                    String cat = m.getCategoria() != null ? m.getCategoria() : "OCIO_OTROS";
                    switch (cat) {
                        case "ALIMENTACION" -> gastoAlimentacion += m.getMonto();
                        case "VIVIENDA" -> gastoVivienda += m.getMonto();
                        case "TRANSPORTE" -> gastoTransporte += m.getMonto();
                        default -> gastoOcioOtros += m.getMonto();
                    }
                }

                if (movimientos.size() < 8) {
                    movimientos.add(new MovimientoVista(
                            m.getFecha() != null ? m.getFecha().format(FMT_FECHA) : "N/A",
                            m.getDescripcion(),
                            montoMostrado
                    ));
                }
            }
        }

        modelo.addAttribute("movimientos", movimientos);
        modelo.addAttribute("totalIngresos", formatear(ingresos));
        modelo.addAttribute("totalGastos", formatear(gastos));

        // Datos crudos (double) para la gráfica de pastel en JS.
        modelo.addAttribute("pieAlimentacion", gastoAlimentacion);
        modelo.addAttribute("pieVivienda", gastoVivienda);
        modelo.addAttribute("pieTransporte", gastoTransporte);
        modelo.addAttribute("pieOcioOtros", gastoOcioOtros);
        modelo.addAttribute("pieTieneDatos", (gastoAlimentacion + gastoVivienda + gastoTransporte + gastoOcioOtros) > 0);

        // ==== Mis créditos activos (tablita de préstamos con saldo pendiente) ====
        List<SolicitudCreditoEntity> misCreditos = solicitudCreditoRepository.findByUsuarioOrderByFechaDesc(usuario);
        List<CreditoActivoVista> creditosActivos = misCreditos.stream()
                .filter(s -> "APROBADA".equals(s.getEstado()) && s.getSaldoPendiente() > 0.0)
                .map(s -> {
                    double pagado = s.getMontoPagado() != null ? s.getMontoPagado() : 0.0;
                    CreditoActivoVista v = new CreditoActivoVista();
                    v.id = s.getId();
                    v.montoTexto = formatear(s.getMontoSolicitado());
                    v.pagadoTexto = formatear(pagado);
                    v.pendienteTexto = formatear(s.getSaldoPendiente());
                    v.fechaProximoPagoTexto = s.getFechaProximoPago() != null
                            ? s.getFechaProximoPago().format(FMT_FECHA_CORTA)
                            : "N/A";
                    return v;
                })
                .toList();
        modelo.addAttribute("misCreditosActivos", creditosActivos);
    }

    // ================= DASHBOARD EJECUTIVO (ADMIN) =================
    private void cargarDashboardEjecutivo(Model modelo) {
        List<UsuarioEntity> clientes = usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() == Rol.CLIENTE)
                .toList();

        List<ClienteVista> clientesVista = new ArrayList<>();
        double saldoTotalBanco = 0.0;

        // Mapa clabe -> nombre construido UNA sola vez, en lugar de volver a
        // consultar la BD por cada movimiento (eso era lo que colgaba el /dashboard
        // del ADMIN por varios minutos: N clientes x M movimientos consultas extra).
        java.util.Map<String, String> nombrePorClabe = new java.util.HashMap<>();

        for (UsuarioEntity cliente : clientes) {
            List<CuentaEntity> cuentas = cuentaRepository.findByUsuario(cliente);
            double saldo = (cuentas != null && !cuentas.isEmpty()) ? cuentas.get(0).getSaldo() : 0.0;
            String clabe = (cuentas != null && !cuentas.isEmpty()) ? cuentas.get(0).getClabe() : "N/A";
            saldoTotalBanco += saldo;

            if (cuentas != null) {
                for (CuentaEntity c : cuentas) {
                    if (c.getClabe() != null) {
                        nombrePorClabe.put(c.getClabe(), cliente.getNombre());
                    }
                }
            }

            List<SolicitudCreditoEntity> solicitudes = solicitudCreditoRepository.findByUsuarioOrderByFechaDesc(cliente);
            String estadoCredito = solicitudes.isEmpty() ? "SIN SOLICITUD" : solicitudes.get(0).getEstado();
            Double montoCredito = solicitudes.isEmpty() ? null : solicitudes.get(0).getMontoSolicitado();

            ClienteVista cv = new ClienteVista();
            cv.nombre = cliente.getNombre();
            cv.username = cliente.getUsername();
            cv.clabe = clabe;
            cv.saldoTexto = formatear(saldo);
            cv.estadoCredito = estadoCredito;
            cv.montoCreditoTexto = montoCredito != null ? formatear(montoCredito) : null;
            clientesVista.add(cv);
        }

        List<MovimientosEntity> todosMovimientos = movimientoRepository.findAll();
        todosMovimientos.sort((a, b) -> {
            if (a.getFecha() == null || b.getFecha() == null) return 0;
            return b.getFecha().compareTo(a.getFecha());
        });

        List<MovimientoGlobalVista> movimientosVista = new ArrayList<>();
        for (MovimientosEntity m : todosMovimientos) {
            if (movimientosVista.size() >= 10) break;

            String nombreOrigen = nombrePorClabe.get(m.getCuentaOrigen());
            String nombreDestino = nombrePorClabe.get(m.getCuentaDestino());

            MovimientoGlobalVista mv = new MovimientoGlobalVista();
            mv.fecha = m.getFecha() != null ? m.getFecha().format(FMT_FECHA) : "N/A";
            mv.origen = nombreOrigen != null ? nombreOrigen : m.getCuentaOrigen();
            mv.destino = nombreDestino != null ? nombreDestino : m.getCuentaDestino();
            mv.montoTexto = formatear(m.getMonto());
            mv.tipo = m.getTipo();
            movimientosVista.add(mv);
        }

        long solicitudesPendientes = clientesVista.stream()
                .filter(c -> "PENDIENTE".equals(c.estadoCredito)).count();

        // Lista real de solicitudes PENDIENTES (con id) para poder aprobar/rechazar
        // directamente desde el dashboard, sin tener que ir al panel completo.
        List<SolicitudPendienteVista> solicitudesPendientesLista = solicitudCreditoRepository.findAllByOrderByFechaDesc()
                .stream()
                .filter(s -> "PENDIENTE".equals(s.getEstado()))
                .map(s -> {
                    SolicitudPendienteVista v = new SolicitudPendienteVista();
                    v.id = s.getId();
                    v.nombreCliente = s.getUsuario() != null ? s.getUsuario().getNombre() : "N/A";
                    v.montoTexto = formatear(s.getMontoSolicitado());
                    v.fechaTexto = s.getFecha() != null ? s.getFecha().format(FMT_FECHA) : "N/A";
                    return v;
                })
                .toList();

        modelo.addAttribute("clientes", clientesVista);
        modelo.addAttribute("saldoTotalBanco", formatear(saldoTotalBanco));
        modelo.addAttribute("totalClientes", clientes.size());
        modelo.addAttribute("solicitudesPendientesDashboard", solicitudesPendientes);
        modelo.addAttribute("solicitudesPendientesLista", solicitudesPendientesLista);
        modelo.addAttribute("movimientosGlobales", movimientosVista);
    }

    // ================= FORMATO MONEDA =================
    private String formatear(double valor) {
        return String.format(MX, "%,.2f", valor);
    }

    // ================= CLASES VISTA =================
    public static class MovimientoVista {
        public String fecha;
        public String descripcion;
        public double monto;

        public MovimientoVista() {}

        public MovimientoVista(String fecha, String descripcion, double monto) {
            this.fecha = fecha;
            this.descripcion = descripcion;
            this.monto = monto;
        }

        public String getFecha() { return fecha; }
        public String getDescripcion() { return descripcion; }
        public double getMonto() { return monto; }
    }

    public static class ClienteVista {
        public String nombre;
        public String username;
        public String clabe;
        public String saldoTexto;
        public String estadoCredito;
        public String montoCreditoTexto;

        public String getNombre() { return nombre; }
        public String getUsername() { return username; }
        public String getClabe() { return clabe; }
        public String getSaldoTexto() { return saldoTexto; }
        public String getEstadoCredito() { return estadoCredito; }
        public String getMontoCreditoTexto() { return montoCreditoTexto; }
    }

    public static class MovimientoGlobalVista {
        public String fecha;
        public String origen;
        public String destino;
        public String montoTexto;
        public String tipo;

        public String getFecha() { return fecha; }
        public String getOrigen() { return origen; }
        public String getDestino() { return destino; }
        public String getMontoTexto() { return montoTexto; }
        public String getTipo() { return tipo; }
    }

    public static class CreditoActivoVista {
        public Long id;
        public String montoTexto;
        public String pagadoTexto;
        public String pendienteTexto;
        public String fechaProximoPagoTexto;

        public Long getId() { return id; }
        public String getMontoTexto() { return montoTexto; }
        public String getPagadoTexto() { return pagadoTexto; }
        public String getPendienteTexto() { return pendienteTexto; }
        public String getFechaProximoPagoTexto() { return fechaProximoPagoTexto; }
    }

    public static class SolicitudPendienteVista {
        public Long id;
        public String nombreCliente;
        public String montoTexto;
        public String fechaTexto;

        public Long getId() { return id; }
        public String getNombreCliente() { return nombreCliente; }
        public String getMontoTexto() { return montoTexto; }
        public String getFechaTexto() { return fechaTexto; }
    }
}
