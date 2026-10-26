package no.entur.protobuf.json;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import no.entur.protobuf.json.jsonformat.JsonFormatComparison;
import no.entur.protobuf.json.test.RandomMessages;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared by the tests of the full protobuf writers.
 */

public final class TestSupport {

    // fail on duplicate keys
    public static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(StreamWriteFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    public static final JsonFormat.TypeRegistry TYPE_REGISTRY = RandomMessages.createTypeRegistry();

    private TestSupport() {
    }

    /**
     * Write to a byte-based (UTF-8) generator.
     */

    public static String writeBytes(ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer, MessageOrBuilder message) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = MAPPER.createGenerator(output)) {
            writer.write(message, generator);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    /**
     * Write to a char-based generator.
     */

    public static String writeChars(ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer, MessageOrBuilder message) throws Exception {
        StringWriter output = new StringWriter();
        try (JsonGenerator generator = MAPPER.createGenerator(output)) {
            writer.write(message, generator);
        }
        return output.toString();
    }

    public static JsonFormatComparison comparison(ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer) {
        return JsonFormatComparison.newBuilder().withTypeRegistry(TYPE_REGISTRY).withWriter(writer).build();
    }

    /**
     * Compare a message, also as dynamic message and builder.
     */

    public static void assertSameAsJsonFormat(JsonFormatComparison comparison, MessageOrBuilder message, String description) {
        assertSame(comparison, message, description);
        if (message instanceof Message m && !(m instanceof DynamicMessage)) {
            try {
                assertSame(comparison, DynamicMessage.parseFrom(m.getDescriptorForType(), m.toByteString()), description + " (DynamicMessage)");
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalStateException(e);
            }
            assertSame(comparison, m.toBuilder(), description + " (builder)");
        }
    }

    private static void assertSame(JsonFormatComparison comparison, MessageOrBuilder message, String description) {
        JsonFormatComparison.Result result = comparison.compare(message);
        if (!result.isSame()) {
            fail(description + ": " + result);
        }
    }
}
