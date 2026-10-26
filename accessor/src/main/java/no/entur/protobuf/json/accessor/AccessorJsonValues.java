package no.entur.protobuf.json.accessor;

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
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import no.entur.protobuf.json.filter.JsonTimes;
import no.entur.protobuf.json.path.FilterType;
import no.entur.protobuf.json.plan.FilteredJsonValues;
import no.entur.protobuf.json.plan.JsonValues;
import no.entur.protobuf.json.plan.MessagePlan.EnumNames;
import no.entur.protobuf.json.plan.MessagePlan.FieldInfo;
import no.entur.protobuf.json.plan.WellKnownType;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;

import java.io.IOException;

/**
 * Writes values read with the accessors of generated messages; called by the generated writers, through
 * {@linkplain OutOfLineJsonValues}. The name is null for array elements, and for map values (the key is written before).
 * <br><br>
 * The filtering methods return false if writing stopped because of max size.
 */

final class AccessorJsonValues {

    // what to do with a field, after filtering by path
    public static final int WRITE = 0;
    public static final int ANONYMIZE = 1;
    public static final int DONE = 2;
    public static final int STOPPED = 3;

    private AccessorJsonValues() {
    }

    // ---- objects and arrays ----

    public static void startObject(JsonGenerator generator, SerializableString name) {
        if (name != null) {
            generator.writeName(name);
        }
        generator.writeStartObject();
    }

    public static void endObject(JsonGenerator generator) {
        generator.writeEndObject();
    }

    public static void startArray(JsonGenerator generator, SerializableString name) {
        generator.writeName(name);
        generator.writeStartArray();
    }

    public static void endArray(JsonGenerator generator) {
        generator.writeEndArray();
    }

    public static void writeKey(JsonGenerator generator, String key) {
        generator.writeName(key);
    }

    // ---- scalars ----

    public static void writeInt32(JsonGenerator generator, SerializableString name, int value) {
        if (name != null) {
            generator.writeName(name);
        }
        generator.writeNumber(value);
    }

    public static void writeUInt32(JsonGenerator generator, SerializableString name, int value) {
        if (name != null) {
            generator.writeName(name);
        }
        generator.writeNumber(Integer.toUnsignedLong(value));
    }

    public static void writeInt64(JsonGenerator generator, SerializableString name, long value) {
        if (name != null) {
            generator.writeName(name);
        }
        generator.writeString(Long.toString(value));
    }

    public static void writeUInt64(JsonGenerator generator, SerializableString name, long value) {
        if (name != null) {
            generator.writeName(name);
        }
        generator.writeString(Long.toUnsignedString(value));
    }

    public static void writeBool(JsonGenerator generator, SerializableString name, boolean value) {
        if (name != null) {
            generator.writeName(name);
        }
        generator.writeBoolean(value);
    }

    public static void writeFloat(JsonGenerator generator, SerializableString name, float value) {
        if (name != null) {
            generator.writeName(name);
        }
        JsonValues.writeFloat(generator, value);
    }

    public static void writeDouble(JsonGenerator generator, SerializableString name, double value) {
        if (name != null) {
            generator.writeName(name);
        }
        JsonValues.writeDouble(generator, value);
    }

    public static void writeString(JsonGenerator generator, SerializableString name, String value) {
        if (name != null) {
            generator.writeName(name);
        }
        JsonValues.writeString(generator, value);
    }

    public static void writeBytes(JsonGenerator generator, SerializableString name, ByteString value) {
        if (name != null) {
            generator.writeName(name);
        }
        byte[] bytes = value.toByteArray();
        JsonValues.writeBase64(generator, bytes, 0, bytes.length);
    }

    public static void writeEnum(JsonGenerator generator, SerializableString name, EnumNames names, int number) {
        if (name != null) {
            generator.writeName(name);
        }
        JsonValues.writeEnum(generator, names, number);
    }

    // ---- well-known types ----

    public static void writeTimestamp(JsonGenerator generator, SerializableString name, Timestamp value, AccessorProtobufJsonWriter writer) throws IOException {
        long seconds = value.getSeconds();
        int nanos = value.getNanos();
        if (!JsonTimes.isValidTimestamp(seconds, nanos)) {
            // written as an object
            writeWellKnown(generator, name, WellKnownType.TIMESTAMP, value, writer);
            return;
        }
        if (name != null) {
            generator.writeName(name);
        }
        JsonValues.writeTimestamp(generator, seconds, nanos);
    }

    public static void writeDuration(JsonGenerator generator, SerializableString name, Duration value, AccessorProtobufJsonWriter writer) throws IOException {
        long seconds = value.getSeconds();
        int nanos = value.getNanos();
        if (!JsonTimes.isValidDuration(seconds, nanos)) {
            // written as an object
            writeWellKnown(generator, name, WellKnownType.DURATION, value, writer);
            return;
        }
        if (name != null) {
            generator.writeName(name);
        }
        JsonValues.writeDuration(generator, seconds, nanos);
    }

