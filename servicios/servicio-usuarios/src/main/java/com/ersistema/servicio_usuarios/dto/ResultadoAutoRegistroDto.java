package com.ersistema.servicio_usuarios.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultadoAutoRegistroDto {
    private Long idUsuarioErp;
    private String keycloakId;
    private String nombre;
    private String email;
    private Long idEmpresa;
    private Long idEmpresaUsuario;
}

