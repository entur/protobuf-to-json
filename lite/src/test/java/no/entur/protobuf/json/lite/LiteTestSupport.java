package no.entur.protobuf.json.lite;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.MessageLiteOrBuilder;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.filter.ReferenceFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Shared by the lite tests.
 */

public final class LiteTestSupport {

    public static final String TEST_ALL_TYPES_PROTO3_URL = "type.googleapis.com/protobuf_test_messages.proto3.TestAllTypesProto3";

    public static final JsonFactory FACTORY = new JsonFactory();
    public static final JsonFactory COMBINING_FACTORY = JsonFactory.builder().enable(JsonWriteFeature.COMBINE_UNICODE_SURROGATES_IN_UTF8).build();

    public static final ObjectMapper MAPPER = new ObjectMapper();

    /** Jackson 2 outputs for the reference filter */
    public static final ReferenceFilter.Output CHARS = output(FACTORY, false);
    public static final ReferenceFilter.Output UTF8 = output(COMBINING_FACTORY, true);
    public static final ReferenceFilter.Output UTF8_ESCAPED_SURROGATES = output(FACTORY, true);

    private LiteTestSupport() {
    }

    public static LiteProtobufJsonWriter.Builder newBuilder() {
        return LiteProtobufJsonWriter.newBuilder().withAnyType(TEST_ALL_TYPES_PROTO3_URL, TestAllTypesProto3.getDefaultInstance());
    }

    public static ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> writer() {
        return newBuilder().build();
    }

    /**
     * Write to a char-based generator.
     */

    public static String writeChars(ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> writer, MessageLiteOrBuilder message) throws IOException {
        return writtenChars(writer, message).json();
    }

    /**
     * The output of a write, and whether the whole message was written.
     */

    public record Written(String json, boolean complete) {
    }

    public static Written writtenChars(ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> writer, MessageLiteOrBuilder message) throws IOException {
        StringWriter output = new StringWriter();
        boolean complete;
        try (JsonGenerator generator = FACTORY.createGenerator(output)) {
            complete = writer.write(message, generator);
        }
        return new Written(output.toString(), complete);
    }

    /**
     * Write to a byte-based generator, with surrogate pairs escaped (the Jackson 2 default) or combined.
     */

    public static String writeBytes(ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> writer, MessageLiteOrBuilder message, boolean combineSurrogates) throws IOException {
        return writtenBytes(writer, message, combineSurrogates).json();
    }

