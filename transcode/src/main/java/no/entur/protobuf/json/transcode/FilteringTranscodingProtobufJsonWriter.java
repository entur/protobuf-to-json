package no.entur.protobuf.json.transcode;

import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.BytesValue;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf.WireFormat;
import com.google.protobuf.util.FieldMaskUtil;
import com.google.protobuf.util.JsonFormat;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.filter.JsonTimes;
import no.entur.protobuf.json.path.FilterType;
import no.entur.protobuf.json.plan.FilteredJsonValues;
import no.entur.protobuf.json.plan.JsonValues;
import no.entur.protobuf.json.plan.MessagePlan;
import no.entur.protobuf.json.plan.MessagePlan.FieldInfo;
import no.entur.protobuf.json.plan.MessagePlan.Plans;
import no.entur.protobuf.json.plan.ProtobufJsonWriterSupport;
import no.entur.protobuf.json.plan.WellKnownType;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@linkplain TranscodingProtobufJsonWriter} with filtering, like json-log-filter:
 * <ul>
 *     <li>anonymize paths: the structure is kept, but every scalar value is replaced</li>
 *     <li>prune paths: the whole value is replaced, without reading it</li>
 *     <li>max string length: long strings are truncated, also when written directly from UTF-8 bytes, but not 64-bit
 *     integers, enum names, timestamps and durations</li>
 *     <li>max size: writing stops at the first value which does not fit, then the open objects and arrays are closed</li>
 * </ul>
 * Paths match the JSON field names, map keys and the {@code @type} key of google.protobuf.Any.
 * For a given message, the output is the same as filtering the output of {@linkplain TranscodingProtobufJsonWriter} afterwards,
 * with the rules of the json-log-filter Jackson filters.
 * <br><br>
 * The methods return false when writing stopped because of max size.
 */

public class FilteringTranscodingProtobufJsonWriter implements ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> {

    private static final byte[] EMPTY = new byte[0];

    // state of a repeated or map field
    private static final int NOT_OPENED = 0;
    private static final int OPENED = 1;
    private static final int ANONYMIZED = 2; // opened, values are anonymized
    private static final int PRUNED = 3;     // replaced as a whole; the remaining values are skipped

    private final Plans plans;
    private final JsonFormat.TypeRegistry typeRegistry;
    private final JsonFilter filter;

    public FilteringTranscodingProtobufJsonWriter(Plans plans, JsonFormat.TypeRegistry typeRegistry, JsonFilter filter) {
        this.plans = plans;
        this.typeRegistry = typeRegistry;
        this.filter = filter;
    }

    public JsonFilter getFilter() {
        return filter;
    }

    @Override
    public void write(MessageOrBuilder message, JsonGenerator generator) throws IOException {
        Message m;
        if (message instanceof Message msg) {
            m = msg;
        } else {
            m = ((Message.Builder) message).buildPartial();
        }
        write(m, new FilteredJsonValues(filter, generator));
    }

    @Override
    public String writeAsString(MessageOrBuilder message) throws IOException {
        return ProtobufJsonWriterSupport.writeAsString(this, message);
    }

    /**
     * Write a message as the root value.
     *
     * @param message message
     * @param values  state of the write
     * @throws IOException if writing fails
     */

    public void write(Message message, FilteredJsonValues values) throws IOException {
        MessagePlan plan = plans.get(message.getDescriptorForType());
        if (plan.wellKnownType != null) {
            writeWellKnown(plan.wellKnownType, message, values, null, 0, false);
        } else {
            byte[] bytes = message.toByteArray();
            writeMessage(bytes, 0, bytes.length, plan, values, null, null, 0, false);
        }
    }

    /**
     * Write a message as a JSON object, ignoring any well-known type mapping.
     *
     * @param typeUrl   type url, for Any, or null
     * @param name      field name, or null
     * @param nameSize  size of the field name
     * @param anonymize whether to anonymize all values
     */

