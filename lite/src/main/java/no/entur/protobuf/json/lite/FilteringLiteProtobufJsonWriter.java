package no.entur.protobuf.json.lite;

import com.fasterxml.jackson.core.JsonGenerator;
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
import com.google.protobuf.MessageLiteOrBuilder;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.filter.JsonTimes;
import no.entur.protobuf.json.path.FilterType;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * {@linkplain LiteProtobufJsonWriter} with filtering, like json-log-filter:
 * <ul>
 *     <li>anonymize paths: the structure is kept, but every scalar value is replaced</li>
 *     <li>prune paths: the whole value is replaced, without reading it</li>
 *     <li>max string length: long strings are truncated, but not 64-bit integers, enum names, timestamps and durations</li>
 *     <li>max size: writing stops at the first value which does not fit, then the open objects and arrays are closed</li>
 * </ul>
 * Paths match the JSON field names, map keys and the {@code @type} key of google.protobuf.Any.
 * For a given message, the output is the same as filtering the output of {@linkplain LiteProtobufJsonWriter} afterwards,
 * with the rules of the json-log-filter Jackson filters.
 * <br><br>
 * The methods return false when writing stopped because of max size.
 */

public class FilteringLiteProtobufJsonWriter implements ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> {

    private final LiteMessagePlan.Plans plans = new LiteMessagePlan.Plans();
    private final Map<String, MessageLite> anyTypes;
    private final JsonFilter filter;

    protected FilteringLiteProtobufJsonWriter(Map<String, MessageLite> anyTypes, JsonFilter filter) {
        this.anyTypes = anyTypes;
        this.filter = filter;
    }

    public JsonFilter getFilter() {
        return filter;
    }

    @Override
    public void write(MessageLiteOrBuilder message, JsonGenerator generator) throws IOException {
        MessageLite m = LiteProtobufJsonWriter.toMessage(message);
        FilteredJsonValues values = new FilteredJsonValues(filter, generator);
        LiteMessagePlan plan = plans.get(m.getClass());
        if (plan.wellKnownType != null) {
            writeWellKnown(plan.wellKnownType, m, values, null, 0, false);
        } else {
            writeMessage(m, plan, values, null, null, 0, false);
        }
    }

    @Override
    public String writeAsString(MessageLiteOrBuilder message) throws IOException {
        return LiteProtobufJsonWriter.writeAsString(this, message);
    }

    private boolean writeMessage(Object message, LiteMessagePlan plan, FilteredJsonValues values, String typeUrl, Object name, int nameSize, boolean anonymize) throws IOException {
        if (!values.startObject(name, nameSize)) {
            return false;
        }
        boolean writing = true;
        if (typeUrl != null) {
            writing = writeTypeUrl(values, typeUrl, anonymize);
        }
        if (writing) {
            writing = writeFields(message, plan, values, anonymize);
        }
        values.endObject();
        return writing;
    }

    private static boolean writeTypeUrl(FilteredJsonValues values, String typeUrl, boolean anonymize) throws IOException {
        FilterType type = anonymize ? FilterType.ANON : values.match(LiteProtobufJsonWriter.TYPE_NAME);
        int nameSize = values.getNameSize(LiteProtobufJsonWriter.TYPE_NAME);
        if (type != null) {
            return values.writeFiltered(LiteProtobufJsonWriter.TYPE_NAME, nameSize, type);
        }
        return values.writeString(LiteProtobufJsonWriter.TYPE_NAME, nameSize, typeUrl);
    }

