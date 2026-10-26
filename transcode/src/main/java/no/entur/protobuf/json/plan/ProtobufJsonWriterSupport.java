package no.entur.protobuf.json.plan;

import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.ProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Internal; shared by the JSON writer implementations.
 */

public final class ProtobufJsonWriterSupport {

    public static final JsonFactory JSON_FACTORY = JsonFactory.builder().build();

    private ProtobufJsonWriterSupport() {
    }

    /**
     * Write a message as a JSON string.
     *
     * @param writer  writer
     * @param message message or builder
     * @return JSON
     * @throws IOException if writing fails
     */

    public static String writeAsString(ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer, MessageOrBuilder message) throws IOException {
        int size = message instanceof MessageLite messageLite ? messageLite.getSerializedSize() * 2 : 256;
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(size, 32));
        try (JsonGenerator generator = JSON_FACTORY.createGenerator(ObjectWriteContext.empty(), output)) {
            writer.write(message, generator);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
