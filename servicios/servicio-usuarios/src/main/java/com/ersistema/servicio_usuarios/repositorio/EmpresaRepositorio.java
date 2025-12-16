package com.ersistema.servicio_usuarios.repositorio;


import com.ersistema.servicio_usuarios.dominio.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpresaRepositorio extends JpaRepository<Empresa, Long> {
}

