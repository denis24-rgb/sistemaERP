package com.ersistema.servicio_usuarios.excepcion;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
