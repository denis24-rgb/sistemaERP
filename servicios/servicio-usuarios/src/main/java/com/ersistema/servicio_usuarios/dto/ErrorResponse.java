package com.ersistema.servicio_usuarios.dto;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private OffsetDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String traceId;   // opcional (si luego usas MDC o header)
    private String path;

    // opcional (pero útil) para validar campos
    private Map<String, String> details;

    private List<FieldErrorItem> fieldErrors; // para @Valid

    @Getter @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldErrorItem {
        private String field;
        private String message;
    }
}
