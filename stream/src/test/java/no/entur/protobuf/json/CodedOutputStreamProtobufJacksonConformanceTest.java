package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.stream.CodedOutputStreamProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

public class CodedOutputStreamProtobufJacksonConformanceTest extends AbstractProtobufJacksonConformanceTest {

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer() {
        return CodedOutputStreamProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build();
    }
}
