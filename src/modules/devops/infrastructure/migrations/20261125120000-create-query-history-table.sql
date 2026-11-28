-- historial de queries (feature 8): una fila por ejecucion (el SQL enviado, sea una sentencia o varias)
CREATE TABLE IF NOT EXISTS `app_query_history` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT,
    `connection_uuid` VARCHAR(50) NOT NULL,
    `connection_name` VARCHAR(100) NOT NULL,
    `database_name` VARCHAR(100),
    `sql_query` TEXT NOT NULL,
    `executed_at` VARCHAR(19) NOT NULL,
    `elapsed_ms` INTEGER NOT NULL DEFAULT 0,
    `rows_count` INTEGER NOT NULL DEFAULT 0,
    `has_error` INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS `ix_query_history_connection` ON `app_query_history` (`connection_uuid`, `id`)
