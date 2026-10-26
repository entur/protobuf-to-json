package no.entur.protobuf.json.lite;

import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.MessageLite;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;

/**
 * Well-known types with a special JSON mapping, see <a href="https://protobuf.dev/programming-guides/json/">ProtoJSON format</a>.
 * google.protobuf.Empty needs no special mapping.
 */

enum LiteWellKnownType {

    ANY(Any.class, Any.getDefaultInstance()),
    TIMESTAMP(Timestamp.class, Timestamp.getDefaultInstance()),
    DURATION(Duration.class, Duration.getDefaultInstance()),
    FIELD_MASK(FieldMask.class, FieldMask.getDefaultInstance()),
    STRUCT(Struct.class, Struct.getDefaultInstance()),
    VALUE(Value.class, Value.getDefaultInstance()),
    LIST_VALUE(ListValue.class, ListValue.getDefaultInstance()),
    BOOL_VALUE(BoolValue.class, BoolValue.getDefaultInstance()),
    INT32_VALUE(Int32Value.class, Int32Value.getDefaultInstance()),
    UINT32_VALUE(UInt32Value.class, UInt32Value.getDefaultInstance()),
    INT64_VALUE(Int64Value.class, Int64Value.getDefaultInstance()),
    UINT64_VALUE(UInt64Value.class, UInt64Value.getDefaultInstance()),
    STRING_VALUE(StringValue.class, StringValue.getDefaultInstance()),
    BYTES_VALUE(BytesValue.class, BytesValue.getDefaultInstance()),
    FLOAT_VALUE(FloatValue.class, FloatValue.getDefaultInstance()),
    DOUBLE_VALUE(DoubleValue.class, DoubleValue.getDefaultInstance());

    private final Class<?> type;
    private final MessageLite defaultInstance;

    LiteWellKnownType(Class<?> type, MessageLite defaultInstance) {
        this.type = type;
        this.defaultInstance = defaultInstance;
    }

    MessageLite getDefaultInstance() {
        return defaultInstance;
    }

    /**
     * Full name of the type, for type urls.
     *
     * @return full name, i.e. google.protobuf.Timestamp
     */

    String getFullName() {
        return "google.protobuf." + type.getSimpleName();
    }

    static LiteWellKnownType of(Class<?> type) {
        if (type.getPackage() == null || !type.getName().startsWith("com.google.protobuf.")) {
            return null;
        }
        for (LiteWellKnownType value : values()) {
            if (value.type == type) {
                return value;
            }
        }
        return null;
    }
}
