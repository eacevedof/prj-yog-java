package modules.settings.infrastructure.repositories;

import java.util.List;
import java.util.Map;

import modules.shared.infrastructure.repositories.AbstractSqliteRepository;
import static modules.shared.application.Primitives.getTrimmedString;

/**
 * app_settings: una fila por preferencia. Lectura y escritura juntas: es una tabla clave-valor de dos consultas.
 */
public final class SettingsSqliteRepository extends AbstractSqliteRepository {

    private SettingsSqliteRepository() {
    }

    public static SettingsSqliteRepository getInstance() {
        return new SettingsSqliteRepository();
    }

    /**
     * null si no se ha guardado nunca.
     */
    public String getSettingValue(String settingKey) {
        String sql = """
            -- getSettingValue
            SELECT setting_value
            FROM `app_settings`
            WHERE 1=1
            AND setting_key = ?
            """;
        List<Map<String, Object>> rows = query(sql, settingKey);
        return rows.isEmpty() ? null : getTrimmedString(rows.getFirst().get("setting_value"));
    }

    public void upsertSetting(String settingKey, String settingValue, String updatedAt) {
        String sql = """
            -- upsertSetting
            INSERT INTO `app_settings` (setting_key, setting_value, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value, updated_at = excluded.updated_at
            """;
        execute(sql, settingKey, settingValue, updatedAt);
    }
}
