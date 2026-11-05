package no.entur.protobuf.json.plan;

import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;

/**
 * Well-known types with a special JSON mapping, see
 * <a href="https://protobuf.dev/programming-guides/json/">ProtoJSON format</a>.
 */

public enum WellKnownType {

    ANY(Any.getDescriptor()),
    TIMESTAMP(Timestamp.getDescriptor()),
    DURATION(Duration.getDescriptor()),
    FIELD_MASK(FieldMask.getDescriptor()),
    STRUCT(Struct.getDescriptor()),
    VALUE(Value.getDescriptor()),
    LIST_VALUE(ListValue.getDescriptor()),
    BOOL_VALUE(BoolValue.getDescriptor()),
    INT32_VALUE(Int32Value.getDescriptor()),
    UINT32_VALUE(UInt32Value.getDescriptor()),
    INT64_VALUE(Int64Value.getDescriptor()),
    UINT64_VALUE(UInt64Value.getDescriptor()),
    STRING_VALUE(StringValue.getDescriptor()),
    BYTES_VALUE(BytesValue.getDescriptor()),
    FLOAT_VALUE(FloatValue.getDescriptor()),
    DOUBLE_VALUE(DoubleValue.getDescriptor());

    private final String fullName;
    private final Descriptor descriptor;

    WellKnownType(Descriptor descriptor) {
        this.fullName = descriptor.getFullName();
        this.descriptor = descriptor;
    }

    /**
     * @param descriptor message type
     * @return well-known type, or null
     */

    public static WellKnownType of(Descriptor descriptor) {
        String name = descriptor.getFullName();
        if (!name.startsWith("google.protobuf.")) {
            return null;
        }
        for (WellKnownType type : values()) {
            if (type.fullName.equals(name)) {
                return type;
            }
        }
        return null;
    }

    /**
     * The descriptor of a well-known type which comes with protobuf-java, i.e. for a google.protobuf.Any whose type is not
     * in the type registry.
     *
     * @param fullName full name of the type, i.e. google.protobuf.Duration
     * @return descriptor, or null if the type is not a well-known type with a special JSON mapping
     */

    public static Descriptor findDescriptor(String fullName) {
        if (!fullName.startsWith("google.protobuf.")) {
            return null;
        }
        for (WellKnownType type : values()) {
            if (type.fullName.equals(fullName)) {
                return type.descriptor;
            }
        }
        return null;
    }
}
