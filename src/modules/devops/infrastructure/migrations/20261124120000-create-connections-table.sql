-- conexiones guardadas: por ahora solo MySQL directo (los tuneles SSH/HTTP iran en otra migracion)
CREATE TABLE IF NOT EXISTS `app_connections` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT,
    `connection_uuid` VARCHAR(50) NOT NULL UNIQUE,
    `connection_name` VARCHAR(100) NOT NULL UNIQUE,
    `db_engine` VARCHAR(20) NOT NULL DEFAULT 'mysql',
    `host` VARCHAR(255) NOT NULL,
    `port` INTEGER NOT NULL DEFAULT 3306,
    `user_name` VARCHAR(100) NOT NULL,
    `user_password` VARCHAR(255),
    `database_name` VARCHAR(100),
    `color_hex` VARCHAR(7),
    `is_production` INTEGER NOT NULL DEFAULT 0,
    `created_at` VARCHAR(19) NOT NULL,
    `updated_at` VARCHAR(19) NOT NULL
);
