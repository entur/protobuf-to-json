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

    ANY("google.protobuf.Any"),
    TIMESTAMP("google.protobuf.Timestamp"),
    DURATION("google.protobuf.Duration"),
    FIELD_MASK("google.protobuf.FieldMask"),
    STRUCT("google.protobuf.Struct"),
    VALUE("google.protobuf.Value"),
    LIST_VALUE("google.protobuf.ListValue"),
    BOOL_VALUE("google.protobuf.BoolValue"),
    INT32_VALUE("google.protobuf.Int32Value"),
    UINT32_VALUE("google.protobuf.UInt32Value"),
    INT64_VALUE("google.protobuf.Int64Value"),
    UINT64_VALUE("google.protobuf.UInt64Value"),
    STRING_VALUE("google.protobuf.StringValue"),
    BYTES_VALUE("google.protobuf.BytesValue"),
    FLOAT_VALUE("google.protobuf.FloatValue"),
    DOUBLE_VALUE("google.protobuf.DoubleValue");

    private final String fullName;

    WellKnownType(String fullName) {
        this.fullName = fullName;
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
     * The descriptor of a well-known type which comes with protobuf-java, e.g. for a google.protobuf.Any whose type is not
     * in the type registry. Only then is the generated class of the type initialized, rather than those of all the types when
     * the first writer is created.
     *
     * @param fullName full name of the type, e.g. google.protobuf.Duration
     * @return descriptor, or null if the type is not a well-known type with a special JSON mapping
     */

    public static Descriptor findDescriptor(String fullName) {
        if (!fullName.startsWith("google.protobuf.")) {
            return null;
        }
        for (WellKnownType type : values()) {
            if (type.fullName.equals(fullName)) {
                return type.getDescriptor();
            }
        }
        return null;
    }

    private Descriptor getDescriptor() {
        return switch (this) {
            case ANY -> Any.getDescriptor();
            case TIMESTAMP -> Timestamp.getDescriptor();
            case DURATION -> Duration.getDescriptor();
            case FIELD_MASK -> FieldMask.getDescriptor();
            case STRUCT -> Struct.getDescriptor();
            case VALUE -> Value.getDescriptor();
            case LIST_VALUE -> ListValue.getDescriptor();
            case BOOL_VALUE -> BoolValue.getDescriptor();
            case INT32_VALUE -> Int32Value.getDescriptor();
            case UINT32_VALUE -> UInt32Value.getDescriptor();
            case INT64_VALUE -> Int64Value.getDescriptor();
            case UINT64_VALUE -> UInt64Value.getDescriptor();
            case STRING_VALUE -> StringValue.getDescriptor();
            case BYTES_VALUE -> BytesValue.getDescriptor();
            case FLOAT_VALUE -> FloatValue.getDescriptor();
            case DOUBLE_VALUE -> DoubleValue.getDescriptor();
        };
    }
}
