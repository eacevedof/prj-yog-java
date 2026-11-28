package modules.dbcopy.domain.services;

/**
 * "Duplicar BD": el nombre de la copia en el mismo servidor, &lt;bd&gt;_copy_&lt;ymdhis&gt;, como "Duplicar tabla". MySQL
 * no admite nombres de BD de mas de 64 caracteres: si no cabe, se recorta el nombre original, nunca la fecha.
 */
public final class DatabaseCopyNameBuilder {

    private static final String COPY_SUFFIX = "_copy_";
    private static final int MAX_DATABASE_NAME_LENGTH = 64;

    private DatabaseCopyNameBuilder() {
    }

    public static DatabaseCopyNameBuilder getInstance() {
        return new DatabaseCopyNameBuilder();
    }

    /**
     * @param nowDt fecha-hora "yyyy-MM-dd HH:mm:ss": solo se usan sus digitos
     */
    public String getCopyDatabaseName(String databaseName, String nowDt) {
        String suffix = COPY_SUFFIX + nowDt.replaceAll("\\D", "");
        int maxPrefixLength = MAX_DATABASE_NAME_LENGTH - suffix.length();
        String prefix = databaseName.length() > maxPrefixLength ? databaseName.substring(0, maxPrefixLength) : databaseName;
        return prefix + suffix;
    }
}