    private boolean writeFields(Object message, LiteMessagePlan plan, FilteredJsonValues values, boolean anonymize) throws IOException {
        for (LiteFieldInfo field : plan.fields) {
            switch (field.kind) {
                case LiteFieldInfo.SINGULAR: {
                    LiteValueType valueType = field.valueType;
                    boolean enumValue = valueType == LiteValueType.ENUM || valueType == LiteValueType.NULL_VALUE;
                    Object value = null;
                    int number = 0;
                    if (field.hazzer != null) {
                        if (!LiteJsonValues.has(field.hazzer, message)) {
                            continue;
                        }
                    } else if (enumValue) {
                        number = LiteProtobufJsonWriter.getEnumNumber(field, message);
                        if (number == 0) {
                            continue;
                        }
                    } else {
                        value = LiteJsonValues.invoke(field.getter, message);
                        if (LiteProtobufJsonWriter.isDefault(field, value)) {
                            continue;
                        }
                    }
                    FilterType type = anonymize ? FilterType.ANON : values.match(field);
                    int nameSize = values.getNameSize(field);
                    if (type == FilterType.PRUNE) {
                        // without reading the value
                        if (!values.writePruned(field.name, nameSize)) {
                            return false;
                        }
                        continue;
                    }
                    boolean written;
                    if (enumValue) {
                        if (type == FilterType.ANON) {
                            written = values.writeAnonymized(field.name, nameSize);
                        } else if (valueType == LiteValueType.NULL_VALUE) {
                            written = values.writeNull(field.name, nameSize);
                        } else {
                            if (field.hazzer != null) {
                                number = LiteProtobufJsonWriter.getEnumNumber(field, message);
                            }
                            written = values.writeEnum(field.name, nameSize, field.enumNames, number);
                        }
                    } else if (type == FilterType.ANON && valueType != LiteValueType.MESSAGE && valueType != LiteValueType.DYNAMIC) {
                        // without reading the value
                        written = values.writeAnonymized(field.name, nameSize);
                    } else {
                        if (value == null) {
                            value = LiteJsonValues.invoke(field.getter, message);
                        }
                        written = writeValue(values, valueType, value, field.enumNames, field.name, nameSize, type == FilterType.ANON);
                    }
                    if (!written) {
                        return false;
                    }
                    break;
                }
                case LiteFieldInfo.REPEATED: {
                    List<?> list = (List<?>) LiteJsonValues.invoke(field.getter, message);
                    if (list.isEmpty()) {
                        continue;
                    }
                    FilterType type = anonymize ? FilterType.ANON : values.match(field);
                    int nameSize = values.getNameSize(field);
                    if (type == FilterType.PRUNE) {
                        if (!values.writePruned(field.name, nameSize)) {
                            return false;
                        }
                        continue;
                    }
                    if (!values.startArray(field.name, nameSize)) {
                        return false;
                    }
                    boolean writing = writeElements(field, list, message, values, type == FilterType.ANON);
                    values.endArray();
                    if (!writing) {
                        return false;
                    }
                    break;
                }
                default: {
                    Map<?, ?> map = (Map<?, ?>) LiteJsonValues.invoke(field.getter, message);
                    if (map.isEmpty()) {
                        continue;
                    }
                    FilterType type = anonymize ? FilterType.ANON : values.match(field);
                    int nameSize = values.getNameSize(field);
                    if (type == FilterType.PRUNE) {
                        if (!values.writePruned(field.name, nameSize)) {
                            return false;
                        }
                        continue;
                    }
                    if (!values.startObject(field.name, nameSize)) {
                        return false;
                    }
                    boolean writing = writeEntries(field, map, message, values, type == FilterType.ANON);
                    values.endObject();
                    if (!writing) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean writeElements(LiteFieldInfo field, List<?> list, Object message, FilteredJsonValues values, boolean anonymize) throws IOException {
        if (field.valueType == LiteValueType.ENUM && field.valueGetter != null) {
            // the numbers of unrecognized values are only available here
            for (Object number : (List<?>) LiteJsonValues.invoke(field.valueGetter, message)) {
                boolean written = anonymize ? values.writeAnonymized(null, 0) : values.writeEnum(null, 0, field.enumNames, (Integer) number);
                if (!written) {
                    return false;
                }
            }
            return true;
        }
        for (Object element : list) {
            if (!writeValue(values, field.valueType, element, field.enumNames, null, 0, anonymize)) {
                return false;
            }
        }
        return true;
    }

    private boolean writeEntries(LiteFieldInfo field, Map<?, ?> map, Object message, FilteredJsonValues values, boolean anonymize) throws IOException {
        boolean numbers = field.valueType == LiteValueType.ENUM && field.valueGetter != null;
        Map<?, ?> entries = numbers ? (Map<?, ?>) LiteJsonValues.invoke(field.valueGetter, message) : map;
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            String key = LiteJsonValues.toKey(entry.getKey());
            int nameSize = values.getNameSize(key);
            FilterType type = anonymize ? FilterType.ANON : values.match(key);
            boolean written;
            if (type == FilterType.PRUNE) {
                written = values.writePruned(key, nameSize);
            } else if (numbers) {
                written = type == FilterType.ANON ? values.writeAnonymized(key, nameSize) : values.writeEnum(key, nameSize, field.enumNames, (Integer) entry.getValue());
            } else {
                written = writeValue(values, field.valueType, entry.getValue(), field.enumNames, key, nameSize, type == FilterType.ANON);
            }
            if (!written) {
                return false;
            }
        }
        return true;
    }

    private boolean writeValue(FilteredJsonValues values, LiteValueType type, Object value, LiteEnumNames enumNames, Object name, int nameSize, boolean anonymize) throws IOException {
        if (type == LiteValueType.MESSAGE) {
            return writeMessageValue((MessageLite) value, values, name, nameSize, anonymize);
        }
        if (type == LiteValueType.DYNAMIC) {
            return writeValue(values, LiteValueType.ofValue(value), value, null, name, nameSize, anonymize);
        }
        if (anonymize) {
            return values.writeAnonymized(name, nameSize);
        }
        switch (type) {
            case INT:
                return values.writeNumber(name, nameSize, (int) (Integer) value);
            case LONG:
                return values.writeUntruncated(name, nameSize, Long.toString((Long) value));
            case FLOAT:
                return values.writeFloat(name, nameSize, (Float) value);
            case DOUBLE:
                return values.writeDouble(name, nameSize, (Double) value);
            case BOOL:
                return values.writeBoolean(name, nameSize, (Boolean) value);
            case STRING:
                return values.writeString(name, nameSize, (String) value);
            case BYTES:
                return values.writeBase64(name, nameSize, (com.google.protobuf.ByteString) value);
            case ENUM:
                return values.writeEnum(name, nameSize, enumNames != null ? enumNames : plans.getEnumNames(((Enum<?>) value).getDeclaringClass()), LiteEnumNames.getNumber(value));
            case NULL_VALUE:
                return values.writeNull(name, nameSize);
            default:
                throw new IllegalStateException("Unexpected value type " + type);
        }
    }

    private boolean writeMessageValue(MessageLite message, FilteredJsonValues values, Object name, int nameSize, boolean anonymize) throws IOException {
        LiteMessagePlan plan = plans.get(message.getClass());
        if (plan.wellKnownType != null) {
            return writeWellKnown(plan.wellKnownType, message, values, name, nameSize, anonymize);
        }
        return writeMessage(message, plan, values, null, name, nameSize, anonymize);
    }

    // ---- well-known types ----

    private boolean writeWellKnown(LiteWellKnownType type, MessageLite message, FilteredJsonValues values, Object name, int nameSize, boolean anonymize) throws IOException {
        switch (type) {
            case ANY:
                return writeAny((Any) message, values, name, nameSize, anonymize);
            case TIMESTAMP: {
                Timestamp timestamp = (Timestamp) message;
                if (!JsonTimes.isValidTimestamp(timestamp.getSeconds(), timestamp.getNanos())) {
                    // out of range; write the fields rather than fail (JsonFormat fails)
                    return writeMessage(message, plans.get(message.getClass()), values, null, name, nameSize, anonymize);
                }
                if (anonymize) {
                    return values.writeAnonymized(name, nameSize);
                }
                return values.writeUntruncated(name, nameSize, JsonTimes.formatTimestamp(timestamp.getSeconds(), timestamp.getNanos()));
            }
            case DURATION: {
                Duration duration = (Duration) message;
                if (!JsonTimes.isValidDuration(duration.getSeconds(), duration.getNanos())) {
                    // out of range; write the fields rather than fail (JsonFormat fails)
                    return writeMessage(message, plans.get(message.getClass()), values, null, name, nameSize, anonymize);
                }
                if (anonymize) {
                    return values.writeAnonymized(name, nameSize);
                }
                return values.writeUntruncated(name, nameSize, JsonTimes.formatDuration(duration.getSeconds(), duration.getNanos()));
            }
            case STRUCT:
                return writeStruct((Struct) message, values, name, nameSize, anonymize);
            case VALUE:
                return writeStructValue((Value) message, values, name, nameSize, anonymize);
            case LIST_VALUE:
                return writeListValue((ListValue) message, values, name, nameSize, anonymize);
            default:
                // scalars
        }
        if (anonymize) {
            return values.writeAnonymized(name, nameSize);
        }
        switch (type) {
            case FIELD_MASK:
                return values.writeString(name, nameSize, LiteJsonValues.formatFieldMask(((FieldMask) message).getPathsList()));
            case BOOL_VALUE:
                return values.writeBoolean(name, nameSize, ((BoolValue) message).getValue());
            case INT32_VALUE:
                return values.writeNumber(name, nameSize, ((Int32Value) message).getValue());
            case UINT32_VALUE:
                return values.writeNumber(name, nameSize, Integer.toUnsignedLong(((UInt32Value) message).getValue()));
            case INT64_VALUE:
                return values.writeUntruncated(name, nameSize, Long.toString(((Int64Value) message).getValue()));
            case UINT64_VALUE:
                return values.writeUntruncated(name, nameSize, Long.toUnsignedString(((UInt64Value) message).getValue()));
            case STRING_VALUE:
                return values.writeString(name, nameSize, ((StringValue) message).getValue());
            case BYTES_VALUE:
                return values.writeBase64(name, nameSize, ((BytesValue) message).getValue());
            case FLOAT_VALUE:
                return values.writeFloat(name, nameSize, ((FloatValue) message).getValue());
            case DOUBLE_VALUE:
                return values.writeDouble(name, nameSize, ((DoubleValue) message).getValue());
            default:
                throw new IllegalStateException("Unexpected well-known type " + type);
        }
    }

    private boolean writeStruct(Struct struct, FilteredJsonValues values, Object name, int nameSize, boolean anonymize) throws IOException {
        if (!values.startObject(name, nameSize)) {
            return false;
        }
        boolean writing = true;
        for (Map.Entry<String, Value> entry : struct.getFieldsMap().entrySet()) {
            String key = entry.getKey();
            int keyNameSize = values.getNameSize(key);
            FilterType type = anonymize ? FilterType.ANON : values.match(key);
            if (type == FilterType.PRUNE) {
                writing = values.writePruned(key, keyNameSize);
            } else {
                writing = writeStructValue(entry.getValue(), values, key, keyNameSize, type == FilterType.ANON);
            }
            if (!writing) {
                break;
            }
        }
        values.endObject();
        return writing;
    }

    private boolean writeStructValue(Value value, FilteredJsonValues values, Object name, int nameSize, boolean anonymize) throws IOException {
        switch (value.getKindCase()) {
            case STRUCT_VALUE:
                return writeStruct(value.getStructValue(), values, name, nameSize, anonymize);
            case LIST_VALUE:
                return writeListValue(value.getListValue(), values, name, nameSize, anonymize);
            default:
                // scalars
        }
        if (anonymize) {
            return values.writeAnonymized(name, nameSize);
        }
        switch (value.getKindCase()) {
            case NUMBER_VALUE:
                // JsonFormat fails for NaN and infinity, write them as strings instead
                return values.writeDouble(name, nameSize, value.getNumberValue());
            case STRING_VALUE:
                return values.writeString(name, nameSize, value.getStringValue());
            case BOOL_VALUE:
                return values.writeBoolean(name, nameSize, value.getBoolValue());
            default:
                // null value or kind not set
                return values.writeNull(name, nameSize);
        }
    }

    private boolean writeListValue(ListValue listValue, FilteredJsonValues values, Object name, int nameSize, boolean anonymize) throws IOException {
        if (!values.startArray(name, nameSize)) {
            return false;
        }
        boolean writing = true;
        for (Value value : listValue.getValuesList()) {
            if (!writeStructValue(value, values, null, 0, anonymize)) {
                writing = false;
                break;
            }
        }
        values.endArray();
        return writing;
    }

    private boolean writeAny(Any any, FilteredJsonValues values, Object name, int nameSize, boolean anonymize) throws IOException {
        String typeUrl = any.getTypeUrl();
        if (typeUrl.isEmpty() && any.getValue().isEmpty()) {
            if (!values.startObject(name, nameSize)) {
                return false;
            }
            values.endObject();
            return true;
        }
        MessageLite content = LiteJsonValues.parse(LiteProtobufJsonWriter.findType(anyTypes, typeUrl), any.getValue());
        LiteMessagePlan plan = plans.get(content.getClass());
        if (plan.wellKnownType != null) {
            if (!values.startObject(name, nameSize)) {
                return false;
            }
            boolean writing = writeTypeUrl(values, typeUrl, anonymize);
            if (writing) {
                FilterType type = anonymize ? FilterType.ANON : values.match(LiteProtobufJsonWriter.VALUE_NAME);
                int valueNameSize = values.getNameSize(LiteProtobufJsonWriter.VALUE_NAME);
                if (type == FilterType.PRUNE) {
                    writing = values.writePruned(LiteProtobufJsonWriter.VALUE_NAME, valueNameSize);
                } else {
                    writing = writeWellKnown(plan.wellKnownType, content, values, LiteProtobufJsonWriter.VALUE_NAME, valueNameSize, type == FilterType.ANON);
                }
            }
            values.endObject();
            return writing;
        }
        return writeMessage(content, plan, values, typeUrl, name, nameSize, anonymize);
    }
}
