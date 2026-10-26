package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.accessor.AccessorProtobufJsonWriter;
import no.entur.protobuf.json.accessor.FilteringAccessorProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

import static com.google.common.truth.Truth.assertThat;

/**
 * The filtering writer, with filters which do not change anything.
 */

public class FilteringAccessorProtobufJacksonConformanceTest extends AbstractProtobufJacksonConformanceTest {

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer() {
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = AccessorProtobufJsonWriter.newBuilder()
                .withTypeRegistry(TestSupport.TYPE_REGISTRY)
                .withMaxSize(Integer.MAX_VALUE)
                .withAnonymizePaths("$.notAField", "$.*.notAField")
                .withPruneKeys("notAField")
                .withMaxStringLength(1_000_000)
                .build();
        assertThat(writer).isInstanceOf(FilteringAccessorProtobufJsonWriter.class);
        return writer;
    }
}
