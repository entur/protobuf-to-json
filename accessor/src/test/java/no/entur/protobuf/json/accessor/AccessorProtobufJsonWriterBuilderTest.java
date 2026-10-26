package no.entur.protobuf.json.accessor;

import com.google.protobuf.Any;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf_test_messages.proto2.TestMessagesProto2.TestAllTypesProto2;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.TestSupport;
import no.entur.protobuf.json.test.RandomMessages;
import no.entur.protobuf.json.test.protobufjackson.GithubApi.SearchResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AccessorProtobufJsonWriterBuilderTest {

    @Test
    public void warmup() throws Exception {
        TestAllTypesProto3 message = new RandomMessages(1).fill(TestAllTypesProto3.newBuilder());
        String expected = AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build().writeAsString(message);

        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = AccessorProtobufJsonWriter.newBuilder()
                .withTypeRegistry(TestSupport.TYPE_REGISTRY)
                .withWarmup(message, new RandomMessages(2).fill(TestAllTypesProto3.newBuilder()))
                .withWarmup(List.of(new RandomMessages(3).fill(TestAllTypesProto2.newBuilder())))
                .withWarmupIterations(100)
                .build();
        assertThat(writer.writeAsString(message)).isEqualTo(expected);

        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> filtering = AccessorProtobufJsonWriter.newBuilder()
                .withTypeRegistry(TestSupport.TYPE_REGISTRY)
                .withMaxSize(Integer.MAX_VALUE)
                .withWarmup(message)
                .withWarmupIterations(100)
                .build();
        assertThat(filtering).isInstanceOf(FilteringAccessorProtobufJsonWriter.class);
        assertThat(filtering.writeAsString(message)).isEqualTo(expected);
    }

    @Test
    public void warmupWritesTheMessages() {
        // the type of the google.protobuf.Any value is not in the (default, empty) type registry
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalAny(Any.pack(TestAllTypesProto3.getDefaultInstance())).build();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AccessorProtobufJsonWriter.newBuilder().withWarmup(message).withWarmupIterations(1).build());
        assertThat(e).hasMessageThat().contains("warmup");

        // not written
        AccessorProtobufJsonWriter.newBuilder().withWarmup(message).withWarmupIterations(0).build();
    }

    @Test
    public void warmupTypes() throws Exception {
        TestAllTypesProto3 message = new RandomMessages(1).fill(TestAllTypesProto3.newBuilder());
        String expected = AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build().writeAsString(message);

        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = AccessorProtobufJsonWriter.newBuilder()
                .withTypeRegistry(TestSupport.TYPE_REGISTRY)
                .withWarmupTypes(TestAllTypesProto3.class, TestAllTypesProto2.class)
                .withWarmupTypes(List.of(SearchResponse.class))
                .withWarmupIterations(10)
                .build();
        assertThat(writer.writeAsString(message)).isEqualTo(expected);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AccessorProtobufJsonWriter.newBuilder().withWarmupTypes(DynamicMessage.class).build());
        assertThat(e).hasMessageThat().contains(DynamicMessage.class.getName());
    }

    @Test
    public void warmupIterations() {
        assertThrows(IllegalArgumentException.class, () -> AccessorProtobufJsonWriter.newBuilder().withWarmupIterations(-1));
    }
}
