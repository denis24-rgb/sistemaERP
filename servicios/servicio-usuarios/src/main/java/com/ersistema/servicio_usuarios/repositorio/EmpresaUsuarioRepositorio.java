package com.ersistema.servicio_usuarios.repositorio;



import com.ersistema.servicio_usuarios.dominio.EmpresaUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface EmpresaUsuarioRepositorio extends JpaRepository<EmpresaUsuario, Long> {

    Optional<EmpresaUsuario> findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(Long idEmpresa, Long idUsuarioErp);
    Page<EmpresaUsuario> findByEmpresa_IdEmpresa(Long idEmpresa, Pageable pageable);
    boolean existsByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(Long idEmpresa, Long idUsuarioErp);

}
