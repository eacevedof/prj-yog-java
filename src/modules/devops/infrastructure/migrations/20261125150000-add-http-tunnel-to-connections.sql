-- tunel HTTP por conexion (feature 13, contra el tunel PHP de prj-yog): la URL lleva el token de acceso, va cifrada
ALTER TABLE `app_connections` ADD COLUMN `http_enabled` INTEGER NOT NULL DEFAULT 0;
ALTER TABLE `app_connections` ADD COLUMN `http_url` VARCHAR(1000);
ALTER TABLE `app_connections` ADD COLUMN `http_timeout_s` INTEGER NOT NULL DEFAULT 30
