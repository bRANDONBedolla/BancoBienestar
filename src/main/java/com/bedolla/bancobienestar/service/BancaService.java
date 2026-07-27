package com.bedolla.bancobienestar.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

import com.bedolla.bancobienestar.entity.CuentaEntity;
import com.bedolla.bancobienestar.entity.MovimientosEntity;
import com.bedolla.bancobienestar.entity.Rol;
import com.bedolla.bancobienestar.entity.SolicitudCreditoEntity;
import com.bedolla.bancobienestar.entity.UsuarioEntity;
import com.bedolla.bancobienestar.exception.FondosInsuficientesException;
import com.bedolla.bancobienestar.repository.CuentaRepository;
import com.bedolla.bancobienestar.repository.MovimientoCuentaRepository;
import com.bedolla.bancobienestar.repository.SolicitudCreditoRepository;
import com.bedolla.bancobienestar.repository.UsuarioRepository;

/**
 * Capa de servicio que centraliza la lógica de negocio bancaria
 * (transferencias, alta de clientes y solicitudes de crédito) para
 * que los controllers dejen de manipular repositorios directamente.
 */
@Service
public class BancaService {

    private static final Random RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final CuentaRepository cuentaRepository;
    private final MovimientoCuentaRepository movimientoRepository;
    private final SolicitudCreditoRepository solicitudCreditoRepository;
    private final PasswordEncoder passwordEncoder;

    public BancaService(UsuarioRepository usuarioRepository,
                         CuentaRepository cuentaRepository,
                         MovimientoCuentaRepository movimientoRepository,
                         SolicitudCreditoRepository solicitudCreditoRepository,
                         PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.solicitudCreditoRepository = solicitudCreditoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------------------------------------------------------------
    // Transferencias
    // ---------------------------------------------------------------

    /** Transferencia entre CLABEs con garantía transaccional (ACID). */
    @Transactional(rollbackFor = Exception.class)
    public MovimientosEntity transferirMonto(String clabeOrigen, String clabeDestino, Double monto, String descripcion,
                                              String firmaBase64) {
        if (monto == null || monto <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero.");
        }
        if (clabeOrigen.equals(clabeDestino)) {
            throw new IllegalArgumentException("La cuenta de destino no puede ser la misma que la de origen.");
        }
        if (firmaBase64 == null || firmaBase64.isBlank()) {
            throw new IllegalArgumentException("Debes firmar para autorizar la transferencia.");
        }

        CuentaEntity origen = cuentaRepository.findByClabe(clabeOrigen)
                .orElseThrow(() -> new RuntimeException("La cuenta de origen no existe."));

        CuentaEntity destino = cuentaRepository.findByClabe(clabeDestino)
                .orElseThrow(() -> new RuntimeException("La cuenta de destino no existe."));

        // Validar fondos del remitente
        if (origen.getSaldo() < monto) {
            throw new FondosInsuficientesException("No cuentas con saldo suficiente para esta operación.");
        }

        // 1. CARGO a la cuenta origen
        origen.setSaldo(origen.getSaldo() - monto);
        cuentaRepository.save(origen);

        // --- PUNTO DE FALLO POTENCIAL ---
        // Si ocurriera un error a partir de aquí (por ejemplo, desconexión de DB),
        // @Transactional deshace el cargo ya aplicado a la cuenta de origen.

        // 2. ABONO a la cuenta destino
        destino.setSaldo(destino.getSaldo() + monto);
        cuentaRepository.save(destino);

        // 3. Registrar el movimiento
        MovimientosEntity movimiento = new MovimientosEntity();
        movimiento.setCuentaOrigen(clabeOrigen);
        movimiento.setCuentaDestino(clabeDestino);
        movimiento.setMonto(monto);
        movimiento.setTipo("TRANSFERENCIA");
        movimiento.setCategoria("OCIO_OTROS");
        movimiento.setDescripcion((descripcion == null || descripcion.isBlank()) ? "Transferencia SPEI" : descripcion);
        movimiento.setFecha(LocalDateTime.now());
        movimiento.setEstadoMovimiento("Completado");
        movimiento.setFirmaBase64(firmaBase64);

        return movimientoRepository.save(movimiento);
    }

    /** Inicia una transferencia usando el usuario autenticado (auditoría activa). */
    @Transactional(rollbackFor = Exception.class)
    public MovimientosEntity transferirDesdeUsuario(String username, String clabeDestino, Double monto, String descripcion,
                                                      String firmaBase64) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        List<CuentaEntity> cuentas = cuentaRepository.findByUsuario(usuario);
        if (cuentas.isEmpty()) {
            throw new RuntimeException("El usuario no tiene una cuenta bancaria asignada.");
        }

        // Usamos la cuenta principal (la primera asignada) del usuario autenticado
        String clabeOrigen = cuentas.get(0).getClabe();

        return transferirMonto(clabeOrigen, clabeDestino, monto, descripcion, firmaBase64);
    }