    public static void writeFieldMask(JsonGenerator generator, SerializableString name, FieldMask value, AccessorProtobufJsonWriter writer) throws IOException {
        writeWellKnown(generator, name, WellKnownType.FIELD_MASK, value, writer);
    }

    public static void writeStruct(JsonGenerator generator, SerializableString name, Struct value, AccessorProtobufJsonWriter writer) throws IOException {
        writeWellKnown(generator, name, WellKnownType.STRUCT, value, writer);
    }

    public static void writeValue(JsonGenerator generator, SerializableString name, Value value, AccessorProtobufJsonWriter writer) throws IOException {
        writeWellKnown(generator, name, WellKnownType.VALUE, value, writer);
    }

    public static void writeListValue(JsonGenerator generator, SerializableString name, ListValue value, AccessorProtobufJsonWriter writer) throws IOException {
        writeWellKnown(generator, name, WellKnownType.LIST_VALUE, value, writer);
    }

    public static void writeAny(JsonGenerator generator, SerializableString name, Any value, AccessorProtobufJsonWriter writer) throws IOException {
        writeWellKnown(generator, name, WellKnownType.ANY, value, writer);
    }

    private static void writeWellKnown(JsonGenerator generator, SerializableString name, WellKnownType type, Message value, AccessorProtobufJsonWriter writer) throws IOException {
        if (name != null) {
            generator.writeName(name);
        }
        writer.transcoder.writeWellKnown(type, value, generator);
    }

    public static void writeBoolValue(JsonGenerator generator, SerializableString name, BoolValue value, AccessorProtobufJsonWriter writer) {
        writeBool(generator, name, value.getValue());
    }

    public static void writeInt32Value(JsonGenerator generator, SerializableString name, Int32Value value, AccessorProtobufJsonWriter writer) {
        writeInt32(generator, name, value.getValue());
    }

    public static void writeUInt32Value(JsonGenerator generator, SerializableString name, UInt32Value value, AccessorProtobufJsonWriter writer) {
        writeUInt32(generator, name, value.getValue());
    }

    public static void writeInt64Value(JsonGenerator generator, SerializableString name, Int64Value value, AccessorProtobufJsonWriter writer) {
        writeInt64(generator, name, value.getValue());
    }

    public static void writeUInt64Value(JsonGenerator generator, SerializableString name, UInt64Value value, AccessorProtobufJsonWriter writer) {
        writeUInt64(generator, name, value.getValue());
    }

    public static void writeStringValue(JsonGenerator generator, SerializableString name, StringValue value, AccessorProtobufJsonWriter writer) {
        writeString(generator, name, value.getValue());
    }

    public static void writeBytesValue(JsonGenerator generator, SerializableString name, BytesValue value, AccessorProtobufJsonWriter writer) {
        writeBytes(generator, name, value.getValue());
    }

    public static void writeFloatValue(JsonGenerator generator, SerializableString name, FloatValue value, AccessorProtobufJsonWriter writer) {
        writeFloat(generator, name, value.getValue());
    }

    public static void writeDoubleValue(JsonGenerator generator, SerializableString name, DoubleValue value, AccessorProtobufJsonWriter writer) {
        writeDouble(generator, name, value.getValue());
    }

    // ---- map keys, which are always strings ----

    public static String keyString(Object key) {
        return (String) key;
    }

    public static String keyInt32(Object key) {
        return Integer.toString((Integer) key);
    }

    public static String keyUInt32(Object key) {
        return Integer.toUnsignedString((Integer) key);
    }

    public static String keyInt64(Object key) {
        return Long.toString((Long) key);
    }

    public static String keyUInt64(Object key) {
        return Long.toUnsignedString((Long) key);
    }

    public static String keyBool(Object key) {
        return (Boolean) key ? "true" : "false";
    }

    // ---- filtering ----

    /**
     * Filter a scalar field by path: write the replacement if the field is anonymized or pruned.
     *
     * @return {@linkplain #WRITE}, {@linkplain #DONE} or {@linkplain #STOPPED}
     */

    public static int filterScalar(FilteredJsonValues values, FieldInfo field, boolean anonymize) {
        FilterType type = anonymize ? FilterType.ANON : values.match(field);
        if (type == null) {
            return WRITE;
        }
        return values.writeFiltered(field.name, values.getNameSize(field), type) ? DONE : STOPPED;
    }

