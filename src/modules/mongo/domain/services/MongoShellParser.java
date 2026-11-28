package modules.mongo.domain.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import modules.mongo.domain.entities.MongoShellCommandEntity;

/**
 * Lo que entiende la consola de MongoDB, un subconjunto de mongosh sin JavaScript:
 * <ul>
 *     <li>show dbs, show collections, use &lt;bd&gt;</li>
 *     <li>db.&lt;coleccion&gt;.&lt;metodo&gt;(args) y db.getCollection("x").&lt;metodo&gt;(args), con .sort() .limit() .skip()
 *     .projection() detras</li>
 *     <li>db.runCommand({...}), db.dropDatabase() y un documento {...} suelto (= runCommand)</li>
 * </ul>
 * Los argumentos son JSON "relajado" (claves sin comillas, ObjectId("..."), ISODate("...")): aqui solo se separan; los
 * interpreta el driver.
 */
public final class MongoShellParser {

    private static final Pattern USE_PATTERN = Pattern.compile("^use\\s+(\\S+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOT_COLLECTION_PATTERN = Pattern.compile("^db\\.([A-Za-z_$][\\w$-]*)\\.([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern GET_COLLECTION_PATTERN = Pattern.compile("^db\\.getCollection\\(\\s*([\"'])(.+?)\\1\\s*\\)\\.([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern DB_METHOD_PATTERN = Pattern.compile("^db\\.(runCommand|dropDatabase|adminCommand)\\s*\\(");
    private static final Pattern MODIFIER_PATTERN = Pattern.compile("^\\.\\s*([A-Za-z_]\\w*)\\s*\\(");

    private MongoShellParser() {
    }

    public static MongoShellParser getInstance() {
        return new MongoShellParser();
    }

    /**
     * @throws IllegalArgumentException si la linea no es una orden que se entienda (con el motivo)
     */
    public MongoShellCommandEntity getCommand(String commandLine) {
        String line = commandLine.trim();
        while (line.endsWith(";")) line = line.substring(0, line.length() - 1).trim();
        if (line.isEmpty()) throw new IllegalArgumentException("empty");

        String lowerLine = line.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (lowerLine.equals("show dbs") || lowerLine.equals("show databases")) {
            return new MongoShellCommandEntity(MongoShellCommandEntity.SHOW_DATABASES, "", "", List.of(), List.of());
        }
        if (lowerLine.equals("show collections") || lowerLine.equals("show tables")) {
            return new MongoShellCommandEntity(MongoShellCommandEntity.SHOW_COLLECTIONS, "", "", List.of(), List.of());
        }
        Matcher useMatcher = USE_PATTERN.matcher(line);
        if (useMatcher.matches()) {
            return new MongoShellCommandEntity(MongoShellCommandEntity.USE, useMatcher.group(1), "", List.of(), List.of());
        }
        if (line.startsWith("{")) {
            return new MongoShellCommandEntity(MongoShellCommandEntity.RUN_COMMAND, "", "", List.of(line), List.of());
        }

        Matcher dbMethodMatcher = DB_METHOD_PATTERN.matcher(line);
        if (dbMethodMatcher.find()) {
            int argumentsEnd = getClosingParenthesis(line, dbMethodMatcher.end() - 1);
            failIfTrailing(line, argumentsEnd);
            List<String> arguments = getArguments(line.substring(dbMethodMatcher.end(), argumentsEnd));
            if ("dropDatabase".equals(dbMethodMatcher.group(1))) {
                return new MongoShellCommandEntity(MongoShellCommandEntity.DROP_DATABASE, "", "", List.of(), List.of());
            }
            if (arguments.size() != 1) throw new IllegalArgumentException("runCommand expects one document");
            return new MongoShellCommandEntity(MongoShellCommandEntity.RUN_COMMAND, dbMethodMatcher.group(1), "", arguments, List.of());
        }

        String collectionName;
        String method;
        int argumentsStart;
        Matcher getCollectionMatcher = GET_COLLECTION_PATTERN.matcher(line);
        Matcher dotCollectionMatcher = DOT_COLLECTION_PATTERN.matcher(line);
        if (getCollectionMatcher.find()) {
            collectionName = getCollectionMatcher.group(2);
            method = getCollectionMatcher.group(3);
            argumentsStart = getCollectionMatcher.end();
        }
        else if (dotCollectionMatcher.find()) {
            collectionName = dotCollectionMatcher.group(1);
            method = dotCollectionMatcher.group(2);
            argumentsStart = dotCollectionMatcher.end();
        }
        else {
            throw new IllegalArgumentException(line);
        }
        int argumentsEnd = getClosingParenthesis(line, argumentsStart - 1);
        List<String> arguments = getArguments(line.substring(argumentsStart, argumentsEnd));
        List<List<String>> modifiers = getModifiers(line, argumentsEnd + 1);
        return new MongoShellCommandEntity(MongoShellCommandEntity.COLLECTION_METHOD, collectionName, method, arguments, modifiers);
    }

    /**
     * .sort({a: 1}).limit(10): cada modificador con su unico argumento.
     */
    private List<List<String>> getModifiers(String line, int start) {
        List<List<String>> modifiers = new ArrayList<>();
        int i = start;
        while (i < line.length()) {
            String rest = line.substring(i).stripLeading();
            if (rest.isEmpty()) break;
            int restStart = line.length() - rest.length();
            Matcher modifierMatcher = MODIFIER_PATTERN.matcher(rest);
            if (!modifierMatcher.find()) throw new IllegalArgumentException("unexpected: " + rest);
            int openIndex = restStart + modifierMatcher.end() - 1;
            int closeIndex = getClosingParenthesis(line, openIndex);
            List<String> arguments = getArguments(line.substring(openIndex + 1, closeIndex));
            modifiers.add(List.of(modifierMatcher.group(1), arguments.isEmpty() ? "" : arguments.getFirst()));
            i = closeIndex + 1;
        }
        return modifiers;
    }

    private void failIfTrailing(String line, int closeIndex) {
        if (!line.substring(closeIndex + 1).isBlank()) throw new IllegalArgumentException("unexpected: " + line.substring(closeIndex + 1));
    }

    /**
     * Donde se cierra el parentesis que se abre en openIndex (saltando cadenas y llaves/corchetes anidados).
     */
    private int getClosingParenthesis(String line, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"' || current == '\'') {
                i = getStringEnd(line, i);
                continue;
            }
            if (current == '(' || current == '{' || current == '[') depth++;
            if (current == ')' || current == '}' || current == ']') {
                depth--;
                if (depth == 0) {
                    if (current != ')') throw new IllegalArgumentException("unbalanced brackets");
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("unbalanced brackets");
    }

    /**
     * Los argumentos separados por las comas de primer nivel.
     */
    private List<String> getArguments(String argumentsText) {
        List<String> arguments = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < argumentsText.length(); i++) {
            char current = argumentsText.charAt(i);
            if (current == '"' || current == '\'') {
                i = getStringEnd(argumentsText, i);
                continue;
            }
            if (current == '{' || current == '[' || current == '(') depth++;
            if (current == '}' || current == ']' || current == ')') depth--;
            if (current == ',' && depth == 0) {
                arguments.add(argumentsText.substring(start, i).trim());
                start = i + 1;
            }
        }
        String last = argumentsText.substring(start).trim();
        if (!last.isEmpty() || !arguments.isEmpty()) arguments.add(last);
        return arguments;
    }

    private int getStringEnd(String text, int start) {
        char quote = text.charAt(start);
        for (int i = start + 1; i < text.length(); i++) {
            if (text.charAt(i) == '\\') {
                i++;
                continue;
            }
            if (text.charAt(i) == quote) return i;
        }
        throw new IllegalArgumentException("unclosed string");
    }
}
