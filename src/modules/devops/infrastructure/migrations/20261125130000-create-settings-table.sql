-- preferencias de la app (clave -> valor): de momento el tema (feature 37)
CREATE TABLE IF NOT EXISTS `app_settings` (
    `setting_key` VARCHAR(50) PRIMARY KEY,
    `setting_value` VARCHAR(255) NOT NULL,
    `updated_at` VARCHAR(19) NOT NULL
)
