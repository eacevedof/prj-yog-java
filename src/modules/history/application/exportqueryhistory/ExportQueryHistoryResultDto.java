package modules.history.application.exportqueryhistory;

import java.util.Map;

import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

public final class ExportQueryHistoryResultDto {

    private final int exportedCount;
    private final String filePath;

    private ExportQueryHistoryResultDto(Map<String, Object> primitives) {
        this.exportedCount = getInt(primitives.get("exportedCount"));
        this.filePath = getTrimmedString(primitives.get("filePath"));
    }

    public static ExportQueryHistoryResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ExportQueryHistoryResultDto(primitives);
    }

    public int getExportedCount() {
        return exportedCount;
    }

    public String getFilePath() {
        return filePath;
    }
}
