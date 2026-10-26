package no.entur.protobuf.json.filter;

import static com.google.common.truth.Truth.assertWithMessage;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

/**
 * The size calculations must match what the Jackson 2 and Jackson 3 generators write, with default settings.
 */

public class JsonOutputSizeTest {

    private static final JsonFactory JACKSON3 = new JsonFactory();
    private static final com.fasterxml.jackson.core.JsonFactory JACKSON2 = new com.fasterxml.jackson.core.JsonFactory();

    @Test
    public void strings() throws Exception {
        Random random = new Random(1);
        for (int i = 0; i < 5000; i++) {
            String value = RandomStrings.randomString(random, random.nextInt(20));
            byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);

            assertSize(value, JsonOutputSize.getQuotedSize(value, true), jackson3Bytes(g -> g.writeString(value)));
            assertSize(value, JsonOutputSize.getQuotedSize(value, false), jackson3Chars(g -> g.writeString(value)));
            // Jackson 2 escapes surrogates when writing strings as UTF-8, unless configured to combine them
            assertSize(value, JsonOutputSize.getQuotedSize(value, true, true), jackson2Bytes(g -> g.writeString(value)));
            assertSize(value, JsonOutputSize.getQuotedSize(value, true, false), jackson2CombinedBytes(g -> g.writeString(value)));
            assertSize(value, JsonOutputSize.getQuotedSize(value, false), jackson2Chars(g -> g.writeString(value)));

            char[] chars = value.toCharArray();
            assertSize(value, JsonOutputSize.getQuotedSize(chars, 0, chars.length, true), jackson3Bytes(g -> g.writeString(value)));

            // raw UTF-8
            assertSize(value, JsonOutputSize.getQuotedUtf8Size(utf8, 0, utf8.length, true), jackson3Bytes(g -> g.writeUTF8String(utf8, 0, utf8.length)));
            assertSize(value, JsonOutputSize.getQuotedUtf8Size(utf8, 0, utf8.length, true), jackson2Bytes(g -> g.writeUTF8String(utf8, 0, utf8.length)));
            assertSize(value, JsonOutputSize.getQuotedUtf8Size(utf8, 0, utf8.length, false), jackson3Chars(g -> g.writeString(value)));

            // field names
            assertSize(value, JsonOutputSize.getNameSize(value, true) + 3, jackson3Bytes(g -> {
                g.writeStartObject();
                g.writeName(value);
                g.writeNumber(1);
                g.writeEndObject();
            }));
            assertSize(value, JsonOutputSize.getNameSize(value, false) + 3, jackson2Chars(g -> {
                g.writeStartObject();
                g.writeFieldName(value);
                g.writeNumber(1);
                g.writeEndObject();
            }));
            assertSize(value, JsonOutputSize.getNameSize(value, true, true) + 3, jackson2Bytes(g -> {
                g.writeStartObject();
                g.writeFieldName(value);
                g.writeNumber(1);
                g.writeEndObject();
            }));

            // raw values
            String raw = "\"" + value + "\"";
            assertSize(value, JsonOutputSize.getRawSize(raw, true), jackson3Bytes(g -> g.writeRawValue(raw)));
            assertSize(value, JsonOutputSize.getRawSize(raw, false), jackson3Chars(g -> g.writeRawValue(raw)));
        }
    }

    @Test
    public void numbers() throws Exception {
        long[] values = {0, 1, -1, 9, 10, -10, 99, 100, Integer.MAX_VALUE, Integer.MIN_VALUE, 1L + Integer.MAX_VALUE, 9_999_999_999L, 10_000_000_000L,
                Long.MAX_VALUE, Long.MIN_VALUE, Long.MIN_VALUE + 1, 123_456_789_012_345_678L, -999_999_999_999_999_999L};
        for (long value : values) {
            assertSize(Long.toString(value), JsonOutputSize.getSize(value), jackson3Chars(g -> g.writeNumber(value)));
        }
        Random random = new Random(1);
        for (int i = 0; i < 10000; i++) {
            long value = random.nextLong() >> random.nextInt(64);
            assertSize(Long.toString(value), JsonOutputSize.getSize(value), jackson3Chars(g -> g.writeNumber(value)));
            int intValue = (int) value;
            assertSize(Integer.toString(intValue), JsonOutputSize.getSize(intValue), jackson2Chars(g -> g.writeNumber(intValue)));
        }
    }

    private static void assertSize(String value, int expected, int actual) {
        assertWithMessage(RandomStrings.escape(value)).that(expected).isEqualTo(actual);
    }

    interface Jackson3Write {
        void write(JsonGenerator generator) throws Exception;
    }

    interface Jackson2Write {
        void write(com.fasterxml.jackson.core.JsonGenerator generator) throws Exception;
    }

    private static int jackson3Bytes(Jackson3Write write) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = JACKSON3.createGenerator(ObjectWriteContext.empty(), output)) {
            write.write(generator);
        }
        return output.size();
    }

    private static int jackson3Chars(Jackson3Write write) throws Exception {
        StringWriter output = new StringWriter();
        try (JsonGenerator generator = JACKSON3.createGenerator(ObjectWriteContext.empty(), output)) {
            write.write(generator);
        }
        return output.getBuffer().length();
    }

    private static int jackson2Bytes(Jackson2Write write) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (com.fasterxml.jackson.core.JsonGenerator generator = JACKSON2.createGenerator(output)) {
            write.write(generator);
        }
        return output.size();
    }

    private static int jackson2CombinedBytes(Jackson2Write write) throws Exception {
        com.fasterxml.jackson.core.JsonFactory factory = com.fasterxml.jackson.core.JsonFactory.builder()
                .enable(com.fasterxml.jackson.core.json.JsonWriteFeature.COMBINE_UNICODE_SURROGATES_IN_UTF8)
                .build();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (com.fasterxml.jackson.core.JsonGenerator generator = factory.createGenerator(output)) {
            write.write(generator);
        }
        return output.size();
    }

    private static int jackson2Chars(Jackson2Write write) throws Exception {
        StringWriter output = new StringWriter();
        try (com.fasterxml.jackson.core.JsonGenerator generator = JACKSON2.createGenerator(output)) {
            write.write(generator);
        }
        return output.getBuffer().length();
    }
}
