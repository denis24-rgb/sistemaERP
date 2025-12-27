package com.ersistema.servicio_usuarios.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrearEmpresaRequest {
    @NotBlank(message = "nombre es obligatorio")
    private String nombre;

    // opcionales (si tu entidad tiene estos campos)
    private String nit;
    private String razonSocial;

}
