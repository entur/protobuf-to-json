package no.entur.protobuf.json.stream;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors.FieldDescriptor.Type;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import com.google.protobuf.WireFormat;
import no.entur.protobuf.json.path.FilterType;
import no.entur.protobuf.json.plan.FilteredJsonValues;
import no.entur.protobuf.json.plan.JsonValues;
import no.entur.protobuf.json.plan.MessagePlan;
import no.entur.protobuf.json.plan.MessagePlan.FieldInfo;
import no.entur.protobuf.json.plan.MessagePlan.Plans;
import no.entur.protobuf.json.transcode.FilteringTranscodingProtobufJsonWriter;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * {@linkplain CodedOutputStreamJsonSink} with filtering. Every value is first prepared (see {@linkplain #prepare(FieldInfo)}),
 * which matches paths and starts the array or object of a repeated or map field, then written, anonymized, pruned or skipped.
 * Pruned messages are not serialized.
 * <br><br>
 * Once max size is reached, {@linkplain FilteredJsonValues} writes nothing more but closing brackets, while the frames
 * are still tracked so that the open arrays and objects are closed.
 * <br><br>
 * Not thread-safe; create one instance per message.
 */

final class FilteringCodedOutputStreamJsonSink implements CodedOutputStreamSink {

    // state of a repeated or map field
    private static final int NOT_OPENED = 0;
    private static final int OPENED = 1;
    private static final int ANONYMIZED = 2; // opened, values are anonymized
    private static final int PRUNED = 3;     // replaced as a whole; the remaining values are skipped

    // what to do with a value
    private static final int SKIP = 0;
    private static final int WRITE = 1;
    private static final int ANONYMIZE = 2;
    private static final int PRUNE = 3;

    private static final class Frame {
        MessagePlan plan;
        boolean anonymize;          // all values of the message are anonymized
        FieldInfo open;             // current repeated or map field
        int state;                  // state of the current repeated or map field
        int last;                   // last field number
        Set<String> keys;           // keys of the current map field

        // tag written, value to follow
        int pendingNumber;
        int pendingWireType;

        // packed field
        boolean expectPackedLength;
        FieldInfo packed;           // null if skipped
        int packedRemaining;        // remaining bytes

        // map entry
        FieldInfo entry;
        String key;
        boolean entryDone;
        boolean entryTagged;        // entry written as tag, length, key and value (rather than as a message)
        boolean expectEntryLength;

        void reset(MessagePlan plan, FieldInfo entry, boolean anonymize) {
            this.plan = plan;
            this.anonymize = anonymize;
            this.open = null;
            this.state = NOT_OPENED;
            this.last = 0;
            this.pendingNumber = 0;
            this.pendingWireType = 0;
            this.expectPackedLength = false;
            this.packed = null;
            this.packedRemaining = 0;
            this.entry = entry;
            this.key = null;
            this.entryDone = false;
            this.entryTagged = false;
            this.expectEntryLength = false;
        }
    }

    private final Plans plans;
    private final FilteringTranscodingProtobufJsonWriter transcoder;
    private final FilteredJsonValues values;
    private final CodedOutputStream output;

    private Frame[] frames = new Frame[8];
    private int depth = -1;
    private Frame frame;

    private int skipGroups; // > 0 while within a skipped (i.e. unknown or pruned) group

    // name of the prepared value
    private SerializableString name;
    private int nameSize;

    FilteringCodedOutputStreamJsonSink(Plans plans, FilteringTranscodingProtobufJsonWriter transcoder, FilteredJsonValues values) {
        this.plans = plans;
        this.transcoder = transcoder;
        this.values = values;
        this.output = JsonCodedOutputStreamFactory.create(this);
    }

    void write(Message message) throws IOException {
        push(plans.get(message.getDescriptorForType()), null, false);
        writeObject(message, null, 0);
        pop();
    }

    /**
     * Write a message as an object, in the current frame.
     */

    private void writeObject(MessageLite message, SerializableString name, int nameSize) throws IOException {
        if (!values.startObject(name, nameSize)) {
            // max size reached
            return;
        }
        message.writeTo(output);
        finishTaggedEntry();
        closeOpen();
        values.endObject();
    }

    // ---- frames ----

    private void push(MessagePlan plan, FieldInfo entry, boolean anonymize) {
        depth++;
        if (depth == frames.length) {
            frames = Arrays.copyOf(frames, frames.length * 2);
        }
        Frame f = frames[depth];
        if (f == null) {
            frames[depth] = f = new Frame();
        }
        f.reset(plan, entry, anonymize);
        frame = f;
    }

    private void pop() {
        depth--;
        frame = depth >= 0 ? frames[depth] : null;
    }

    private void closeOpen() {
        Frame f = frame;
        if (f.open != null) {
            if (f.state == OPENED || f.state == ANONYMIZED) {
                if (f.open.map) {
                    values.endObject();
                } else {
                    values.endArray();
                }
            }
            f.open = null;
        }
    }

    /**
     * Select a field for writing a value with the given wire type.
     *
     * @return the field, or null if the value should be skipped
     */

    private FieldInfo select(int number, int wireType) {
        Frame f = frame;
        FieldInfo field;
        if (f.open != null && f.open.number == number) {
            field = f.open;
        } else {
            closeOpen();
            field = f.plan.getField(number);
            if (field == null || number <= f.last) {
                // unknown field, extension or out of order
                return null;
            }
            f.last = number;
            if (field.repeated) {
                f.open = field;
                f.state = NOT_OPENED;
                if (field.map) {
                    if (f.keys == null) {
                        f.keys = new HashSet<>();
                    } else {
                        f.keys.clear();
                    }
                }
            }
        }
        if (!field.accepts(wireType)) {
            return null;
        }
        return field;
    }

    /**
     * Start the array or object of a repeated or map field, when its first value is written.
     *
     * @param f frame of the message with the field
     * @return new state
     */

    private int open(Frame f, FieldInfo field) {
        if (f.state != NOT_OPENED) {
            return f.state;
        }
        FilterType type = f.anonymize ? FilterType.ANON : values.match(field);
        int size = values.getNameSize(field);
        if (type == FilterType.PRUNE) {
            if (values.writePruned(field.name, size)) {
                f.state = PRUNED;
            }
            return f.state;
        }
        boolean started = field.map ? values.startObject(field.name, size) : values.startArray(field.name, size);
        if (started) {
            f.state = type == FilterType.ANON ? ANONYMIZED : OPENED;
        }
        return f.state;
    }

    /**
     * Prepare writing a value of a field of the current message.
     *
     * @param field selected field
     * @return what to do with the value; the name to write it with is in {@linkplain #name}
     */

    private int prepare(FieldInfo field) {
        Frame f = frame;
        if (field.repeated) {
            name = null;
            nameSize = 0;
            int state = open(f, field);
            if (state == OPENED) {
                return WRITE;
            }
            return state == ANONYMIZED ? ANONYMIZE : SKIP;
        }
        name = field.name;
        nameSize = values.getNameSize(field);
        if (f.anonymize) {
            return ANONYMIZE;
        }
        return toAction(values.match(field));
    }

    /**
     * Prepare writing the value of the current map entry. Entries with a key which was already written are skipped,
     * so that no JSON key is written twice (generated messages never have duplicate keys).
     *
     * @return what to do with the value; the name to write it with is in {@linkplain #name}
     */

    private int prepareEntry() {
        Frame f = frame;
        String key = f.key != null ? f.key : JsonValues.defaultKey(f.entry.mapKey);
        // the map field is tracked by the frame of the message with the map field
        Frame parent = frames[depth - 1];
        if (!parent.keys.add(key)) {
            return SKIP;
        }
        int state = open(parent, f.entry);
        if (state != OPENED && state != ANONYMIZED) {
            return SKIP;
        }
        name = new SerializedString(key);
        nameSize = values.getNameSize(name);
        if (state == ANONYMIZED) {
            return ANONYMIZE;
        }
        return toAction(values.match(key));
    }

    private static int toAction(FilterType type) {
        if (type == null) {
            return WRITE;
        }
        return type == FilterType.PRUNE ? PRUNE : ANONYMIZE;
    }

    /**
     * Write anonymized and pruned values.
     *
     * @return true if the value should be written as is
     */

    private boolean isWrite(int action) {
        switch (action) {
            case WRITE:
                return true;
            case ANONYMIZE:
                values.writeAnonymized(name, nameSize);
                return false;
            case PRUNE:
                values.writePruned(name, nameSize);
                return false;
            default:
                return false;
        }
    }

    private static boolean isKnown(FieldInfo field, long value) {
        return field.enumNames.getName((int) value) != null || field.enumNames.isNullValue();
    }

    // ---- map entries ----

    /**
     * Called after a map entry value was handled. Entries written as tag + length + key + value always end with
     * the value (see MapEntryLite), so the entry is then complete.
     */

    private void entryValueDone() {
        Frame f = frame;
        f.entryDone = true;
        if (f.entryTagged) {
            pop();
        }
    }

    /**
     * Complete a map entry without value (written as tag + length + key + value).
     */

    private void finishTaggedEntry() throws IOException {
        Frame f = frame;
        if (f.entry != null && f.entryTagged) {
            if (!f.entryDone) {
                writeDefaultEntryValue(f.entry.mapValue);
            }
            pop();
        }
    }

    private void writeDefaultEntryValue(FieldInfo valueField) throws IOException {
        int action = prepareEntry();
        if (action == PRUNE) {
            values.writePruned(name, nameSize);
        } else if (action == WRITE || action == ANONYMIZE) {
            transcoder.writeDefault(valueField, values, name, nameSize, action == ANONYMIZE);
        }
    }

    // ---- tagged values ----

    private void varint(int number, long value) {
        if (skipGroups > 0) {
            return;
        }
        Frame f = frame;
        if (f.entry != null) {
            if (number == 1 && f.entry.mapKey.accepts(WireFormat.WIRETYPE_VARINT)) {
                f.key = JsonValues.varintKey(f.entry.mapKey, value);
            } else if (number == 2 && !f.entryDone && f.entry.mapValue.accepts(WireFormat.WIRETYPE_VARINT)) {
                FieldInfo valueField = f.entry.mapValue;
                // protobuf keeps entries with unknown closed enum values as unknown fields
                if (!valueField.closedEnum || isKnown(valueField, value)) {
                    if (isWrite(prepareEntry())) {
                        values.writeVarint(name, nameSize, valueField, value);
                    }
                }
                entryValueDone();
            }
            return;
        }
        FieldInfo field = select(number, WireFormat.WIRETYPE_VARINT);
        if (field == null || (field.closedEnum && !isKnown(field, value))) {
            return;
        }
        if (isWrite(prepare(field))) {
            values.writeVarint(name, nameSize, field, value);
        }
    }

    @Override
    public void writeInt32(int fieldNumber, int value) {
        varint(fieldNumber, value);
    }

    @Override
    public void writeUInt32(int fieldNumber, int value) {
        varint(fieldNumber, value & 0xFFFFFFFFL);
    }

    @Override
    public void writeUInt64(int fieldNumber, long value) {
        varint(fieldNumber, value);
    }

    @Override
    public void writeBool(int fieldNumber, boolean value) {
        varint(fieldNumber, value ? 1 : 0);
    }

    @Override
    public void writeFixed32(int fieldNumber, int value) {
        if (skipGroups > 0) {
            return;
        }
        Frame f = frame;
        if (f.entry != null) {
            if (fieldNumber == 1 && f.entry.mapKey.accepts(WireFormat.WIRETYPE_FIXED32)) {
                f.key = JsonValues.fixed32Key(f.entry.mapKey, value);
            } else if (fieldNumber == 2 && !f.entryDone && f.entry.mapValue.accepts(WireFormat.WIRETYPE_FIXED32)) {
                if (isWrite(prepareEntry())) {
                    values.writeFixed32(name, nameSize, f.entry.mapValue, value);
                }
                entryValueDone();
            }
            return;
        }
        FieldInfo field = select(fieldNumber, WireFormat.WIRETYPE_FIXED32);
        if (field != null && isWrite(prepare(field))) {
            values.writeFixed32(name, nameSize, field, value);
        }
    }

    @Override
    public void writeFixed64(int fieldNumber, long value) {
        if (skipGroups > 0) {
            return;
        }
        Frame f = frame;
        if (f.entry != null) {
            if (fieldNumber == 1 && f.entry.mapKey.accepts(WireFormat.WIRETYPE_FIXED64)) {
                f.key = JsonValues.fixed64Key(f.entry.mapKey, value);
            } else if (fieldNumber == 2 && !f.entryDone && f.entry.mapValue.accepts(WireFormat.WIRETYPE_FIXED64)) {
                if (isWrite(prepareEntry())) {
                    values.writeFixed64(name, nameSize, f.entry.mapValue, value);
                }
                entryValueDone();
            }
            return;
        }
        FieldInfo field = select(fieldNumber, WireFormat.WIRETYPE_FIXED64);
        if (field != null && isWrite(prepare(field))) {
            values.writeFixed64(name, nameSize, field, value);
        }
    }

    @Override
    public void writeString(int fieldNumber, String value) {
        if (skipGroups > 0) {
            return;
        }
        Frame f = frame;
        if (f.entry != null) {
            if (fieldNumber == 1 && f.entry.mapKey.type == Type.STRING) {
                f.key = value;
            } else if (fieldNumber == 2 && !f.entryDone && f.entry.mapValue.type == Type.STRING) {
                if (isWrite(prepareEntry())) {
                    values.writeString(name, nameSize, value);
                }
                entryValueDone();
            }
            return;
        }
        FieldInfo field = select(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        if (field != null && field.type == Type.STRING && isWrite(prepare(field))) {
            values.writeString(name, nameSize, value);
        }
    }

    @Override
    public void writeBytes(int fieldNumber, ByteString value) {
        writeBytes(fieldNumber, value, null, 0, 0);
    }

    @Override
    public void writeByteArray(int fieldNumber, byte[] value) {
        writeBytes(fieldNumber, null, value, 0, value.length);
    }

    @Override
    public void writeByteArray(int fieldNumber, byte[] value, int offset, int length) {
        writeBytes(fieldNumber, null, value, offset, length);
    }

    @Override
    public void writeByteBuffer(int fieldNumber, ByteBuffer value) {
        byte[] bytes = new byte[value.remaining()];
        value.duplicate().get(bytes);
        writeBytes(fieldNumber, null, bytes, 0, bytes.length);
    }

    /**
     * Write a string or bytes value, given either as a ByteString or a byte array.
     */

    private void writeBytes(int fieldNumber, ByteString byteString, byte[] bytes, int offset, int length) {
        if (skipGroups > 0) {
            return;
        }
        Frame f = frame;
        if (f.entry != null) {
            if (fieldNumber == 1 && f.entry.mapKey.type == Type.STRING) {
                f.key = byteString != null ? byteString.toStringUtf8() : new String(bytes, offset, length, StandardCharsets.UTF_8);
            } else if (fieldNumber == 2 && !f.entryDone) {
                FieldInfo valueField = f.entry.mapValue;
                if (valueField.type == Type.STRING || valueField.type == Type.BYTES) {
                    if (isWrite(prepareEntry())) {
                        writeBytesValue(valueField, byteString, bytes, offset, length);
                    }
                    entryValueDone();
                }
            }
            return;
        }
        FieldInfo field = select(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        if (field != null && (field.type == Type.STRING || field.type == Type.BYTES) && isWrite(prepare(field))) {
            writeBytesValue(field, byteString, bytes, offset, length);
        }
    }

    private void writeBytesValue(FieldInfo field, ByteString byteString, byte[] bytes, int offset, int length) {
        if (byteString != null) {
            bytes = byteString.toByteArray();
            offset = 0;
            length = bytes.length;
        }
        if (field.type == Type.STRING) {
            // not yet decoded string
            values.writeUtf8(name, nameSize, field.validateUtf8, bytes, offset, length);
        } else {
            values.writeBase64(name, nameSize, bytes, offset, length);
        }
    }

    @Override
    public void writeMessage(int fieldNumber, MessageLite value) throws IOException {
        if (skipGroups > 0) {
            return;
        }
        Frame f = frame;
        if (f.entry != null) {
            FieldInfo valueField = f.entry.mapValue;
            if (fieldNumber == 2 && !f.entryDone && valueField.type == Type.MESSAGE) {
                writeMessageValue(valueField, value, prepareEntry());
                entryValueDone();
            }
            return;
        }
        FieldInfo field = select(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        if (field == null) {
            return;
        }
        if (field.map) {
            // map entry as a message
            push(null, field, false);
            value.writeTo(output);
            if (!frame.entryDone) {
                writeDefaultEntryValue(field.mapValue);
            }
            pop();
        } else if (field.type == Type.MESSAGE) {
            writeMessageValue(field, value, prepare(field));
        }
    }

    private void writeMessageValue(FieldInfo field, MessageLite value, int action) throws IOException {
        if (action == SKIP) {
            return;
        }
        if (action == PRUNE) {
            // not serialized at all
            values.writePruned(name, nameSize);
            return;
        }
        boolean anonymize = action == ANONYMIZE;
        MessagePlan plan = field.getMessagePlan();
        if (plan.wellKnownType != null) {
            if (value instanceof Message message) {
                transcoder.writeWellKnown(plan.wellKnownType, message, values, name, nameSize, anonymize);
            } else {
                byte[] bytes = value.toByteArray();
                transcoder.writeWellKnown(plan.wellKnownType, bytes, 0, bytes.length, values, name, nameSize, anonymize);
            }
        } else {
            push(plan, null, anonymize);
            writeObject(value, name, nameSize);
            pop();
        }
    }

    // ---- tags ----

    @Override
    public void writeTag(int fieldNumber, int wireType) {
        switch (wireType) {
            case WireFormat.WIRETYPE_START_GROUP -> startGroup(fieldNumber);
            case WireFormat.WIRETYPE_END_GROUP -> endGroup();
            default -> {
                if (skipGroups == 0) {
                    // value to follow
                    Frame f = frame;
                    f.pendingNumber = fieldNumber;
                    f.pendingWireType = wireType;
                }
            }
        }
    }

    private void startGroup(int fieldNumber) {
        if (skipGroups > 0 || frame.entry != null) {
            skipGroups++;
            return;
        }
        FieldInfo field = select(fieldNumber, WireFormat.WIRETYPE_START_GROUP);
        if (field == null) {
            skipGroups = 1;
            return;
        }
        int action = prepare(field);
        if (action == SKIP || action == PRUNE) {
            if (action == PRUNE) {
                values.writePruned(name, nameSize);
            }
            skipGroups = 1;
            return;
        }
        SerializableString groupName = name;
        int groupNameSize = nameSize;
        push(field.getMessagePlan(), null, action == ANONYMIZE);
        if (!values.startObject(groupName, groupNameSize)) {
            // max size reached
            pop();
            skipGroups = 1;
        }
    }

    private void endGroup() {
        if (skipGroups > 0) {
            skipGroups--;
            return;
        }
        closeOpen();
        values.endObject();
        pop();
    }

    /**
     * A length-delimited tag followed by a length: a map entry or a packed field. The object of a map field is
     * started when an entry is written.
     */

    private void startLengthDelimited(int fieldNumber) {
        Frame f = frame;
        FieldInfo field = f.entry == null ? select(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED) : null;
        if (field != null && field.map) {
            // map entry, written as tag, length, key and value
            push(null, field, false);
            frame.entryTagged = true;
            frame.expectEntryLength = true;
        } else {
            f.packed = field != null && field.packable ? field : null;
            f.expectPackedLength = true;
        }
    }

    // ---- untagged values ----

    @Override
    public void writeUInt32NoTag(int value) throws IOException {
        if (skipGroups > 0) {
            return;
        }
        Frame f = frame;
        if (f.expectEntryLength) {
            f.expectEntryLength = false;
        } else if (f.packedRemaining > 0) {
            packedVarint(value & 0xFFFFFFFFL, CodedOutputStream.computeUInt32SizeNoTag(value));
        } else if (f.expectPackedLength) {
            f.expectPackedLength = false;
            f.packedRemaining = value;
        } else if (f.pendingNumber != 0) {
            int wireType = f.pendingWireType;
            int number = takePending();
            if (wireType == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
                startLengthDelimited(number);
                writeUInt32NoTag(value); // the length
            } else {
                varint(number, value & 0xFFFFFFFFL);
            }
        } else if (WireFormat.getTagWireType(value) == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
            // tag of a packed field or map entry, written by generated code
            startLengthDelimited(WireFormat.getTagFieldNumber(value));
        }
    }

    private int takePending() {
        Frame f = frame;
        int number = f.pendingNumber;
        f.pendingNumber = 0;
        return number;
    }

    @Override
    public void writeInt32NoTag(int value) {
        if (skipGroups > 0) {
            return;
        }
        Frame f = frame;
        if (f.packedRemaining > 0) {
            packedVarint(value, CodedOutputStream.computeInt32SizeNoTag(value));
        } else if (f.pendingNumber != 0) {
            varint(takePending(), value);
        }
    }

    @Override
    public void writeUInt64NoTag(long value) {
        if (skipGroups > 0) {
            return;
        }
        Frame f = frame;
        if (f.packedRemaining > 0) {
            packedVarint(value, CodedOutputStream.computeUInt64SizeNoTag(value));
        } else if (f.pendingNumber != 0) {
            varint(takePending(), value);
        }
    }

    @Override
    public void write(byte value) {
        // writeBoolNoTag
        if (skipGroups > 0) {
            return;
        }
        Frame f = frame;
        if (f.packedRemaining > 0) {
            packedVarint(value, 1);
        } else if (f.pendingNumber != 0) {
            varint(takePending(), value);
        }
    }

    private void packedVarint(long value, int size) {
        Frame f = frame;
        f.packedRemaining -= size;
        FieldInfo field = f.packed;
        if (field == null || (field.closedEnum && !isKnown(field, value))) {
            return;
        }
        if (isWrite(prepare(field))) {
            values.writeVarint(null, 0, field, value);
        }
    }

    @Override
    public void writeFixed32NoTag(int value) {
        if (skipGroups > 0) {
            return;
        }
        Frame f = frame;
        if (f.packedRemaining > 0) {
            f.packedRemaining -= 4;
            if (f.packed != null && isWrite(prepare(f.packed))) {
                values.writeFixed32(null, 0, f.packed, value);
            }
        } else if (f.pendingNumber != 0) {
            writeFixed32(takePending(), value);
        }
    }

    @Override
    public void writeFixed64NoTag(long value) {
        if (skipGroups > 0) {
            return;
        }
        Frame f = frame;
        if (f.packedRemaining > 0) {
            f.packedRemaining -= 8;
            if (f.packed != null && isWrite(prepare(f.packed))) {
                values.writeFixed64(null, 0, f.packed, value);
            }
        } else if (f.pendingNumber != 0) {
            writeFixed64(takePending(), value);
        }
    }

    @Override
    public void writeStringNoTag(String value) {
        if (skipGroups == 0 && frame.pendingNumber != 0) {
            writeString(takePending(), value);
        }
    }

    @Override
    public void writeBytesNoTag(ByteString value) {
        if (skipGroups == 0 && frame.pendingNumber != 0) {
            writeBytes(takePending(), value);
        }
    }

    @Override
    public void writeByteArrayNoTag(byte[] value, int offset, int length) {
        if (skipGroups == 0 && frame.pendingNumber != 0) {
            writeByteArray(takePending(), value, offset, length);
        }
    }

    @Override
    public void writeMessageNoTag(MessageLite value) throws IOException {
        if (skipGroups == 0 && frame.pendingNumber != 0) {
            writeMessage(takePending(), value);
        }
    }

    // ---- not used by generated code for regular fields ----

    @Override
    public void writeRawBytes(ByteBuffer value) {
    }

    @Override
    public void writeMessageSetExtension(int fieldNumber, MessageLite value) {
    }

    @Override
    public void writeRawMessageSetExtension(int fieldNumber, ByteString value) {
    }

    @Override
    public void write(byte[] value, int offset, int length) {
    }

    @Override
    public void writeLazy(byte[] value, int offset, int length) {
    }

    @Override
    public void write(ByteBuffer value) {
    }

    @Override
    public void writeLazy(ByteBuffer value) {
    }

    @Override
    public void flush() {
    }

    @Override
    public int spaceLeft() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getTotalBytesWritten() {
        return 0;
    }
}
