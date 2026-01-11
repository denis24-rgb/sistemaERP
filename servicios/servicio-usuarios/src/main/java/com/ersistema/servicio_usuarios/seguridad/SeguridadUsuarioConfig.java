package com.ersistema.servicio_usuarios.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.*;

@Configuration
@EnableMethodSecurity
public class SeguridadUsuarioConfig {

    private static final String CLIENT_ID = "erp-backend"; // Debe coincidir con resource_access.<clientId>
    private static final String GROUPS_CLAIM = "groups";

    @Bean
    public SecurityFilterChain seguridad(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Swagger / OpenAPI
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Actuator (en dev ok; en prod lo restringimos)
                        .requestMatchers("/actuator/**").permitAll()

                        // todo lo demás protegido
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                .build();
    }

    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {

        // scopes -> SCOPE_profile, SCOPE_email, etc (opcional)
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthorityPrefix("SCOPE_");
        scopes.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new HashSet<>();

            // 1) scopes
            Collection<GrantedAuthority> fromScopes = scopes.convert(jwt);
            if (fromScopes != null) authorities.addAll(fromScopes);

            // 2) CLIENT ROLES: resource_access[CLIENT_ID].roles
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess != null) {
                Object clientObj = resourceAccess.get(CLIENT_ID);
                if (clientObj instanceof Map<?, ?> clientMap) {
                    Object clientRolesObj = clientMap.get("roles");
                    if (clientRolesObj instanceof Collection<?> clientRoles) {
                        for (Object r : clientRoles) {
                            String roleOrPerm = r.toString();

                            // Roles tipo ADMIN, VENTAS, COMPRAS -> ROLE_*
                            if (roleOrPerm.matches("^[A-Z0-9_]+$")) {
                                authorities.add(new SimpleGrantedAuthority("ROLE_" + roleOrPerm));
                            }

                            // Siempre agregarlo como authority plano (para permisos tipo ventas:write, roles:asignar)
                            authorities.add(new SimpleGrantedAuthority(roleOrPerm));
                        }
                    }
                }
            }

            // 3) GROUPS: "/empresa-<id>/<ROL>" -> "EMPRESA_<id>_<ROL>"
            Object groupsObj = jwt.getClaim(GROUPS_CLAIM);
            if (groupsObj instanceof Collection<?> groups) {
                for (Object g : groups) {
                    if (g == null) continue;
                    String groupPath = g.toString().trim();
                    String empresaAuthority = parseEmpresaAuthority(groupPath);
                    if (empresaAuthority != null) {
                        authorities.add(new SimpleGrantedAuthority(empresaAuthority));
                    }
                }
            }

            return authorities;
        });

        return converter;
    }

    /**
     * Convierte: "/empresa-3/VENTAS" -> "EMPRESA_3_VENTAS"
     * Retorna null si no cumple el formato esperado.
     */
    private String parseEmpresaAuthority(String groupPath) {
        if (groupPath == null || groupPath.isBlank()) return null;

        // Acepta con o sin slash inicial
        String normalized = groupPath.startsWith("/") ? groupPath.substring(1) : groupPath;

        // Esperamos: empresa-<id>/<ROL>
        String[] parts = normalized.split("/");
        if (parts.length != 2) return null;

        String empresaPart = parts[0]; // "empresa-3"
        String rolPart = parts[1];     // "VENTAS" o "ADMIN"

        if (!empresaPart.startsWith("empresa-")) return null;

        String idStr = empresaPart.substring("empresa-".length());
        if (idStr.isBlank()) return null;

        // Seguridad básica: rol en mayúsculas/números/guión bajo
        if (!rolPart.matches("^[A-Z0-9_]+$")) return null;

        return "EMPRESA_" + idStr + "_" + rolPart;
    }
}
