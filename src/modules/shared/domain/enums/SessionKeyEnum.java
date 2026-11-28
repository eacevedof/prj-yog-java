package modules.shared.domain.enums;

/**
 * Las claves de las sesiones abiertas contra una BD (TargetSessionFactory, RedisSessionFactory, MongoSessionFactory):
 * "<pestaña o prefijo>:<sufijo>". Al cerrar una pestaña o acabar una operacion se cierra todo lo que cuelga de su
 * prefijo.
 */
public final class SessionKeyEnum {

    public static final String SEPARATOR = ":";

    // pestaña de conexion: el arbol, el autocompletado, la carpeta de ficheros y cada pestaña Query o Datos ("query-3")
    public static final String EXPLORER = "explorer";
    public static final String COMPLETION = "completion";
    public static final String FILES = "files";
    public static final String QUERY_PREFIX = "query-";
    public static final String DATA_PREFIX = "data-";
    // la sesion propia con la que se guarda el resultado editable de una pestaña Query: "<sesion>:edit"
    public static final String EDIT = "edit";
    // el fichero .sql que se ejecuta desde su dialogo
    public static final String RUN = "run";

    // Redis y MongoDB: navegador y consola, cada uno con su sesion
    public static final String REDIS_BROWSER = "redis-browser";
    public static final String REDIS_CONSOLE = "redis-console";
    public static final String MONGO_BROWSER = "mongo-browser";
    public static final String MONGO_CONSOLE = "mongo-console";

    // copiar, volcar y renombrar BD: origen, destino ("target-<uuid>" en el dialogo), comprobacion, escritura y carga
    public static final String SOURCE = "source";
    public static final String TARGET = "target";
    public static final String TARGET_PREFIX = "target-";
    public static final String CHECK = "check";
    public static final String WRITE = "write";
    public static final String LOAD = "load";

    private SessionKeyEnum() {
    }
}
