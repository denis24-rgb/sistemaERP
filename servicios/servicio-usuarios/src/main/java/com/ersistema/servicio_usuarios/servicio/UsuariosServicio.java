package com.ersistema.servicio_usuarios.servicio;

import com.ersistema.servicio_usuarios.dominio.Empresa;
import com.ersistema.servicio_usuarios.dominio.EmpresaUsuario;
import com.ersistema.servicio_usuarios.dominio.UsuarioErp;
import com.ersistema.servicio_usuarios.dto.CrearEmpresaRequest;
import com.ersistema.servicio_usuarios.dto.PerfilUsuarioDto;
import com.ersistema.servicio_usuarios.dto.ResultadoAutoRegistroDto;
import com.ersistema.servicio_usuarios.dto.UsuarioEmpresaResumenDto;
import com.ersistema.servicio_usuarios.excepcion.BadRequestException;
import com.ersistema.servicio_usuarios.excepcion.ConflictException;
import com.ersistema.servicio_usuarios.excepcion.ForbiddenException;
import com.ersistema.servicio_usuarios.excepcion.NotFoundException;
import com.ersistema.servicio_usuarios.keycloak.KeycloakAdminService;
import com.ersistema.servicio_usuarios.repositorio.EmpresaRepositorio;
import com.ersistema.servicio_usuarios.repositorio.EmpresaUsuarioRepositorio;
import com.ersistema.servicio_usuarios.repositorio.UsuarioErpRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UsuariosServicio {

    private final UsuarioErpRepositorio usuarioRepo;
    private final EmpresaRepositorio empresaRepo;
    private final EmpresaUsuarioRepositorio empresaUsuarioRepo;

    // ✅ Keycloak Admin API (tu clase)
    private final KeycloakAdminService keycloakAdminService;

    // =========================
    // AUTO-REGISTRO (membresía)
    // =========================
    @Transactional
    public ResultadoAutoRegistroDto autoRegistrar(
            String keycloakId,
            String nombre,
            String email,
            Long idEmpresa
    ) {
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new BadRequestException("Token inválido: sub vacío.");
        }
        if (idEmpresa == null) {
            throw new BadRequestException("idEmpresa es obligatorio.");
        }

        Empresa empresa = empresaRepo.findById(idEmpresa)
                .orElseThrow(() -> new NotFoundException("La empresa no existe: " + idEmpresa));

        UsuarioErp usuario = usuarioRepo.findByKeycloakId(keycloakId)
                .orElseGet(() -> usuarioRepo.save(
                        UsuarioErp.builder()
                                .keycloakId(keycloakId)
                                .nombre(nombre)
                                .email(email)
                                .estado(true)
                                .build()
                ));

        EmpresaUsuario empresaUsuario = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(
                        empresa.getIdEmpresa(),
                        usuario.getIdUsuarioErp()
                )
                .orElseGet(() -> empresaUsuarioRepo.save(
                        EmpresaUsuario.builder()
                                .empresa(empresa)
                                .usuario(usuario)
                                .estado(true)
                                .build()
                ));

        if (!Boolean.TRUE.equals(empresaUsuario.getEstado())) {
            empresaUsuario.setEstado(true);
        }

        return ResultadoAutoRegistroDto.builder()
                .idUsuarioErp(usuario.getIdUsuarioErp())
                .keycloakId(usuario.getKeycloakId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .idEmpresa(empresa.getIdEmpresa())
                .idEmpresaUsuario(empresaUsuario.getIdEmpresaUsuario())
                .build();
    }

    // =========================
    // ROLES POR EMPRESA (Keycloak)
    // =========================

    @Transactional
    public void asignarRolesEmpresa(Long idEmpresa, Long idUsuarioErp, List<String> roles) {
        List<String> nuevos = normalizarRoles(roles);

        EmpresaUsuario eu = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(idEmpresa, idUsuarioErp)
                .orElseThrow(() -> new NotFoundException("El usuario no pertenece a la empresa"));

        if (!Boolean.TRUE.equals(eu.getEstado())) {
            throw new BadRequestException("El usuario está inactivo en la empresa.");
        }

        String keycloakUserId = eu.getUsuario().getKeycloakId(); // debe ser UUID (sub)
        for (String rol : nuevos) {
            keycloakAdminService.addUserToEmpresaRole(idEmpresa, rol, keycloakUserId);
        }
    }

    @Transactional(readOnly = true)
    public List<String> obtenerRolesEmpresa(Long idEmpresa, Long idUsuarioErp) {
        EmpresaUsuario eu = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(idEmpresa, idUsuarioErp)
                .orElseThrow(() -> new NotFoundException("El usuario no pertenece a la empresa"));

        String keycloakIdTarget = eu.getUsuario().getKeycloakId();
        List<String> groups = keycloakAdminService.getUserGroupPaths(keycloakIdTarget);

        String prefix = "/empresa-" + idEmpresa + "/";
        return groups.stream()
                .filter(g -> g != null && g.startsWith(prefix))
                .map(g -> g.substring(prefix.length()))
                .filter(r -> !r.isBlank())
                .distinct()
                .toList();
    }

    @Transactional
    public void quitarRol(Long idEmpresa, Long idUsuarioErp, String codigoRol) {
        if (codigoRol == null || codigoRol.isBlank()) {
            throw new BadRequestException("codigoRol no puede estar vacío.");
        }

        EmpresaUsuario eu = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(idEmpresa, idUsuarioErp)
                .orElseThrow(() -> new NotFoundException("El usuario no pertenece a la empresa"));

        String keycloakUserId = eu.getUsuario().getKeycloakId();
        keycloakAdminService.removeUserFromEmpresaRole(idEmpresa, codigoRol, keycloakUserId);
    }

    @Transactional
    public void reemplazarRoles(Long idEmpresa, Long idUsuarioErp, List<String> roles) {
        List<String> nuevos = normalizarRoles(roles);

        EmpresaUsuario eu = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(idEmpresa, idUsuarioErp)
                .orElseThrow(() -> new NotFoundException("El usuario no pertenece a la empresa"));

        String keycloakIdTarget = eu.getUsuario().getKeycloakId();
        String prefix = "/empresa-" + idEmpresa + "/";

        // 1) quitar roles actuales de esa empresa
        List<String> actuales = keycloakAdminService.getUserGroupPaths(keycloakIdTarget);
        actuales.stream()
                .filter(g -> g != null && g.startsWith(prefix))
                .filter(g -> g.split("/").length == 3)
                .forEach(g -> {
                    String rol = g.substring(prefix.length());
                    keycloakAdminService.removeUserFromEmpresaRole(idEmpresa, rol, keycloakIdTarget);
                });
        // 2) asignar nuevos
        for (String rol : nuevos) {
            keycloakAdminService.addUserToGroupByPath(keycloakIdTarget, prefix + rol);
        }
    }

    // =========================
    // MEMBRESÍA / PERFIL
    // =========================

    @Transactional(readOnly = true)
    public PerfilUsuarioDto obtenerMiPerfil(Long idEmpresa, String keycloakId, List<String> rolesDesdeToken) {
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new BadRequestException("Token inválido: sub vacío.");
        }

        UsuarioErp usuario = usuarioRepo.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new NotFoundException("El usuario no está registrado en el ERP."));

        EmpresaUsuario eu = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(idEmpresa, usuario.getIdUsuarioErp())
                .orElseThrow(() -> new NotFoundException("El usuario no pertenece a la empresa"));

        return PerfilUsuarioDto.builder()
                .idUsuario(usuario.getIdUsuarioErp())
                .keycloakId(usuario.getKeycloakId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .idEmpresa(idEmpresa)
                .estado(eu.getEstado())
                // ✅ aquí NO se consulta BD, viene del token (o null si no lo mandas)
                .roles(rolesDesdeToken)
                .build();
    }

    @Transactional(readOnly = true)
    public void validarPerteneceEmpresa(Long idEmpresa, String keycloakId) {
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new BadRequestException("Token inválido: sub vacío.");
        }

        UsuarioErp usuario = usuarioRepo.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new NotFoundException("El usuario no está registrado en el ERP."));

        EmpresaUsuario eu = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(idEmpresa, usuario.getIdUsuarioErp())
                .orElseThrow(() -> new ForbiddenException("No tienes acceso a la empresa: " + idEmpresa));

        if (!Boolean.TRUE.equals(eu.getEstado())) {
            throw new ForbiddenException("Tu usuario está inactivo en la empresa: " + idEmpresa);
        }
    }

    @Transactional(readOnly = true)
    public Page<UsuarioEmpresaResumenDto> listarUsuariosPorEmpresa(Long idEmpresa, Pageable pageable, boolean incluirRoles) {
        empresaRepo.findById(idEmpresa)
                .orElseThrow(() -> new NotFoundException("La empresa no existe: " + idEmpresa));

        Page<EmpresaUsuario> page = empresaUsuarioRepo.findByEmpresa_IdEmpresa(idEmpresa, pageable);

        return page.map(eu -> {
            UsuarioErp u = eu.getUsuario();
            List<String> roles = null;

            if (incluirRoles) {
                // ⚠️ Esto hace llamadas a Keycloak por usuario (OK para admin y pocas filas).
                // En producción lo optimizas con cache o batch.
                roles = obtenerRolesPorKeycloakGroupPaths(idEmpresa, u.getKeycloakId());
            }

            return UsuarioEmpresaResumenDto.builder()
                    .idUsuario(u.getIdUsuarioErp())
                    .nombre(u.getNombre())
                    .email(u.getEmail())
                    .estado(eu.getEstado())
                    .roles(roles)
                    .build();
        });
    }

    private List<String> obtenerRolesPorKeycloakGroupPaths(Long idEmpresa, String keycloakId) {
        List<String> groups = keycloakAdminService.getUserGroupPaths(keycloakId);
        String prefix = "/empresa-" + idEmpresa + "/";
        return groups.stream()
                .filter(g -> g != null && g.startsWith(prefix))
                .map(g -> g.substring(prefix.length()))
                .filter(r -> !r.isBlank())
                .distinct()
                .toList();
    }

    @Transactional
    public void cambiarEstadoUsuarioEnEmpresa(Long idEmpresa, Long idUsuarioErp, Boolean estado) {
        if (estado == null) throw new BadRequestException("El campo estado es obligatorio.");

        EmpresaUsuario eu = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(idEmpresa, idUsuarioErp)
                .orElseThrow(() -> new NotFoundException("El usuario no pertenece a la empresa"));

        eu.setEstado(estado);
    }

    @Transactional
    public Long crearEmpresa(CrearEmpresaRequest request) {
        String nombre = request.getNombre().trim();

        boolean existe = empresaRepo.existsByNombreIgnoreCase(nombre);
        if (existe) throw new ConflictException("Ya existe una empresa con ese nombre.");

        Empresa empresa = Empresa.builder()
                .nombre(nombre)
                .nit(request.getNit())
                .razonSocial(request.getRazonSocial())
                .estado(true)
                .fechaRegistro(LocalDateTime.now())
                .build();

        return empresaRepo.save(empresa).getIdEmpresa();
    }

    private List<String> normalizarRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new BadRequestException("La lista de roles no puede estar vacía.");
        }

        List<String> normalizados = roles.stream()
                .map(r -> r == null ? "" : r.trim())
                .map(String::toUpperCase)
                .filter(r -> !r.isBlank())
                .distinct()
                .toList();

        if (normalizados.isEmpty()) {
            throw new BadRequestException("La lista de roles no puede contener valores vacíos.");
        }
        return normalizados;
    }
}