    public boolean writeMessage(byte[] bytes, int offset, int length, MessagePlan plan, FilteredJsonValues values, String typeUrl, SerializableString name, int nameSize, boolean anonymize) throws IOException {
        if (!values.startObject(name, nameSize)) {
            return false;
        }
        boolean writing = true;
        if (typeUrl != null) {
            writing = writeTypeUrl(values, typeUrl, anonymize);
        }
        if (writing) {
            CodedInputStream input = CodedInputStream.newInstance(bytes, offset, length);
            writing = writeFields(input, bytes, offset, plan, values, anonymize);
        }
        values.endObject();
        return writing;
    }

    private static boolean writeTypeUrl(FilteredJsonValues values, String typeUrl, boolean anonymize) {
        FilterType type = anonymize ? FilterType.ANON : values.match(FilteredJsonValues.TYPE_NAME);
        int nameSize = values.getNameSize(FilteredJsonValues.TYPE_NAME);
        if (type != null) {
            return values.writeFiltered(FilteredJsonValues.TYPE_NAME, nameSize, type);
        }
        return values.writeString(FilteredJsonValues.TYPE_NAME, nameSize, typeUrl);
    }

    /**
     * Write fields until the end of the input (or the current limit), or until an end group tag.
     *
     * @param base offset of the input within the bytes
     * @return false if writing stopped
     */