    public static Written writtenBytes(ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> writer, MessageLiteOrBuilder message, boolean combineSurrogates) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean complete;
        try (JsonGenerator generator = (combineSurrogates ? COMBINING_FACTORY : FACTORY).createGenerator(output)) {
            complete = writer.write(message, generator);
        }
        return new Written(output.toString(StandardCharsets.UTF_8), complete);
    }

    private static ReferenceFilter.Output output(JsonFactory factory, boolean utf8) {
        return new ReferenceFilter.Output() {
            @Override
            public String write(List<ReferenceFilter.Token> tokens) {
                try {
                    if (utf8) {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        try (JsonGenerator generator = factory.createGenerator(output)) {
                            writeTokens(tokens, generator);
                        }
                        return output.toString(StandardCharsets.UTF_8);
                    }
                    StringWriter output = new StringWriter();
                    try (JsonGenerator generator = factory.createGenerator(output)) {
                        writeTokens(tokens, generator);
                    }
                    return output.toString();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public int size(String output) {
                return utf8 ? output.getBytes(StandardCharsets.UTF_8).length : output.length();
            }
        };
    }

    private static void writeTokens(List<ReferenceFilter.Token> tokens, JsonGenerator generator) throws IOException {
        for (ReferenceFilter.Token token : tokens) {
            if (token.name() != null) {
                generator.writeFieldName(token.name());
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

    public static byte[] readResource(String name) throws IOException {
        try (InputStream in = LiteTestSupport.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalArgumentException("No resource " + name);
            }
            return in.readAllBytes();
        }
    }

    /**
     * Read the messages of a file written by PayloadFiles: a line per message, with its name, its type, its binary form in
     * base64 and the JsonFormat output.
     *
     * @param name resource name
     * @return the columns of each line
     */

    public static List<String[]> readMessages(String name) throws IOException {
        List<String[]> messages = new ArrayList<>();
        for (String line : new String(readResource(name), StandardCharsets.UTF_8).split("\n")) {
            messages.add(line.split("\t", -1));
        }
        return messages;
    }

    public static JsonNode readTree(String json) throws IOException {
        return MAPPER.readTree(json);
    }

    private static final BigInteger TWO_TO_32 = BigInteger.ONE.shiftLeft(32);
    private static final BigInteger TWO_TO_64 = BigInteger.ONE.shiftLeft(64);

    /**
     * Compare the output of JsonFormat with the output of the lite writer, as JSON values. Numbers are compared numerically.
     * The documented differences are tolerated: unsigned values written as signed, and JSON names with a leading upper case letter.
     *
     * @return the differences
     */

    public static List<String> compare(JsonNode expected, JsonNode actual) {
        List<String> differences = new ArrayList<>();
        compare("", expected, actual, differences);
        return differences;
    }

    private static void compare(String path, JsonNode expected, JsonNode actual, List<String> differences) {
        if (expected.isObject() && actual.isObject()) {
            if (expected.size() != actual.size()) {
                differences.add(path + ": expected " + expected.size() + " fields, got " + actual.size() + " (" + fieldNames(expected) + " vs " + fieldNames(actual) + ")");
            }
            for (Iterator<Map.Entry<String, JsonNode>> iterator = expected.fields(); iterator.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                String key = entry.getKey();
                JsonNode value = actual.get(key);
                if (value == null) {
                    value = actual.get(signedKey(key));
                }
                if (value == null && !key.isEmpty()) {
                    // leading upper case letters are not known in lite messages
                    value = actual.get(Character.toLowerCase(key.charAt(0)) + key.substring(1));
                }
                if (value == null) {
                    differences.add(path + "/" + key + ": missing");
                    continue;
                }
                compare(path + "/" + key, entry.getValue(), value, differences);
            }
        } else if (expected.isArray() && actual.isArray()) {
            if (expected.size() != actual.size()) {
                differences.add(path + ": expected " + expected.size() + " elements, got " + actual.size());
                return;
            }
            for (int i = 0; i < expected.size(); i++) {
                compare(path + "/" + i, expected.get(i), actual.get(i), differences);
            }
        } else if (expected.isNumber() && actual.isNumber()) {
            BigDecimal e = expected.decimalValue();
            BigDecimal a = actual.decimalValue();
            if (e.compareTo(a) != 0 && !isUnsignedAsSigned(e, a)) {
                differences.add(path + ": expected " + expected + ", got " + actual);
            }
        } else if (expected.isTextual() && actual.isTextual()) {
            if (!expected.textValue().equals(actual.textValue()) && !isUnsignedAsSigned(expected.textValue(), actual.textValue())) {
                differences.add(path + ": expected " + expected + ", got " + actual);
            }
        } else if (!expected.equals(actual)) {
            differences.add(path + ": expected " + expected + ", got " + actual);
        }
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static String signedKey(String key) {
        try {
            BigInteger value = new BigInteger(key);
            if (value.bitLength() == 32 && value.signum() > 0) {
                return value.subtract(TWO_TO_32).toString();
            }
            if (value.bitLength() == 64 && value.signum() > 0) {
                return value.subtract(TWO_TO_64).toString();
            }
        } catch (NumberFormatException e) {
            // not a number
        }
        return key;
    }

    private static boolean isUnsignedAsSigned(String expected, String actual) {
        try {
            return isUnsignedAsSigned(new BigDecimal(expected), new BigDecimal(actual));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isUnsignedAsSigned(BigDecimal expected, BigDecimal actual) {
        BigDecimal difference = expected.subtract(actual);
        return difference.compareTo(new BigDecimal(TWO_TO_32)) == 0 || difference.compareTo(new BigDecimal(TWO_TO_64)) == 0;
    }
}