    /**
     * Filter a message, repeated or map field by path: write the replacement if it is pruned. Anonymized messages, arrays
     * and objects keep their structure.
     *
     * @return {@linkplain #WRITE}, {@linkplain #ANONYMIZE}, {@linkplain #DONE} or {@linkplain #STOPPED}
     */

    public static int filterStructure(FilteredJsonValues values, FieldInfo field, boolean anonymize) {
        if (anonymize) {
            return ANONYMIZE;
        }
        FilterType type = values.match(field);
        if (type == null) {
            return WRITE;
        }
        if (type == FilterType.ANON) {
            return ANONYMIZE;
        }
        return values.writePruned(field.name, values.getNameSize(field)) ? DONE : STOPPED;
    }

    /**
     * Filter a map entry by path, like a field.
     *
     * @param action {@linkplain #WRITE} or {@linkplain #ANONYMIZE}, for the map
     * @return {@linkplain #WRITE}, {@linkplain #ANONYMIZE}, {@linkplain #DONE} or {@linkplain #STOPPED}
     */

    public static int filterEntry(FilteredJsonValues values, SerializableString key, int action) {
        if (action == ANONYMIZE) {
            return ANONYMIZE;
        }
        FilterType type = values.match(key);
        if (type == null) {
            return WRITE;
        }
        if (type == FilterType.ANON) {
            return ANONYMIZE;
        }
        return values.writePruned(key, values.getNameSize(key)) ? DONE : STOPPED;
    }

    public static SerializableString keyName(String key) {
        return new SerializedString(key);
    }

    // ---- filtered scalars ----

    public static boolean writeInt32(FilteredJsonValues values, SerializableString name, int nameSize, int value, boolean anonymize) {
        return anonymize ? values.writeAnonymized(name, nameSize) : values.writeNumber(name, nameSize, value);
    }

    public static boolean writeUInt32(FilteredJsonValues values, SerializableString name, int nameSize, int value, boolean anonymize) {
        return anonymize ? values.writeAnonymized(name, nameSize) : values.writeNumber(name, nameSize, Integer.toUnsignedLong(value));
    }

    public static boolean writeInt64(FilteredJsonValues values, SerializableString name, int nameSize, long value, boolean anonymize) {
        return anonymize ? values.writeAnonymized(name, nameSize) : values.writeUntruncated(name, nameSize, Long.toString(value));
    }

    public static boolean writeUInt64(FilteredJsonValues values, SerializableString name, int nameSize, long value, boolean anonymize) {
        return anonymize ? values.writeAnonymized(name, nameSize) : values.writeUntruncated(name, nameSize, Long.toUnsignedString(value));
    }

    public static boolean writeBool(FilteredJsonValues values, SerializableString name, int nameSize, boolean value, boolean anonymize) {
        return anonymize ? values.writeAnonymized(name, nameSize) : values.writeBoolean(name, nameSize, value);
    }

    public static boolean writeFloat(FilteredJsonValues values, SerializableString name, int nameSize, float value, boolean anonymize) {
        return anonymize ? values.writeAnonymized(name, nameSize) : values.writeFloat(name, nameSize, value);
    }

    public static boolean writeDouble(FilteredJsonValues values, SerializableString name, int nameSize, double value, boolean anonymize) {
        return anonymize ? values.writeAnonymized(name, nameSize) : values.writeDouble(name, nameSize, value);
    }

    public static boolean writeString(FilteredJsonValues values, SerializableString name, int nameSize, String value, boolean anonymize) {
        return anonymize ? values.writeAnonymized(name, nameSize) : values.writeString(name, nameSize, value);
    }

    public static boolean writeBytes(FilteredJsonValues values, SerializableString name, int nameSize, ByteString value, boolean anonymize) {
        if (anonymize) {
            return values.writeAnonymized(name, nameSize);
        }
        byte[] bytes = value.toByteArray();
        return values.writeBase64(name, nameSize, bytes, 0, bytes.length);
    }

    public static boolean writeEnum(FilteredJsonValues values, SerializableString name, int nameSize, EnumNames names, int number, boolean anonymize) {
        return anonymize ? values.writeAnonymized(name, nameSize) : values.writeEnum(name, nameSize, names, number);
    }

    // ---- filtered well-known types ----

    public static boolean writeTimestamp(FilteredJsonValues values, SerializableString name, int nameSize, Timestamp value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) throws IOException {
        long seconds = value.getSeconds();
        int nanos = value.getNanos();
        if (!JsonTimes.isValidTimestamp(seconds, nanos)) {
            // written as an object
            return writer.transcoder.writeWellKnown(WellKnownType.TIMESTAMP, value, values, name, nameSize, anonymize);
        }
        return anonymize ? values.writeAnonymized(name, nameSize) : values.writeUntruncated(name, nameSize, JsonTimes.formatTimestamp(seconds, nanos));
    }

