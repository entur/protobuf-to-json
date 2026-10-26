package no.entur.protobuf.json.filter;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import no.entur.protobuf.json.path.FilterType;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

/**
 * Reference writer: copies a JSON document token by token, filtered via {@linkplain FilterContext} the same way as
 * the protobuf writers do.
 */

public final class TokenReplay {

    private static final JsonFactory FACTORY = new JsonFactory();

    private TokenReplay() {
    }

    public static String toChars(String json, JsonFilter filter) {
        StringWriter output = new StringWriter();
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), json);
             JsonGenerator generator = FACTORY.createGenerator(ObjectWriteContext.empty(), output)) {
            FilterContext context = filter.newContext(false);
            writeValue(parser, generator, context, parser.nextToken(), null, false);
        }
        return output.toString();
    }

    public static String toBytes(String json, JsonFilter filter) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), json);
             JsonGenerator generator = FACTORY.createGenerator(ObjectWriteContext.empty(), output)) {
            FilterContext context = filter.newContext(true);
            writeValue(parser, generator, context, parser.nextToken(), null, false);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static boolean writeValue(JsonParser parser, JsonGenerator generator, FilterContext context, JsonToken token, String name, boolean anonymize) {
        JsonFilter filter = context.getFilter();
        int nameSize = name != null && context.isSizing() ? context.getNameSize(name) : 0;
        if (token == JsonToken.START_OBJECT) {
            if (!context.startObject(nameSize)) {
                parser.skipChildren();
                return false;
            }
            if (name != null) {
                generator.writeName(name);
            }
            generator.writeStartObject();
            boolean writing = true;
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                String fieldName = parser.currentName();
                JsonToken valueToken = parser.nextToken();
                if (!writing) {
                    parser.skipChildren();
                    continue;
                }
                FilterType type = !anonymize && context.hasPaths() ? context.matchName(fieldName) : null;
                if (type == FilterType.PRUNE) {
                    int fieldNameSize = context.isSizing() ? context.getNameSize(fieldName) : 0;
                    if (context.scalar(fieldNameSize, context.getPruneSize())) {
                        generator.writeName(fieldName);
                        generator.writeRawValue(filter.getPruneRawJsonValue());
                    } else {
                        writing = false;
                    }
                    parser.skipChildren();
                } else {
                    writing = writeValue(parser, generator, context, valueToken, fieldName, anonymize || type == FilterType.ANON);
                }
            }
            context.endObject();
            generator.writeEndObject();
            return writing;
        }
        if (token == JsonToken.START_ARRAY) {
            if (!context.startArray(nameSize)) {
                parser.skipChildren();
                return false;
            }
            if (name != null) {
                generator.writeName(name);
            }
            generator.writeStartArray();
            boolean writing = true;
            JsonToken next;
            while ((next = parser.nextToken()) != JsonToken.END_ARRAY) {
                if (!writing) {
                    parser.skipChildren();
                    continue;
                }
                writing = writeValue(parser, generator, context, next, null, anonymize);
            }
            context.endArray();
            generator.writeEndArray();
            return writing;
        }
        if (anonymize) {
            if (!context.scalar(nameSize, context.getAnonymizeSize())) {
                return false;
            }
            if (name != null) {
                generator.writeName(name);
            }
            generator.writeRawValue(filter.getAnonymizeRawJsonValue());
            return true;
        }
        if (token == JsonToken.VALUE_STRING) {
            String text = parser.getString();
            int keep = MaxStringLength.getTruncatedLength(text, filter.getMaxStringLength(), filter.getTruncateMessageLength());
            if (keep != -1) {
                int removed = text.length() - keep;
                if (!context.scalar(nameSize, context.isSizing() ? context.getTruncatedSize(text, keep, removed) : 0)) {
                    return false;
                }
                char[] buffer = new char[keep + filter.getTruncateMessageChars().length + 10];
                int length = MaxStringLength.fill(text, keep, filter.getTruncateMessageChars(), removed, buffer);
                if (name != null) {
                    generator.writeName(name);
                }
                generator.writeString(buffer, 0, length);
                return true;
            }
            if (!context.scalar(nameSize, context.isSizing() ? context.getQuotedSize(text) : 0)) {
                return false;
            }
            if (name != null) {
                generator.writeName(name);
            }
            generator.writeString(text);
            return true;
        }
        String text = parser.getString();
        if (!context.scalar(nameSize, text.length())) {
            return false;
        }
        if (name != null) {
            generator.writeName(name);
        }
        switch (token) {
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                generator.writeNumber(text);
                break;
            case VALUE_TRUE:
                generator.writeBoolean(true);
                break;
            case VALUE_FALSE:
                generator.writeBoolean(false);
                break;
            case VALUE_NULL:
                generator.writeNull();
                break;
            default:
                throw new IllegalStateException("Unexpected " + token);
        }
        return true;
    }
}
