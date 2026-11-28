-- tunel SSH por conexion (feature 12): SQLite anade las columnas de una en una
ALTER TABLE `app_connections` ADD COLUMN `ssh_enabled` INTEGER NOT NULL DEFAULT 0;
ALTER TABLE `app_connections` ADD COLUMN `ssh_host` VARCHAR(255);
ALTER TABLE `app_connections` ADD COLUMN `ssh_port` INTEGER NOT NULL DEFAULT 22;
ALTER TABLE `app_connections` ADD COLUMN `ssh_user` VARCHAR(100);
ALTER TABLE `app_connections` ADD COLUMN `ssh_password` VARCHAR(255);
ALTER TABLE `app_connections` ADD COLUMN `ssh_private_key_path` VARCHAR(500);
ALTER TABLE `app_connections` ADD COLUMN `ssh_passphrase` VARCHAR(255)
