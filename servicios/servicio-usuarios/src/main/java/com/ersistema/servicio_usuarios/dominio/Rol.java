package com.ersistema.servicio_usuarios.dominio;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Long idRol;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo; // ADMIN, VENTAS, CAJERO

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false)
    @Builder.Default
    private Boolean estado = true;
}
