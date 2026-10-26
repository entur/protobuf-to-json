package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import no.entur.protobuf.json.transcode.FilteringTranscodingProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

import static com.google.common.truth.Truth.assertThat;

/**
 * The filtering writer, with filters which do not change anything.
 */

public class FilteringTranscodingProtobufJacksonConformanceTest extends AbstractProtobufJacksonConformanceTest {

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer() {
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = TranscodingProtobufJsonWriter.newBuilder()
                .withTypeRegistry(TestSupport.TYPE_REGISTRY)
                .withMaxSize(Integer.MAX_VALUE)
                .withAnonymizePaths("$.notAField", "$.*.notAField")
                .withPruneKeys("notAField")
                .withMaxStringLength(1_000_000)
                .build();
        assertThat(writer).isInstanceOf(FilteringTranscodingProtobufJsonWriter.class);
        return writer;
    }
}
