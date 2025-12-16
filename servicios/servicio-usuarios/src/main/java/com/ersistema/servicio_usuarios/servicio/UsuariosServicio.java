package com.ersistema.servicio_usuarios.servicio;


import com.ersistema.servicio_usuarios.dominio.*;
import com.ersistema.servicio_usuarios.dto.ResultadoAutoRegistroDto;
import com.ersistema.servicio_usuarios.repositorio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UsuariosServicio {

    private final UsuarioErpRepositorio usuarioRepo;
    private final EmpresaRepositorio empresaRepo;
    private final EmpresaUsuarioRepositorio empresaUsuarioRepo;
    private final EmpresaUsuarioRolRepositorio empresaUsuarioRolRepo;
    private final RolRepositorio rolRepo;

    @Transactional
    public ResultadoAutoRegistroDto autoRegistrar(String keycloakId, String nombre, String email, Long idEmpresa) {

        Empresa empresa = empresaRepo.findById(idEmpresa)
                .orElseThrow(() -> new IllegalArgumentException("La empresa no existe: " + idEmpresa));

        UsuarioErp usuario = usuarioRepo.findByKeycloakId(keycloakId)
                .orElseGet(() -> usuarioRepo.save(
                        UsuarioErp.builder()
                                .keycloakId(keycloakId)
                                .nombre(nombre)
                                .email(email)
                                .estado(true)
                                .build()
                ));

        EmpresaUsuario relacion = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(empresa.getIdEmpresa(), usuario.getIdUsuarioErp())
                .orElseGet(() -> empresaUsuarioRepo.save(
                        EmpresaUsuario.builder()
                                .empresa(empresa)
                                .usuario(usuario)
                                .estado(true)
                                .build()
                ));

        return ResultadoAutoRegistroDto.builder()
                .idUsuarioErp(usuario.getIdUsuarioErp())
                .keycloakId(usuario.getKeycloakId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .idEmpresa(empresa.getIdEmpresa())
                .idEmpresaUsuario(relacion.getIdEmpresaUsuario())
                .build();
    }
    @Transactional
    public void asignarRolesEmpresa(
            Long idEmpresa,
            Long idUsuarioErp,
            List<String> codigosRol
    ) {
        EmpresaUsuario eu = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(idEmpresa, idUsuarioErp)
                .orElseThrow(() ->
                        new IllegalArgumentException("El usuario no pertenece a la empresa"));

        for (String codigo : codigosRol) {
            Rol rol = rolRepo.findByCodigo(codigo)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Rol no existe: " + codigo));

            empresaUsuarioRolRepo
                    .findByEmpresaUsuario_IdEmpresaUsuarioAndRol_IdRol(
                            eu.getIdEmpresaUsuario(),
                            rol.getIdRol()
                    )
                    .ifPresentOrElse(
                            existente -> existente.setEstado(true),
                            () -> empresaUsuarioRolRepo.save(
                                    EmpresaUsuarioRol.builder()
                                            .empresaUsuario(eu)
                                            .rol(rol)
                                            .estado(true)
                                            .build()
                            )
                    );
        }
    }
    @Transactional(readOnly = true)
    public List<String> obtenerRolesEmpresa(Long idEmpresa, Long idUsuarioErp) {

        EmpresaUsuario eu = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(idEmpresa, idUsuarioErp)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no pertenece a la empresa"));

        return empresaUsuarioRolRepo
                .findByEmpresaUsuario_IdEmpresaUsuarioAndEstadoTrue(eu.getIdEmpresaUsuario())
                .stream()
                .map(eur -> eur.getRol().getCodigo())
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean tienePermiso(Long idEmpresa, String keycloakId, String codigoRol) {
        if (keycloakId == null || keycloakId.isBlank()) return false;
        if (codigoRol == null || codigoRol.isBlank()) return false;

        return empresaUsuarioRolRepo
                .existsByEmpresaUsuario_Empresa_IdEmpresaAndEmpresaUsuario_Usuario_KeycloakIdAndRol_CodigoAndEstadoTrue(
                        idEmpresa,
                        keycloakId,
                        codigoRol.toUpperCase()
                );
    }



}
