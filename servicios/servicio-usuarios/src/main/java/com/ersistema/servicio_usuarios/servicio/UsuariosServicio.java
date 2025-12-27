package com.ersistema.servicio_usuarios.servicio;


import com.ersistema.servicio_usuarios.dominio.*;
import com.ersistema.servicio_usuarios.dto.CrearEmpresaRequest;
import com.ersistema.servicio_usuarios.dto.PerfilUsuarioDto;
import com.ersistema.servicio_usuarios.dto.ResultadoAutoRegistroDto;
import com.ersistema.servicio_usuarios.excepcion.ConflictException;
import com.ersistema.servicio_usuarios.excepcion.ForbiddenException;
import com.ersistema.servicio_usuarios.repositorio.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ersistema.servicio_usuarios.excepcion.NotFoundException;
import com.ersistema.servicio_usuarios.excepcion.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.ersistema.servicio_usuarios.dto.UsuarioEmpresaResumenDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UsuariosServicio {

    private final UsuarioErpRepositorio usuarioRepo;
    private final EmpresaRepositorio empresaRepo;
    private final EmpresaUsuarioRepositorio empresaUsuarioRepo;
    private final EmpresaUsuarioRolRepositorio empresaUsuarioRolRepo;
    private final RolRepositorio rolRepo;

    @Transactional
    public ResultadoAutoRegistroDto autoRegistrar(
            String keycloakId,
            String nombre,
            String email,
            Long idEmpresa,
            List<String> rolesKeycloak
    ) {

        // 0) Validaciones mínimas
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new BadRequestException("Token inválido: sub vacío.");
        }
        if (idEmpresa == null) {
            throw new BadRequestException("idEmpresa es obligatorio.");
        }

        // 1) Validar empresa
        Empresa empresa = empresaRepo.findById(idEmpresa)
                .orElseThrow(() -> new NotFoundException("La empresa no existe: " + idEmpresa));

        // 2) Crear u obtener usuario ERP
        UsuarioErp usuario = usuarioRepo.findByKeycloakId(keycloakId)
                .orElseGet(() -> usuarioRepo.save(
                        UsuarioErp.builder()
                                .keycloakId(keycloakId)
                                .nombre(nombre)
                                .email(email)
                                .estado(true)
                                .build()
                ));

        // 3) Crear u obtener relación empresa_usuario
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

        // Si existe la relación pero estaba inactiva, la reactivamos
        if (!Boolean.TRUE.equals(empresaUsuario.getEstado())) {
            empresaUsuario.setEstado(true);
        }

        // 4) Guardar SOLO roles del token (ej: ADMIN) en la BD para ESTA empresa
        if (rolesKeycloak != null && !rolesKeycloak.isEmpty()) {

            // Normalizamos roles de negocio (MAYÚSCULAS)
            List<String> rolesNormalizados = rolesKeycloak.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::trim)
                    .filter(r -> !r.isBlank())
                    .map(String::toUpperCase)
                    .distinct()
                    .toList();

            for (String codigo : rolesNormalizados) {

                // Upsert en tabla Rol (crea si no existe)
                Rol rol = rolRepo.findByCodigo(codigo)
                        .orElseGet(() -> {
                            // ⚠️ Ajusta aquí si tu entidad Rol requiere más campos obligatorios
                            return rolRepo.save(
                                    Rol.builder()
                                            .codigo(codigo)
                                            .build()
                            );
                        });

                // Upsert relación EmpresaUsuarioRol
                empresaUsuarioRolRepo
                        .findByEmpresaUsuario_IdEmpresaUsuarioAndRol_IdRol(
                                empresaUsuario.getIdEmpresaUsuario(),
                                rol.getIdRol()
                        )
                        .ifPresentOrElse(
                                existente -> existente.setEstado(true),
                                () -> empresaUsuarioRolRepo.save(
                                        EmpresaUsuarioRol.builder()
                                                .empresaUsuario(empresaUsuario)
                                                .rol(rol)
                                                .estado(true)
                                                .build()
                                )
                        );
            }
        }
        // 5) Respuesta
        return ResultadoAutoRegistroDto.builder()
                .idUsuarioErp(usuario.getIdUsuarioErp())
                .keycloakId(usuario.getKeycloakId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .idEmpresa(empresa.getIdEmpresa())
                .idEmpresaUsuario(empresaUsuario.getIdEmpresaUsuario())
                .build();
    }


    @Transactional
    public void asignarRolesEmpresa(
            Long idEmpresa,
            Long idUsuarioErp,
            List<String> codigosRol
    ) {
        // ✅ valida + normaliza
        codigosRol = normalizarRoles(codigosRol);

        EmpresaUsuario eu = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(idEmpresa, idUsuarioErp)
                .orElseThrow(() -> new NotFoundException("El usuario no pertenece a la empresa"));
        if (!Boolean.TRUE.equals(eu.getEstado())) {
            throw new BadRequestException("El usuario está inactivo en la empresa.");
        }

        for (String codigo : codigosRol) {
            Rol rol = rolRepo.findByCodigo(codigo)
                    .orElseThrow(() -> new NotFoundException("Rol no existe: " + codigo));

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
                .orElseThrow(() -> new NotFoundException("El usuario no pertenece a la empresa"));

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

        // Si el usuario mandó roles pero todos eran basura (espacios/null), aquí lo detectas.
        // Si quieres ser más estricto y detectar cualquier rol inválido:
        long invalidos = roles.stream().filter(r -> r == null || r.trim().isEmpty()).count();
        if (invalidos > 0) {
            throw new BadRequestException("La lista de roles contiene valores vacíos o nulos.");
        }

        return normalizados;
    }
    @Transactional(readOnly = true)
    public Page<UsuarioEmpresaResumenDto> listarUsuariosPorEmpresa(Long idEmpresa, Pageable pageable, boolean incluirRoles) {

        // valida que exista empresa (404)
        empresaRepo.findById(idEmpresa)
                .orElseThrow(() -> new NotFoundException("La empresa no existe: " + idEmpresa));

        Page<EmpresaUsuario> page = empresaUsuarioRepo.findByEmpresa_IdEmpresa(idEmpresa, pageable);

        return page.map(eu -> {
            UsuarioErp u = eu.getUsuario();

            List<String> roles = null;
            if (incluirRoles) {
                roles = empresaUsuarioRolRepo
                        .findByEmpresaUsuario_IdEmpresaUsuarioAndEstadoTrue(eu.getIdEmpresaUsuario())
                        .stream()
                        .map(eur -> eur.getRol().getCodigo())
                        .toList();
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
    @Transactional
    public void cambiarEstadoUsuarioEnEmpresa(Long idEmpresa, Long idUsuarioErp, Boolean estado) {
        if (estado == null) {
            throw new BadRequestException("El campo estado es obligatorio.");
        }

        EmpresaUsuario eu = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(idEmpresa, idUsuarioErp)
                .orElseThrow(() -> new NotFoundException("El usuario no pertenece a la empresa"));

        eu.setEstado(estado);
    }
    @Transactional
    public void quitarRol(Long idEmpresa, Long idUsuarioErp, String codigoRol) {
        if (codigoRol == null || codigoRol.isBlank()) {
            throw new BadRequestException("codigoRol no puede estar vacío.");
        }
        String rolNorm = codigoRol.trim().toUpperCase();

        EmpresaUsuario eu = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(idEmpresa, idUsuarioErp)
                .orElseThrow(() -> new NotFoundException("El usuario no pertenece a la empresa"));

        Rol rol = rolRepo.findByCodigo(rolNorm)
                .orElseThrow(() -> new NotFoundException("Rol no existe: " + rolNorm));

        EmpresaUsuarioRol eur = empresaUsuarioRolRepo
                .findByEmpresaUsuario_IdEmpresaUsuarioAndRol_IdRol(eu.getIdEmpresaUsuario(), rol.getIdRol())
                .orElseThrow(() -> new NotFoundException("El usuario no tiene asignado el rol: " + rolNorm));

        eur.setEstado(false);
    }
    @Transactional
    public void reemplazarRoles(Long idEmpresa, Long idUsuarioErp, List<String> roles) {

        List<String> nuevos = normalizarRoles(roles);

        EmpresaUsuario eu = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(idEmpresa, idUsuarioErp)
                .orElseThrow(() -> new NotFoundException("El usuario no pertenece a la empresa"));

        // 1) desactivar todos
        empresaUsuarioRolRepo
                .findByEmpresaUsuario_IdEmpresaUsuarioAndEstadoTrue(eu.getIdEmpresaUsuario())
                .forEach(eur -> eur.setEstado(false));

        // 2) activar/asignar los nuevos
        for (String codigo : nuevos) {
            Rol rol = rolRepo.findByCodigo(codigo)
                    .orElseThrow(() -> new NotFoundException("Rol no existe: " + codigo));

            empresaUsuarioRolRepo
                    .findByEmpresaUsuario_IdEmpresaUsuarioAndRol_IdRol(eu.getIdEmpresaUsuario(), rol.getIdRol())
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
    public PerfilUsuarioDto obtenerMiPerfil(Long idEmpresa, String keycloakId) {
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new BadRequestException("Token inválido: sub vacío.");
        }

        UsuarioErp usuario = usuarioRepo.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new NotFoundException("El usuario no está registrado en el ERP."));

        EmpresaUsuario eu = empresaUsuarioRepo
                .findByEmpresa_IdEmpresaAndUsuario_IdUsuarioErp(idEmpresa, usuario.getIdUsuarioErp())
                .orElseThrow(() -> new NotFoundException("El usuario no pertenece a la empresa"));

        List<String> roles = empresaUsuarioRolRepo
                .findByEmpresaUsuario_IdEmpresaUsuarioAndEstadoTrue(eu.getIdEmpresaUsuario())
                .stream()
                .map(eur -> eur.getRol().getCodigo())
                .toList();

        return PerfilUsuarioDto.builder()
                .idUsuario(usuario.getIdUsuarioErp())
                .keycloakId(usuario.getKeycloakId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .idEmpresa(idEmpresa)
                .estado(eu.getEstado())
                .roles(roles)
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
    @Transactional
    public Long crearEmpresa(CrearEmpresaRequest request) {
        String nombre = request.getNombre().trim();

        // (opcional) evitar duplicados por nombre si quieres
        boolean existe = empresaRepo.existsByNombreIgnoreCase(nombre);
         if (existe) throw new ConflictException("Ya existe una empresa con ese nombre.");

        Empresa empresa = Empresa.builder()
                .nombre(nombre)
                // si tienes estos campos en la entidad Empresa
                .nit(request.getNit())
                .razonSocial(request.getRazonSocial())
                .estado(true)
                .fechaRegistro(LocalDateTime.now())
                .build();

        Empresa guardada = empresaRepo.save(empresa);
        return guardada.getIdEmpresa();
    }
//    @Transactional(readOnly = true)
//    public void exigirAdminEmpresa(Long idEmpresa, String keycloakId) {
//
//        boolean esAdmin = empresaUsuarioRolRepo
//                .existsByEmpresaUsuario_Empresa_IdEmpresaAndEmpresaUsuario_Usuario_KeycloakIdAndRol_CodigoAndEstadoTrue(
//                        idEmpresa,
//                        keycloakId,
//                        "ADMIN"
//                );
//
//        if (!esAdmin) {
//            throw new ForbiddenException(
//                    "Solo un ADMIN de la empresa puede realizar esta acción."
//            );
//        }
//    }
//    @Transactional(readOnly = true)
//    public boolean tienePermisoToken(Jwt jwt, String permiso) {
//        if (permiso == null || permiso.isBlank()) return false;
//
//        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
//        if (resourceAccess == null) return false;
//
//        Object clientObj = resourceAccess.get("erp-backend");
//        if (!(clientObj instanceof Map<?, ?> clientMap)) return false;
//
//        Object rolesObj = clientMap.get("roles");
//        if (!(rolesObj instanceof java.util.Collection<?> roles)) return false;
//
//        return roles.stream().anyMatch(r -> permiso.equalsIgnoreCase(r.toString()));
//    }












}
