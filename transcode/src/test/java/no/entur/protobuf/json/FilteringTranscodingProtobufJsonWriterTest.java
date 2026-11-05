package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.plan.FilteredJsonValues;
import no.entur.protobuf.json.transcode.FilteringTranscodingProtobufJsonWriter;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;

import java.io.StringWriter;

import static com.google.common.truth.Truth.assertThat;

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

    @Test
    public void writeMessageReportsTruncation() throws Exception {
        FilteringTranscodingProtobufJsonWriter writer = (FilteringTranscodingProtobufJsonWriter) filtered(Filter.maxSize(48));
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalInt32(1).setOptionalString("a".repeat(100)).build();

        StringWriter output = new StringWriter();
        boolean complete;
        try (JsonGenerator generator = TestSupport.MAPPER.createGenerator(output)) {
            complete = writer.write(message, new FilteredJsonValues(writer.getFilter(), generator));
        }
        assertThat(complete).isFalse();
        assertThat(output.toString()).isEqualTo("{\"optionalInt32\":1}");
    }
}