    public static boolean writeDuration(FilteredJsonValues values, SerializableString name, int nameSize, Duration value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) throws IOException {
        long seconds = value.getSeconds();
        int nanos = value.getNanos();
        if (!JsonTimes.isValidDuration(seconds, nanos)) {
            // written as an object
            return writer.transcoder.writeWellKnown(WellKnownType.DURATION, value, values, name, nameSize, anonymize);
        }
        return anonymize ? values.writeAnonymized(name, nameSize) : values.writeUntruncated(name, nameSize, JsonTimes.formatDuration(seconds, nanos));
    }

    public static boolean writeFieldMask(FilteredJsonValues values, SerializableString name, int nameSize, FieldMask value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) throws IOException {
        return writer.transcoder.writeWellKnown(WellKnownType.FIELD_MASK, value, values, name, nameSize, anonymize);
    }

    public static boolean writeStruct(FilteredJsonValues values, SerializableString name, int nameSize, Struct value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) throws IOException {
        return writer.transcoder.writeWellKnown(WellKnownType.STRUCT, value, values, name, nameSize, anonymize);
    }

    public static boolean writeValue(FilteredJsonValues values, SerializableString name, int nameSize, Value value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) throws IOException {
        return writer.transcoder.writeWellKnown(WellKnownType.VALUE, value, values, name, nameSize, anonymize);
    }

    public static boolean writeListValue(FilteredJsonValues values, SerializableString name, int nameSize, ListValue value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) throws IOException {
        return writer.transcoder.writeWellKnown(WellKnownType.LIST_VALUE, value, values, name, nameSize, anonymize);
    }

    public static boolean writeAny(FilteredJsonValues values, SerializableString name, int nameSize, Any value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) throws IOException {
        return writer.transcoder.writeWellKnown(WellKnownType.ANY, value, values, name, nameSize, anonymize);
    }

    public static boolean writeBoolValue(FilteredJsonValues values, SerializableString name, int nameSize, BoolValue value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) {
        return writeBool(values, name, nameSize, value.getValue(), anonymize);
    }

    public static boolean writeInt32Value(FilteredJsonValues values, SerializableString name, int nameSize, Int32Value value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) {
        return writeInt32(values, name, nameSize, value.getValue(), anonymize);
    }

    public static boolean writeUInt32Value(FilteredJsonValues values, SerializableString name, int nameSize, UInt32Value value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) {
        return writeUInt32(values, name, nameSize, value.getValue(), anonymize);
    }

    public static boolean writeInt64Value(FilteredJsonValues values, SerializableString name, int nameSize, Int64Value value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) {
        return writeInt64(values, name, nameSize, value.getValue(), anonymize);
    }

    public static boolean writeUInt64Value(FilteredJsonValues values, SerializableString name, int nameSize, UInt64Value value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) {
        return writeUInt64(values, name, nameSize, value.getValue(), anonymize);
    }

    public static boolean writeStringValue(FilteredJsonValues values, SerializableString name, int nameSize, StringValue value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) {
        return writeString(values, name, nameSize, value.getValue(), anonymize);
    }

    public static boolean writeBytesValue(FilteredJsonValues values, SerializableString name, int nameSize, BytesValue value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) {
        return writeBytes(values, name, nameSize, value.getValue(), anonymize);
    }

    public static boolean writeFloatValue(FilteredJsonValues values, SerializableString name, int nameSize, FloatValue value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) {
        return writeFloat(values, name, nameSize, value.getValue(), anonymize);
    }

    public static boolean writeDoubleValue(FilteredJsonValues values, SerializableString name, int nameSize, DoubleValue value, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) {
        return writeDouble(values, name, nameSize, value.getValue(), anonymize);
    }

    /**
     * Name of the method for writing a well-known type: unfiltered and filtered methods have the same name.
     */

    static String getWriteMethodName(WellKnownType type) {
        return switch (type) {
            case ANY -> "writeAny";
            case TIMESTAMP -> "writeTimestamp";
            case DURATION -> "writeDuration";
            case FIELD_MASK -> "writeFieldMask";
            case STRUCT -> "writeStruct";
            case VALUE -> "writeValue";
            case LIST_VALUE -> "writeListValue";
            case BOOL_VALUE -> "writeBoolValue";
            case INT32_VALUE -> "writeInt32Value";
            case UINT32_VALUE -> "writeUInt32Value";
            case INT64_VALUE -> "writeInt64Value";
            case UINT64_VALUE -> "writeUInt64Value";
            case STRING_VALUE -> "writeStringValue";
            case BYTES_VALUE -> "writeBytesValue";
            case FLOAT_VALUE -> "writeFloatValue";
            case DOUBLE_VALUE -> "writeDoubleValue";
        };
    }
}
