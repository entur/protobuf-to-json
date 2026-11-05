package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.accessor.AccessorProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

public class FilteringAccessorProtobufJsonWriterTest extends AbstractFilteringProtobufJsonWriterTest {

    private final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> unfiltered = AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build();

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> unfiltered() {
        return unfiltered;
    }

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> filtered(Filter filter) {
        return filter.configure(AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY)).build();
    }
}
