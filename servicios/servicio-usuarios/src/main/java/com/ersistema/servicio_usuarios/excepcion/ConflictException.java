package com.ersistema.servicio_usuarios.excepcion;

public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}
