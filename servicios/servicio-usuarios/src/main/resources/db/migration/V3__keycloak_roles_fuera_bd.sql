-- V3: Keycloak maneja roles/permisos por empresa (groups).
-- Este microservicio solo guarda membresía (empresa_usuario) y perfil básico (usuarios_erp).

-- 1) Eliminar tablas de roles locales (si existen)
--    Importante: primero la tabla hija, luego la padre.

DROP TABLE IF EXISTS empresa_usuario_rol;
DROP TABLE IF EXISTS roles;

-- 2) Limpiar índices que ya no aplican (por si existían)
DROP INDEX IF EXISTS idx_eur_empresa_usuario;
DROP INDEX IF EXISTS idx_eur_rol;

-- 3) Reforzar índices útiles para membresía (opcional pero recomendado)
--    (No fallan si ya existen)

CREATE INDEX IF NOT EXISTS idx_usuario_keycloak ON usuarios_erp(keycloak_id);
CREATE INDEX IF NOT EXISTS idx_empresa_usuario_empresa ON empresa_usuario(id_empresa);
CREATE INDEX IF NOT EXISTS idx_empresa_usuario_usuario ON empresa_usuario(id_usuario_erp);

-- Útil para listados/admin:
CREATE INDEX IF NOT EXISTS idx_empresa_usuario_empresa_estado ON empresa_usuario(id_empresa, estado);

-- 4) (Opcional) Asegurar defaults en empresa_usuario (si tu V1 ya lo tiene, no cambia nada)
ALTER TABLE empresa_usuario
    ALTER COLUMN estado SET DEFAULT TRUE;

ALTER TABLE empresa_usuario
    ALTER COLUMN fecha_asignacion SET DEFAULT now();
