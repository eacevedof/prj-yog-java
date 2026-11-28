package modules.explorer.domain.entities;

/**
 * Las opciones de una BD MySQL/MariaDB que cambia un ALTER DATABASE. Cada servidor da las suyas: ENCRYPTION es de
 * MySQL 8, READ ONLY de MySQL 8.0.22+ y COMMENT de MariaDB 10.5+; las que no da llevan su isXSupported a false y no
 * se escriben nunca.
 */
public record DatabaseOptionsEntity(
    String databaseName,
    String characterSet,
    String collation,
    boolean isEncryptionSupported,
    boolean isEncrypted,
    boolean isReadOnlySupported,
    boolean isReadOnly,
    boolean isCommentSupported,
    String comment
) {

    /**
     * La misma BD con lo editado; lo que el servidor no soporta se queda como estaba.
     */
    public DatabaseOptionsEntity withEdited(String characterSet, String collation, boolean isEncrypted, boolean isReadOnly, String comment) {
        return new DatabaseOptionsEntity(
            databaseName,
            characterSet,
            collation,
            isEncryptionSupported,
            isEncryptionSupported ? isEncrypted : this.isEncrypted,
            isReadOnlySupported,
            isReadOnlySupported ? isReadOnly : this.isReadOnly,
            isCommentSupported,
            isCommentSupported ? comment : this.comment
        );
    }
}
