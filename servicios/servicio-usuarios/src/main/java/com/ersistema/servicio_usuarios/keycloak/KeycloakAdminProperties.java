package com.ersistema.servicio_usuarios.keycloak;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "keycloak.admin")
public class KeycloakAdminProperties {
    private String baseUrl;      // http://localhost:8080
    private String realm;        // micro
    private String clientId;     // erp-admin
    private String clientSecret; // env var
    private String username;
    private String password;
}
