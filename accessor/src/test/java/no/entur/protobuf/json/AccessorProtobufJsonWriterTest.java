package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.accessor.AccessorProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

public class AccessorProtobufJsonWriterTest extends AbstractProtobufJsonWriterTest {

    private final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build();

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer() {
        return writer;
    }
}
