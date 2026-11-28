package modules.mongo.application.savedocument;

import java.util.Map;

public final class SaveMongoDocumentResultDto {

    private final String idJson;

    private SaveMongoDocumentResultDto(Map<String, Object> primitives) {
        this.idJson = String.valueOf(primitives.get("idJson"));
    }

    public static SaveMongoDocumentResultDto fromPrimitives(Map<String, Object> primitives) {
        return new SaveMongoDocumentResultDto(primitives);
    }

    /**
     * El _id del documento guardado o borrado, como JSON de mongosh (ObjectId("...")).
     */
    public String getIdJson() {
        return idJson;
    }
}
