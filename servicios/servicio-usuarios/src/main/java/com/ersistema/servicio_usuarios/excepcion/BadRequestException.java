package com.ersistema.servicio_usuarios.excepcion;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
