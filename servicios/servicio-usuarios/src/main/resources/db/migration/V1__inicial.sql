CREATE TABLE empresas (
                          id_empresa BIGSERIAL PRIMARY KEY,
                          nombre VARCHAR(150) NOT NULL,
                          nit VARCHAR(30),
                          razon_social VARCHAR(150),
                          estado BOOLEAN NOT NULL DEFAULT TRUE,
                          fecha_registro TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE usuarios_erp (
                              id_usuario_erp BIGSERIAL PRIMARY KEY,
                              keycloak_id VARCHAR(150) NOT NULL UNIQUE,
                              nombre VARCHAR(100),
                              email VARCHAR(120),
                              estado BOOLEAN NOT NULL DEFAULT TRUE,
                              fecha_registro TIMESTAMP NOT NULL DEFAULT now()
);

-- Membresía: usuario pertenece a empresa
CREATE TABLE empresa_usuario (
                                 id_empresa_usuario BIGSERIAL PRIMARY KEY,
                                 id_empresa BIGINT NOT NULL,
                                 id_usuario_erp BIGINT NOT NULL,
                                 estado BOOLEAN NOT NULL DEFAULT TRUE,
                                 fecha_asignacion TIMESTAMP NOT NULL DEFAULT now(),
                                 CONSTRAINT fk_eu_empresa FOREIGN KEY (id_empresa) REFERENCES empresas(id_empresa),
                                 CONSTRAINT fk_eu_usuario FOREIGN KEY (id_usuario_erp) REFERENCES usuarios_erp(id_usuario_erp),
                                 CONSTRAINT uq_empresa_usuario UNIQUE (id_empresa, id_usuario_erp)
);

-- Roles por empresa (múltiples)
CREATE TABLE roles (
                       id_rol BIGSERIAL PRIMARY KEY,
                       codigo VARCHAR(50) NOT NULL UNIQUE,   -- ADMIN, VENTAS, COMPRAS, CAJERO, etc.
                       nombre VARCHAR(120) NOT NULL,
                       estado BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE empresa_usuario_rol (
                                     id_empresa_usuario_rol BIGSERIAL PRIMARY KEY,
                                     id_empresa_usuario BIGINT NOT NULL,
                                     id_rol BIGINT NOT NULL,
                                     estado BOOLEAN NOT NULL DEFAULT TRUE,
                                     fecha_asignacion TIMESTAMP NOT NULL DEFAULT now(),
                                     CONSTRAINT fk_eur_empresa_usuario FOREIGN KEY (id_empresa_usuario) REFERENCES empresa_usuario(id_empresa_usuario),
                                     CONSTRAINT fk_eur_rol FOREIGN KEY (id_rol) REFERENCES roles(id_rol),
                                     CONSTRAINT uq_empresa_usuario_rol UNIQUE (id_empresa_usuario, id_rol)
);

-- Índices útiles
CREATE INDEX idx_usuario_keycloak ON usuarios_erp(keycloak_id);
CREATE INDEX idx_empresa_usuario_empresa ON empresa_usuario(id_empresa);
CREATE INDEX idx_empresa_usuario_usuario ON empresa_usuario(id_usuario_erp);
CREATE INDEX idx_eur_empresa_usuario ON empresa_usuario_rol(id_empresa_usuario);
CREATE INDEX idx_eur_rol ON empresa_usuario_rol(id_rol);
