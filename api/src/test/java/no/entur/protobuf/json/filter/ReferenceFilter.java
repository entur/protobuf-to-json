package no.entur.protobuf.json.filter;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

/**
 * Reference implementation of the filters, for tests. It filters a JSON document the straightforward way, independent of
 * {@linkplain FilterContext}, the path state machine and the size formulas of {@linkplain JsonOutputSize}:
 * <ol>
 *     <li>parse the document into a tree</li>
 *     <li>prune and anonymize, matching every path expression against the full path of each field</li>
 *     <li>truncate long strings</li>
 *     <li>list the tokens of the result, and drop the tokens which do not fit within the max size, measuring each token by writing it</li>
 * </ol>
 * The tokens must be written like they were measured, see {@linkplain Output}.
 * <br><br>
 * The rules, as in json-log-filter:
 * <ul>
 *     <li>Paths: {@code $.a.b} matches the full path of a field, where {@code *} matches any name. {@code $..b} matches
 *     a field name at any depth. Arrays are transparent: only names of fields count as path elements.</li>
 *     <li>Prune beats anonymize. Paths are not matched within anonymized or pruned values.</li>
 *     <li>Anonymize replaces every scalar within the value, keeping objects and arrays. Prune replaces the whole value.</li>
 *     <li>Max string length: strings (not field names) longer than the max length, counted in UTF-16 characters, keep that
 *     many characters (one less rather than splitting a surrogate pair), followed by the truncate message and the number of
 *     removed characters. Only if the string gets shorter, i.e. if more characters are removed than the length of the
 *     escaped truncate message plus the number of digits. Strings which are not text (64-bit integers, enum names, timestamps,
 *     durations, and NaN and infinity) are never truncated; the JSON does not tell them apart, so their locations are given
 *     (lists of field names, map keys and array indexes, from the root).</li>
 *     <li>Max size: tokens are written in order while they fit. A field name is written only together with its value, and
 *     each open object or array reserves room for its closing bracket. At the first token which does not fit, writing stops,
 *     and only the closing brackets of the open objects and arrays are written. The root object or array, or a root scalar,
 *     is always written.</li>
 * </ul>
 */

public final class ReferenceFilter {

    public enum Kind {
        START_OBJECT, END_OBJECT, START_ARRAY, END_ARRAY,
        /** a string value */
        STRING,
        /** any other value, written as is: numbers, booleans, null, and the anonymize and prune values */
        RAW
    }

    /**
     * A token of the output.
     *
     * @param kind  kind
     * @param name  field name, or null if the token is not a field value
     * @param value value of a string or raw token
     */

    public record Token(Kind kind, String name, String value) {
    }

    /**
     * Writes tokens with a JSON generator, and measures the result in the unit of max size.
     */

    public interface Output {

        String write(List<Token> tokens);

        /**
         * @param output written output
         * @return size, in characters or bytes
         */

        int size(String output);
    }

    private static final JsonFactory FACTORY = new JsonFactory();

    /** Jackson 3 generator writing characters */
    public static final Output CHARS = new Output() {
        @Override
        public String write(List<Token> tokens) {
            StringWriter output = new StringWriter();
            try (JsonGenerator generator = FACTORY.createGenerator(ObjectWriteContext.empty(), output)) {
                writeTokens(tokens, generator);
            }
            return output.toString();
        }

        @Override
        public int size(String output) {
            return output.length();
        }
    };

    /** Jackson 3 generator writing UTF-8 */
    public static final Output UTF8 = new Output() {
        @Override
        public String write(List<Token> tokens) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (JsonGenerator generator = FACTORY.createGenerator(ObjectWriteContext.empty(), output)) {
                writeTokens(tokens, generator);
            }
            return output.toString(StandardCharsets.UTF_8);
        }

