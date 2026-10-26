package no.entur.protobuf.json.lite;

import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ListValue;
import com.google.protobuf.MessageLiteOrBuilder;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf_test_messages.proto2.TestMessagesProto2;
import com.google.protobuf_test_messages.proto2.TestMessagesProto2.TestAllTypesProto2;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.ForeignEnum;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.ForeignMessage;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3.AliasedEnum;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3.NestedEnum;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3.NestedMessage;
import no.entur.protobuf.json.ProtobufJsonWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LiteProtobufJsonWriterTest {

    private final ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> writer = LiteTestSupport.writer();

    private String write(MessageLiteOrBuilder message) throws IOException {
        String chars = LiteTestSupport.writeChars(writer, message);
        // the same JSON when writing bytes, with surrogates escaped or combined
        String combined = LiteTestSupport.writeBytes(writer, message, true);
        assertThat(combined).isEqualTo(chars);
        assertThat(LiteTestSupport.readTree(LiteTestSupport.writeBytes(writer, message, false))).isEqualTo(LiteTestSupport.readTree(chars));
        return chars;
    }

    @Test
    public void scalars() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalInt32(1)
                .setOptionalInt64(-2)
                .setOptionalUint32(3)
                .setOptionalUint64(4)
                .setOptionalSint32(-5)
                .setOptionalSint64(-6)
                .setOptionalFixed32(7)
                .setOptionalFixed64(8)
                .setOptionalSfixed32(-9)
                .setOptionalSfixed64(-10)
                .setOptionalFloat(1.5f)
                .setOptionalDouble(-2.25)
                .setOptionalBool(true)
                .setOptionalString("æ\"\\😀\n\u0001")
                .setOptionalBytes(ByteString.copyFrom(new byte[]{0, 1, 2, (byte) 0xff}))
                .build();

        assertThat(write(message)).isEqualTo("{\"optionalInt32\":1,\"optionalInt64\":\"-2\",\"optionalUint32\":3,\"optionalUint64\":\"4\",\"optionalSint32\":-5,"
                + "\"optionalSint64\":\"-6\",\"optionalFixed32\":7,\"optionalFixed64\":\"8\",\"optionalSfixed32\":-9,\"optionalSfixed64\":\"-10\","
                + "\"optionalFloat\":1.5,\"optionalDouble\":-2.25,\"optionalBool\":true,\"optionalString\":\"æ\\\"\\\\😀\\n\\u0001\",\"optionalBytes\":\"AAEC/w==\"}");
    }

    @Test
    public void defaultValuesAreNotWritten() throws Exception {
        assertThat(write(TestAllTypesProto3.newBuilder().setOptionalInt32(0).setOptionalString("").setOptionalNestedEnum(NestedEnum.FOO).build())).isEqualTo("{}");
        assertThat(write(TestAllTypesProto3.getDefaultInstance())).isEqualTo("{}");
        // like JsonFormat, as -0.0 is not the default value
        assertThat(write(TestAllTypesProto3.newBuilder().setOptionalDouble(-0.0d).build())).isEqualTo("{\"optionalDouble\":-0.0}");
    }

    @Test
    public void unsignedValuesAreWrittenAsSigned() throws Exception {
        // a known difference from JsonFormat, which writes 4294967295 and "18446744073709551615"
        assertThat(write(TestAllTypesProto3.newBuilder().setOptionalUint32(-1).setOptionalUint64(-1).build())).isEqualTo("{\"optionalUint32\":-1,\"optionalUint64\":\"-1\"}");
    }

    @Test
    public void nanAndInfinity() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalFloat(Float.NaN).setOptionalDouble(Double.NEGATIVE_INFINITY).build();
        assertThat(write(message)).isEqualTo("{\"optionalFloat\":\"NaN\",\"optionalDouble\":\"-Infinity\"}");
    }

    @Test
    public void enums() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalNestedEnum(NestedEnum.NEG)
                .setOptionalForeignEnum(ForeignEnum.FOREIGN_BAZ)
                .setOptionalAliasedEnum(AliasedEnum.ALIAS_BAZ)
                .addRepeatedNestedEnumValue(1)
                .addRepeatedNestedEnumValue(99)
                .putMapStringNestedEnumValue("a", 2)
                .putMapStringNestedEnumValue("b", 98)
                .build();

        assertThat(write(message)).isEqualTo("{\"optionalNestedEnum\":\"NEG\",\"optionalForeignEnum\":\"FOREIGN_BAZ\",\"optionalAliasedEnum\":\"ALIAS_BAZ\","
                + "\"repeatedNestedEnum\":[\"BAR\",99],\"mapStringNestedEnum\":{\"a\":\"BAZ\",\"b\":98}}");

        // unknown values are written as numbers
        assertThat(write(TestAllTypesProto3.newBuilder().setOptionalNestedEnumValue(77).build())).isEqualTo("{\"optionalNestedEnum\":77}");
    }

    @Test
    public void repeated() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .addAllRepeatedInt32(Arrays.asList(1, 2))
                .addRepeatedInt64(3)
                .addRepeatedString("a")
                .addRepeatedBytes(ByteString.copyFromUtf8("b"))
                .addRepeatedNestedMessage(NestedMessage.newBuilder().setA(1))
                .addAllPackedInt32(Arrays.asList(4))
                .addAllUnpackedInt32(Arrays.asList(5))
                .addRepeatedTimestamp(Timestamp.newBuilder().setSeconds(1))
                .addRepeatedValue(Value.newBuilder().setBoolValue(true))
                .build();

        assertThat(write(message)).isEqualTo("{\"repeatedInt32\":[1,2],\"repeatedInt64\":[\"3\"],\"repeatedString\":[\"a\"],\"repeatedBytes\":[\"Yg==\"],"
                + "\"repeatedNestedMessage\":[{\"a\":1}],\"packedInt32\":[4],\"unpackedInt32\":[5],\"repeatedTimestamp\":[\"1970-01-01T00:00:01Z\"],\"repeatedValue\":[true]}");
    }

    @Test
    public void maps() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .putMapInt32Int32(1, 2)
                .putMapInt64Int64(-3, -4)
                .putMapUint32Uint32(5, 6)
                .putMapUint64Uint64(7, 8)
                .putMapSint32Sint32(-9, -10)
                .putMapSint64Sint64(-11, -12)
                .putMapFixed32Fixed32(13, 14)
                .putMapFixed64Fixed64(15, 16)
                .putMapSfixed32Sfixed32(17, 18)
                .putMapSfixed64Sfixed64(19, 20)
                .putMapInt32Float(21, 1.5f)
                .putMapInt32Double(22, 2.5)
                .putMapBoolBool(true, false)
                .putMapStringString("a", "b")
                .putMapStringBytes("c", ByteString.copyFromUtf8("d"))
                .putMapStringNestedMessage("e", NestedMessage.newBuilder().setA(1).build())
                .putMapStringForeignMessage("f", ForeignMessage.newBuilder().setC(2).build())
                .putMapStringNestedEnum("g", NestedEnum.BAR)
                .putMapStringForeignEnum("h", ForeignEnum.FOREIGN_BAR)
                .build();

        assertThat(write(message)).isEqualTo("{\"mapInt32Int32\":{\"1\":2},\"mapInt64Int64\":{\"-3\":\"-4\"},\"mapUint32Uint32\":{\"5\":6},\"mapUint64Uint64\":{\"7\":\"8\"},"
                + "\"mapSint32Sint32\":{\"-9\":-10},\"mapSint64Sint64\":{\"-11\":\"-12\"},\"mapFixed32Fixed32\":{\"13\":14},\"mapFixed64Fixed64\":{\"15\":\"16\"},"
                + "\"mapSfixed32Sfixed32\":{\"17\":18},\"mapSfixed64Sfixed64\":{\"19\":\"20\"},\"mapInt32Float\":{\"21\":1.5},\"mapInt32Double\":{\"22\":2.5},"
                + "\"mapBoolBool\":{\"true\":false},\"mapStringString\":{\"a\":\"b\"},\"mapStringBytes\":{\"c\":\"ZA==\"},\"mapStringNestedMessage\":{\"e\":{\"a\":1}},"
                + "\"mapStringForeignMessage\":{\"f\":{\"c\":2}},\"mapStringNestedEnum\":{\"g\":\"BAR\"},\"mapStringForeignEnum\":{\"h\":\"FOREIGN_BAR\"}}");
    }

    @Test
    public void oneofs() throws Exception {
        // set oneof members are written even if they have the default value
        assertThat(write(TestAllTypesProto3.newBuilder().setOneofUint32(0).build())).isEqualTo("{\"oneofUint32\":0}");
        assertThat(write(TestAllTypesProto3.newBuilder().setOneofString("").build())).isEqualTo("{\"oneofString\":\"\"}");
        assertThat(write(TestAllTypesProto3.newBuilder().setOneofNestedMessage(NestedMessage.getDefaultInstance()).build())).isEqualTo("{\"oneofNestedMessage\":{}}");
        assertThat(write(TestAllTypesProto3.newBuilder().setOneofEnum(NestedEnum.BAZ).build())).isEqualTo("{\"oneofEnum\":\"BAZ\"}");
        assertThat(write(TestAllTypesProto3.newBuilder().setOneofNullValue(NullValue.NULL_VALUE).build())).isEqualTo("{\"oneofNullValue\":null}");
        assertThat(write(TestAllTypesProto3.newBuilder().setOneofDouble(0.5).build())).isEqualTo("{\"oneofDouble\":0.5}");
    }

    @Test
    public void nestedMessages() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalNestedMessage(NestedMessage.newBuilder().setA(1).setCorecursive(TestAllTypesProto3.newBuilder().setOptionalInt32(2)))
                .setRecursiveMessage(TestAllTypesProto3.newBuilder().setOptionalInt32(3))
                .build();

        assertThat(write(message)).isEqualTo("{\"optionalNestedMessage\":{\"a\":1,\"corecursive\":{\"optionalInt32\":2}},\"recursiveMessage\":{\"optionalInt32\":3}}");
    }

    @Test
    public void wellKnownTypes() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalBoolWrapper(BoolValue.newBuilder().setValue(false))
                .setOptionalInt32Wrapper(Int32Value.newBuilder().setValue(0))
                .setOptionalInt64Wrapper(Int64Value.newBuilder().setValue(1))
                .setOptionalUint32Wrapper(UInt32Value.newBuilder().setValue(-1))
                .setOptionalUint64Wrapper(UInt64Value.newBuilder().setValue(-1))
                .setOptionalFloatWrapper(FloatValue.newBuilder().setValue(1.5f))
                .setOptionalDoubleWrapper(DoubleValue.newBuilder().setValue(2.5))
                .setOptionalStringWrapper(StringValue.newBuilder().setValue("x"))
                .setOptionalBytesWrapper(BytesValue.newBuilder().setValue(ByteString.copyFromUtf8("y")))
                .setOptionalDuration(Duration.newBuilder().setSeconds(-3).setNanos(-500))
                .setOptionalTimestamp(Timestamp.newBuilder().setSeconds(1700000000).setNanos(123000000))
                .setOptionalFieldMask(FieldMask.newBuilder().addPaths("foo_bar").addPaths("a.b_c.d"))
                .setOptionalStruct(Struct.newBuilder()
                        .putFields("a", Value.newBuilder().setStringValue("b").build())
                        .putFields("c", Value.newBuilder().setListValue(ListValue.newBuilder()
                                .addValues(Value.newBuilder().setNumberValue(1))
                                .addValues(Value.newBuilder().setBoolValue(true))
                                .addValues(Value.newBuilder().setNullValue(NullValue.NULL_VALUE))).build()))
                .setOptionalAny(Any.newBuilder()
                        .setTypeUrl(LiteTestSupport.TEST_ALL_TYPES_PROTO3_URL)
                        .setValue(TestAllTypesProto3.newBuilder().setOptionalString("x").build().toByteString()))
                .setOptionalValue(Value.newBuilder().setListValue(ListValue.newBuilder()
                        .addValues(Value.newBuilder().setNullValue(NullValue.NULL_VALUE))
                        .addValues(Value.newBuilder().setStringValue("s"))
                        .addValues(Value.newBuilder().setStructValue(Struct.newBuilder().putFields("k", Value.newBuilder().setNumberValue(-1.5).build())))))
                .setOptionalNullValue(NullValue.NULL_VALUE)
                .setOptionalEmpty(Empty.getDefaultInstance())
                .build();

        assertThat(write(message)).isEqualTo("{\"optionalBoolWrapper\":false,\"optionalInt32Wrapper\":0,\"optionalInt64Wrapper\":\"1\",\"optionalUint32Wrapper\":4294967295,"
                + "\"optionalUint64Wrapper\":\"18446744073709551615\",\"optionalFloatWrapper\":1.5,\"optionalDoubleWrapper\":2.5,\"optionalStringWrapper\":\"x\","
                + "\"optionalBytesWrapper\":\"eQ==\",\"optionalDuration\":\"-3.000000500s\",\"optionalTimestamp\":\"2023-11-14T22:13:20.123Z\","
                + "\"optionalFieldMask\":\"fooBar,a.bC.d\",\"optionalStruct\":{\"a\":\"b\",\"c\":[1.0,true,null]},"
                + "\"optionalAny\":{\"@type\":\"type.googleapis.com/protobuf_test_messages.proto3.TestAllTypesProto3\",\"optionalString\":\"x\"},"
                + "\"optionalValue\":[null,\"s\",{\"k\":-1.5}],\"optionalEmpty\":{}}");
    }

    @Test
    public void any() throws Exception {
        Any duration = Any.newBuilder().setTypeUrl("type.googleapis.com/google.protobuf.Duration").setValue(Duration.newBuilder().setSeconds(1).build().toByteString()).build();
        assertThat(write(TestAllTypesProto3.newBuilder().setOptionalAny(duration).build()))
                .isEqualTo("{\"optionalAny\":{\"@type\":\"type.googleapis.com/google.protobuf.Duration\",\"value\":\"1s\"}}");
        assertThat(write(duration)).isEqualTo("{\"@type\":\"type.googleapis.com/google.protobuf.Duration\",\"value\":\"1s\"}");
        // empty
        assertThat(write(TestAllTypesProto3.newBuilder().setOptionalAny(Any.getDefaultInstance()).build())).isEqualTo("{\"optionalAny\":{}}");

        TestAllTypesProto3 unknown = TestAllTypesProto3.newBuilder().setOptionalAny(Any.newBuilder().setTypeUrl("type.googleapis.com/unknown.Type")).build();
        InvalidProtocolBufferException e = assertThrows(InvalidProtocolBufferException.class, () -> LiteTestSupport.writeChars(writer, unknown));
        assertThat(e).hasMessageThat().isEqualTo("Cannot find type for url: type.googleapis.com/unknown.Type");

        TestAllTypesProto3 invalid = TestAllTypesProto3.newBuilder().setOptionalAny(Any.newBuilder().setTypeUrl("invalid")).build();
        e = assertThrows(InvalidProtocolBufferException.class, () -> LiteTestSupport.writeChars(writer, invalid));
        assertThat(e).hasMessageThat().isEqualTo("Invalid type url found: invalid");
    }

    @Test
    public void wellKnownTypesAsRoot() throws Exception {
        assertThat(write(Timestamp.newBuilder().setSeconds(1700000000).build())).isEqualTo("\"2023-11-14T22:13:20Z\"");
        assertThat(write(Duration.newBuilder().setSeconds(1).setNanos(500000000).build())).isEqualTo("\"1.500s\"");
        assertThat(write(Struct.newBuilder().putFields("a", Value.newBuilder().setStringValue("b").build()).build())).isEqualTo("{\"a\":\"b\"}");
        assertThat(write(Value.newBuilder().setNumberValue(1).build())).isEqualTo("1.0");
        assertThat(write(Value.getDefaultInstance())).isEqualTo("null");
        assertThat(write(Int64Value.newBuilder().setValue(1).build())).isEqualTo("\"1\"");
        assertThat(write(FieldMask.newBuilder().addPaths("foo_bar").build())).isEqualTo("\"fooBar\"");
        assertThat(write(Empty.getDefaultInstance())).isEqualTo("{}");
    }

    @Test
    public void outOfRangeTimestampsAndDurations() throws Exception {
        // like the full writers; JsonFormat fails
        assertThat(write(TestAllTypesProto3.newBuilder().setOptionalTimestamp(Timestamp.newBuilder().setSeconds(Long.MAX_VALUE).setNanos(1)).build()))
                .isEqualTo("{\"optionalTimestamp\":{\"seconds\":\"9223372036854775807\",\"nanos\":1}}");
        assertThat(write(TestAllTypesProto3.newBuilder().setOptionalDuration(Duration.newBuilder().setSeconds(1).setNanos(-1)).build()))
                .isEqualTo("{\"optionalDuration\":{\"seconds\":\"1\",\"nanos\":-1}}");
        // JsonFormat fails for NaN values
        assertThat(write(TestAllTypesProto3.newBuilder().setOptionalValue(Value.newBuilder().setNumberValue(Double.NaN)).build())).isEqualTo("{\"optionalValue\":\"NaN\"}");
    }

    @Test
    public void builder() throws Exception {
        assertThat(write(TestAllTypesProto3.newBuilder().setOptionalString("x").putMapStringString("a", "b"))).isEqualTo("{\"optionalString\":\"x\",\"mapStringString\":{\"a\":\"b\"}}");
    }

    @Test
    public void writeAsString() throws Exception {
        assertThat(writer.writeAsString(TestAllTypesProto3.newBuilder().setOptionalString("😀").setOptionalInt64(1).build()))
                .isEqualTo("{\"optionalInt64\":\"1\",\"optionalString\":\"😀\"}");
    }

    @Test
    public void proto2() throws Exception {
        TestAllTypesProto2 message = TestAllTypesProto2.newBuilder()
                .setOptionalInt32(0)
                .setOptionalNestedEnum(TestAllTypesProto2.NestedEnum.BAZ)
                .setData(TestAllTypesProto2.Data.newBuilder().setGroupInt32(1))
                .setDefaultInt32(-123456789)
                .setExtension(TestMessagesProto2.extensionInt32, 5)
                .build();

        // fields with presence are written if set, also with the default value; extensions are not written
        assertThat(write(message)).isEqualTo("{\"optionalInt32\":0,\"optionalNestedEnum\":\"BAZ\",\"data\":{\"groupInt32\":1},\"defaultInt32\":-123456789}");
        assertThat(write(TestAllTypesProto2.getDefaultInstance())).isEqualTo("{}");
    }
}
