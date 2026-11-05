package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.stream.CodedOutputStreamProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

public class CodedOutputStreamProtobufJsonWriterTest extends AbstractProtobufJsonWriterTest {

    private final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = CodedOutputStreamProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build();

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer() {
        return writer;
    }
}
