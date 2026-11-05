package no.entur.protobuf.json;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;

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
    public void unresolvedAnyResolvesWellKnownTypes() throws Exception {
        // the stream and accessor writers write google.protobuf.Any values with this writer
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = TranscodingProtobufJsonWriter.newBuilder()
                .withTypeRegistry(JsonFormat.TypeRegistry.getEmptyTypeRegistry())
                .withUnresolvedAny(UnresolvedAny.VALUE)
                .withPruneKeys("x")
                .withMaxStringLength(4)
                .build();

        // a resolved well-known value is filtered as such
        Any bytes = Any.pack(BytesValue.of(ByteString.copyFrom(new byte[]{0, 1, 2, (byte) 0xff})));
        assertThat(TestSupport.writeBytes(writer, bytes)).isEqualTo("{\"@type\":\"type... + 42\",\"value\":\"AAEC/w==\"}");
        // an unresolved payload is pruned, as the key could match within it
        Any message = Any.pack(TestAllTypesProto3.newBuilder().setOptionalInt32(1).build());
        assertThat(TestSupport.writeBytes(writer, message)).isEqualTo("{\"@type\":\"type... + 64\",\"@value\":\"[removed]\"}");
    }
}
