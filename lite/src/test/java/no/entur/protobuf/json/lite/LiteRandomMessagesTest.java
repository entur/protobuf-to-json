package no.entur.protobuf.json.lite;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.MessageLiteOrBuilder;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.ProtobufJsonWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

/**
 * Random messages (see RandomMessages in the test-support module), written with JsonFormat from the full protobuf messages during
 * the build (see PayloadFiles), are the same when written from the lite messages, apart from the documented differences.
 */

public class LiteRandomMessagesTest {

    private final ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> writer = LiteTestSupport.writer();

    static Stream<Arguments> messages() throws Exception {
        return LiteTestSupport.readMessages("/payloads/random-messages.tsv").stream()
                .map(columns -> Arguments.of(columns[0], columns[2], columns[3]));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    public void sameAsJsonFormat(String name, String base64, String json) throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.parseFrom(Base64.getDecoder().decode(base64));

        String chars = LiteTestSupport.writeChars(writer, message);
        assertThat(LiteTestSupport.writeBytes(writer, message, true)).isEqualTo(chars);

        List<String> differences = LiteTestSupport.compare(LiteTestSupport.readTree(json), LiteTestSupport.readTree(chars));
        assertWithMessage(name + ": " + chars + " vs JsonFormat " + json).that(differences).isEmpty();
    }
}
