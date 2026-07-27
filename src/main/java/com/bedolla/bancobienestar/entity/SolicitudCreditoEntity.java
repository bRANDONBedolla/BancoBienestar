package com.bedolla.bancobienestar.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "solicitud_credito")
public class SolicitudCreditoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double montoSolicitado;

    @Column(columnDefinition = "LONGTEXT")
    private String firmaBase64;

    @Column(nullable = false)
    private String estado = "PENDIENTE";

    private LocalDateTime fecha;

    // true = el cliente ya vio la respuesta (o aún está pendiente); false = hay que avisarle
    @Column(nullable = false)
    private boolean notificada = true;

    // Nombre del ejecutivo que aprobó/rechazó la solicitud
    private String ejecutivoAutorizo;

    // Fecha y hora en que el ejecutivo resolvió la solicitud
    private LocalDateTime fechaAprobacion;

    // Fecha del primer pago (solo aplica si fue aprobada)
    private LocalDate fechaPrimerPago;

    // Cuánto ha abonado el cliente a este crédito hasta ahora (0 si no ha pagado nada)
    @Column(nullable = false)
    private Double montoPagado = 0.0;

    // Fecha en que le toca el siguiente abono; se recalcula tras cada pago y
    // queda en null cuando el crédito se liquida por completo (estado PAGADA)
    private LocalDate fechaProximoPago;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    // Nombre del ejecutivo que canceló un crédito ya aprobado (retiro del abono)
    private String canceladoPor;

    // Fecha y hora en que se canceló el crédito
    private LocalDateTime fechaCancelacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    public SolicitudCreditoEntity() {
    }

    public SolicitudCreditoEntity(Long id, Double montoSolicitado, String firmaBase64, String estado,
                                   LocalDateTime fecha, boolean notificada, UsuarioEntity usuario) {
        this.id = id;
        this.montoSolicitado = montoSolicitado;
        this.firmaBase64 = firmaBase64;
        this.estado = estado;
        this.fecha = fecha;
        this.notificada = notificada;
        this.usuario = usuario;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getMontoSolicitado() {
        return montoSolicitado;
    }

    public void setMontoSolicitado(Double montoSolicitado) {
        this.montoSolicitado = montoSolicitado;
    }

    public String getFirmaBase64() {
        return firmaBase64;
    }

    public void setFirmaBase64(String firmaBase64) {
        this.firmaBase64 = firmaBase64;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public boolean isNotificada() {
        return notificada;
    }

    public void setNotificada(boolean notificada) {
        this.notificada = notificada;
    }

    public String getEjecutivoAutorizo() {
        return ejecutivoAutorizo;
    }

    public void setEjecutivoAutorizo(String ejecutivoAutorizo) {
        this.ejecutivoAutorizo = ejecutivoAutorizo;
    }

    public LocalDateTime getFechaAprobacion() {
        return fechaAprobacion;
    }

    public void setFechaAprobacion(LocalDateTime fechaAprobacion) {
        this.fechaAprobacion = fechaAprobacion;
    }

    public LocalDate getFechaPrimerPago() {
        return fechaPrimerPago;
    }

    public void setFechaPrimerPago(LocalDate fechaPrimerPago) {
        this.fechaPrimerPago = fechaPrimerPago;
    }

    public Double getMontoPagado() {
        return montoPagado;
    }

    public void setMontoPagado(Double montoPagado) {
        this.montoPagado = montoPagado;
    }

    public LocalDate getFechaProximoPago() {
        return fechaProximoPago;
    }

    public void setFechaProximoPago(LocalDate fechaProximoPago) {
        this.fechaProximoPago = fechaProximoPago;
    }

    /** Cuánto le falta por pagar al crédito; nunca negativo. No se persiste, se calcula al vuelo. */
    @Transient
    public double getSaldoPendiente() {
        double solicitado = montoSolicitado != null ? montoSolicitado : 0.0;
        double pagado = montoPagado != null ? montoPagado : 0.0;
        double saldo = solicitado - pagado;
        return saldo > 0 ? saldo : 0.0;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getCanceladoPor() {
        return canceladoPor;
    }

    public void setCanceladoPor(String canceladoPor) {
        this.canceladoPor = canceladoPor;
    }

    public LocalDateTime getFechaCancelacion() {
        return fechaCancelacion;
    }

    public void setFechaCancelacion(LocalDateTime fechaCancelacion) {
        this.fechaCancelacion = fechaCancelacion;
    }

    public UsuarioEntity getUsuario() {
        return usuario;
    }

    public void setUsuario(UsuarioEntity usuario) {
        this.usuario = usuario;
    }
}