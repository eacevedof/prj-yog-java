-- motor "Ficheros": que fichero de la carpeta es cada tabla del SQLite de trabajo, como era (formato, separador,
-- columnas y sus tipos) para volver a escribirlo igual, y como estaba al cargarlo (fecha, tamano y huella de sus filas)
CREATE TABLE IF NOT EXISTS `app_files_tables` (
    `connection_uuid` VARCHAR(50) NOT NULL,
    `table_name` VARCHAR(150) NOT NULL,
    `file_name` VARCHAR(255) NOT NULL,
    `file_format` VARCHAR(20) NOT NULL,
    `csv_delimiter` VARCHAR(1) NOT NULL DEFAULT '',
    `line_separator` VARCHAR(2) NOT NULL,
    `has_bom` INTEGER NOT NULL DEFAULT 0,
    `column_names` TEXT NOT NULL,
    `column_kinds` TEXT NOT NULL,
    `explicit_null_columns` TEXT NOT NULL DEFAULT '',
    `file_mtime` INTEGER NOT NULL,
    `file_size` INTEGER NOT NULL,
    `rows_digest` VARCHAR(64) NOT NULL,
    `loaded_at` VARCHAR(19) NOT NULL,
    PRIMARY KEY (`connection_uuid`, `table_name`)
);
CREATE UNIQUE INDEX IF NOT EXISTS `ux_files_tables_file` ON `app_files_tables` (`connection_uuid`, `file_name`)
