package com.ersistema.servicio_usuarios.repositorio;


import com.ersistema.servicio_usuarios.dominio.UsuarioErp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioErpRepositorio extends JpaRepository<UsuarioErp, Long> {
    Optional<UsuarioErp> findByKeycloakId(String keycloakId);
}

