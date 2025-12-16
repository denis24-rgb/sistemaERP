package com.ersistema.servicio_usuarios.repositorio;



import com.ersistema.servicio_usuarios.dominio.EmpresaUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmpresaUsuarioRepositorio extends JpaRepository<EmpresaUsuario, Long> {

    Optional<EmpresaUsuario> findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(Long idEmpresa, Long idUsuarioErp);
}