    // ---------------------------------------------------------------
    // Alta de clientes
    // ---------------------------------------------------------------

    /** Registra un cliente y le asigna una cuenta (CLABE + tarjeta) con saldo inicial. */
    @Transactional(rollbackFor = Exception.class)
    public UsuarioEntity crearClienteConCuenta(String nombre, String username, String password, Double saldoInicial) {
        String usernameNormalizado = username.trim().toLowerCase();

        if (usuarioRepository.findByUsername(usernameNormalizado).isPresent()) {
            throw new RuntimeException("El nombre de usuario ya está registrado.");
        }

        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNombre(nombre);
        usuario.setUsername(usernameNormalizado);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(Rol.CLIENTE);
        usuario.setFechaRegistro(LocalDateTime.now());
        UsuarioEntity usuarioGuardado = usuarioRepository.save(usuario);

        CuentaEntity cuenta = new CuentaEntity();
        cuenta.setClabe(generarClabeUnica());
        cuenta.setNoTarjeta(generarTarjetaUnica());
        cuenta.setFechaExpiracion(LocalDate.now().plusYears(5));
        cuenta.setCsv(100 + RANDOM.nextInt(900));
        cuenta.setSaldo(saldoInicial != null ? saldoInicial : 0.0);
        cuenta.setEstado("ACTIVA");
        cuenta.setUsuario(usuarioGuardado);
        cuentaRepository.save(cuenta);

        List<CuentaEntity> cuentas = new ArrayList<>();
        cuentas.add(cuenta);
        usuarioGuardado.setCuentas(cuentas);

        return usuarioGuardado;
    }

    // ---------------------------------------------------------------
    // Solicitudes de crédito
    // ---------------------------------------------------------------

    /** Guarda una solicitud de crédito firmada por el cliente, en espera de autorización del ejecutivo. */
    @Transactional(rollbackFor = Exception.class)
    public SolicitudCreditoEntity guardarSolicitudCredito(String username, Double monto, String firmaBase64) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        if (firmaBase64 == null || firmaBase64.isBlank()) {
            throw new RuntimeException("Debes firmar para poder enviar la solicitud.");
        }

        SolicitudCreditoEntity solicitud = new SolicitudCreditoEntity();
        solicitud.setUsuario(usuario);
        solicitud.setMontoSolicitado(monto);
        solicitud.setFirmaBase64(firmaBase64);
        solicitud.setEstado("PENDIENTE");
        solicitud.setFecha(LocalDateTime.now());
        solicitud.setNotificada(true); // aún no hay respuesta que notificar

