package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

public class FilteringTranscodingProtobufJsonWriterTest extends AbstractFilteringProtobufJsonWriterTest {

    private final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> unfiltered = TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build();

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> unfiltered() {
        return unfiltered;
    }

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> filtered(Filter filter) {
        return filter.configure(TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY)).build();
    }
}
