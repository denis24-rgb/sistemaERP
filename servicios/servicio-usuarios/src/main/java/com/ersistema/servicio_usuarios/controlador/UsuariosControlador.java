package com.ersistema.servicio_usuarios.controlador;

import com.ersistema.servicio_usuarios.dto.PermisoRespuestaDto;
import com.ersistema.servicio_usuarios.dto.ResultadoAutoRegistroDto;
import com.ersistema.servicio_usuarios.servicio.UsuariosServicio;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuariosControlador {

    private final UsuariosServicio usuariosServicio;

    @PostMapping("/auto-registro")
    public ResponseEntity<ResultadoAutoRegistroDto> autoRegistro(
            @RequestHeader("X-Empresa-Id") Long idEmpresa,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String keycloakId = jwt.getSubject();           // sub
        String nombre = jwt.getClaimAsString("name");   // puede ser null
        String email = jwt.getClaimAsString("email");   // puede ser null

        ResultadoAutoRegistroDto resultado = usuariosServicio.autoRegistrar(keycloakId, nombre, email, idEmpresa);
        return ResponseEntity.ok(resultado);
    }
    @PostMapping("/empresas/{idEmpresa}/usuarios/{idUsuario}/roles")
    public ResponseEntity<Void> asignarRoles(
            @PathVariable Long idEmpresa,
            @PathVariable Long idUsuario,
            @RequestBody List<String> roles
    ) {
        usuariosServicio.asignarRolesEmpresa(idEmpresa, idUsuario, roles);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/empresas/{idEmpresa}/usuarios/{idUsuario}/roles")
    public ResponseEntity<List<String>> obtenerRoles(
            @PathVariable Long idEmpresa,
            @PathVariable Long idUsuario
    ) {
        return ResponseEntity.ok(
                usuariosServicio.obtenerRolesEmpresa(idEmpresa, idUsuario)
        );
    }

    @GetMapping("/empresas/{idEmpresa}/permisos/{codigoRol}")
    public ResponseEntity<PermisoRespuestaDto> validarPermiso(
            @PathVariable Long idEmpresa,
            @PathVariable String codigoRol,
            @AuthenticationPrincipal Jwt jwt
    ) {
        // Keycloak: el subject (sub) es el id del usuario en Keycloak
        String keycloakId = jwt.getSubject();

        boolean permitido = usuariosServicio.tienePermiso(idEmpresa, keycloakId, codigoRol);

        return ResponseEntity.ok(PermisoRespuestaDto.builder()
                .permitido(permitido)
                .build());
    }

}

