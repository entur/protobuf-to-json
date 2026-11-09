package no.entur.protobuf.json;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Duration;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;

import java.io.StringWriter;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TranscodingProtobufJsonWriterTest extends AbstractProtobufJsonWriterTest {

    private final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build();

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer() {
        return writer;
    }

    @Test
    public void unresolvedAnyResolvesWellKnownTypes() throws Exception {
        // the stream and accessor writers write google.protobuf.Any values with this writer
        JsonFormat.TypeRegistry empty = JsonFormat.TypeRegistry.getEmptyTypeRegistry();
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> unresolving = TranscodingProtobufJsonWriter.newBuilder()
                .withTypeRegistry(empty)
                .withUnresolvedAny(UnresolvedAny.VALUE)
                .build();
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> failing = TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(empty).build();

        // as the lite writer, which always knows the well-known types
        Any bytes = Any.pack(BytesValue.of(ByteString.copyFrom(new byte[]{0, 1, 2, (byte) 0xff})));
        Any duration = Any.pack(Duration.newBuilder().setSeconds(1).build());
        assertThat(TestSupport.writeBytes(unresolving, bytes)).isEqualTo("{\"@type\":\"" + bytes.getTypeUrl() + "\",\"value\":\"AAEC/w==\"}");
        assertThat(TestSupport.writeChars(unresolving, Any.pack(duration))).isEqualTo("{\"@type\":\"" + Any.pack(duration).getTypeUrl() + "\",\"value\":"
                + "{\"@type\":\"" + duration.getTypeUrl() + "\",\"value\":\"1s\"}}");

        // other types are not resolved
        Any message = Any.pack(TestAllTypesProto3.newBuilder().setOptionalInt32(1).build());
        assertThat(TestSupport.writeBytes(unresolving, message)).isEqualTo("{\"@type\":\"" + message.getTypeUrl() + "\",\"@value\":\"CAE=\"}");

        // by default, the same as JsonFormat, which fails if a well-known type is not registered
        for (Any any : List.of(bytes, duration, message)) {
            assertThrows(InvalidProtocolBufferException.class, () -> JsonFormat.printer().usingTypeRegistry(empty).print(any));
            InvalidProtocolBufferException e = TestSupport.assertInvalidProtocolBuffer(() -> TestSupport.writeBytes(failing, any));
            assertThat(e).hasMessageThat().isEqualTo("Cannot find type for url: " + any.getTypeUrl());
        }
    }

    @Test
    public void writeMessageReportsCompletion() throws Exception {
        // with the class type and a message, the more specific write(Message, JsonGenerator) is selected
        TranscodingProtobufJsonWriter transcodingWriter = (TranscodingProtobufJsonWriter) writer;
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalInt32(1).build();

        StringWriter output = new StringWriter();
        boolean complete;
        try (JsonGenerator generator = TestSupport.MAPPER.createGenerator(output)) {
            complete = transcodingWriter.write(message, generator);
        }
        assertThat(complete).isTrue();
        assertThat(output.toString()).isEqualTo("{\"optionalInt32\":1}");
    }
}