        @Override
        public int size(String output) {
            return output.getBytes(StandardCharsets.UTF_8).length;
        }
    };

    public static void writeTokens(List<Token> tokens, JsonGenerator generator) {
        for (Token token : tokens) {
            if (token.name() != null) {
                generator.writeName(token.name());
            }
            switch (token.kind()) {
                case START_OBJECT -> generator.writeStartObject();
                case END_OBJECT -> generator.writeEndObject();
                case START_ARRAY -> generator.writeStartArray();
                case END_ARRAY -> generator.writeEndArray();
                case STRING -> generator.writeString(token.value());
                case RAW -> generator.writeRawValue(token.value());
            }
        }
    }

    /**
     * A message as a raw JSON value, i.e. for the anonymize and prune messages.
     *
     * @param message message
     * @return quoted and escaped message
     */

    public static String quote(String message) {
        return CHARS.write(List.of(new Token(Kind.STRING, null, message)));
    }

    // the parsed tree: ObjectValue, ArrayValue, String or RawValue

    private record Field(String name, Object value) {
    }

    private record ObjectValue(List<Field> fields) {
    }

    private record ArrayValue(List<Object> elements) {
    }

    private record RawValue(String raw) {
    }

    private enum Type {
        ANONYMIZE, PRUNE
    }

    private final List<String[]> anonymizePaths = new ArrayList<>();
    private final Set<String> anonymizeAnyPaths = new HashSet<>();
    private final List<String[]> prunePaths = new ArrayList<>();
    private final Set<String> pruneAnyPaths = new HashSet<>();
    private final int maxStringLength;
    private final int maxSize;
    private final String anonymizeRawJsonValue;
    private final String pruneRawJsonValue;
    private final String truncateMessage;

    /**
     * @param anonymizes            anonymize expressions
     * @param prunes                prune expressions
     * @param maxStringLength       max string length, or -1
     * @param maxSize               max size, or -1
     * @param anonymizeRawJsonValue anonymize value
     * @param pruneRawJsonValue     prune value
     * @param truncateMessage       truncate message (not escaped)
     */

    public ReferenceFilter(List<String> anonymizes, List<String> prunes, int maxStringLength, int maxSize, String anonymizeRawJsonValue, String pruneRawJsonValue,
                           String truncateMessage) {
        for (String expression : anonymizes) {
            add(expression, anonymizePaths, anonymizeAnyPaths);
        }
        for (String expression : prunes) {
            add(expression, prunePaths, pruneAnyPaths);
        }
        this.maxStringLength = maxStringLength;
        this.maxSize = maxSize;
        this.anonymizeRawJsonValue = anonymizeRawJsonValue;
        this.pruneRawJsonValue = pruneRawJsonValue;
        this.truncateMessage = truncateMessage;
    }

    public ReferenceFilter(List<String> anonymizes, List<String> prunes, int maxStringLength, int maxSize) {
        this(anonymizes, prunes, maxStringLength, maxSize, JsonFilter.DEFAULT_ANONYMIZE_RAW_JSON_VALUE, JsonFilter.DEFAULT_PRUNE_RAW_JSON_VALUE,
                JsonFilter.DEFAULT_TRUNCATE_MESSAGE);
    }

    private static void add(String expression, List<String[]> paths, Set<String> anyPaths) {
        if (expression.startsWith("$..")) {
            anyPaths.add(expression.substring(3));
        } else if (expression.startsWith("$.")) {
            paths.add(expression.substring(2).split("\\.", -1));
        } else {
            throw new IllegalArgumentException(expression);
        }
    }

    /**
     * Filter a document.
     *
     * @param json   document
     * @param output output, for measuring tokens
     * @return the filtered document, written to the output
     */

    public String process(String json, Output output) {
        return process(json, output, Set.of());
    }

    /**
     * Filter a document.
     *
     * @param json        document
     * @param output      output, for measuring tokens
     * @param untruncated locations of strings which are not text, and so are never truncated
     * @return the filtered document, written to the output
     */

    public String process(String json, Output output, Set<List<Object>> untruncated) {
        return output.write(filter(json, output, untruncated));
    }

    /**
     * Filter a document.
     *
     * @param json        document
     * @param output      output, for measuring tokens
     * @param untruncated locations of strings which are not text, and so are never truncated
     * @return the tokens of the filtered document
     */

    public List<Token> filter(String json, Output output, Set<List<Object>> untruncated) {
        Object tree;
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), json)) {
            tree = parse(parser, parser.nextToken());
        }
        List<Token> tokens = new ArrayList<>();
        addTokens(null, filter(tree, new ArrayList<>(), new ArrayList<>(), untruncated), tokens);
        if (maxSize == -1) {
            return tokens;
        }
        return limit(tokens, output);
    }

    private static Object parse(JsonParser parser, JsonToken token) {
        switch (token) {
            case START_OBJECT: {
                List<Field> fields = new ArrayList<>();
                while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                    String name = parser.currentName();
                    fields.add(new Field(name, parse(parser, parser.nextToken())));
                }
                return new ObjectValue(fields);
            }
            case START_ARRAY: {
                List<Object> elements = new ArrayList<>();
                JsonToken next;
                while ((next = parser.nextToken()) != JsonToken.END_ARRAY) {
                    elements.add(parse(parser, next));
                }
                return new ArrayValue(elements);
            }
            case VALUE_STRING:
                return parser.getString();
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
            case VALUE_TRUE:
            case VALUE_FALSE:
            case VALUE_NULL:
                return new RawValue(parser.getString());
            default:
                throw new IllegalStateException("Unexpected " + token);
        }
    }

    // paths and max string length

    private Object filter(Object value, List<String> path, List<Object> location, Set<List<Object>> untruncated) {
        if (value instanceof ObjectValue object) {
            List<Field> fields = new ArrayList<>();
            for (Field field : object.fields()) {
                List<String> fieldPath = new ArrayList<>(path);
                fieldPath.add(field.name());

                Type type = match(fieldPath);
                Object filtered;
                if (type == Type.PRUNE) {
                    filtered = new RawValue(pruneRawJsonValue);
                } else if (type == Type.ANONYMIZE) {
                    filtered = anonymize(field.value());
                } else {
                    filtered = filter(field.value(), fieldPath, append(location, field.name()), untruncated);
                }
                fields.add(new Field(field.name(), filtered));
            }
            return new ObjectValue(fields);
        }
        if (value instanceof ArrayValue array) {
            List<Object> elements = new ArrayList<>();
            for (int i = 0; i < array.elements().size(); i++) {
                // arrays are transparent for paths
                elements.add(filter(array.elements().get(i), path, append(location, i), untruncated));
            }
            return new ArrayValue(elements);
        }
        if (value instanceof String string) {
            return untruncated.contains(location) ? string : truncate(string);
        }
        return value;
    }

    private static List<Object> append(List<Object> location, Object element) {
        List<Object> next = new ArrayList<>(location);
        next.add(element);
        return next;
    }

    private Type match(List<String> path) {
        String name = path.get(path.size() - 1);
        if (pruneAnyPaths.contains(name) || matches(prunePaths, path)) {
            return Type.PRUNE;
        }
        if (anonymizeAnyPaths.contains(name) || matches(anonymizePaths, path)) {
            return Type.ANONYMIZE;
        }
        return null;
    }

    private static boolean matches(List<String[]> expressions, List<String> path) {
        for (String[] expression : expressions) {
            if (matches(expression, path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(String[] expression, List<String> path) {
        if (expression.length != path.size()) {
            return false;
        }
        for (int i = 0; i < expression.length; i++) {
            if (!expression[i].equals("*") && !expression[i].equals(path.get(i))) {
                return false;
            }
        }
        return true;
    }

    private Object anonymize(Object value) {
        if (value instanceof ObjectValue object) {
            List<Field> fields = new ArrayList<>();
            for (Field field : object.fields()) {
                fields.add(new Field(field.name(), anonymize(field.value())));
            }
            return new ObjectValue(fields);
        }
        if (value instanceof ArrayValue array) {
            List<Object> elements = new ArrayList<>();
            for (Object element : array.elements()) {
                elements.add(anonymize(element));
            }
            return new ArrayValue(elements);
        }
        return new RawValue(anonymizeRawJsonValue);
    }

    private String truncate(String value) {
        if (maxStringLength == -1 || value.length() <= maxStringLength) {
            return value;
        }
        int keep = maxStringLength;
        if (keep > 0 && Character.isHighSurrogate(value.charAt(keep - 1)) && Character.isLowSurrogate(value.charAt(keep))) {
            // do not split the surrogate pair
            keep--;
        }
        int removed = value.length() - keep;
        String digits = Integer.toString(removed);
        if (removed <= escapedLength(truncateMessage) + digits.length()) {
            // would not get shorter
            return value;
        }
        return value.substring(0, keep) + truncateMessage + digits;
    }

    private static int escapedLength(String value) {
        int length = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\' || c == '\b' || c == '\f' || c == '\n' || c == '\r' || c == '\t') {
                length += 2;
            } else if (c < 0x20) {
                length += 6;
            } else {
                length++;
            }
        }
        return length;
    }

    private static void addTokens(String name, Object value, List<Token> tokens) {
        if (value instanceof ObjectValue object) {
            tokens.add(new Token(Kind.START_OBJECT, name, null));
            for (Field field : object.fields()) {
                addTokens(field.name(), field.value(), tokens);
            }
            tokens.add(new Token(Kind.END_OBJECT, null, null));
        } else if (value instanceof ArrayValue array) {
            tokens.add(new Token(Kind.START_ARRAY, name, null));
            for (Object element : array.elements()) {
                addTokens(null, element, tokens);
            }
            tokens.add(new Token(Kind.END_ARRAY, null, null));
        } else if (value instanceof String string) {
            tokens.add(new Token(Kind.STRING, name, string));
        } else {
            tokens.add(new Token(Kind.RAW, name, ((RawValue) value).raw()));
        }
    }

    // max size

    private List<Token> limit(List<Token> tokens, Output output) {
        List<Token> result = new ArrayList<>();
        List<Boolean> open = new ArrayList<>(); // the open objects and arrays, and whether they have elements yet
        long written = 0;
        boolean stopped = false;
        int skipped = 0; // depth within objects and arrays which are not written

        for (Token token : tokens) {
            switch (token.kind()) {
                case START_OBJECT, START_ARRAY -> {
                    if (stopped) {
                        skipped++;
                    } else if (open.isEmpty()) {
                        // the root is always written
                        result.add(token);
                        written++;
                        open.add(false);
                    } else {
                        long size = separatorSize(open) + nameSize(token, output) + 1;
                        // also reserve room for the closing bracket
                        if (written + size + open.size() + 1 > maxSize) {
                            stopped = true;
                            skipped++;
                        } else {
                            result.add(token);
                            written += size;
                            open.set(open.size() - 1, true);
                            open.add(false);
                        }
                    }
                }
                case END_OBJECT, END_ARRAY -> {
                    if (skipped > 0) {
                        skipped--;
                    } else {
                        result.add(token);
                        written++;
                        open.remove(open.size() - 1);
                    }
                }
                default -> {
                    if (stopped) {
                        continue;
                    }
                    if (open.isEmpty()) {
                        // a root scalar is always written
                        result.add(token);
                        continue;
                    }
                    long size = separatorSize(open) + nameSize(token, output) + valueSize(token, output);
                    if (written + size + open.size() > maxSize) {
                        stopped = true;
                    } else {
                        result.add(token);
                        written += size;
                        open.set(open.size() - 1, true);
                    }
                }
            }
        }
        return result;
    }

    private static int separatorSize(List<Boolean> open) {
        return open.get(open.size() - 1) ? 1 : 0;
    }

    private static int nameSize(Token token, Output output) {
        if (token.name() == null) {
            return 0;
        }
        // the quoted name and the colon
        return stringSize(token.name(), output) + 1;
    }

    private static int valueSize(Token token, Output output) {
        if (token.kind() == Kind.STRING) {
            return stringSize(token.value(), output);
        }
        return output.size(token.value());
    }

    private static int stringSize(String value, Output output) {
        return output.size(output.write(List.of(new Token(Kind.STRING, null, value))));
    }
}
