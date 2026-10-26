package no.entur.protobuf.json.benchmark;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import no.entur.protobuf.json.test.BenchmarkPayloads;
import no.entur.protobuf.json.test.RandomMessages;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertWithMessage;

/**
 * Every path of the filters matches a field of its payload.
 */

public class BenchmarkFiltersTest {

    private static final JsonFormat.TypeRegistry TYPE_REGISTRY = RandomMessages.createTypeRegistry();

    @Test
    public void everyPathMatches() throws Exception {
        for (String payload : BenchmarkPayloads.NAMES) {
            Message message = BenchmarkPayloads.create(payload);
            String unfiltered = TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(TYPE_REGISTRY).build().writeAsString(message);

            List<String> anonymize = new ArrayList<>(BenchmarkFilters.ANY_PATHS.get(payload));
            anonymize.addAll(BenchmarkFilters.MIXED_ANONYMIZE.get(payload));
            for (String path : anonymize) {
                String filtered = TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(TYPE_REGISTRY).withAnonymizePaths(path).build().writeAsString(message);
                assertWithMessage(payload + " " + path).that(filtered).isNotEqualTo(unfiltered);
            }
            for (String path : BenchmarkFilters.MIXED_PRUNE.get(payload)) {
                String filtered = TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(TYPE_REGISTRY).withPrunePaths(path).build().writeAsString(message);
                assertWithMessage(payload + " " + path).that(filtered).isNotEqualTo(unfiltered);
            }
        }
    }
}
