package no.entur.protobuf.json;

import com.google.protobuf.Any;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.Timestamp;
import no.entur.protobuf.json.jsonformat.JsonFormatComparison;
import no.entur.protobuf.json.test.ProtobufJacksonTestMessages;
import no.entur.protobuf.json.test.RandomMessages;
import no.entur.protobuf.json.test.protobufjackson.GithubApi.SearchResponse;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestAllTypes;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestAny;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestDuration;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestFieldMask;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestTimestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.databind.util.TokenBuffer;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.Stream;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The tests of curioswitch protobuf-jackson (https://github.com/curioswitch/protobuf-jackson, MIT License, Copyright (c) Choko
 * (choko@curioswitch.org)) which apply to writing, ported: the messages of {@linkplain ProtobufJacksonTestMessages} must be written
 * like JsonFormat writes them, compared as JSON values, so that differences in escaping do not count. Also the literal expectations
 * of OptionalTest and ProtobufUtilTest, and writing to a generator which is not closed, or is not one of Jackson's JSON generators,
 * as in ObjectMapperTest.
 */

public abstract class AbstractProtobufJacksonConformanceTest {

    protected abstract ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer();

    static Stream<Arguments> messages() {
        return ProtobufJacksonTestMessages.create().entrySet().stream().map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    public void sameAsJsonFormat(String name, Message message) {
        // also as DynamicMessage and as builder
        TestSupport.assertSameAsJsonFormat(TestSupport.comparison(writer()), message, name);
    }

    // OptionalTest

    @Test
    public void optionalNotSet() throws Exception {
        assertThat(writer().writeAsString(TestAllTypes.newBuilder().build())).isEqualTo("{}");
    }

    @Test
    public void optionalSetToDefaultValue() throws Exception {
        assertThat(writer().writeAsString(TestAllTypes.newBuilder().setVeryOptionalInt32(0).build())).isEqualTo("{\"veryOptionalInt32\":0}");
    }

    // MessageMarshallerTest

    @Test
    public void htmlEscape() throws Exception {
        // JsonFormat escapes HTML characters as unicode (</script>), escaping is left to the generator here
        assertThat(writer().writeAsString(TestAllTypes.newBuilder().setOptionalString("</script>").build())).isEqualTo("{\"optionalString\":\"</script>\"}");
    }

    @Test
    public void customJsonName() throws Exception {
        assertThat(writer().writeAsString(ProtobufJacksonTestMessages.create().get("customJsonName"))).isEqualTo("{\"@value\":12345}");
    }

    @Test
    public void protoFieldReservedName() throws Exception {
        assertThat(writer().writeAsString(ProtobufJacksonTestMessages.create().get("protoFieldReservedName"))).isEqualTo("{\"class\":\"hello\"}");
        assertThat(writer().writeAsString(ProtobufJacksonTestMessages.create().get("protoFieldAlreadyCamelCase"))).isEqualTo("{\"feedIds\":[\"1\"]}");
    }

    @Test
    public void anyWithTypeNotInTypeRegistry() {
        // MessageMarshallerIncrementalTest: types must be registered. Here, like for JsonFormat, only the types of google.protobuf.Any values
        TestAny message = TestAny.newBuilder().setAnyValue(Any.pack(SearchResponse.newBuilder().setTotalCount(10).build())).build();

        assertThrows(IOException.class, () -> writer().writeAsString(message));
    }

    @Test
    public void githubApi() {
        // GithubApiTest parses a search response of the GitHub API; here, random responses are written
        JsonFormatComparison comparison = TestSupport.comparison(writer());
        for (int seed = 0; seed < 200; seed++) {
            TestSupport.assertSameAsJsonFormat(comparison, new RandomMessages(seed).fill(SearchResponse.newBuilder()), "seed " + seed);
        }
    }

    // ProtobufUtilTest

    @Test
    public void durationStringFormat() throws Exception {
        // 0, 3, 6 or 9 fractional digits
        assertDuration(Duration.newBuilder().setSeconds(1).build(), "1s");
        assertDuration(Duration.newBuilder().setNanos(10000000).build(), "0.010s");
        assertDuration(Duration.newBuilder().setNanos(10000).build(), "0.000010s");
        assertDuration(Duration.newBuilder().setNanos(10).build(), "0.000000010s");
        assertDuration(Duration.newBuilder().setSeconds(315576000000L).setNanos(999999999).build(), "315576000000.999999999s");
        assertDuration(Duration.newBuilder().setSeconds(-315576000000L).setNanos(-999999999).build(), "-315576000000.999999999s");
    }

    private void assertDuration(Duration duration, String expected) throws IOException {
        assertThat(writer().writeAsString(TestDuration.newBuilder().setDurationValue(duration).build())).isEqualTo("{\"durationValue\":\"" + expected + "\"}");
    }

    @Test
    public void invalidDurationFormat() throws Exception {
        // JsonFormat fails
        assertInvalidDuration(Duration.newBuilder().setSeconds(-315576000001L).build(), "{\"seconds\":\"-315576000001\"}");
        assertInvalidDuration(Duration.newBuilder().setSeconds(315576000001L).build(), "{\"seconds\":\"315576000001\"}");
        assertInvalidDuration(Duration.newBuilder().setSeconds(1).setNanos(-1).build(), "{\"seconds\":\"1\",\"nanos\":-1}");
        assertInvalidDuration(Duration.newBuilder().setSeconds(-1).setNanos(1).build(), "{\"seconds\":\"-1\",\"nanos\":1}");
    }

    private void assertInvalidDuration(Duration duration, String expected) throws IOException {
        assertThat(writer().writeAsString(TestDuration.newBuilder().setDurationValue(duration).build())).isEqualTo("{\"durationValue\":" + expected + "}");
    }

    @Test
    public void timestampStringFormat() throws Exception {
        assertTimestamp(Timestamp.newBuilder().setSeconds(-62135596800L).build(), "0001-01-01T00:00:00Z");
        assertTimestamp(Timestamp.newBuilder().setSeconds(253402300799L).setNanos(999999999).build(), "9999-12-31T23:59:59.999999999Z");
        assertTimestamp(Timestamp.getDefaultInstance(), "1970-01-01T00:00:00Z");
        // 3, 6 or 9 fractional digits
        assertTimestamp(Timestamp.newBuilder().setNanos(10).build(), "1970-01-01T00:00:00.000000010Z");
        assertTimestamp(Timestamp.newBuilder().setNanos(10000).build(), "1970-01-01T00:00:00.000010Z");
        assertTimestamp(Timestamp.newBuilder().setNanos(10000000).build(), "1970-01-01T00:00:00.010Z");
    }

    private void assertTimestamp(Timestamp timestamp, String expected) throws IOException {
        assertThat(writer().writeAsString(TestTimestamp.newBuilder().setTimestampValue(timestamp).build())).isEqualTo("{\"timestampValue\":\"" + expected + "\"}");
    }

    @Test
    public void timestampInvalidStringFormat() throws Exception {
        // JsonFormat fails
        assertThat(writer().writeAsString(TestTimestamp.newBuilder().setTimestampValue(Timestamp.newBuilder().setNanos(1000000000)).build()))
                .isEqualTo("{\"timestampValue\":{\"nanos\":1000000000}}");
    }

    @Test
    public void fieldMaskToJsonString() throws Exception {
        assertFieldMask("");
        assertFieldMask("foo", "foo");
        assertFieldMask("foo.barBaz", "foo.bar_baz", "");
        assertFieldMask("foo,barBaz", "foo", "bar_baz");
    }

    private void assertFieldMask(String expected, String... paths) throws IOException {
        TestFieldMask message = TestFieldMask.newBuilder().setFieldMaskValue(FieldMask.newBuilder().addAllPaths(List.of(paths))).build();
        assertThat(writer().writeAsString(message)).isEqualTo("{\"fieldMaskValue\":\"" + expected + "\"}");
    }

    // ObjectMapperTest

    @Test
    public void doesNotCloseJsonGenerator() throws Exception {
        TestAllTypes first = ProtobufJacksonTestMessages.testAllTypesAllFields();
        TestAllTypes second = TestAllTypes.newBuilder().setOptionalString("second").build();

        StringWriter output = new StringWriter();
        try (JsonGenerator generator = TestSupport.MAPPER.createGenerator(output)) {
            generator.writeStartArray();
            writer().write(first, generator);
            assertThat(generator.isClosed()).isFalse();
            writer().write(second, generator);
            generator.writeEndArray();
        }
        assertThat(output.toString()).isEqualTo("[" + writer().writeAsString(first) + "," + writer().writeAsString(second) + "]");
    }

    @Test
    public void tokenBuffer() throws Exception {
        // a generator which is not one of Jackson's JSON generators
        for (Message message : ProtobufJacksonTestMessages.create().values()) {
            TokenBuffer buffer = TokenBuffer.forGeneration();
            writer().write(message, buffer);

            StringWriter output = new StringWriter();
            try (JsonParser parser = buffer.asParser(ObjectReadContext.empty()); JsonGenerator generator = TestSupport.MAPPER.createGenerator(output)) {
                parser.nextToken();
                generator.copyCurrentStructure(parser);
            }
            assertThat(output.toString()).isEqualTo(writer().writeAsString(message));
        }
    }
}
