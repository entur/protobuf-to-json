package no.entur.protobuf.json.test;

import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.FieldMaskUtil;
import com.google.protobuf.util.Timestamps;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestAllTypes;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestAllTypes.NestedEnum;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestAllTypes.NestedMessage;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestAny;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestCustomJsonName;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestDuration;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestFieldMask;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestFieldOrder;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestMap;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestOneof;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestRegression;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestStruct;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestTimestamp;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto.TestWrappers;

import java.text.ParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The messages written in the tests of curioswitch protobuf-jackson (https://github.com/curioswitch/protobuf-jackson, MIT License,
 * Copyright (c) Choko (choko@curioswitch.org)), which its MessageMarshaller must write the same as JsonFormat:
 * MessageMarshallerTest and OptionalTest, and the values of the Timestamp, Duration and FieldMask formatting tests of ProtobufUtilTest.
 * The messages are of the types in {@code support/protobuf/protobuf_jackson/json_test.proto}.
 * <br><br>
 * Not ported, as the writers only write JSON, and always without insignificant whitespace: the parser tests,
 * the tests of the printer options (including default values, proto field names, enums as numbers, sorted map keys and
 * pretty printing), and the tests of the Jackson databind module and of registering types.
 */

public final class ProtobufJacksonTestMessages {

    private ProtobufJacksonTestMessages() {
    }

    /**
     * @return messages by name: the name of the test, and a description if the test writes several messages
     */

