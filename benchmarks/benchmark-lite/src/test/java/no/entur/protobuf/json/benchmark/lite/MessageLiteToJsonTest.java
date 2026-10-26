package no.entur.protobuf.json.benchmark.lite;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3.NestedEnum;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static com.google.common.truth.Truth.assertThat;

public class MessageLiteToJsonTest {

    private static final JsonFactory FACTORY = new JsonFactory();

    private static String write(MessageLite message) throws Exception {
        StringWriter output = new StringWriter();
        try (JsonGenerator generator = FACTORY.createGenerator(output)) {
            MessageLiteToJson.toString(generator, message);
        }
        return output.toString();
    }

    @Test
    public void writesAllPayloads() throws Exception {
        // the medium payload has empty map and Struct keys
        for (String name : new String[]{"small", "medium", "large"}) {
            String json = write(LitePayloads.load(name));
            try (JsonParser parser = FACTORY.createParser(json)) {
                while (parser.nextToken() != null) {
                    // well-formed
                }
            }
        }
    }

    @Test
    public void writesMapFieldsWithNamesStartingWithMap() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .putMapStringString("", "a")
                .putMapStringString("ABC", "b")
                .build();

        assertThat(write(message)).contains("\"mapStringString\":{\"\":\"a\",\"ABC\":\"b\"}");
    }

    @Test
    public void writesMapKeysAsIs() throws Exception {
        Struct struct = Struct.newBuilder()
                .putFields("", Value.newBuilder().setStringValue("a").build())
                .putFields("Key", Value.newBuilder().setBoolValue(true).build())
                .build();

        assertThat(write(struct)).isEqualTo("{\"fields\":{\"\":{\"stringValue\":\"a\"},\"Key\":{\"boolValue\":true}}}");
    }

    @Test
    public void writesEnumMapsOnce() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().putMapStringNestedEnum("a", NestedEnum.BAR).build();

        String json = write(message);

        assertThat(json).contains("\"mapStringNestedEnum\":{\"a\":\"BAR\"}");
        assertThat(json).doesNotContain("mapStringNestedEnumValue");
    }

    @Test
    public void writesUnknownEnumValuesAsNumbers() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalNestedEnumValue(999)
                .addRepeatedNestedEnum(NestedEnum.FOO)
                .addRepeatedNestedEnumValue(998)
                .putMapStringNestedEnumValue("a", 997)
                .build();

        String json = write(message);

        assertThat(json).contains("\"optionalNestedEnum\":999");
        assertThat(json).contains("\"repeatedNestedEnum\":[\"FOO\",998]");
        assertThat(json).contains("\"mapStringNestedEnum\":{\"a\":997}");
        assertThat(json).doesNotContain("UNRECOGNIZED");
        // no fields with the numbers
        assertThat(json).doesNotContain("NestedEnumValue");
    }

    @Test
    public void doesNotWriteDefaultInt64Values() throws Exception {
        String json = write(TestAllTypesProto3.newBuilder().setOptionalInt32(1).build());

        assertThat(json).contains("\"optionalInt32\":1");
        assertThat(json).doesNotContain("optionalInt64");
        assertThat(json).doesNotContain("optionalFixed64");
    }

    @Test
    public void writesInvalidTimestampsAndDurationsAsMessages() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalTimestamp(Timestamp.newBuilder().setSeconds(1).setNanos(-1))
                .setOptionalDuration(Duration.newBuilder().setSeconds(1).setNanos(-1))
                .build();

        String json = write(message);

        assertThat(json).contains("\"optionalTimestamp\":{\"nanos\":-1,\"seconds\":1}");
        assertThat(json).contains("\"optionalDuration\":{\"nanos\":-1,\"seconds\":1}");

        TestAllTypesProto3 valid = TestAllTypesProto3.newBuilder()
                .setOptionalTimestamp(Timestamp.newBuilder().setSeconds(1700000000).setNanos(1000))
                .setOptionalDuration(Duration.newBuilder().setSeconds(-1).setNanos(-500000000))
                .build();
        assertThat(write(valid)).contains("\"optionalTimestamp\":\"2023-11-14T22:13:20.000001Z\"");
        assertThat(write(valid)).contains("\"optionalDuration\":\"-1.500s\"");
    }

    @Test
    public void writesBytesAsBase64() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalBytes(ByteString.copyFrom(new byte[]{0, 1, 2, (byte) 0xff})).build();

        assertThat(write(message)).contains("\"optionalBytes\":\"AAEC/w==\"");
    }

    @Test
    public void writesFieldNamesInLowerCamelCase() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalString("x").build();

        assertThat(write(message)).contains("\"optionalString\":\"x\"");
    }
}
