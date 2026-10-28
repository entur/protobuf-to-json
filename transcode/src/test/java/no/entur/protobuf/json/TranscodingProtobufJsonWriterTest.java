package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;

import java.io.StringWriter;

import static com.google.common.truth.Truth.assertThat;

public class TranscodingProtobufJsonWriterTest extends AbstractProtobufJsonWriterTest {

    private final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build();

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer() {
        return writer;
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
