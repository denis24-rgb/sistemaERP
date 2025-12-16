package com.ersistema.servicio_usuarios.repositorio;

import com.ersistema.servicio_usuarios.dominio.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolRepositorio extends JpaRepository<Rol, Long> {

    Optional<Rol> findByCodigo(String codigo);
}
