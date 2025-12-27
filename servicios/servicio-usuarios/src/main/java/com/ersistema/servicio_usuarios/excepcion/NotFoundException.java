package com.ersistema.servicio_usuarios.excepcion;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
