package no.entur.protobuf.json.benchmark;

import com.google.protobuf.Any;
import com.google.protobuf.Int32Value;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.jsonformat.JsonFormatComparison;
import no.entur.protobuf.json.test.BenchmarkPayloads;
import no.entur.protobuf.json.test.RandomMessages;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

/**
 * HubSpot jackson-datatype-protobuf writes the benchmark payloads like JsonFormat does, but differs for other messages.
 */

public class HubSpotJacksonDatatypeProtobufTest {

    private static final JsonFormat.Printer JSON_FORMAT = JsonFormat.printer().usingTypeRegistry(RandomMessages.createTypeRegistry()).omittingInsignificantWhitespace();

    private final JsonMapper mapper = HubSpotJacksonDatatypeProtobuf.createMapper();

    @Test
    public void writesThePayloadsLikeJsonFormat() throws Exception {
        JsonFormatComparison comparison = JsonFormatComparison.newBuilder()
                .withTypeRegistry(RandomMessages.createTypeRegistry())
                .withWriter(HubSpotJacksonDatatypeProtobuf.createWriter(mapper))
                .build();
        for (String payload : BenchmarkPayloads.NAMES) {
            JsonFormatComparison.Result result = comparison.compare(BenchmarkPayloads.create(payload));
            assertWithMessage(payload + ": " + result).that(result.isSame()).isTrue();
            assertThat(mapper.writeValueAsBytes(BenchmarkPayloads.create(payload)).length).isGreaterThan(2);
        }
    }

    @Test
    public void writesAnyAsRegularMessage() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalAny(Any.pack(Int32Value.of(1))).build();

        assertThat(JSON_FORMAT.print(message)).isEqualTo("{\"optionalAny\":{\"@type\":\"type.googleapis.com/google.protobuf.Int32Value\",\"value\":1}}");
        assertThat(mapper.writeValueAsString(message)).isEqualTo("{\"optionalAny\":{\"typeUrl\":\"type.googleapis.com/google.protobuf.Int32Value\",\"value\":\"CAE=\"}}");
    }

    @Test
    public void writesUnknownEnumValuesAsNames() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalNestedEnumValue(1065).build();

        assertThat(JSON_FORMAT.print(message)).isEqualTo("{\"optionalNestedEnum\":1065}");
        assertThat(mapper.writeValueAsString(message)).isEqualTo("{\"optionalNestedEnum\":\"UNKNOWN_ENUM_VALUE_NestedEnum_1065\"}");
    }

    @Test
    public void writesUnsignedMapKeysAsSigned() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().putMapUint64Uint64(-1L, 1L).build();

        assertThat(JSON_FORMAT.print(message)).isEqualTo("{\"mapUint64Uint64\":{\"18446744073709551615\":\"1\"}}");
        assertThat(mapper.writeValueAsString(message)).isEqualTo("{\"mapUint64Uint64\":{\"-1\":\"1\"}}");
    }

    @Test
    public void doesNotUseTheJsonNames() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setFieldName7(1).setFieldName8(2).setFieldName18(3).build();

        assertThat(JSON_FORMAT.print(message)).isEqualTo("{\"fieldName7\":1,\"FieldName8\":2,\"FieldName18\":3}");
        assertThat(mapper.writeValueAsString(message)).isEqualTo("{\"fieldname7\":1,\"fieldname8\":2,\"fieldName18\":3}");
    }
}
