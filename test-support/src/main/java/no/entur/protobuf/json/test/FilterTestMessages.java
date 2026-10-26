package no.entur.protobuf.json.test;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Int32Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf_test_messages.proto2.TestMessagesProto2.TestAllTypesProto2;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.ForeignMessage;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3.NestedMessage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Messages with all kinds of values for the filter tests, i.e. strings with surrogate pairs and escapes, unknown enum values,
 * NaN, well-known types and google.protobuf.Any, and well-known types as the root.
 */

public final class FilterTestMessages {

    public static final String TEST_ALL_TYPES_PROTO3_URL = "type.googleapis.com/protobuf_test_messages.proto3.TestAllTypesProto3";

    private FilterTestMessages() {
    }

    /**
     * @return messages by name
     */

    public static Map<String, Message> create() {
        Map<String, Message> messages = new LinkedHashMap<>();
        messages.put("allKinds", TestAllTypesProto3.newBuilder()
                .setOptionalInt32(-1)
                .setOptionalInt64(Long.MIN_VALUE)
                .setOptionalFloat(Float.NaN)
                .setOptionalDouble(1e-300)
                .setOptionalBool(true)
                .setOptionalString("😀 smile \"quoted\" \\ \n\t\u0001 æøå € 😀😀 the end")
                .setOptionalBytes(ByteString.copyFrom(new byte[100]))
                .setOptionalNestedMessage(NestedMessage.newBuilder().setA(1).setCorecursive(TestAllTypesProto3.newBuilder()
                        .setOptionalString("nested 😀")
                        .addRepeatedNestedEnumValue(1)
                        .addRepeatedNestedEnumValue(1234)))
                .setOptionalForeignMessage(ForeignMessage.newBuilder().setC(3))
                .setOptionalNestedEnumValue(999)
                .addAllRepeatedString(List.of("a", "😀😀😀😀", "a long string which is longer than most limits"))
                .addRepeatedNestedMessage(NestedMessage.newBuilder().setA(2))
                .addRepeatedNestedMessage(NestedMessage.newBuilder().setA(3))
                .addAllPackedSint64(List.of(-1L, 1L, Long.MAX_VALUE))
                .putMapStringString("key 😀", "value 😀")
                .putMapStringString("optionalString", "a map value with the same key as a field")
                .putMapInt32Int32(-1, 1)
                .putMapBoolBool(false, true)
                .putMapStringNestedEnumValue("known", 2)
                .putMapStringNestedEnumValue("unknown", 77)
                .putMapStringNestedMessage("m", NestedMessage.newBuilder().setA(4).build())
                .setOneofString("oneof 😀")
                .setOptionalUint64Wrapper(UInt64Value.newBuilder().setValue(-1))
                .setOptionalDuration(Duration.newBuilder().setSeconds(-1).setNanos(-1))
                .setOptionalTimestamp(Timestamp.newBuilder().setSeconds(1700000000).setNanos(1))
                .setOptionalStruct(Struct.newBuilder()
                        .putFields("a", Value.newBuilder().setStringValue("b 😀").build())
                        .putFields("c", Value.newBuilder().setListValue(ListValue.newBuilder()
                                .addValues(Value.newBuilder().setNumberValue(1))
                                .addValues(Value.newBuilder().setNullValue(NullValue.NULL_VALUE))
                                .addValues(Value.newBuilder().setStructValue(Struct.newBuilder().putFields("d", Value.newBuilder().setBoolValue(false).build())))).build()))
                .setOptionalAny(Any.newBuilder()
                        .setTypeUrl(TEST_ALL_TYPES_PROTO3_URL)
                        .setValue(TestAllTypesProto3.newBuilder().setOptionalString("any 😀").addRepeatedInt32(5).build().toByteString()))
                .addRepeatedAny(Any.newBuilder()
                        .setTypeUrl("type.googleapis.com/google.protobuf.Int32Value")
                        .setValue(Int32Value.newBuilder().setValue(7).build().toByteString()))
                .setOptionalValue(Value.newBuilder().setStringValue("value"))
                .build());
        messages.put("proto2", TestAllTypesProto2.newBuilder()
                .setOptionalInt32(0)
                .setOptionalString("proto2")
                .setOptionalNestedEnum(TestAllTypesProto2.NestedEnum.BAZ)
                .setData(TestAllTypesProto2.Data.newBuilder().setGroupInt32(1).setGroupUint32(2))
                .addRepeatedNestedEnum(TestAllTypesProto2.NestedEnum.FOO)
                .build());
        messages.put("rootStruct", Struct.newBuilder()
                .putFields("a", Value.newBuilder().setStringValue("root struct").build())
                .putFields("b", Value.newBuilder().setListValue(ListValue.newBuilder().addValues(Value.newBuilder().setNumberValue(2))).build())
                .build());
        messages.put("rootTimestamp", Timestamp.newBuilder().setSeconds(1700000000).setNanos(123456789).build());
        return Collections.unmodifiableMap(messages);
    }
}
