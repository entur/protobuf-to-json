package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

public class TranscodingProtobufJacksonConformanceTest extends AbstractProtobufJacksonConformanceTest {

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer() {
        return TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build();
    }
}
