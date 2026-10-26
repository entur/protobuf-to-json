package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.stream.CodedOutputStreamProtobufJsonWriter;
import no.entur.protobuf.json.stream.FilteringCodedOutputStreamProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

import static com.google.common.truth.Truth.assertThat;

/**
 * The filtering writer, with filters which do not change anything, writes the same as JsonFormat.
 */

public class FilteringCodedOutputStreamJsonFormatConformanceTest extends AbstractJsonFormatConformanceTest {

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer() {
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = CodedOutputStreamProtobufJsonWriter.newBuilder()
                .withTypeRegistry(TestSupport.TYPE_REGISTRY)
                .withMaxSize(Integer.MAX_VALUE)
                .withAnonymizePaths("$.notAField", "$.*.notAField")
                .withPruneKeys("notAField")
                .withMaxStringLength(1_000_000)
                .build();
        assertThat(writer).isInstanceOf(FilteringCodedOutputStreamProtobufJsonWriter.class);
        return writer;
    }
}