    private boolean writeFields(CodedInputStream input, byte[] bytes, int base, MessagePlan plan, FilteredJsonValues values, boolean anonymize) throws IOException {
        FieldInfo open = null;   // current repeated or map field
        int state = NOT_OPENED;  // state of the current repeated or map field
        int last = 0;            // last field number
        Set<String> keys = null; // keys of the current map field
        while (true) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            int wireType = WireFormat.getTagWireType(tag);
            if (wireType == WireFormat.WIRETYPE_END_GROUP) {
                break;
            }
            int number = WireFormat.getTagFieldNumber(tag);

            FieldInfo field;
            if (open != null && open.number == number) {
                field = open;
            } else {
                if (open != null) {
                    close(values, open, state);
                    open = null;
                }
                field = plan.getField(number);
                if (field == null || number <= last) {
                    // unknown field, extension or out of order
                    input.skipField(tag);
                    continue;
                }
                last = number;
                if (field.repeated) {
                    open = field;
                    state = NOT_OPENED;
                    if (field.map) {
                        if (keys == null) {
                            keys = new HashSet<>();
                        } else {
                            keys.clear();
                        }
                    }
                }
            }
            if (!field.accepts(wireType)) {
                input.skipField(tag);
                continue;
            }

            if (field.repeated) {
                if (state == PRUNED) {
                    input.skipField(tag);
                    continue;
                }
                if (field.map) {
                    state = writeMapEntry(input, bytes, base, field, values, state, keys, anonymize);
                } else if (field.packable && wireType == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
                    int limit = input.pushLimit(input.readRawVarint32());
                    while (input.getBytesUntilLimit() > 0) {
                        state = writeElement(input, bytes, base, field, values, state, anonymize, -1);
                        if (state == PRUNED) {
                            input.skipRawBytes(input.getBytesUntilLimit());
                            break;
                        }
                        if (values.isStopped()) {
                            break;
                        }
                    }
                    input.popLimit(limit);
                } else {
                    state = writeElement(input, bytes, base, field, values, state, anonymize, tag);
                }
                if (values.isStopped()) {
                    close(values, open, state);
                    return false;
                }
            } else if (field.closedEnum) {
                int value = (int) input.readRawVarint64();
                if (isKnown(field, value)) {
                    FilterType type = anonymize ? FilterType.ANON : values.match(field);
                    int nameSize = values.getNameSize(field);
                    boolean written = type != null ? values.writeFiltered(field.name, nameSize, type) : values.writeEnum(field.name, nameSize, field.enumNames, value);
                    if (!written) {
                        return false;
                    }
                }
            } else {
                FilterType type = anonymize ? FilterType.ANON : values.match(field);
                int nameSize = values.getNameSize(field);
                if (type == FilterType.PRUNE) {
                    if (!values.writePruned(field.name, nameSize)) {
                        return false;
                    }
                    input.skipField(tag);
                } else if (!writeValue(input, bytes, base, field, values, field.name, nameSize, type == FilterType.ANON)) {
                    return false;
                }
            }
        }
        if (open != null) {
            close(values, open, state);
        }
        return true;
    }

    /**
     * Write an element of a repeated (not map) field.
     *
     * @param tag tag of a value which is not packed, or -1
     * @return new state
     */

    private int writeElement(CodedInputStream input, byte[] bytes, int base, FieldInfo field, FilteredJsonValues values, int state, boolean anonymize, int tag) throws IOException {
        if (field.closedEnum) {
            int value = (int) input.readRawVarint64();
            if (!isKnown(field, value)) {
                return state;
            }
            state = open(values, field, state, anonymize);
            if (state == OPENED) {
                values.writeEnum(null, 0, field.enumNames, value);
            } else if (state == ANONYMIZED) {
                values.writeAnonymized(null, 0);
            }
            return state;
        }
        state = open(values, field, state, anonymize);
        if (state == OPENED || state == ANONYMIZED) {
            writeValue(input, bytes, base, field, values, null, 0, state == ANONYMIZED);
        } else if (state == PRUNED && tag != -1) {
            input.skipField(tag);
        }
        return state;
    }

    private static boolean isKnown(FieldInfo field, int value) {
        return field.enumNames.getName(value) != null || field.enumNames.isNullValue();
    }

    /**
     * Start a repeated or map field, when its first value is written.
     *
     * @return new state; not opened if writing stopped
     */

    private static int open(FilteredJsonValues values, FieldInfo field, int state, boolean anonymize) {
        if (state != NOT_OPENED) {
            return state;
        }
        FilterType type = anonymize ? FilterType.ANON : values.match(field);
        int nameSize = values.getNameSize(field);
        if (type == FilterType.PRUNE) {
            return values.writePruned(field.name, nameSize) ? PRUNED : NOT_OPENED;
        }
        boolean started = field.map ? values.startObject(field.name, nameSize) : values.startArray(field.name, nameSize);
        if (!started) {
            return NOT_OPENED;
        }
        return type == FilterType.ANON ? ANONYMIZED : OPENED;
    }

    private static void close(FilteredJsonValues values, FieldInfo field, int state) {
        if (state == OPENED || state == ANONYMIZED) {
            if (field.map) {
                values.endObject();
            } else {
                values.endArray();
            }
        }
    }

    /**
     * Write a single (non-packed) value.
     */

    private boolean writeValue(CodedInputStream input, byte[] bytes, int base, FieldInfo field, FilteredJsonValues values, SerializableString name, int nameSize, boolean anonymize) throws IOException {
        switch (field.type) {
            case MESSAGE -> {
                int length = input.readRawVarint32();
                return writeMessageValue(input, bytes, base, field.getMessagePlan(), length, values, name, nameSize, anonymize);
            }
            case GROUP -> {
                if (!values.startObject(name, nameSize)) {
                    return false;
                }
                boolean writing = writeFields(input, bytes, base, field.getMessagePlan(), values, anonymize);
                values.endObject();
                return writing;
            }
            case STRING -> {
                int length = input.readRawVarint32();
                boolean written;
                if (anonymize) {
                    written = values.writeAnonymized(name, nameSize);
                } else {
                    written = values.writeUtf8(name, nameSize, field.validateUtf8, bytes, base + input.getTotalBytesRead(), length);
                }
                input.skipRawBytes(length);
                return written;
            }
            case BYTES -> {
                int length = input.readRawVarint32();
                boolean written;
                if (anonymize) {
                    written = values.writeAnonymized(name, nameSize);
                } else {
                    written = values.writeBase64(name, nameSize, bytes, base + input.getTotalBytesRead(), length);
                }
                input.skipRawBytes(length);
                return written;
            }
            case FIXED32, SFIXED32, FLOAT -> {
                int raw = input.readRawLittleEndian32();
                return anonymize ? values.writeAnonymized(name, nameSize) : values.writeFixed32(name, nameSize, field, raw);
            }
            case FIXED64, SFIXED64, DOUBLE -> {
                long raw = input.readRawLittleEndian64();
                return anonymize ? values.writeAnonymized(name, nameSize) : values.writeFixed64(name, nameSize, field, raw);
            }
            default -> {
                long raw = input.readRawVarint64();
                return anonymize ? values.writeAnonymized(name, nameSize) : values.writeVarint(name, nameSize, field, raw);
            }
        }
    }

    private boolean writeMessageValue(CodedInputStream input, byte[] bytes, int base, MessagePlan plan, int length, FilteredJsonValues values, SerializableString name, int nameSize, boolean anonymize) throws IOException {
        if (plan.wellKnownType != null) {
            boolean written = writeWellKnown(plan.wellKnownType, bytes, base + input.getTotalBytesRead(), length, values, name, nameSize, anonymize);
            input.skipRawBytes(length);
            return written;
        }
        if (!values.startObject(name, nameSize)) {
            return false;
        }
        int limit = input.pushLimit(length);
        boolean writing = writeFields(input, bytes, base, plan, values, anonymize);
        input.popLimit(limit);
        values.endObject();
        return writing;
    }

    /**
     * Write a map entry. Entries with a key already written are skipped, so that no JSON key is written twice.
     * Map keys are matched like field names.
     *
     * @param keys keys already written for the map field
     * @return new state
     */

    private int writeMapEntry(CodedInputStream input, byte[] bytes, int base, FieldInfo field, FilteredJsonValues values, int state, Set<String> keys, boolean anonymize) throws IOException {
        FieldInfo keyField = field.mapKey;
        FieldInfo valueField = field.mapValue;

        int limit = input.pushLimit(input.readRawVarint32());
        String key = null;
        boolean done = false;
        while (true) {
            int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            int number = WireFormat.getTagFieldNumber(tag);
            int wireType = WireFormat.getTagWireType(tag);
            if (!done && number == 1 && keyField.accepts(wireType)) {
                key = readKey(input, keyField, wireType);
            } else if (!done && number == 2 && valueField.accepts(wireType)) {
                done = true;
                String name = key != null ? key : JsonValues.defaultKey(keyField);
                if (valueField.closedEnum) {
                    int value = (int) input.readRawVarint64();
                    // protobuf keeps entries with unknown closed enum values as unknown fields
                    if (isKnown(valueField, value) && keys.add(name)) {
                        state = open(values, field, state, anonymize);
                        if (state == OPENED || state == ANONYMIZED) {
                            SerializableString keyName = new SerializedString(name);
                            int nameSize = values.getNameSize(keyName);
                            FilterType type = state == ANONYMIZED ? FilterType.ANON : values.match(name);
                            if (type != null) {
                                values.writeFiltered(keyName, nameSize, type);
                            } else {
                                values.writeEnum(keyName, nameSize, valueField.enumNames, value);
                            }
                        }
                    }
                } else if (keys.add(name)) {
                    state = open(values, field, state, anonymize);
                    if (state == OPENED || state == ANONYMIZED) {
                        SerializableString keyName = new SerializedString(name);
                        int nameSize = values.getNameSize(keyName);
                        FilterType type = state == ANONYMIZED ? FilterType.ANON : values.match(name);
                        if (type == FilterType.PRUNE) {
                            values.writePruned(keyName, nameSize);
                            input.skipField(tag);
                        } else {
                            writeValue(input, bytes, base, valueField, values, keyName, nameSize, type == FilterType.ANON);
                        }
                    } else {
                        input.skipField(tag);
                    }
                } else {
                    input.skipField(tag);
                }
                if (values.isStopped()) {
                    break;
                }
            } else {
                input.skipField(tag);
            }
        }
        if (!done && !values.isStopped()) {
            String name = key != null ? key : JsonValues.defaultKey(keyField);
            if (keys.add(name)) {
                state = open(values, field, state, anonymize);
                if (state == OPENED || state == ANONYMIZED) {
                    SerializableString keyName = new SerializedString(name);
                    int nameSize = values.getNameSize(keyName);
                    FilterType type = state == ANONYMIZED ? FilterType.ANON : values.match(name);
                    if (type == FilterType.PRUNE) {
                        values.writePruned(keyName, nameSize);
                    } else {
                        writeDefault(valueField, values, keyName, nameSize, type == FilterType.ANON);
                    }
                }
            }
        }
        input.popLimit(limit);
        return state;
    }

    private static String readKey(CodedInputStream input, FieldInfo keyField, int wireType) throws IOException {
        return switch (wireType) {
            case WireFormat.WIRETYPE_VARINT -> JsonValues.varintKey(keyField, input.readRawVarint64());
            case WireFormat.WIRETYPE_FIXED32 -> JsonValues.fixed32Key(keyField, input.readRawLittleEndian32());
            case WireFormat.WIRETYPE_FIXED64 -> JsonValues.fixed64Key(keyField, input.readRawLittleEndian64());
            default -> input.readString();
        };
    }

    /**
     * Write the default value of a field, i.e. for map entries without value.
     */

    public boolean writeDefault(FieldInfo field, FilteredJsonValues values, SerializableString name, int nameSize, boolean anonymize) throws IOException {
        if (field.type == FieldDescriptor.Type.MESSAGE || field.type == FieldDescriptor.Type.GROUP) {
            MessagePlan plan = field.getMessagePlan();
            if (plan.wellKnownType != null) {
                return writeWellKnown(plan.wellKnownType, EMPTY, 0, 0, values, name, nameSize, anonymize);
            }
            if (!values.startObject(name, nameSize)) {
                return false;
            }
            values.endObject();
            return true;
        }
        if (anonymize) {
            return values.writeAnonymized(name, nameSize);
        }
        return switch (field.type) {
            case STRING, BYTES -> values.writeString(name, nameSize, "");
            case BOOL -> values.writeBoolean(name, nameSize, false);
            case ENUM -> values.writeEnum(name, nameSize, field.enumNames, ((EnumValueDescriptor) field.descriptor.getDefaultValue()).getNumber());
            case INT64, UINT64, SINT64, FIXED64, SFIXED64 -> values.writeUntruncated(name, nameSize, "0");
            case FLOAT, DOUBLE -> values.writeDouble(name, nameSize, 0.0d);
            default -> values.writeNumber(name, nameSize, 0);
        };
    }

    // ---- well-known types ----

    public boolean writeWellKnown(WellKnownType type, byte[] bytes, int offset, int length, FilteredJsonValues values, SerializableString name, int nameSize, boolean anonymize) throws IOException {
        switch (type) {
            case ANY -> {
                return writeAny(Any.parser().parseFrom(bytes, offset, length), values, name, nameSize, anonymize);
            }
            case TIMESTAMP -> {
                return writeTimestamp(Timestamp.parser().parseFrom(bytes, offset, length), values, name, nameSize, anonymize);
            }
            case DURATION -> {
                return writeDuration(Duration.parser().parseFrom(bytes, offset, length), values, name, nameSize, anonymize);
            }
            case STRUCT -> {
                return writeStruct(Struct.parser().parseFrom(bytes, offset, length), values, name, nameSize, anonymize);
            }
            case VALUE -> {
                return writeValue(Value.parser().parseFrom(bytes, offset, length), values, name, nameSize, anonymize);
            }
            case LIST_VALUE -> {
                return writeListValue(ListValue.parser().parseFrom(bytes, offset, length), values, name, nameSize, anonymize);
            }
            default -> {
                // scalars
            }
        }
        if (anonymize) {
            return values.writeAnonymized(name, nameSize);
        }
        return switch (type) {
            case FIELD_MASK -> values.writeString(name, nameSize, FieldMaskUtil.toJsonString(FieldMask.parser().parseFrom(bytes, offset, length)));
            case BOOL_VALUE -> values.writeBoolean(name, nameSize, BoolValue.parser().parseFrom(bytes, offset, length).getValue());
            case INT32_VALUE -> values.writeNumber(name, nameSize, Int32Value.parser().parseFrom(bytes, offset, length).getValue());
            case UINT32_VALUE -> values.writeNumber(name, nameSize, Integer.toUnsignedLong(UInt32Value.parser().parseFrom(bytes, offset, length).getValue()));
            case INT64_VALUE -> values.writeUntruncated(name, nameSize, Long.toString(Int64Value.parser().parseFrom(bytes, offset, length).getValue()));
            case UINT64_VALUE -> values.writeUntruncated(name, nameSize, Long.toUnsignedString(UInt64Value.parser().parseFrom(bytes, offset, length).getValue()));
            case STRING_VALUE -> values.writeString(name, nameSize, StringValue.parser().parseFrom(bytes, offset, length).getValue());
            case BYTES_VALUE -> {
                byte[] value = BytesValue.parser().parseFrom(bytes, offset, length).getValue().toByteArray();
                yield values.writeBase64(name, nameSize, value, 0, value.length);
            }
            case FLOAT_VALUE -> values.writeFloat(name, nameSize, FloatValue.parser().parseFrom(bytes, offset, length).getValue());
            case DOUBLE_VALUE -> values.writeDouble(name, nameSize, DoubleValue.parser().parseFrom(bytes, offset, length).getValue());
            default -> throw new IllegalStateException("Unexpected well-known type " + type);
        };
    }

    public boolean writeWellKnown(WellKnownType type, Message message, FilteredJsonValues values, SerializableString name, int nameSize, boolean anonymize) throws IOException {
        // use the generated classes directly if possible
        if (message instanceof Timestamp timestamp) {
            return writeTimestamp(timestamp, values, name, nameSize, anonymize);
        } else if (message instanceof Duration duration) {
            return writeDuration(duration, values, name, nameSize, anonymize);
        } else if (message instanceof Any any) {
            return writeAny(any, values, name, nameSize, anonymize);
        } else if (message instanceof Struct struct) {
            return writeStruct(struct, values, name, nameSize, anonymize);
        } else if (message instanceof Value value) {
            return writeValue(value, values, name, nameSize, anonymize);
        } else if (message instanceof ListValue listValue) {
            return writeListValue(listValue, values, name, nameSize, anonymize);
        }
        byte[] bytes = message.toByteArray();
        return writeWellKnown(type, bytes, 0, bytes.length, values, name, nameSize, anonymize);
    }

    private boolean writeTimestamp(Timestamp timestamp, FilteredJsonValues values, SerializableString name, int nameSize, boolean anonymize) throws IOException {
        long seconds = timestamp.getSeconds();
        int nanos = timestamp.getNanos();
        if (!JsonTimes.isValidTimestamp(seconds, nanos)) {
            // out of range; write the fields rather than fail (JsonFormat fails)
            byte[] bytes = timestamp.toByteArray();
            return writeMessage(bytes, 0, bytes.length, plans.get(Timestamp.getDescriptor()), values, null, name, nameSize, anonymize);
        }
        if (anonymize) {
            return values.writeAnonymized(name, nameSize);
        }
        return values.writeUntruncated(name, nameSize, JsonTimes.formatTimestamp(seconds, nanos));
    }

    private boolean writeDuration(Duration duration, FilteredJsonValues values, SerializableString name, int nameSize, boolean anonymize) throws IOException {
        long seconds = duration.getSeconds();
        int nanos = duration.getNanos();
        if (!JsonTimes.isValidDuration(seconds, nanos)) {
            // out of range; write the fields rather than fail (JsonFormat fails)
            byte[] bytes = duration.toByteArray();
            return writeMessage(bytes, 0, bytes.length, plans.get(Duration.getDescriptor()), values, null, name, nameSize, anonymize);
        }
        if (anonymize) {
            return values.writeAnonymized(name, nameSize);
        }
        return values.writeUntruncated(name, nameSize, JsonTimes.formatDuration(seconds, nanos));
    }

    private boolean writeStruct(Struct struct, FilteredJsonValues values, SerializableString name, int nameSize, boolean anonymize) {
        if (!values.startObject(name, nameSize)) {
            return false;
        }
        boolean writing = true;
        for (Map.Entry<String, Value> entry : struct.getFieldsMap().entrySet()) {
            String key = entry.getKey();
            SerializableString keyName = new SerializedString(key);
            int keyNameSize = values.getNameSize(keyName);
            FilterType type = anonymize ? FilterType.ANON : values.match(key);
            if (type == FilterType.PRUNE) {
                writing = values.writePruned(keyName, keyNameSize);
            } else {
                writing = writeValue(entry.getValue(), values, keyName, keyNameSize, type == FilterType.ANON);
            }
            if (!writing) {
                break;
            }
        }
        values.endObject();
        return writing;
    }

    private boolean writeValue(Value value, FilteredJsonValues values, SerializableString name, int nameSize, boolean anonymize) {
        switch (value.getKindCase()) {
            case STRUCT_VALUE -> {
                return writeStruct(value.getStructValue(), values, name, nameSize, anonymize);
            }
            case LIST_VALUE -> {
                return writeListValue(value.getListValue(), values, name, nameSize, anonymize);
            }
            default -> {
                // scalars
            }
        }
        if (anonymize) {
            return values.writeAnonymized(name, nameSize);
        }
        return switch (value.getKindCase()) {
            // JsonFormat fails for NaN and infinity, write them as strings instead
            case NUMBER_VALUE -> values.writeDouble(name, nameSize, value.getNumberValue());
            case STRING_VALUE -> values.writeString(name, nameSize, value.getStringValue());
            case BOOL_VALUE -> values.writeBoolean(name, nameSize, value.getBoolValue());
            default -> values.writeNull(name, nameSize); // null value or kind not set
        };
    }

    private boolean writeListValue(ListValue listValue, FilteredJsonValues values, SerializableString name, int nameSize, boolean anonymize) {
        if (!values.startArray(name, nameSize)) {
            return false;
        }
        boolean writing = true;
        for (Value value : listValue.getValuesList()) {
            if (!writeValue(value, values, null, 0, anonymize)) {
                writing = false;
                break;
            }
        }
        values.endArray();
        return writing;
    }

    private boolean writeAny(Any any, FilteredJsonValues values, SerializableString name, int nameSize, boolean anonymize) throws IOException {
        String typeUrl = any.getTypeUrl();
        if (typeUrl.isEmpty() && any.getValue().isEmpty()) {
            if (!values.startObject(name, nameSize)) {
                return false;
            }
            values.endObject();
            return true;
        }
        MessagePlan plan = plans.get(findDescriptor(typeUrl));
        byte[] content = any.getValue().toByteArray();
        if (plan.wellKnownType != null) {
            if (!values.startObject(name, nameSize)) {
                return false;
            }
            boolean writing = writeTypeUrl(values, typeUrl, anonymize);
            if (writing) {
                FilterType type = anonymize ? FilterType.ANON : values.match(FilteredJsonValues.VALUE_NAME);
                int valueNameSize = values.getNameSize(FilteredJsonValues.VALUE_NAME);
                if (type == FilterType.PRUNE) {
                    writing = values.writePruned(FilteredJsonValues.VALUE_NAME, valueNameSize);
                } else {
                    writing = writeWellKnown(plan.wellKnownType, content, 0, content.length, values, FilteredJsonValues.VALUE_NAME, valueNameSize, type == FilterType.ANON);
                }
            }
            values.endObject();
            return writing;
        }
        return writeMessage(content, 0, content.length, plan, values, typeUrl, name, nameSize, anonymize);
    }

    private Descriptor findDescriptor(String typeUrl) throws InvalidProtocolBufferException {
        // same as JsonFormat
        int index = typeUrl.lastIndexOf('/');
        if (index == -1) {
            throw new InvalidProtocolBufferException("Invalid type url found: " + typeUrl);
        }
        Descriptor descriptor = typeRegistry.find(typeUrl.substring(index + 1));
        if (descriptor == null) {
            throw new InvalidProtocolBufferException("Cannot find type for url: " + typeUrl);
        }
        return descriptor;
    }
}
