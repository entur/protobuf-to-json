package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.accessor.AccessorProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

public class AccessorProtobufJacksonConformanceTest extends AbstractProtobufJacksonConformanceTest {

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer() {
        return AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build();
    }
}
