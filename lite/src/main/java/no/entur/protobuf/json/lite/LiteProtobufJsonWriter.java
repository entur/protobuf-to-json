package no.entur.protobuf.json.lite;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
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
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ListValue;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import no.entur.protobuf.json.AbstractProtobufJsonWriterBuilder;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.filter.JsonTimes;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes protobuf lite messages as JSON, like JsonFormat does for full protobuf messages
 * (the <a href="https://protobuf.dev/programming-guides/json/">ProtoJSON format</a>, without insignificant whitespace).
 * Lite messages have no descriptors, so the fields are found once per message class from the generated code
 * (see {@linkplain LiteMessagePlan}), and read via reflection.
 * <br><br>
 * Differences from JsonFormat, as type information is missing in lite messages:
 * <ul>
 *     <li>unsigned 32 and 64-bit field values (uint32, fixed32, uint64, fixed64) above the signed max value are written as negative numbers;
 *     the unsigned well-known wrapper types are written correctly</li>
 *     <li>custom json_name options are not available; JSON names are derived from the field names, assuming lower case first letters</li>
 *     <li>proto2 group fields are named after the group type, in lower camel case</li>
 *     <li>extensions are not written</li>
 * </ul>
 * Like JsonFormat, google.protobuf.Any types must be registered (see {@linkplain Builder#withAnyType(String, MessageLite)}),
 * otherwise writing fails; the well-known types are always registered.
 * <br><br>
 * Use the {@linkplain #newBuilder() builder} for filtering (anonymize and prune paths, max string length and max size);
 * see {@linkplain FilteringLiteProtobufJsonWriter}. Implementations are thread-safe.
 */

public class LiteProtobufJsonWriter implements ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> {

    static final JsonFactory JSON_FACTORY = new JsonFactory();

    static final String TYPE_NAME = "@type";
    static final String VALUE_NAME = "value";

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder extends AbstractProtobufJsonWriterBuilder<Builder> {

        private final Map<String, MessageLite> anyTypes = new HashMap<>();

        /**
         * Register a message type, for google.protobuf.Any.
         *
         * @param typeUrl         type url, i.e. type.googleapis.com/my.package.MyMessage, or the full type name
         * @param defaultInstance a message of the type
         * @return this builder
         */

        public Builder withAnyType(String typeUrl, MessageLite defaultInstance) {
            int index = typeUrl.lastIndexOf('/');
            anyTypes.put(index == -1 ? typeUrl : typeUrl.substring(index + 1), defaultInstance.getDefaultInstanceForType());
            return this;
        }

        /**
         * @return a filtering writer if any filter option is set, otherwise a plain writer
         */

        public ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> build() {
            Map<String, MessageLite> types = new HashMap<>();
            for (LiteWellKnownType type : LiteWellKnownType.values()) {
                types.put(type.getFullName(), type.getDefaultInstance());
            }
            types.putAll(anyTypes);

            JsonFilter filter = buildFilter();
            if (filter != null) {
                return new FilteringLiteProtobufJsonWriter(types, filter);
            }
            return new LiteProtobufJsonWriter(types);
        }
    }

    private final LiteMessagePlan.Plans plans = new LiteMessagePlan.Plans();
    private final Map<String, MessageLite> anyTypes;

    protected LiteProtobufJsonWriter(Map<String, MessageLite> anyTypes) {
        this.anyTypes = anyTypes;
    }

    @Override
    public boolean write(MessageLiteOrBuilder message, JsonGenerator generator) throws IOException {
        MessageLite m = toMessage(message);
        LiteMessagePlan plan = plans.get(m.getClass());
        boolean utf8 = LiteJsonValues.isUtf8(generator);
        if (plan.wellKnownType != null) {
            writeWellKnown(plan.wellKnownType, m, generator, utf8);
        } else {
            writeMessage(m, plan, generator, utf8, null);
        }
        return true;
    }

    @Override
    public String writeAsString(MessageLiteOrBuilder message) throws IOException {
        return writeAsString(this, message);
    }

    static String writeAsString(ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> writer, MessageLiteOrBuilder message) throws IOException {
        int size = message instanceof MessageLite ? ((MessageLite) message).getSerializedSize() * 2 : 256;
        StringWriter output = new StringWriter(Math.max(size, 32));
        try (JsonGenerator generator = JSON_FACTORY.createGenerator(output)) {
            writer.write(message, generator);
        }
        return output.toString();
    }

    static MessageLite toMessage(MessageLiteOrBuilder message) {
        if (message instanceof MessageLite) {
            return (MessageLite) message;
        }
        return ((MessageLite.Builder) message).buildPartial();
    }

    private void writeMessage(Object message, LiteMessagePlan plan, JsonGenerator generator, boolean utf8, String typeUrl) throws IOException {
        generator.writeStartObject();
        if (typeUrl != null) {
            generator.writeFieldName(TYPE_NAME);
            generator.writeString(typeUrl);
        }
        writeFields(message, plan, generator, utf8);
        generator.writeEndObject();
    }

    private void writeFields(Object message, LiteMessagePlan plan, JsonGenerator generator, boolean utf8) throws IOException {
        for (LiteFieldInfo field : plan.fields) {
            switch (field.kind) {
                case LiteFieldInfo.SINGULAR: {
                    if (field.hazzer != null && !LiteJsonValues.has(field.hazzer, message)) {
                        continue;
                    }
                    if (field.valueType == LiteValueType.ENUM || field.valueType == LiteValueType.NULL_VALUE) {
                        int number = getEnumNumber(field, message);
                        if (field.hazzer == null && number == 0) {
                            continue;
                        }
                        generator.writeFieldName(field.name);
                        if (field.valueType == LiteValueType.NULL_VALUE) {
                            generator.writeNull();
                        } else {
                            writeEnum(generator, field.enumNames, number);
                        }
                        break;
                    }
                    Object value = LiteJsonValues.invoke(field.getter, message);
                    if (field.hazzer == null && isDefault(field, value)) {
                        continue;
                    }
                    generator.writeFieldName(field.name);
                    writeValue(generator, field.valueType, value, field.enumNames, utf8);
                    break;
                }
                case LiteFieldInfo.REPEATED: {
                    List<?> list = (List<?>) LiteJsonValues.invoke(field.getter, message);
                    if (list.isEmpty()) {
                        continue;
                    }
                    generator.writeFieldName(field.name);
                    generator.writeStartArray();
                    if (field.valueType == LiteValueType.ENUM && field.valueGetter != null) {
                        // the numbers of unrecognized values are only available here
                        for (Object number : (List<?>) LiteJsonValues.invoke(field.valueGetter, message)) {
                            writeEnum(generator, field.enumNames, (Integer) number);
                        }
                    } else {
                        for (Object element : list) {
                            writeValue(generator, field.valueType, element, field.enumNames, utf8);
                        }
                    }
                    generator.writeEndArray();
                    break;
                }
                default: {
                    Map<?, ?> map = (Map<?, ?>) LiteJsonValues.invoke(field.getter, message);
                    if (map.isEmpty()) {
                        continue;
                    }
                    generator.writeFieldName(field.name);
                    generator.writeStartObject();
                    if (field.valueType == LiteValueType.ENUM && field.valueGetter != null) {
                        for (Map.Entry<?, ?> entry : ((Map<?, ?>) LiteJsonValues.invoke(field.valueGetter, message)).entrySet()) {
                            generator.writeFieldName(LiteJsonValues.toKey(entry.getKey()));
                            writeEnum(generator, field.enumNames, (Integer) entry.getValue());
                        }
                    } else {
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            generator.writeFieldName(LiteJsonValues.toKey(entry.getKey()));
                            writeValue(generator, field.valueType, entry.getValue(), field.enumNames, utf8);
                        }
                    }
                    generator.writeEndObject();
                }
            }
        }
    }

    /**
     * Check whether the value of a field without presence is the default, so that it is not written.
     * Enum values are checked by their number.
     */

    static boolean isDefault(LiteFieldInfo field, Object value) {
        switch (field.valueType) {
            case INT:
                return (Integer) value == 0;
            case LONG:
                return (Long) value == 0L;
            case FLOAT:
                return Float.floatToRawIntBits((Float) value) == 0;
            case DOUBLE:
                return Double.doubleToRawLongBits((Double) value) == 0L;
            case BOOL:
                return !(Boolean) value;
            case STRING:
                return ((String) value).isEmpty();
            case BYTES:
                return ((ByteString) value).isEmpty();
            case MESSAGE:
                return value == ((MessageLite) value).getDefaultInstanceForType();
            default:
                return false;
        }
    }

    /**
     * Number of the value of a singular enum field, also for unrecognized values (of open enums).
     */

    static int getEnumNumber(LiteFieldInfo field, Object message) {
        if (field.valueGetter != null) {
            return (Integer) LiteJsonValues.invoke(field.valueGetter, message);
        }
        return LiteEnumNames.getNumber(LiteJsonValues.invoke(field.getter, message));
    }

    private static void writeEnum(JsonGenerator generator, LiteEnumNames names, int number) throws IOException {
        SerializableString name = names.getName(number);
        if (name != null) {
            generator.writeString(name);
        } else {
            generator.writeNumber(number);
        }
    }

    private void writeValue(JsonGenerator generator, LiteValueType type, Object value, LiteEnumNames enumNames, boolean utf8) throws IOException {
        switch (type) {
            case INT:
                generator.writeNumber((Integer) value);
                break;
            case LONG:
                generator.writeString(Long.toString((Long) value));
                break;
            case FLOAT:
                writeFloat(generator, (Float) value);
                break;
            case DOUBLE:
                writeDouble(generator, (Double) value);
                break;
            case BOOL:
                generator.writeBoolean((Boolean) value);
                break;
            case STRING:
                generator.writeString((String) value);
                break;
            case BYTES:
                LiteJsonValues.writeBase64(generator, (ByteString) value);
                break;
            case ENUM:
                writeEnum(generator, enumNames != null ? enumNames : plans.getEnumNames(((Enum<?>) value).getDeclaringClass()), LiteEnumNames.getNumber(value));
                break;
            case NULL_VALUE:
                generator.writeNull();
                break;
            case MESSAGE:
                writeMessageValue((MessageLite) value, generator, utf8);
                break;
            default:
                writeValue(generator, LiteValueType.ofValue(value), value, null, utf8);
        }
    }

    private static void writeFloat(JsonGenerator generator, float value) throws IOException {
        if (LiteJsonValues.isFinite(value)) {
            generator.writeNumber(value);
        } else {
            generator.writeString(LiteJsonValues.toString(value));
        }
    }

    private static void writeDouble(JsonGenerator generator, double value) throws IOException {
        if (LiteJsonValues.isFinite(value)) {
            generator.writeNumber(value);
        } else {
            generator.writeString(LiteJsonValues.toString(value));
        }
    }

    private void writeMessageValue(MessageLite message, JsonGenerator generator, boolean utf8) throws IOException {
        LiteMessagePlan plan = plans.get(message.getClass());
        if (plan.wellKnownType != null) {
            writeWellKnown(plan.wellKnownType, message, generator, utf8);
        } else {
            writeMessage(message, plan, generator, utf8, null);
        }
    }

    // ---- well-known types ----

    private void writeWellKnown(LiteWellKnownType type, MessageLite message, JsonGenerator generator, boolean utf8) throws IOException {
        switch (type) {
            case ANY:
                writeAny((Any) message, generator, utf8);
                break;
            case TIMESTAMP: {
                Timestamp timestamp = (Timestamp) message;
                if (JsonTimes.isValidTimestamp(timestamp.getSeconds(), timestamp.getNanos())) {
                    generator.writeString(JsonTimes.formatTimestamp(timestamp.getSeconds(), timestamp.getNanos()));
                } else {
                    // out of range; write the fields rather than fail (JsonFormat fails)
                    writeMessage(message, plans.get(message.getClass()), generator, utf8, null);
                }
                break;
            }
            case DURATION: {
                Duration duration = (Duration) message;
                if (JsonTimes.isValidDuration(duration.getSeconds(), duration.getNanos())) {
                    generator.writeString(JsonTimes.formatDuration(duration.getSeconds(), duration.getNanos()));
                } else {
                    // out of range; write the fields rather than fail (JsonFormat fails)
                    writeMessage(message, plans.get(message.getClass()), generator, utf8, null);
                }
                break;
            }
            case FIELD_MASK:
                generator.writeString(LiteJsonValues.formatFieldMask(((FieldMask) message).getPathsList()));
                break;
            case STRUCT:
                writeStruct((Struct) message, generator, utf8);
                break;
            case VALUE:
                writeStructValue((Value) message, generator, utf8);
                break;
            case LIST_VALUE:
                writeListValue((ListValue) message, generator, utf8);
                break;
            case BOOL_VALUE:
                generator.writeBoolean(((BoolValue) message).getValue());
                break;
            case INT32_VALUE:
                generator.writeNumber(((Int32Value) message).getValue());
                break;
            case UINT32_VALUE:
                generator.writeNumber(Integer.toUnsignedLong(((UInt32Value) message).getValue()));
                break;
            case INT64_VALUE:
                generator.writeString(Long.toString(((Int64Value) message).getValue()));
                break;
            case UINT64_VALUE:
                generator.writeString(Long.toUnsignedString(((UInt64Value) message).getValue()));
                break;
            case STRING_VALUE:
                generator.writeString(((StringValue) message).getValue());
                break;
            case BYTES_VALUE:
                LiteJsonValues.writeBase64(generator, ((BytesValue) message).getValue());
                break;
            case FLOAT_VALUE:
                writeFloat(generator, ((FloatValue) message).getValue());
                break;
            case DOUBLE_VALUE:
                writeDouble(generator, ((DoubleValue) message).getValue());
                break;
            default:
                throw new IllegalStateException("Unexpected well-known type " + type);
        }
    }

    private void writeStruct(Struct struct, JsonGenerator generator, boolean utf8) throws IOException {
        generator.writeStartObject();
        for (Map.Entry<String, Value> entry : struct.getFieldsMap().entrySet()) {
            generator.writeFieldName(entry.getKey());
            writeStructValue(entry.getValue(), generator, utf8);
        }
        generator.writeEndObject();
    }

    private void writeStructValue(Value value, JsonGenerator generator, boolean utf8) throws IOException {
        switch (value.getKindCase()) {
            case NUMBER_VALUE:
                // JsonFormat fails for NaN and infinity, write them as strings instead
                writeDouble(generator, value.getNumberValue());
                break;
            case STRING_VALUE:
                generator.writeString(value.getStringValue());
                break;
            case BOOL_VALUE:
                generator.writeBoolean(value.getBoolValue());
                break;
            case STRUCT_VALUE:
                writeStruct(value.getStructValue(), generator, utf8);
                break;
            case LIST_VALUE:
                writeListValue(value.getListValue(), generator, utf8);
                break;
            default:
                // null value or kind not set
                generator.writeNull();
        }
    }

    private void writeListValue(ListValue listValue, JsonGenerator generator, boolean utf8) throws IOException {
        generator.writeStartArray();
        for (Value value : listValue.getValuesList()) {
            writeStructValue(value, generator, utf8);
        }
        generator.writeEndArray();
    }

    private void writeAny(Any any, JsonGenerator generator, boolean utf8) throws IOException {
        String typeUrl = any.getTypeUrl();
        if (typeUrl.isEmpty() && any.getValue().isEmpty()) {
            generator.writeStartObject();
            generator.writeEndObject();
            return;
        }
        MessageLite content = LiteJsonValues.parse(findType(typeUrl), any.getValue());
        LiteMessagePlan plan = plans.get(content.getClass());
        if (plan.wellKnownType != null) {
            generator.writeStartObject();
            generator.writeFieldName(TYPE_NAME);
            generator.writeString(typeUrl);
            generator.writeFieldName(VALUE_NAME);
            writeWellKnown(plan.wellKnownType, content, generator, utf8);
            generator.writeEndObject();
        } else {
            writeMessage(content, plan, generator, utf8, typeUrl);
        }
    }

    private MessageLite findType(String typeUrl) throws InvalidProtocolBufferException {
        return findType(anyTypes, typeUrl);
    }

    static MessageLite findType(Map<String, MessageLite> anyTypes, String typeUrl) throws InvalidProtocolBufferException {
        MessageLite prototype = anyTypes.get(LiteJsonValues.getTypeName(typeUrl));
        if (prototype == null) {
            throw new InvalidProtocolBufferException("Cannot find type for url: " + typeUrl);
        }
        return prototype;
    }
}
