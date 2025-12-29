package com.ersistema.servicio_usuarios.controlador;

import com.ersistema.servicio_usuarios.dto.*;
import com.ersistema.servicio_usuarios.excepcion.BadRequestException;
import com.ersistema.servicio_usuarios.servicio.UsuariosServicio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuariosControlador {

    private final UsuariosServicio usuariosServicio;

    @PostMapping("/auto-registro")
    public ResponseEntity<ResultadoAutoRegistroDto> autoRegistro(
            @RequestHeader(value = "X-Empresa-Id", required = false) String idEmpresaHeader,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (idEmpresaHeader == null || idEmpresaHeader.isBlank()) {
            throw new BadRequestException("Falta el header X-Empresa-Id.");
        }

        final Long idEmpresa;
        try {
            idEmpresa = Long.parseLong(idEmpresaHeader.trim());
        } catch (NumberFormatException e) {
            throw new BadRequestException("El header X-Empresa-Id debe ser un número válido.");
        }

        String keycloakId = jwt.getSubject();
        String nombre = jwt.getClaimAsString("name");
        String email = jwt.getClaimAsString("email");

        ResultadoAutoRegistroDto resultado =
                usuariosServicio.autoRegistrar(keycloakId, nombre, email, idEmpresa);

        return ResponseEntity.ok(resultado);
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('roles:asignar')")
    @PostMapping("/empresas/{idEmpresa}/usuarios/{idUsuario}/roles")
    public ResponseEntity<Void> asignarRoles(
            @PathVariable Long idEmpresa,
            @PathVariable Long idUsuario,
            @Valid @RequestBody AsignarRolesRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        usuariosServicio.validarPerteneceEmpresa(idEmpresa, jwt.getSubject());
        usuariosServicio.asignarRolesEmpresa(idEmpresa, idUsuario, request.getRoles());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('roles:obtener')")
    @GetMapping("/empresas/{idEmpresa}/usuarios/{idUsuario}/roles")
    public ResponseEntity<List<String>> obtenerRoles(
            @PathVariable Long idEmpresa,
            @PathVariable Long idUsuario,
            @AuthenticationPrincipal Jwt jwt
    ) {
        usuariosServicio.validarPerteneceEmpresa(idEmpresa, jwt.getSubject());
        return ResponseEntity.ok(usuariosServicio.obtenerRolesEmpresa(idEmpresa, idUsuario));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/empresas/{idEmpresa}")
    public ResponseEntity<Page<UsuarioEmpresaResumenDto>> listarUsuariosPorEmpresa(
            @PathVariable Long idEmpresa,
            @PageableDefault(size = 10) Pageable pageable,
            @RequestParam(defaultValue = "false") boolean incluirRoles,
            @AuthenticationPrincipal Jwt jwt
    ) {
        usuariosServicio.validarPerteneceEmpresa(idEmpresa, jwt.getSubject());
        return ResponseEntity.ok(
                usuariosServicio.listarUsuariosPorEmpresa(idEmpresa, pageable, incluirRoles)
        );
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('roles:cambiarEstado')")
    @PatchMapping("/empresas/{idEmpresa}/usuarios/{idUsuario}/estado")
    public ResponseEntity<Void> cambiarEstado(
            @PathVariable Long idEmpresa,
            @PathVariable Long idUsuario,
            @Valid @RequestBody CambiarEstadoUsuarioRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        usuariosServicio.validarPerteneceEmpresa(idEmpresa, jwt.getSubject());
        usuariosServicio.cambiarEstadoUsuarioEnEmpresa(idEmpresa, idUsuario, request.getEstado());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('roles:eliminar')")
    @DeleteMapping("/empresas/{idEmpresa}/usuarios/{idUsuario}/roles/{codigoRol}")
    public ResponseEntity<Void> quitarRol(
            @PathVariable Long idEmpresa,
            @PathVariable Long idUsuario,
            @PathVariable String codigoRol,
            @AuthenticationPrincipal Jwt jwt
    ) {
        usuariosServicio.validarPerteneceEmpresa(idEmpresa, jwt.getSubject());
        usuariosServicio.quitarRol(idEmpresa, idUsuario, codigoRol);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN') or hasAuthority('roles:asignar')")
    @PutMapping("/empresas/{idEmpresa}/usuarios/{idUsuario}/roles")
    public ResponseEntity<Void> reemplazarRoles(
            @PathVariable Long idEmpresa,
            @PathVariable Long idUsuario,
            @Valid @RequestBody AsignarRolesRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        usuariosServicio.validarPerteneceEmpresa(idEmpresa, jwt.getSubject());
        usuariosServicio.reemplazarRoles(idEmpresa, idUsuario, request.getRoles());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<PerfilUsuarioDto> miPerfil(
            @RequestParam Long empresaId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String keycloakId = jwt.getSubject();

        // ✅ roles para UI directamente desde el token (client roles de erp-backend)
        List<String> rolesToken = jwt.getClaim("resource_access") instanceof java.util.Map<?,?> ra
                && ra.get("erp-backend") instanceof java.util.Map<?,?> client
                && client.get("roles") instanceof java.util.Collection<?> roles
                ? roles.stream().map(Object::toString).toList()
                : List.of();

        return ResponseEntity.ok(
                usuariosServicio.obtenerMiPerfil(empresaId, keycloakId, rolesToken)
        );
    }
}
