package no.entur.protobuf.json.lite;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.json.UTF8JsonGenerator;
import com.fasterxml.jackson.core.util.JsonGeneratorDelegate;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Value formatting for the <a href="https://protobuf.dev/programming-guides/json/">ProtoJSON format</a>, like JsonFormat.
 * Timestamps and durations are formatted with {@linkplain no.entur.protobuf.json.filter.JsonTimes}.
 */

final class LiteJsonValues {


    private static final JsonGenerator.Feature COMBINE_SURROGATES = combineSurrogatesFeature();

    private LiteJsonValues() {
    }

    private static JsonGenerator.Feature combineSurrogatesFeature() {
        try {
            // Jackson 2.18 and later
            return JsonGenerator.Feature.valueOf("COMBINE_UNICODE_SURROGATES_IN_UTF8");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Check whether a generator writes bytes, so that it supports writing UTF-8 strings directly.
     *
     * @param generator generator
     * @return true if UTF-8 bytes are written
     */

    static boolean isUtf8(JsonGenerator generator) {
        while (generator instanceof JsonGeneratorDelegate) {
            generator = ((JsonGeneratorDelegate) generator).getDelegate();
        }
        return generator instanceof UTF8JsonGenerator;
    }

    /**
     * Check whether a generator which writes UTF-8 escapes surrogate pairs in strings (which Jackson 2 does by default).
     *
     * @param generator generator
     * @return true if surrogates are escaped
     */

    @SuppressWarnings("deprecation")
    static boolean isEscapeSurrogates(JsonGenerator generator) {
        return COMBINE_SURROGATES == null || !generator.isEnabled(COMBINE_SURROGATES);
    }

    // ---- numbers ----

    static String toString(float value) {
        if (Float.isNaN(value)) {
            return "NaN";
        }
        return value > 0 ? "Infinity" : "-Infinity";
    }

    static String toString(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        return value > 0 ? "Infinity" : "-Infinity";
    }

    static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    // ---- map keys ----

    /**
     * Map keys are always strings.
     *
     * @param key map key
     * @return JSON object key
     */

    static String toKey(Object key) {
        if (key instanceof String) {
            return (String) key;
        }
        // numbers and booleans
        return key.toString();
    }

    // ---- bytes ----

    // Jackson's MIME_NO_LINEFEEDS variant is standard base64 with padding, like java.util.Base64, which is not available
    // before Android 8

    static String encodeBase64(ByteString value) {
        return Base64Variants.MIME_NO_LINEFEEDS.encode(value.toByteArray());
    }

    static void writeBase64(JsonGenerator generator, ByteString value) throws IOException {
        byte[] bytes = value.toByteArray();
        generator.writeBinary(Base64Variants.MIME_NO_LINEFEEDS, bytes, 0, bytes.length);
    }

    // ---- field masks ----

    /**
     * Format field mask paths like FieldMaskUtil.toJsonString: each path converted from lower underscore
     * to lower camel case (like Guava's CaseFormat), joined by commas. Empty paths are skipped.
     *
     * @param paths paths
     * @return JSON string value
     */

    static String formatFieldMask(List<String> paths) {
        StringBuilder builder = new StringBuilder();
        for (String path : paths) {
            if (path.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            boolean firstWord = true;
            boolean wordStart = true;
            for (int i = 0; i < path.length(); i++) {
                char c = path.charAt(i);
                if (c == '_') {
                    firstWord = false;
                    wordStart = true;
                    continue;
                }
                if (wordStart && !firstWord) {
                    builder.append(toUpperCase(c));
                } else {
                    builder.append(toLowerCase(c));
                }
                wordStart = false;
            }
        }
        return builder.toString();
    }

    private static char toUpperCase(char c) {
        return c >= 'a' && c <= 'z' ? (char) (c - ('a' - 'A')) : c;
    }

    private static char toLowerCase(char c) {
        return c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c;
    }

    // ---- any ----

    /**
     * Type name of a type url, like JsonFormat.
     *
     * @param typeUrl type url, i.e. type.googleapis.com/google.protobuf.Duration
     * @return type name, i.e. google.protobuf.Duration
     * @throws InvalidProtocolBufferException if the type url is not valid
     */

    static String getTypeName(String typeUrl) throws InvalidProtocolBufferException {
        int index = typeUrl.lastIndexOf('/');
        if (index == -1) {
            throw new InvalidProtocolBufferException("Invalid type url found: " + typeUrl);
        }
        return typeUrl.substring(index + 1);
    }

    static MessageLite parse(MessageLite prototype, ByteString value) throws InvalidProtocolBufferException {
        return prototype.getParserForType().parseFrom(value);
    }

    // ---- reflection ----

    static Object invoke(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to access " + method, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Unexpected exception from " + method, cause);
        }
    }

    static boolean has(Method hazzer, Object message) {
        return (Boolean) invoke(hazzer, message);
    }
}