    public static Map<String, Message> create() {
        Map<String, Message> messages = new LinkedHashMap<>();
        try {
            addMessageMarshallerTest(messages);
            addOptionalTest(messages);
            addProtobufUtilTest(messages);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
        return Collections.unmodifiableMap(messages);
    }

    private static void addMessageMarshallerTest(Map<String, Message> messages) throws ParseException {
        messages.put("allFields", testAllTypesAllFields());

        messages.put("unknownEnumValue", TestAllTypes.newBuilder()
                .setOptionalNestedEnumValue(12345)
                .addRepeatedNestedEnumValue(12345)
                .addRepeatedNestedEnumValue(0)
                .build());
        messages.put("unknownEnumValue-map", TestMap.newBuilder()
                .putInt32ToEnumMapValue(1, 0)
                .putInt32ToEnumMapValue(2, 12345)
                .build());

        messages.put("specialFloatValues", TestAllTypes.newBuilder()
                .addRepeatedFloat(Float.NaN)
                .addRepeatedFloat(Float.POSITIVE_INFINITY)
                .addRepeatedFloat(Float.NEGATIVE_INFINITY)
                .addRepeatedDouble(Double.NaN)
                .addRepeatedDouble(Double.POSITIVE_INFINITY)
                .addRepeatedDouble(Double.NEGATIVE_INFINITY)
                .build());

        messages.put("mapFields", testMapAllTypes());
        messages.put("mapFields-int32", TestMap.newBuilder().putInt32ToInt32Map(1, 2).putInt32ToInt32Map(3, 4).build());

        TestWrappers.Builder wrappers = TestWrappers.newBuilder();
        wrappers.getBoolValueBuilder().setValue(false);
        wrappers.getInt32ValueBuilder().setValue(0);
        wrappers.getInt64ValueBuilder().setValue(0);
        wrappers.getUint32ValueBuilder().setValue(0);
        wrappers.getUint64ValueBuilder().setValue(0);
        wrappers.getFloatValueBuilder().setValue(0.0f);
        wrappers.getDoubleValueBuilder().setValue(0.0);
        wrappers.getStringValueBuilder().setValue("");
        wrappers.getBytesValueBuilder().setValue(ByteString.EMPTY);
        messages.put("wrappers-defaults", wrappers.build());

        wrappers = TestWrappers.newBuilder();
        wrappers.getBoolValueBuilder().setValue(true);
        wrappers.getInt32ValueBuilder().setValue(1);
        wrappers.getInt64ValueBuilder().setValue(2);
        wrappers.getUint32ValueBuilder().setValue(3);
        wrappers.getUint64ValueBuilder().setValue(4);
        wrappers.getFloatValueBuilder().setValue(5.0f);
        wrappers.getDoubleValueBuilder().setValue(6.0);
        wrappers.getStringValueBuilder().setValue("7");
        wrappers.getBytesValueBuilder().setValue(ByteString.copyFrom(new byte[] {8}));
        messages.put("wrappers", wrappers.build());

        messages.put("timestamp", TestTimestamp.newBuilder().setTimestampValue(Timestamps.parse("1970-01-01T00:00:00Z")).build());
        messages.put("duration", TestDuration.newBuilder().setDurationValue(Durations.parse("12345s")).build());
        messages.put("fieldMask", TestFieldMask.newBuilder().setFieldMaskValue(FieldMaskUtil.fromString("foo.bar,baz,foo_bar.baz")).build());

        // a struct with all kinds of values
        TestStruct.Builder struct = TestStruct.newBuilder();
        Struct.Builder structValue = struct.getStructValueBuilder();
        structValue.putFields("null_value", Value.newBuilder().setNullValueValue(0).build());
        structValue.putFields("number_value", Value.newBuilder().setNumberValue(1.25).build());
        structValue.putFields("string_value", Value.newBuilder().setStringValue("hello").build());
        structValue.putFields("struct_value", Value.newBuilder()
                .setStructValue(Struct.newBuilder().putFields("number_value", Value.newBuilder().setNumberValue(1234).build()))
                .build());
        structValue.putFields("list_value", Value.newBuilder()
                .setListValue(ListValue.newBuilder()
                        .addValues(Value.newBuilder().setNumberValue(1.125).build())
                        .addValues(Value.newBuilder().setNullValueValue(0).build()))
                .build());
        messages.put("struct", struct.build());
        messages.put("struct-nullValue", TestStruct.newBuilder().setValue(Value.newBuilder().setNullValueValue(0).build()).build());
        messages.put("struct-listValue", TestStruct.newBuilder()
                .setListValue(ListValue.newBuilder()
                        .addValues(Value.newBuilder().setNumberValue(31831.125).build())
                        .addValues(Value.newBuilder().setNullValueValue(0).build()))
                .build());

        TestAllTypes content = TestAllTypes.newBuilder().setOptionalInt32(1234).build();
        messages.put("anyFields", TestAny.newBuilder().setAnyValue(Any.pack(content)).build());
        messages.put("anyFields-defaultAny", TestAny.newBuilder().setAnyValue(Any.getDefaultInstance()).build());
        // well-known types have a special formatting when embedded in Any
        messages.put("anyFields-anyInAny", Any.pack(Any.pack(content)));
        messages.put("anyFields-int32Value", Any.pack(Int32Value.newBuilder().setValue(12345).build()));
        messages.put("anyFields-uint32Value", Any.pack(UInt32Value.newBuilder().setValue(12345).build()));
        messages.put("anyFields-int64Value", Any.pack(Int64Value.newBuilder().setValue(12345).build()));
        messages.put("anyFields-uint64Value", Any.pack(UInt64Value.newBuilder().setValue(12345).build()));
        messages.put("anyFields-floatValue", Any.pack(FloatValue.newBuilder().setValue(12345).build()));
        messages.put("anyFields-doubleValue", Any.pack(DoubleValue.newBuilder().setValue(12345).build()));
        messages.put("anyFields-boolValue", Any.pack(BoolValue.newBuilder().setValue(true).build()));
        messages.put("anyFields-stringValue", Any.pack(StringValue.newBuilder().setValue("Hello").build()));
        messages.put("anyFields-bytesValue", Any.pack(BytesValue.newBuilder().setValue(ByteString.copyFrom(new byte[] {1, 2})).build()));
        messages.put("anyFields-timestamp", Any.pack(Timestamps.parse("1969-12-31T23:59:59Z")));
        messages.put("anyFields-duration", Any.pack(Durations.parse("12345.10s")));
        messages.put("anyFields-fieldMask", Any.pack(FieldMaskUtil.fromString("foo.bar,baz")));
        messages.put("anyFields-struct", Any.pack(Struct.newBuilder().putFields("number", Value.newBuilder().setNumberValue(1.125).build()).build()));
        messages.put("anyFields-numberValue", Any.pack(Value.newBuilder().setNumberValue(1).build()));
        messages.put("anyFields-nullValue", Any.pack(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()));

        Value numberValue = Value.newBuilder().setNumberValue(1.125).build();
        Value nullValue = Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        messages.put("anyInMaps", TestAny.newBuilder()
                .putAnyMap("int32_wrapper", Any.pack(Int32Value.newBuilder().setValue(123).build()))
                .putAnyMap("int64_wrapper", Any.pack(Int64Value.newBuilder().setValue(456).build()))
                .putAnyMap("timestamp", Any.pack(Timestamps.parse("1969-12-31T23:59:59Z")))
                .putAnyMap("duration", Any.pack(Durations.parse("12345.1s")))
                .putAnyMap("field_mask", Any.pack(FieldMaskUtil.fromString("foo.bar,baz")))
                .putAnyMap("struct", Any.pack(Struct.newBuilder().putFields("number", numberValue).build()))
                .putAnyMap("list_value", Any.pack(ListValue.newBuilder().addValues(numberValue).addValues(nullValue).build()))
                .putAnyMap("number_value", Any.pack(numberValue))
                .putAnyMap("any_value_number", Any.pack(Any.pack(numberValue)))
                .putAnyMap("any_value_default", Any.pack(Any.getDefaultInstance()))
                .putAnyMap("default", Any.getDefaultInstance())
                .build());

        messages.put("customJsonName", TestCustomJsonName.newBuilder().setValue(12345).build());
        messages.put("htmlEscape", TestAllTypes.newBuilder().setOptionalString("</script>").build());

        // includingDefaultValueFields, preservingProtoFieldNames, printingEnumsAsInts, omittingInsignificantWhitespace
        // and sortedMapKeys, with the default options
        messages.put("defaults-testAllTypes", TestAllTypes.getDefaultInstance());
        messages.put("defaults-testMap", TestMap.getDefaultInstance());
        messages.put("defaults-testOneof", TestOneof.getDefaultInstance());
        messages.put("defaults-oneofInt32", TestOneof.newBuilder().setOneofInt32(42).build());
        messages.put("defaults-oneofNullValue", TestOneof.newBuilder().setOneofNullValue(NullValue.NULL_VALUE).build());
        messages.put("preservingProtoFieldNames", TestAllTypes.newBuilder().setOptionalInt32(12345).build());
        messages.put("printingEnumsAsInts", TestAllTypes.newBuilder().setOptionalNestedEnum(NestedEnum.BAR).build());
        messages.put("sortedMapKeys", TestMap.newBuilder()
                .putStringToInt32Map("𝄠", 3) // utf-8 F0 9D 84 A0
                .putStringToInt32Map("foo", 99)
                .putStringToInt32Map("xxx", 123)
                .putStringToInt32Map("€", 1) // utf-8 E2 82 AC
                .putStringToInt32Map("abc", 20)
                .putStringToInt32Map("19", 19)
                .putStringToInt32Map("8", 8)
                .putStringToInt32Map("ﬀ", 2) // utf-8 EF AC 80
                .putInt32ToInt32Map(3, 3)
                .putInt32ToInt32Map(10, 10)
                .putInt32ToInt32Map(5, 5)
                .putInt32ToInt32Map(4, 4)
                .putInt32ToInt32Map(1, 1)
                .putInt32ToInt32Map(2, 2)
                .putInt32ToInt32Map(-3, -3)
                .build());

        // https://github.com/curioswitch/curiostack/issues/7
        messages.put("protoFieldAlreadyCamelCase", TestRegression.newBuilder().addFeedIds(1).build());
        // https://github.com/curioswitch/curiostack/issues/43
        messages.put("protoFieldReservedName", TestRegression.newBuilder().setClass_("hello").build());
        messages.put("fieldsOutOfOrder", TestFieldOrder.newBuilder().setValue1("foo").setValue2("bar").build());
    }

    private static void addOptionalTest(Map<String, Message> messages) {
        messages.put("optionalNotSet", TestAllTypes.newBuilder().build());
        messages.put("optionalSetToDefaultValue", TestAllTypes.newBuilder().setVeryOptionalInt32(0).build());
    }

    private static void addProtobufUtilTest(Map<String, Message> messages) throws ParseException {
        // 0, 3, 6 or 9 fractional digits
        messages.put("durationStringFormat-seconds", duration(Duration.newBuilder().setSeconds(1).build()));
        messages.put("durationStringFormat-millis", duration(Duration.newBuilder().setNanos(10000000).build()));
        messages.put("durationStringFormat-micros", duration(Duration.newBuilder().setNanos(10000).build()));
        messages.put("durationStringFormat-nanos", duration(Duration.newBuilder().setNanos(10).build()));
        messages.put("durationStringFormat-0.1s", duration(Durations.parse("0.1s")));
        messages.put("durationStringFormat-0.0001s", duration(Durations.parse("0.0001s")));
        messages.put("durationStringFormat-0.0000001s", duration(Durations.parse("0.0000001s")));
        messages.put("durationStringFormat-max", duration(Durations.parse("315576000000.999999999s")));
        messages.put("durationStringFormat-min", duration(Durations.parse("-315576000000.999999999s")));

        messages.put("timestampStringFormat-min", timestamp(Timestamps.parse("0001-01-01T00:00:00Z")));
        messages.put("timestampStringFormat-max", timestamp(Timestamps.parse("9999-12-31T23:59:59.999999999Z")));
        messages.put("timestampStringFormat-epoch", timestamp(Timestamps.parse("1970-01-01T00:00:00Z")));
        messages.put("timestampStringFormat-nanos", timestamp(Timestamp.newBuilder().setNanos(10).build()));
        messages.put("timestampStringFormat-micros", timestamp(Timestamp.newBuilder().setNanos(10000).build()));
        messages.put("timestampStringFormat-millis", timestamp(Timestamp.newBuilder().setNanos(10000000).build()));

        messages.put("toJsonString-empty", fieldMask());
        messages.put("toJsonString-foo", fieldMask("foo"));
        messages.put("toJsonString-emptyPath", fieldMask("foo.bar_baz", ""));
        messages.put("toJsonString-twoPaths", fieldMask("foo", "bar_baz"));

        // snakeToCamel, via the paths of a field mask
        for (String path : List.of("a", "abc", "abc_", "Abc", "abc_bdef", "abc__bdef", "abc_bdef_ghi", "Abc_bdef_ghi", "_", "____", "_abc", "_abc_", "_abc_d")) {
            messages.put("snakeToCamel-" + path, fieldMask(path));
        }
    }

    private static TestDuration duration(Duration duration) {
        return TestDuration.newBuilder().setDurationValue(duration).build();
    }

    private static TestTimestamp timestamp(Timestamp timestamp) {
        return TestTimestamp.newBuilder().setTimestampValue(timestamp).build();
    }

    private static TestFieldMask fieldMask(String... paths) {
        return TestFieldMask.newBuilder().setFieldMaskValue(FieldMask.newBuilder().addAllPaths(List.of(paths))).build();
    }

    public static TestAllTypes testAllTypesAllFields() {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        builder.setOptionalInt32(1234);
        builder.setOptionalInt64(1234567890123456789L);
        builder.setOptionalUint32(5678);
        builder.setOptionalUint64(2345678901234567890L);
        builder.setOptionalSint32(9012);
        builder.setOptionalSint64(3456789012345678901L);
        builder.setOptionalFixed32(3456);
        builder.setOptionalFixed64(4567890123456789012L);
        builder.setOptionalSfixed32(7890);
        builder.setOptionalSfixed64(5678901234567890123L);
        builder.setOptionalFloat(1.5f);
        builder.setOptionalDouble(1.25);
        builder.setOptionalBool(true);
        builder.setOptionalString("Hello world!");
        builder.setOptionalBytes(ByteString.copyFrom(new byte[] {0, 1, 2}));
        builder.setOptionalNestedEnum(NestedEnum.BAR);
        builder.setVeryOptionalInt32(50);
        builder.getOptionalNestedMessageBuilder().setValue(100);

        builder.addRepeatedInt32(1234);
        builder.addRepeatedInt64(1234567890123456789L);
        builder.addRepeatedUint32(5678);
        builder.addRepeatedUint64(2345678901234567890L);
        builder.addRepeatedSint32(9012);
        builder.addRepeatedSint64(3456789012345678901L);
        builder.addRepeatedFixed32(3456);
        builder.addRepeatedFixed64(4567890123456789012L);
        builder.addRepeatedSfixed32(7890);
        builder.addRepeatedSfixed64(5678901234567890123L);
        builder.addRepeatedFloat(1.5f);
        builder.addRepeatedDouble(1.25);
        builder.addRepeatedBool(true);
        builder.addRepeatedString("Hello world!");
        builder.addRepeatedBytes(ByteString.copyFrom(new byte[] {0, 1, 2}));
        builder.addRepeatedNestedEnum(NestedEnum.BAR);
        builder.addRepeatedNestedMessageBuilder().setValue(100);

        builder.addRepeatedInt32(234);
        builder.addRepeatedInt64(234567890123456789L);
        builder.addRepeatedUint32(678);
        builder.addRepeatedUint64(345678901234567890L);
        builder.addRepeatedSint32(10); // 012 (octal) in the original
        builder.addRepeatedSint64(456789012345678901L);
        builder.addRepeatedFixed32(456);
        builder.addRepeatedFixed64(567890123456789012L);
        builder.addRepeatedSfixed32(890);
        builder.addRepeatedSfixed64(678901234567890123L);
        builder.addRepeatedFloat(11.5f);
        builder.addRepeatedDouble(11.25);
        builder.addRepeatedBool(true);
        builder.addRepeatedString("ello world!");
        builder.addRepeatedBytes(ByteString.copyFrom(new byte[] {1, 2}));
        builder.addRepeatedNestedEnum(NestedEnum.BAZ);
        builder.addRepeatedNestedMessageBuilder().setValue(200);
        return builder.build();
    }

    public static TestMap testMapAllTypes() {
        TestMap.Builder builder = TestMap.newBuilder();
        builder.putInt32ToInt32Map(1, 10);
        builder.putInt64ToInt32Map(1234567890123456789L, 10);
        builder.putUint32ToInt32Map(2, 20);
        builder.putUint64ToInt32Map(2234567890123456789L, 20);
        builder.putSint32ToInt32Map(3, 30);
        builder.putSint64ToInt32Map(3234567890123456789L, 30);
        builder.putFixed32ToInt32Map(4, 40);
        builder.putFixed64ToInt32Map(4234567890123456789L, 40);
        builder.putSfixed32ToInt32Map(5, 50);
        builder.putSfixed64ToInt32Map(5234567890123456789L, 50);
        builder.putBoolToInt32Map(false, 6);
        builder.putStringToInt32Map("Hello", 10);

        builder.putInt32ToInt64Map(1, 1234567890123456789L);
        builder.putInt32ToUint32Map(2, 20);
        builder.putInt32ToUint64Map(2, 2234567890123456789L);
        builder.putInt32ToSint32Map(3, 30);
        builder.putInt32ToSint64Map(3, 3234567890123456789L);
        builder.putInt32ToFixed32Map(4, 40);
        builder.putInt32ToFixed64Map(4, 4234567890123456789L);
        builder.putInt32ToSfixed32Map(5, 50);
        builder.putInt32ToSfixed64Map(5, 5234567890123456789L);
        builder.putInt32ToFloatMap(6, 1.5f);
        builder.putInt32ToDoubleMap(6, 1.25);
        builder.putInt32ToBoolMap(7, false);
        builder.putInt32ToStringMap(7, "World");
        builder.putInt32ToBytesMap(8, ByteString.copyFrom(new byte[] {1, 2, 3}));
        builder.putInt32ToMessageMap(8, NestedMessage.newBuilder().setValue(1234).build());
        builder.putInt32ToEnumMap(9, NestedEnum.BAR);
        return builder.build();
    }
}
