package no.entur.protobuf.json.lite;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.Any;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

/**
 * The tests of curioswitch protobuf-jackson (https://github.com/curioswitch/protobuf-jackson, MIT License, Copyright (c) Choko
 * (choko@curioswitch.org)) which apply to writing, for lite messages: the messages of ProtobufJacksonTestMessages, written by the
 * build (see PayloadFiles) with the JsonFormat output, must be written the same, apart from the documented differences.
 */

public class LiteProtobufJacksonConformanceTest {

    private static final Map<String, MessageLite> TYPES = new HashMap<>();

    static {
        for (MessageLite type : new MessageLite[] {
                JsonTestProto.TestAllTypes.getDefaultInstance(),
                JsonTestProto.TestOneof.getDefaultInstance(),
                JsonTestProto.TestMap.getDefaultInstance(),
                JsonTestProto.TestWrappers.getDefaultInstance(),
                JsonTestProto.TestTimestamp.getDefaultInstance(),
                JsonTestProto.TestDuration.getDefaultInstance(),
                JsonTestProto.TestFieldMask.getDefaultInstance(),
                JsonTestProto.TestStruct.getDefaultInstance(),
                JsonTestProto.TestAny.getDefaultInstance(),
                JsonTestProto.TestCustomJsonName.getDefaultInstance(),
                JsonTestProto.TestRecursive.getDefaultInstance(),
                JsonTestProto.TestRegression.getDefaultInstance(),
                JsonTestProto.TestFieldOrder.getDefaultInstance(),
                Any.getDefaultInstance()}) {
            String name = type.getClass().getSimpleName();
            TYPES.put(type instanceof Any ? "google.protobuf.Any" : "json_test." + name, type);
        }
    }

    private final ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> writer = LiteTestSupport.newBuilder()
            .withAnyType("type.googleapis.com/json_test.TestAllTypes", JsonTestProto.TestAllTypes.getDefaultInstance())
            .build();

    static Stream<Arguments> messages() throws Exception {
        return LiteTestSupport.readMessages("/payloads/protobuf-jackson.tsv").stream()
                .map(columns -> Arguments.of(columns[0], columns[1], columns[2], columns[3]));
    }

    private static MessageLite parse(String type, String base64) throws Exception {
        MessageLite prototype = TYPES.get(type);
        assertWithMessage(type).that(prototype).isNotNull();
        return prototype.getParserForType().parseFrom(Base64.getDecoder().decode(base64));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    public void sameAsJsonFormat(String name, String type, String base64, String json) throws Exception {
        MessageLite message = parse(type, base64);

        String chars = LiteTestSupport.writeChars(writer, message);
        String bytes = LiteTestSupport.writeBytes(writer, message, true);
        assertThat(bytes).isEqualTo(chars);

        List<String> differences = LiteTestSupport.compare(LiteTestSupport.readTree(json), LiteTestSupport.readTree(chars));
        if (name.equals("customJsonName")) {
            // json_name options are not available for lite messages
            assertThat(json).isEqualTo("{\"@value\":12345}");
            assertThat(chars).isEqualTo("{\"value\":12345}");
        } else {
            assertWithMessage(name + ": " + chars + " vs JsonFormat " + json).that(differences).isEmpty();
        }
    }

    @Test
    public void allMessagesAreCompared() throws Exception {
        assertThat(messages().count()).isGreaterThan(70L);
    }
}
