package com.ersistema.servicio_usuarios.keycloak;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.ersistema.servicio_usuarios.excepcion.ForbiddenException;
import org.springframework.web.client.RestClientResponseException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    private final KeycloakAdminProperties props;
    private final KeycloakAdminTokenService tokenService;

    private final RestClient restClient = RestClient.create();

    private String adminBase() {
        return props.getBaseUrl() + "/admin/realms/" + props.getRealm();
    }

    private RestClient.RequestHeadersSpec<?> auth(RestClient.RequestHeadersSpec<?> spec) {
        return spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getAccessToken());
    }

    /** Intenta usar endpoint group-by-path (Keycloak moderno). Si falla, lanza. */
    public String getGroupIdByPath(String fullPath) {
        // fullPath ejemplo: "/empresa-1/VENTAS"
        String url = adminBase() + "/group-by-path/" + fullPath.replaceFirst("^/", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> group = (Map<String, Object>) auth(restClient.get().uri(url))
                .retrieve()
                .body(Map.class);

        if (group == null || group.get("id") == null) {
            throw new RuntimeException("No se encontró el grupo por path: " + fullPath);
        }
        return group.get("id").toString();
    }

    /** Crea grupo raíz empresa-{id} si no existe y devuelve su id */
    public String ensureEmpresaRootGroup(Long idEmpresa) {
        String rootPath = "/empresa-" + idEmpresa;

        String existing = tryGetGroupIdByPath(rootPath);
        if (existing != null) return existing;

        String createUrl = adminBase() + "/groups";

        var response = auth(restClient.post().uri(createUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "empresa-" + idEmpresa)))
                .retrieve()
                .toBodilessEntity();

        URI location = response.getHeaders().getLocation();
        if (location == null) throw new RuntimeException("No se obtuvo Location al crear grupo empresa-" + idEmpresa);

        return location.getPath().substring(location.getPath().lastIndexOf('/') + 1);
    }
    /** Crea subgroup ROL si no existe y devuelve id del subgroup */
    public String ensureEmpresaRoleGroup(Long idEmpresa, String rol) {
        String role = rol.trim().toUpperCase();
        String path = "/empresa-" + idEmpresa + "/" + role;

        String existing = tryGetGroupIdByPath(path);
        if (existing != null) return existing;

        String rootId = ensureEmpresaRootGroup(idEmpresa);

        String createChildUrl = adminBase() + "/groups/" + rootId + "/children";

        var response = auth(restClient.post().uri(createChildUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", role)))
                .retrieve()
                .toBodilessEntity();

        URI location = response.getHeaders().getLocation();
        if (location == null) throw new RuntimeException("No se obtuvo Location al crear subgroup " + path);

        return location.getPath().substring(location.getPath().lastIndexOf('/') + 1);
    }
    public void addUserToEmpresaRole(Long idEmpresa, String rol, String userId) {
        String groupId = ensureEmpresaRoleGroup(idEmpresa, rol);
        String url = adminBase() + "/users/" + userId + "/groups/" + groupId;
        safePut(url);
    }

    public void addUserToGroup(String userId, String groupId) {
        String url = adminBase() + "/users/" + userId + "/groups/" + groupId;
        auth(restClient.put().uri(url)).retrieve().toBodilessEntity();
    }

    public void removeUserFromGroup(String userId, String groupId) {
        String url = adminBase() + "/users/" + userId + "/groups/" + groupId;
        auth(restClient.delete().uri(url)).retrieve().toBodilessEntity();
    }
    public void removeUserFromEmpresaRole(Long idEmpresa, String rol, String userId) {
        String path = "/empresa-" + idEmpresa + "/" + rol.trim().toUpperCase();
        String groupId = tryGetGroupIdByPath(path);
        if (groupId == null) return; // si no existe, no pasa nada

        String url = adminBase() + "/users/" + userId + "/groups/" + groupId;
        safeDelete(url);
    }

    /** Quita todos los roles-grupo dentro de empresa-{id} al usuario (para reemplazar roles) */
    public void clearRolesEmpresa(Long idEmpresa, String userId, List<String> rolesPosibles) {
        for (String rol : rolesPosibles) {
            String path = "/empresa-" + idEmpresa + "/" + rol.toUpperCase();
            try {
                String gid = getGroupIdByPath(path);
                removeUserFromGroup(userId, gid);
            } catch (Exception ignored) {}
        }
    }
    /** Agrega usuario a un grupo usando el path (ej: /empresa-1/VENTAS). Crea el grupo si no existe. */
    public void addUserToGroupByPath(String userId, String groupPath) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId es requerido");
        }
        if (groupPath == null || groupPath.isBlank()) {
            throw new IllegalArgumentException("groupPath es requerido");
        }

        // Si es un rol de empresa, garantizamos que exista
        // groupPath esperado: /empresa-{id}/ROL
        String groupId;
        try {
            groupId = getGroupIdByPath(groupPath);
        } catch (Exception ex) {
            // intentamos crearlo si sigue la convención
            // /empresa-1/ADMIN
            String[] parts = groupPath.split("/");
            // parts: ["", "empresa-1", "ADMIN"]
            if (parts.length == 3 && parts[1].startsWith("empresa-")) {
                Long idEmpresa = Long.parseLong(parts[1].replace("empresa-", ""));
                String rol = parts[2];
                groupId = ensureEmpresaRoleGroup(idEmpresa, rol);
            } else {
                throw new RuntimeException("No existe el grupo y no puedo crearlo por convención: " + groupPath, ex);
            }
        }

        addUserToGroup(userId, groupId);
    }

    /** Quita usuario de un grupo usando el path (ej: /empresa-1/VENTAS). */
    public void removeUserFromGroupByPath(String userId, String groupPath) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId es requerido");
        }
        if (groupPath == null || groupPath.isBlank()) {
            throw new IllegalArgumentException("groupPath es requerido");
        }

        String groupId = getGroupIdByPath(groupPath);
        removeUserFromGroup(userId, groupId);
    }

    /** Devuelve los paths de los grupos del usuario. Ej: ["/empresa-1/ADMIN"] */
    @SuppressWarnings("unchecked")
    public List<String> getUserGroupPaths(String userId) {
        String url = adminBase() + "/users/" + userId + "/groups";
        List<Map<String, Object>> groups = (List<Map<String, Object>>) safeGet(url, List.class);
        if (groups == null) return List.of();

        return groups.stream()
                .map(g -> g.get("path"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
    }
    private <T> T safeGet(String url, Class<T> type) {
        try {
            return auth(restClient.get().uri(url)).retrieve().body(type);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 403) {
                throw new ForbiddenException("El service-account de erp-admin no tiene permisos (revisa realm-management: manage-realm/manage-users).");
            }
            throw e;
        }
    }

    private void safePost(String url, Object body) {
        try {
            auth(restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 403) {
                throw new ForbiddenException("El service-account de erp-admin no tiene permisos para crear grupos (manage-realm).");
            }
            throw e;
        }
    }

    private void safePut(String url) {
        try {
            auth(restClient.put().uri(url)).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 403) {
                throw new ForbiddenException("El service-account de erp-admin no tiene permisos para asignar grupos a usuarios (manage-users).");
            }
            throw e;
        }
    }

    private void safeDelete(String url) {
        try {
            auth(restClient.delete().uri(url)).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 403) {
                throw new ForbiddenException("El service-account de erp-admin no tiene permisos para quitar grupos a usuarios (manage-users).");
            }
            throw e;
        }
    }
    public String tryGetGroupIdByPath(String fullPath) {
        String url = adminBase() + "/group-by-path/" + fullPath.replaceFirst("^/", "");
        Map<String, Object> group;
        try {
            group = safeGet(url, Map.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) return null;
            throw e;
        }
        if (group == null || group.get("id") == null) return null;
        return group.get("id").toString();
    }

    @PostConstruct
    void validarConfig() {
        if (props.getBaseUrl() == null || props.getBaseUrl().isBlank()) {
            throw new IllegalStateException("keycloak.admin.base-url NO está configurado");
        }
        if (props.getRealm() == null || props.getRealm().isBlank()) {
            throw new IllegalStateException("keycloak.admin.realm NO está configurado");
        }
        if (props.getClientId() == null || props.getClientId().isBlank()) {
            throw new IllegalStateException("keycloak.admin.client-id NO está configurado");
        }
        if (props.getClientSecret() == null || props.getClientSecret().isBlank()) {
            throw new IllegalStateException("keycloak.admin.client-secret NO está configurado");
        }
    }



}
