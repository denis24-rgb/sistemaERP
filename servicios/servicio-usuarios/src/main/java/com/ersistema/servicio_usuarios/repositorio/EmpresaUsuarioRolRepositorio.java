package com.ersistema.servicio_usuarios.repositorio;

import com.ersistema.servicio_usuarios.dominio.EmpresaUsuarioRol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmpresaUsuarioRolRepositorio
        extends JpaRepository<EmpresaUsuarioRol, Long> {

    List<EmpresaUsuarioRol> findByEmpresaUsuario_IdEmpresaUsuarioAndEstadoTrue(
            Long idEmpresaUsuario
    );

    Optional<EmpresaUsuarioRol> findByEmpresaUsuario_IdEmpresaUsuarioAndRol_IdRol(
            Long idEmpresaUsuario,
            Long idRol
    );
    boolean existsByEmpresaUsuario_Empresa_IdEmpresaAndEmpresaUsuario_Usuario_KeycloakIdAndRol_CodigoAndEstadoTrue(
            Long idEmpresa,
            String keycloakId,
            String codigoRol
    );
    boolean existsByEmpresaUsuario_Empresa_IdEmpresaAndRol_CodigoAndEstadoTrue(
            Long idEmpresa,
            String codigoRol
    );


}
