package com.ersistema.servicio_usuarios.dto;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AsignarRolesRequest {
    @NotEmpty(message = "La lista de roles no puede estar vacía.")
    private List<String> roles;
}
