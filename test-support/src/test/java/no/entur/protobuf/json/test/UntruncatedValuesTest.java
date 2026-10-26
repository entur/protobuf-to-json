package no.entur.protobuf.json.test;

import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;

public class UntruncatedValuesTest {

    private static final JsonFormat.TypeRegistry TYPE_REGISTRY = RandomMessages.createTypeRegistry();

    private static Set<List<Object>> find(Message message) throws Exception {
        String json = JsonFormat.printer().usingTypeRegistry(TYPE_REGISTRY).omittingInsignificantWhitespace().print(message);
        return UntruncatedValues.find(json, message.getDescriptorForType(), TYPE_REGISTRY);
    }

    @Test
    public void numbersEnumsTimestampsAndDurations() throws Exception {
        Set<List<Object>> locations = find(FilterTestMessages.create().get("allKinds"));

        assertThat(locations).containsAtLeast(
                List.of("optionalInt64"),
                List.of("optionalFloat"),
                List.of("optionalNestedMessage", "corecursive", "repeatedNestedEnum", 0),
                List.of("packedSint64", 0),
                List.of("packedSint64", 2),
                List.of("mapStringNestedEnum", "known"),
                List.of("optionalUint64Wrapper"),
                List.of("optionalDuration"),
                List.of("optionalTimestamp"));
        // text, bytes and the type of Any are truncated; numbers, unknown enum values and booleans are not strings
        assertThat(locations).containsNoneOf(
                List.of("optionalString"),
                List.of("optionalBytes"),
                List.of("optionalAny", "@type"),
                List.of("optionalNestedEnum"),
                List.of("mapStringNestedEnum", "unknown"),
                List.of("optionalStruct", "a"));
    }

    @Test
    public void rootTimestamp() throws Exception {
        assertThat(find(FilterTestMessages.create().get("rootTimestamp"))).containsExactly(List.of());
    }

    @Test
    public void unsignedMapKeysAlsoAsSigned() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().putMapUint64Uint64(-1L, 2L).build();

        assertThat(find(message)).containsExactly(List.of("mapUint64Uint64", "18446744073709551615"), List.of("mapUint64Uint64", "-1"));
    }

    @Test
    public void wellKnownTypesInAny() throws Exception {
        Message message = TestAllTypesProto3.newBuilder()
                .setOptionalAny(Any.pack(Timestamp.newBuilder().setSeconds(1).build()))
                .build();

        assertThat(find(message)).containsExactly(List.of("optionalAny", "value"));
    }
}
