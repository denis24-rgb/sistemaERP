package com.ersistema.servicio_usuarios.seguridad;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;

public final class JwtRolesExtractor {

    private JwtRolesExtractor(){}

    public static List<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return List.of();

        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof Collection<?> roles)) return List.of();

        // filtra roles técnicos de keycloak
        Set<String> blacklist = Set.of("offline_access", "uma_authorization", "default-roles-erp");

        return roles.stream()
                .map(Object::toString)
                .map(String::trim)
                .filter(r -> !r.isBlank())
                .filter(r -> !blacklist.contains(r))
                .distinct()
                .toList();
    }
}
