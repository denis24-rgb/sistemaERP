package com.ersistema.servicio_usuarios.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CambiarEstadoUsuarioRequest {
    @NotNull(message = "El campo estado es obligatorio.")
    private Boolean estado;
}
