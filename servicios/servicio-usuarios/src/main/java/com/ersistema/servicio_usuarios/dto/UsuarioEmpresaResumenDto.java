package com.ersistema.servicio_usuarios.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class UsuarioEmpresaResumenDto {
    private Long idUsuario;
    private String nombre;
    private String email;
    private Boolean estado;   // ← estado = activo
    private List<String> roles;
}
