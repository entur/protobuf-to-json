package no.entur.protobuf.json.benchmark;

import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.jsonformat.JsonFormatComparison;
import no.entur.protobuf.json.test.BenchmarkPayloads;
import no.entur.protobuf.json.test.RandomMessages;
import org.curioswitch.common.protobuf.json.MessageMarshaller;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * curioswitch protobuf-jackson writes the benchmark payloads like JsonFormat does, but fails for some valid messages.
 */

public class CurioswitchProtobufJacksonTest {

    private static final JsonFormat.Printer JSON_FORMAT = JsonFormat.printer().usingTypeRegistry(RandomMessages.createTypeRegistry()).omittingInsignificantWhitespace();

    private final MessageMarshaller marshaller = CurioswitchProtobufJackson.createMarshaller();

    @Test
    public void writesThePayloadsLikeJsonFormat() throws Exception {
        JsonFormatComparison comparison = JsonFormatComparison.newBuilder()
                .withTypeRegistry(RandomMessages.createTypeRegistry())
                .withWriter(CurioswitchProtobufJackson.createWriter(marshaller))
                .build();
        for (String payload : BenchmarkPayloads.NAMES) {
            JsonFormatComparison.Result result = comparison.compare(BenchmarkPayloads.create(payload));
            assertWithMessage(payload + ": " + result).that(result.isSame()).isTrue();
            assertThat(marshaller.writeValueAsBytes(BenchmarkPayloads.create(payload)).length).isGreaterThan(2);
        }
    }

    @Test
    public void failsForTimestampsBefore1970WithNanos() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalTimestamp(Timestamp.newBuilder().setSeconds(-1).setNanos(1)).build();

        assertThat(JSON_FORMAT.print(message)).isEqualTo("{\"optionalTimestamp\":\"1969-12-31T23:59:59.000000001Z\"}");
        assertThrows(IllegalArgumentException.class, () -> marshaller.writeValueAsString(message));
    }

    @Test
    public void failsForEmptyAnyFollowedByAnotherField() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalAny(Any.getDefaultInstance())
                .setOptionalValue(Value.newBuilder().setBoolValue(true))
                .build();

        assertThat(JSON_FORMAT.print(message)).isEqualTo("{\"optionalAny\":{},\"optionalValue\":true}");
        assertThrows(IOException.class, () -> marshaller.writeValueAsString(message));
    }
}
