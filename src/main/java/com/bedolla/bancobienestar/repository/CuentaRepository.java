package com.bedolla.bancobienestar.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.bedolla.bancobienestar.entity.CuentaEntity;
import com.bedolla.bancobienestar.entity.UsuarioEntity;

@Repository
public interface CuentaRepository extends JpaRepository<CuentaEntity, Long> {

    // JOIN FETCH: trae el usuario dueño de la cuenta de una sola vez para
    // evitar LazyInitializationException (spring.jpa.open-in-view=false
    // cierra la sesión de Hibernate apenas termina la consulta, y varios
    // controllers necesitan mostrar cuenta.getUsuario().getNombre()).
    @Query("SELECT c FROM CuentaEntity c JOIN FETCH c.usuario WHERE c.clabe = :clabe")
    Optional<CuentaEntity> findByClabe(String clabe);

    List<CuentaEntity> findByUsuario(UsuarioEntity usuario);
    boolean existsByClabe(String clabe);
    boolean existsByNoTarjeta(String noTarjeta);
}
