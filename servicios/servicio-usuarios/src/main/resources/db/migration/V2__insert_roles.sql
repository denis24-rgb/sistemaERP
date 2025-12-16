INSERT INTO roles (codigo, nombre)
VALUES
    ('ADMIN', 'Administrador del sistema'),
    ('VENTAS', 'Ventas'),
    ('COMPRAS', 'Compras'),
    ('INVENTARIO', 'Inventario'),
    ('FACTURACION', 'Facturación'),
    ('CONTABILIDAD', 'Contabilidad'),
    ('PRODUCTOS', 'Gestión de productos'),
    ('PRECIOS', 'Gestión de precios'),
    ('REPORTES', 'Reportes')
    ON CONFLICT (codigo) DO NOTHING;
