package com.ersistema.servicio_usuarios.keycloak;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KeycloakAdminTokenService {

    private final KeycloakAdminProperties props;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    private final RestClient restClient = RestClient.create();

    public String getAccessToken() {
        // cache simple
        if (cachedToken != null && Instant.now().isBefore(expiresAt.minusSeconds(30))) {
            return cachedToken;
        }

        String tokenUrl = props.getBaseUrl()
                + "/realms/" + props.getRealm()
                + "/protocol/openid-connect/token";

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = restClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials"
                        + "&client_id=" + props.getClientId()
                        + "&client_secret=" + props.getClientSecret())
                .retrieve()
                .body(Map.class);

        if (resp == null || resp.get("access_token") == null) {
            throw new RuntimeException("No se pudo obtener access_token del service account erp-admin.");
        }

        cachedToken = resp.get("access_token").toString();
        long expiresIn = Long.parseLong(resp.getOrDefault("expires_in", "60").toString());
        expiresAt = Instant.now().plusSeconds(expiresIn);

        return cachedToken;
    }
}
