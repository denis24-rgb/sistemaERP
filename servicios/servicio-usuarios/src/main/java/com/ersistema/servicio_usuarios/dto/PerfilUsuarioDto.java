package com.ersistema.servicio_usuarios.dto;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PerfilUsuarioDto {
    private Long idUsuario;
    private String keycloakId;
    private String nombre;
    private String email;
    private Long idEmpresa;
    private Boolean estado;
    private List<String> roles;
}
