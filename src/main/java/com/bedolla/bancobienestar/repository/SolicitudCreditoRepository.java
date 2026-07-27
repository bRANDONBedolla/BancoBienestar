package com.bedolla.bancobienestar.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.bedolla.bancobienestar.entity.SolicitudCreditoEntity;
import com.bedolla.bancobienestar.entity.UsuarioEntity;

@Repository
public interface SolicitudCreditoRepository extends JpaRepository<SolicitudCreditoEntity, Long> {
    List<SolicitudCreditoEntity> findByUsuarioOrderByFechaDesc(UsuarioEntity usuario);
    List<SolicitudCreditoEntity> findByUsuarioAndNotificadaFalse(UsuarioEntity usuario);

    // JOIN FETCH: trae el usuario de una sola vez para evitar
    // LazyInitializationException (spring.jpa.open-in-view=false cierra
    // la sesión de Hibernate apenas termina la consulta).
    @Query("SELECT s FROM SolicitudCreditoEntity s JOIN FETCH s.usuario ORDER BY s.fecha DESC")
    List<SolicitudCreditoEntity> findAllByOrderByFechaDesc();

    @Query("SELECT s FROM SolicitudCreditoEntity s JOIN FETCH s.usuario WHERE s.id = :id")
    Optional<SolicitudCreditoEntity> findByIdConUsuario(@Param("id") Long id);
}
