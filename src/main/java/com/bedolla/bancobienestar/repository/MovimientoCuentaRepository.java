package com.bedolla.bancobienestar.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bedolla.bancobienestar.entity.MovimientosEntity;

@Repository
public interface MovimientoCuentaRepository extends JpaRepository<MovimientosEntity, Long> {
    List<MovimientosEntity> findByCuentaOrigenOrCuentaDestinoOrderByFechaDesc(String cuentaOrigen, String cuentaDestino);

    // Historial de abonos (tabla de pagos) de un crédito específico.
    List<MovimientosEntity> findBySolicitudCreditoIdOrderByFechaDesc(Long solicitudCreditoId);
}
