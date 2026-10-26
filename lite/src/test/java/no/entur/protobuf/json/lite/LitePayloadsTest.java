package no.entur.protobuf.json.lite;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import com.google.protobuf.Type;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.test.protobufjackson.GithubApi.SearchResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static com.google.common.truth.Truth.assertWithMessage;

/**
 * The benchmark payloads, written with JsonFormat from the full protobuf messages during the build (see PayloadFiles),
 * are the same when written from the lite messages, apart from the documented differences.
 */

public class LitePayloadsTest {

    public static final List<String> NAMES = Arrays.asList("small", "medium", "large", "xlarge", "xxlarge");

    public static MessageLite parse(String name) throws Exception {
        byte[] bytes = LiteTestSupport.readResource("/payloads/" + name + ".binpb");
        switch (name) {
            case "medium":
            case "xlarge":
            case "xxlarge":
                return SearchResponse.parseFrom(bytes);
            case "large":
                return Type.parseFrom(bytes);
            default:
                return TestAllTypesProto3.parseFrom(bytes);
        }
    }

    @Test
    public void sameAsJsonFormat() throws Exception {
        ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> writer = LiteTestSupport.writer();
        for (String name : NAMES) {
            MessageLite message = parse(name);
            String expected = new String(LiteTestSupport.readResource("/payloads/" + name + ".json"), StandardCharsets.UTF_8);
            String actual = LiteTestSupport.writeChars(writer, message);

            List<String> differences = LiteTestSupport.compare(LiteTestSupport.readTree(expected), LiteTestSupport.readTree(actual));
            assertWithMessage(name + ": " + actual).that(differences).isEmpty();
        }
    }
}