        return solicitudCreditoRepository.save(solicitud);
    }

    // ---------------------------------------------------------------
    // Resolución de solicitudes de crédito (aprobar / rechazar)
    // ---------------------------------------------------------------

    /**
     * Resuelve (aprueba o rechaza) una solicitud de crédito PENDIENTE.
     * Si se aprueba, se abona el monto a la cuenta principal del cliente,
     * se registra el movimiento correspondiente y se guarda quién autorizó,
     * cuándo y con qué observaciones. Si ya estaba resuelta, no hace nada
     * (evita abonar dos veces).
     */
    @Transactional(rollbackFor = Exception.class)
    public SolicitudCreditoEntity resolverSolicitudCredito(Long solicitudId, boolean aprobar,
                                                             String ejecutivoNombre, String observaciones) {
        SolicitudCreditoEntity solicitud = solicitudCreditoRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("La solicitud de crédito no existe."));

        if (!"PENDIENTE".equals(solicitud.getEstado())) {
            // Ya fue resuelta antes; no se vuelve a procesar para no duplicar el abono.
            return solicitud;
        }

        solicitud.setEjecutivoAutorizo(ejecutivoNombre);
        solicitud.setFechaAprobacion(LocalDateTime.now());
        solicitud.setObservaciones(observaciones);
        solicitud.setNotificada(false);

        if (!aprobar) {
            solicitud.setEstado("RECHAZADA");
            return solicitudCreditoRepository.save(solicitud);
        }

        solicitud.setEstado("APROBADA");
        LocalDate fechaPrimerPago = LocalDate.now().plusMonths(1);
        solicitud.setFechaPrimerPago(fechaPrimerPago);
        solicitud.setFechaProximoPago(fechaPrimerPago);
        solicitud.setMontoPagado(0.0);

        UsuarioEntity usuario = solicitud.getUsuario();
        if (usuario != null) {
            List<CuentaEntity> cuentas = cuentaRepository.findByUsuario(usuario);
            if (!cuentas.isEmpty()) {
                CuentaEntity cuenta = cuentas.get(0);
                cuenta.setSaldo(cuenta.getSaldo() + solicitud.getMontoSolicitado());
                cuentaRepository.save(cuenta);

                MovimientosEntity movimiento = new MovimientosEntity();
                movimiento.setCuentaOrigen("CREDITO-BANCO");
                movimiento.setCuentaDestino(cuenta.getClabe());
                movimiento.setMonto(solicitud.getMontoSolicitado());
                movimiento.setTipo("CREDITO");
                movimiento.setCategoria("CREDITO");
                movimiento.setDescripcion("Abono de Crédito Autorizado");
                movimiento.setFecha(LocalDateTime.now());
                movimiento.setEstadoMovimiento("Completado");
                movimientoRepository.save(movimiento);
            }
        }

        return solicitudCreditoRepository.save(solicitud);
    }

    /**
     * Cancela un crédito ya APROBADO (y por lo tanto ya abonado): le retira
     * al cliente el monto que se le había depositado y deja registro tanto
     * en la solicitud como en el historial de movimientos. Si el crédito
     * sigue PENDIENTE o ya fue RECHAZADO no hay dinero que revertir.
     */
    @Transactional(rollbackFor = Exception.class)
    public SolicitudCreditoEntity cancelarSolicitudCredito(Long solicitudId, String ejecutivoNombre) {
        SolicitudCreditoEntity solicitud = solicitudCreditoRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("La solicitud de crédito no existe."));

        if ("CANCELADA".equals(solicitud.getEstado())) {
            throw new RuntimeException("Este crédito ya fue cancelado anteriormente.");
        }
        if (!"APROBADA".equals(solicitud.getEstado())) {
            throw new RuntimeException("Solo se pueden cancelar créditos ya aprobados y abonados.");
        }
        double pagadoHastaAhora = solicitud.getMontoPagado() != null ? solicitud.getMontoPagado() : 0.0;
        if (pagadoHastaAhora > 0.0) {
            throw new RuntimeException(
                    "Este crédito ya tiene abonos registrados; no se puede cancelar por completo desde aquí.");
        }

        UsuarioEntity usuario = solicitud.getUsuario();
        if (usuario == null) {
            throw new RuntimeException("El cliente de esta solicitud ya no existe.");
        }

        List<CuentaEntity> cuentas = cuentaRepository.findByUsuario(usuario);
        if (cuentas.isEmpty()) {
            throw new RuntimeException("El cliente ya no tiene una cuenta bancaria asignada.");
        }
        CuentaEntity cuenta = cuentas.get(0);

        if (cuenta.getSaldo() < solicitud.getMontoSolicitado()) {
            throw new RuntimeException(
                    "No se puede cancelar: el cliente ya no cuenta con los fondos suficientes disponibles.");
        }

        // Retirar el abono que se le había hecho al cliente
        cuenta.setSaldo(cuenta.getSaldo() - solicitud.getMontoSolicitado());
        cuentaRepository.save(cuenta);

        MovimientosEntity movimiento = new MovimientosEntity();
        movimiento.setCuentaOrigen(cuenta.getClabe());
        movimiento.setCuentaDestino("CREDITO-BANCO");
        movimiento.setMonto(solicitud.getMontoSolicitado());
        movimiento.setTipo("CANCELACION_CREDITO");
        movimiento.setCategoria("CREDITO");
        movimiento.setDescripcion("Retiro por cancelación de crédito #" + solicitud.getId());
        movimiento.setFecha(LocalDateTime.now());
        movimiento.setEstadoMovimiento("Completado");
        movimientoRepository.save(movimiento);

        solicitud.setEstado("CANCELADA");
        solicitud.setCanceladoPor(ejecutivoNombre);
        solicitud.setFechaCancelacion(LocalDateTime.now());

        return solicitudCreditoRepository.save(solicitud);
    }

    // ---------------------------------------------------------------
    // Abonos / pagos a créditos (el cliente paga lo que pidió)
    // ---------------------------------------------------------------

    /**
     * Registra el abono que un cliente hace a uno de sus créditos APROBADOS.
     * Le retira el monto de su cuenta, se lo descuenta al saldo pendiente del
     * crédito y deja el movimiento en el historial (para armar la tablita de
     * pagos y el PDF del comprobante). Si con este abono el crédito queda
     * saldado, el estado pasa a PAGADA; si no, se recorre un mes la fecha del
     * siguiente pago.
     */
    @Transactional(rollbackFor = Exception.class)
    public MovimientosEntity abonarCredito(String username, Long solicitudId, Double monto) {
        UsuarioEntity usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        SolicitudCreditoEntity solicitud = solicitudCreditoRepository.findByIdConUsuario(solicitudId)
                .orElseThrow(() -> new RuntimeException("La solicitud de crédito no existe."));

        if (solicitud.getUsuario() == null || !solicitud.getUsuario().getUsername().equalsIgnoreCase(username)) {
            throw new RuntimeException("Este crédito no pertenece a tu cuenta.");
        }
        if (!"APROBADA".equals(solicitud.getEstado())) {
            throw new RuntimeException("Solo puedes abonar a créditos que estén APROBADOS.");
        }
        if (monto == null || monto <= 0) {
            throw new IllegalArgumentException("El monto a abonar debe ser mayor a cero.");
        }

        double saldoPendiente = solicitud.getSaldoPendiente();
        if (saldoPendiente <= 0.0) {
            throw new RuntimeException("Este crédito ya está totalmente pagado.");
        }
        // Tolerancia de un centavo para evitar rechazos por redondeo de decimales
        if (monto > saldoPendiente + 0.01) {
            throw new RuntimeException("No puedes abonar más de lo que debes. Tu saldo pendiente es de $"
                    + String.format(java.util.Locale.forLanguageTag("es-MX"), "%,.2f", saldoPendiente) + " MXN.");
        }

        List<CuentaEntity> cuentas = cuentaRepository.findByUsuario(usuario);
        if (cuentas.isEmpty()) {
            throw new RuntimeException("No tienes una cuenta bancaria asignada.");
        }
        CuentaEntity cuenta = cuentas.get(0);

        if (cuenta.getSaldo() < monto) {
            throw new FondosInsuficientesException("No cuentas con saldo suficiente para hacer este pago.");
        }

        // 1. Se le retira el abono al cliente
        cuenta.setSaldo(cuenta.getSaldo() - monto);
        cuentaRepository.save(cuenta);

        // 2. Se refleja el abono en el crédito
        double pagadoPrevio = solicitud.getMontoPagado() != null ? solicitud.getMontoPagado() : 0.0;
        double nuevoPagado = pagadoPrevio + monto;
        double nuevoSaldoPendiente = solicitud.getMontoSolicitado() - nuevoPagado;

        if (nuevoSaldoPendiente <= 0.01) {
            // Se liquidó el crédito: se ajusta el redondeo para que quede exacto
            solicitud.setMontoPagado(solicitud.getMontoSolicitado());
            solicitud.setEstado("PAGADA");
            solicitud.setFechaProximoPago(null);
        } else {
            solicitud.setMontoPagado(nuevoPagado);
            solicitud.setFechaProximoPago(LocalDate.now().plusMonths(1));
        }
        solicitudCreditoRepository.save(solicitud);

        // 3. Se deja el registro del abono en el historial de movimientos
        MovimientosEntity movimiento = new MovimientosEntity();
        movimiento.setCuentaOrigen(cuenta.getClabe());
        movimiento.setCuentaDestino("CREDITO-BANCO");
        movimiento.setMonto(monto);
        movimiento.setTipo("ABONO_CREDITO");
        movimiento.setCategoria("CREDITO");
        movimiento.setDescripcion("Abono a crédito #" + solicitud.getId());
        movimiento.setFecha(LocalDateTime.now());
        movimiento.setEstadoMovimiento("Completado");
        movimiento.setSolicitudCreditoId(solicitud.getId());

        return movimientoRepository.save(movimiento);
    }

    // ---------------------------------------------------------------
    // Edición de perfil de usuarios (solo el ejecutivo/ADMIN la ejecuta;
    // la restricción de acceso vive en el controller + SecurityConfig)
    // ---------------------------------------------------------------

    /**
     * Actualiza nombre, profesión y foto de perfil de un usuario. Si no llega
     * una foto nueva (o viene vacía), se conserva la que ya tenía.
     */
    @Transactional(rollbackFor = Exception.class)
    public UsuarioEntity actualizarPerfilUsuario(Long usuarioId, String nombre, String profesion,
                                                  MultipartFile foto) {
        UsuarioEntity usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RuntimeException("El usuario no existe."));

        if (nombre != null && !nombre.isBlank()) {
            usuario.setNombre(nombre.trim());
        }
        usuario.setProfesion((profesion == null || profesion.isBlank()) ? null : profesion.trim());

        if (foto != null && !foto.isEmpty()) {
            String contentType = foto.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new RuntimeException("El archivo de foto debe ser una imagen.");
            }
            try {
                String base64 = Base64.getEncoder().encodeToString(foto.getBytes());
                usuario.setFotoBase64("data:" + contentType + ";base64," + base64);
            } catch (IOException e) {
                throw new RuntimeException("No se pudo procesar la imagen de perfil.");
            }
        }

        return usuarioRepository.save(usuario);
    }

    // ---------------------------------------------------------------
    // Cancelación de transferencias entre clientes (solo ADMIN)
    // ---------------------------------------------------------------

    /**
     * Cancela una transferencia ya completada entre dos cuentas, revirtiendo
     * el monto (regresa el dinero a la cuenta origen y lo retira de la
     * cuenta destino) y dejando registro de qué ejecutivo la canceló y cuándo.
     */
    @Transactional(rollbackFor = Exception.class)
    public MovimientosEntity cancelarTransferencia(Long movimientoId, String ejecutivoNombre) {
        MovimientosEntity movimiento = movimientoRepository.findById(movimientoId)
                .orElseThrow(() -> new RuntimeException("El movimiento no existe."));

        if (!"TRANSFERENCIA".equals(movimiento.getTipo())) {
            throw new RuntimeException("Solo se pueden cancelar movimientos de tipo TRANSFERENCIA.");
        }
        if ("Cancelada".equals(movimiento.getEstadoMovimiento())) {
            throw new RuntimeException("Esta transferencia ya fue cancelada anteriormente.");
        }

        CuentaEntity origen = cuentaRepository.findByClabe(movimiento.getCuentaOrigen())
                .orElseThrow(() -> new RuntimeException("La cuenta de origen ya no existe."));
        CuentaEntity destino = cuentaRepository.findByClabe(movimiento.getCuentaDestino())
                .orElseThrow(() -> new RuntimeException("La cuenta de destino ya no existe."));

        if (destino.getSaldo() < movimiento.getMonto()) {
            throw new RuntimeException(
                    "No se puede cancelar: el destinatario ya no cuenta con los fondos suficientes disponibles.");
        }

        // Revertir el movimiento de dinero
        destino.setSaldo(destino.getSaldo() - movimiento.getMonto());
        origen.setSaldo(origen.getSaldo() + movimiento.getMonto());
        cuentaRepository.save(destino);
        cuentaRepository.save(origen);

        movimiento.setEstadoMovimiento("Cancelada");
        movimiento.setCanceladoPor(ejecutivoNombre);
        movimiento.setFechaCancelacion(LocalDateTime.now());

        return movimientoRepository.save(movimiento);
    }

    /**
     * Tipos de movimiento donde solo participa la cuenta del cliente (el
     * "destino" es un concepto virtual: un servicio, un vuelo, un camión, un
     * hotel o un evento). Cancelar cualquiera de estos es simétrico: se le
     * regresa el dinero al cliente que pagó.
     */
    private static final java.util.Set<String> TIPOS_CARGO_SIMPLE =
            java.util.Set.of("PAGO_SERVICIO", "VUELO", "CAMION", "HOTEL", "EVENTO");

    /**
     * Cancela un cargo ya completado (pago de servicio, vuelo, camión, hotel
     * o evento) devolviendo el monto a la cuenta del cliente que lo pagó.
     */
    @Transactional(rollbackFor = Exception.class)
    public MovimientosEntity cancelarCargoSimple(Long movimientoId, String ejecutivoNombre) {
        MovimientosEntity movimiento = movimientoRepository.findById(movimientoId)
                .orElseThrow(() -> new RuntimeException("El movimiento no existe."));

        if (!TIPOS_CARGO_SIMPLE.contains(movimiento.getTipo())) {
            throw new RuntimeException("Este tipo de movimiento no se puede cancelar de esta forma.");
        }
        if ("Cancelada".equals(movimiento.getEstadoMovimiento())) {
            throw new RuntimeException("Este movimiento ya fue cancelado anteriormente.");
        }

        CuentaEntity origen = cuentaRepository.findByClabe(movimiento.getCuentaOrigen())
                .orElseThrow(() -> new RuntimeException("La cuenta que realizó el pago ya no existe."));

        // Devolver el dinero al cliente
        origen.setSaldo(origen.getSaldo() + movimiento.getMonto());
        cuentaRepository.save(origen);

        movimiento.setEstadoMovimiento("Cancelada");
        movimiento.setCanceladoPor(ejecutivoNombre);
        movimiento.setFechaCancelacion(LocalDateTime.now());

        return movimientoRepository.save(movimiento);
    }

    /**
     * Punto de entrada único para cancelar un movimiento desde el panel del
     * ejecutivo: detecta el tipo y aplica la reversión de dinero correcta
     * (transferencia entre dos cuentas, o cargo simple con una sola cuenta:
     * pago de servicio, vuelo, camión, hotel o evento).
     */
    @Transactional(rollbackFor = Exception.class)
    public MovimientosEntity cancelarMovimiento(Long movimientoId, String ejecutivoNombre) {
        MovimientosEntity movimiento = movimientoRepository.findById(movimientoId)
                .orElseThrow(() -> new RuntimeException("El movimiento no existe."));

        if (TIPOS_CARGO_SIMPLE.contains(movimiento.getTipo())) {
            return cancelarCargoSimple(movimientoId, ejecutivoNombre);
        }
        return cancelarTransferencia(movimientoId, ejecutivoNombre);
    }

    /**
     * Elimina un movimiento del historial de forma permanente (solo ADMIN).
     * No revierte saldos: la reversión de dinero se hace con
     * {@link #cancelarTransferencia}; esto solo borra el registro/recibo,
     * por lo que desaparece tanto del panel del ejecutivo como del
     * historial del propio cliente (es la misma tabla).
     */
    @Transactional(rollbackFor = Exception.class)
    public void eliminarMovimiento(Long movimientoId) {
        if (!movimientoRepository.existsById(movimientoId)) {
            throw new RuntimeException("El movimiento no existe o ya fue eliminado.");
        }
        movimientoRepository.deleteById(movimientoId);
    }

    // ---------------------------------------------------------------
    // Utilidades privadas
    // ---------------------------------------------------------------

    private String generarClabeUnica() {
        String clabe;
        do {
            StringBuilder sb = new StringBuilder("012180");
            for (int i = 0; i < 12; i++) {
                sb.append(RANDOM.nextInt(10));
            }
            clabe = sb.toString();
        } while (cuentaRepository.existsByClabe(clabe));
        return clabe;
    }

    private String generarTarjetaUnica() {
        String tarjeta;
        do {
            StringBuilder sb = new StringBuilder("4152");
            for (int i = 0; i < 12; i++) {
                sb.append(RANDOM.nextInt(10));
            }
            tarjeta = sb.toString();
        } while (cuentaRepository.existsByNoTarjeta(tarjeta));
        return tarjeta;
    }
}
