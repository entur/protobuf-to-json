package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

public class TranscodingProtobufJsonWriterTest extends AbstractProtobufJsonWriterTest {

    private final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build();

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer() {
        return writer;
    }
}
