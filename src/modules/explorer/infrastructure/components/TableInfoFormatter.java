package modules.explorer.infrastructure.components;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

import static core.config.Global.__;

import modules.explorer.domain.enums.ExplorerKeyEnum;
import modules.explorer.application.gettableinfo.GetTableInfoResultDto;

/**
 * La pestaña Info en texto (como la de SQLyog): resumen, indices agrupados y el DDL listo para copiar.
 */
public final class TableInfoFormatter {

    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB", "TB"};

    private TableInfoFormatter() {
    }

    public static TableInfoFormatter getInstance() {
        return new TableInfoFormatter();
    }

    public String getText(GetTableInfoResultDto tableInfo) {
        Map<String, Object> status = tableInfo.getStatus();
        StringBuilder text = new StringBuilder();
        // sin comillas: es un comentario y las de MySQL (`) confundian en PostgreSQL o SQL Server
        text.append("/* ").append(tableInfo.isView() ? __("Vista") : __("Tabla")).append(": ")
            .append(tableInfo.getDatabaseName()).append(".").append(tableInfo.getTableName()).append(" */\n\n");

        if (!tableInfo.isView()) {
            long dataLength = getLong(status.get("data_length"));
            long indexLength = getLong(status.get("index_length"));
            addLine(text, "Engine", getTrimmedString(status.get("engine")));
            addLine(text, __("Filas (aprox.)"), getTrimmedString(status.get("table_rows")));
            addLine(text, __("Datos"), getSizeText(dataLength));
            addLine(text, __("Índices"), getSizeText(indexLength));
            addLine(text, __("Total"), getSizeText(dataLength + indexLength));
            addLine(text, "Auto_increment", getTrimmedString(status.get("auto_increment")));
            addLine(text, __("Cotejamiento"), getTrimmedString(status.get("table_collation")));
            addLine(text, __("Creada"), getTrimmedString(status.get("create_time")));
            addLine(text, __("Modificada"), getTrimmedString(status.get("update_time")));
            addLine(text, __("Comentario"), getTrimmedString(status.get("table_comment")));
            text.append('\n').append("/* ").append(__("Índices")).append(" */\n");
            for (String indexLine : getIndexLines(tableInfo.getIndexes())) {
                text.append(indexLine).append('\n');
            }
            text.append('\n');
        }

        text.append("/* DDL */\n").append(tableInfo.getCreateStatement()).append(";\n");
        return text.toString();
    }

    /**
     * "PRIMARY KEY (id)", "UNIQUE uk_email (email) BTREE", "INDEX ix_a_b (a, b) BTREE".
     */
    private List<String> getIndexLines(List<Map<String, Object>> indexRows) {
        Map<String, List<String>> columnsByIndex = new LinkedHashMap<>();
        Map<String, String> headerByIndex = new LinkedHashMap<>();
        for (Map<String, Object> indexRow : indexRows) {
            String indexName = getTrimmedString(indexRow.get("index_name"));
            columnsByIndex.computeIfAbsent(indexName, key -> new ArrayList<>()).add(getTrimmedString(indexRow.get("column_name")));
            String kind = ExplorerKeyEnum.PRIMARY_INDEX_NAME.equals(indexName) ? "PRIMARY KEY" : getInt(indexRow.get("non_unique")) == 0 ? "UNIQUE " + indexName : "INDEX " + indexName;
            headerByIndex.putIfAbsent(indexName, kind + "|" + getTrimmedString(indexRow.get("index_type")));
        }
        List<String> indexLines = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : columnsByIndex.entrySet()) {
            String[] header = headerByIndex.get(entry.getKey()).split("\\|", 2);
            indexLines.add(header[0] + " (" + String.join(", ", entry.getValue()) + ") " + header[1]);
        }
        if (indexLines.isEmpty()) indexLines.add(__("(sin índices)"));
        return indexLines;
    }

    private void addLine(StringBuilder text, String label, String value) {
        if (value.isEmpty()) return;
        text.append(String.format(Locale.ROOT, "%-16s %s%n", label + ":", value));
    }

    public String getSizeText(long bytes) {
        double size = bytes;
        int unitIndex = 0;
        while (size >= 1024 && unitIndex < SIZE_UNITS.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return unitIndex == 0 ? bytes + " B" : String.format(Locale.ROOT, "%.1f %s", size, SIZE_UNITS[unitIndex]);
    }

    private long getLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(getTrimmedString(value));
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }
}
