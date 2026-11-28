-- estado de la ventana que sobrevive al reinicio (clave -> valor): los filtros del panel y la BD activa de cada conexion
CREATE TABLE IF NOT EXISTS `app_workspace_state` (
    `state_key` VARCHAR(150) PRIMARY KEY,
    `state_value` TEXT NOT NULL,
    `updated_at` VARCHAR(19) NOT NULL
);
-- las pestanas de query de cada conexion con su SQL: se recuperan al volver a abrir la conexion
CREATE TABLE IF NOT EXISTS `app_query_tabs` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT,
    `connection_uuid` VARCHAR(50) NOT NULL,
    `tab_position` INTEGER NOT NULL,
    `tab_title` VARCHAR(100) NOT NULL,
    `sql_text` TEXT NOT NULL,
    `is_selected` INTEGER NOT NULL DEFAULT 0,
    `updated_at` VARCHAR(19) NOT NULL
);
CREATE INDEX IF NOT EXISTS `ix_query_tabs_connection` ON `app_query_tabs` (`connection_uuid`, `tab_position`)
