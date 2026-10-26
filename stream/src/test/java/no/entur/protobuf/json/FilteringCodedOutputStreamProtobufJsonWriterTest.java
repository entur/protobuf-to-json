package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.stream.CodedOutputStreamProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

public class FilteringCodedOutputStreamProtobufJsonWriterTest extends AbstractFilteringProtobufJsonWriterTest {

    private final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> unfiltered = CodedOutputStreamProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build();

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> unfiltered() {
        return unfiltered;
    }

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> filtered(Filter filter) {
        return filter.configure(CodedOutputStreamProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY)).build();
    }
}
