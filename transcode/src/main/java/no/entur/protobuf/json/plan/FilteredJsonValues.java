package no.entur.protobuf.json.plan;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.UnsafeByteOperations;
import no.entur.protobuf.json.filter.FilterContext;
import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.filter.JsonOutputSize;
import no.entur.protobuf.json.filter.MaxStringLength;
import no.entur.protobuf.json.path.FilterType;
import no.entur.protobuf.json.plan.MessagePlan.EnumNames;
import no.entur.protobuf.json.plan.MessagePlan.FieldInfo;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Writes JSON values for a single filtered write. Every token is checked with the {@linkplain FilterContext} before it is written,
 * so a field name is written together with its value, and nothing is written once max size is reached. Long strings are truncated,
 * also when written directly from UTF-8 bytes, but not strings which are not text: 64-bit integers, enum names, timestamps,
 * durations, and NaN and infinity (see {@linkplain #writeUntruncated(SerializableString, int, String)}).
 * <br><br>
 * The methods take the pending field name (or null for array elements and the root value) and its size
 * (see {@linkplain #getNameSize(FieldInfo)}), and return false if writing stopped because of max size.
 * <br><br>
 * Internal; shared by the filtering JSON writer implementations. Not thread-safe.
 */

public final class FilteredJsonValues {

    public static final SerializableString TYPE_NAME = new SerializedString("@type");
    public static final SerializableString VALUE_NAME = new SerializedString("value");

    private static final String NAN = "NaN";
    private static final String INFINITY = "Infinity";
    private static final String NEGATIVE_INFINITY = "-Infinity";

    private final JsonGenerator generator;
    private final JsonFilter filter;
    private final FilterContext context;

    private final boolean utf8;
    private final boolean sizing;
    private final boolean paths;
    private final int maxStringLength;
    private final int truncateMessageLength;

    private final String anonymizeRawJsonValue;
    private final String pruneRawJsonValue;
    private final int anonymizeSize;
    private final int pruneSize;

    private char[] charBuffer;
    private byte[] byteBuffer;

    public FilteredJsonValues(JsonFilter filter, JsonGenerator generator) {
        this.generator = generator;
        this.filter = filter;
        this.utf8 = JsonValues.isUtf8(generator);
        this.context = filter.newContext(utf8);

        this.sizing = context.isSizing();
        this.paths = context.hasPaths();
        this.maxStringLength = filter.getMaxStringLength();
        this.truncateMessageLength = filter.getTruncateMessageLength();

        this.anonymizeRawJsonValue = filter.getAnonymizeRawJsonValue();
        this.pruneRawJsonValue = filter.getPruneRawJsonValue();
        this.anonymizeSize = context.getAnonymizeSize();
        this.pruneSize = context.getPruneSize();
    }

    public JsonGenerator getGenerator() {
        return generator;
    }

    public FilterContext getContext() {
        return context;
    }

    public boolean isUtf8() {
        return utf8;
    }

    /**
     * Whether writing stopped because of max size.
     *
     * @return true if only closing brackets may be written
     */

    public boolean isStopped() {
        return context.isStopped();
    }

    // ---- names and paths ----

    public int getNameSize(FieldInfo field) {
        return sizing ? field.getNameSize(utf8) : 0;
    }

    public int getNameSize(SerializableString name) {
        return sizing ? context.getNameSize(name.getValue()) : 0;
    }

    /**
     * Match a field name in the current object.
     *
     * @param field field
     * @return prune, anonymize, or null
     */

    public FilterType match(FieldInfo field) {
        return paths ? context.matchName(field.jsonName) : null;
    }

    public FilterType match(String name) {
        return paths ? context.matchName(name) : null;
    }

    public FilterType match(SerializableString name) {
        return paths ? context.matchName(name.getValue()) : null;
    }

    private void writeName(SerializableString name) {
        if (name != null) {
            generator.writeName(name);
        }
    }

    // ---- objects and arrays ----

    public boolean startObject(SerializableString name, int nameSize) {
        if (!context.startObject(nameSize)) {
            return false;
        }
        writeName(name);
        generator.writeStartObject();
        return true;
    }

    public void endObject() {
        context.endObject();
        generator.writeEndObject();
    }

    public boolean startArray(SerializableString name, int nameSize) {
        if (!context.startArray(nameSize)) {
            return false;
        }
        writeName(name);
        generator.writeStartArray();
        return true;
    }

    public void endArray() {
        context.endArray();
        generator.writeEndArray();
    }

    // ---- anonymized and pruned values ----

    public boolean writeAnonymized(SerializableString name, int nameSize) {
        if (!context.scalar(nameSize, anonymizeSize)) {
            return false;
        }
        writeName(name);
        generator.writeRawValue(anonymizeRawJsonValue);
        return true;
    }

    public boolean writePruned(SerializableString name, int nameSize) {
        if (!context.scalar(nameSize, pruneSize)) {
            return false;
        }
        writeName(name);
        generator.writeRawValue(pruneRawJsonValue);
        return true;
    }

    /**
     * Write an anonymized or pruned value, if the type says so.
     *
     * @param name     field name, or null
     * @param nameSize size of the field name
     * @param type     filter type, or null
     * @return true if the value was written, false if it was not (type is null) or writing stopped
     */

    public boolean writeFiltered(SerializableString name, int nameSize, FilterType type) {
        if (type == FilterType.PRUNE) {
            return writePruned(name, nameSize);
        }
        if (type == FilterType.ANON) {
            return writeAnonymized(name, nameSize);
        }
        return false;
    }

    // ---- strings ----

    /**
     * Write a string, truncated if it is too long.
     */

    public boolean writeString(SerializableString name, int nameSize, String value) {
        int length = value.length();
        if (length > maxStringLength) {
            int keep = MaxStringLength.getTruncatedLength(value, maxStringLength, truncateMessageLength);
            if (keep != -1) {
                int removed = length - keep;
                if (!context.scalar(nameSize, sizing ? context.getTruncatedSize(value, keep, removed) : 0)) {
                    return false;
                }
                writeName(name);
                char[] message = filter.getTruncateMessageChars();
                char[] buffer = getCharBuffer(keep + message.length + 10);
                int bufferLength = MaxStringLength.fill(value, keep, message, removed, buffer);
                generator.writeString(buffer, 0, bufferLength);
                return true;
            }
        }
        if (!context.scalar(nameSize, sizing ? context.getQuotedSize(value) : 0)) {
            return false;
        }
        writeName(name);
        JsonValues.writeString(generator, value);
        return true;
    }

    /**
     * Write a string which is not text as is, as max string length does not apply to it: 64-bit integers, enum names,
     * timestamps, durations, and NaN and infinity.
     */

    public boolean writeUntruncated(SerializableString name, int nameSize, String value) {
        if (!context.scalar(nameSize, sizing ? context.getQuotedSize(value) : 0)) {
            return false;
        }
        writeName(name);
        JsonValues.writeString(generator, value);
        return true;
    }

    /**
     * Write a pre-encoded string which is not text as is, i.e. an enum name.
     */

    public boolean writeUntruncated(SerializableString name, int nameSize, SerializableString value) {
        if (!context.scalar(nameSize, sizing ? context.getQuotedSize(value.getValue()) : 0)) {
            return false;
        }
        writeName(name);
        generator.writeString(value);
        return true;
    }

    /**
     * Write a string from its UTF-8 bytes, without decoding if possible.
     *
     * @param validateUtf8 whether the bytes might not be valid UTF-8
     */

    public boolean writeUtf8(SerializableString name, int nameSize, boolean validateUtf8, byte[] bytes, int offset, int length) {
        if (!utf8 || (validateUtf8 && !UnsafeByteOperations.unsafeWrap(bytes, offset, length).isValidUtf8())) {
            // decoding replaces malformed input, like protobuf does when decoding to a String
            return writeString(name, nameSize, new String(bytes, offset, length, StandardCharsets.UTF_8));
        }
        if (length > maxStringLength) {
            long truncated = MaxStringLength.getTruncatedUtf8(bytes, offset, length, maxStringLength, truncateMessageLength);
            if (truncated != -1) {
                int keepBytes = MaxStringLength.getKeepBytes(truncated);
                int removed = MaxStringLength.getRemovedCharacters(truncated);
                if (!context.scalar(nameSize, sizing ? context.getTruncatedUtf8Size(bytes, offset, keepBytes, removed) : 0)) {
                    return false;
                }
                writeName(name);
                byte[] message = filter.getTruncateMessageUtf8();
                byte[] buffer = getByteBuffer(keepBytes + message.length + 10);
                int bufferLength = MaxStringLength.fill(bytes, offset, keepBytes, message, removed, buffer);
                generator.writeUTF8String(buffer, 0, bufferLength);
                return true;
            }
        }
        if (!context.scalar(nameSize, sizing ? context.getQuotedUtf8Size(bytes, offset, length) : 0)) {
            return false;
        }
        writeName(name);
        generator.writeUTF8String(bytes, offset, length);
        return true;
    }

    /**
     * Write bytes as base64. A truncated value only encodes the bytes which are kept.
     */

    public boolean writeBase64(SerializableString name, int nameSize, byte[] bytes, int offset, int length) {
        int base64Length = MaxStringLength.getBase64Length(length);
        if (base64Length > maxStringLength) {
            // base64 is ASCII, so the string never needs escaping, and there are no surrogates
            int removed = base64Length - maxStringLength;
            if (MaxStringLength.isTruncated(removed, truncateMessageLength)) {
                int keep = maxStringLength;
                if (!context.scalar(nameSize, sizing ? keep + 2 + filter.getTruncateMessageSize(utf8) + MaxStringLength.lengthToDigits(removed) : 0)) {
                    return false;
                }
                writeName(name);

                int encodeLength = Math.min(length, ((keep + 3) / 4) * 3);
                ByteBuffer encoded = Base64.getEncoder().encode(ByteBuffer.wrap(bytes, offset, encodeLength));
                int encodedOffset = encoded.arrayOffset() + encoded.position();
                if (utf8) {
                    byte[] message = filter.getTruncateMessageUtf8();
                    byte[] buffer = getByteBuffer(keep + message.length + 10);
                    int bufferLength = MaxStringLength.fill(encoded.array(), encodedOffset, keep, message, removed, buffer);
                    generator.writeUTF8String(buffer, 0, bufferLength);
                } else {
                    String kept = new String(encoded.array(), encodedOffset, keep, StandardCharsets.ISO_8859_1);
                    char[] message = filter.getTruncateMessageChars();
                    char[] buffer = getCharBuffer(keep + message.length + 10);
                    int bufferLength = MaxStringLength.fill(kept, keep, message, removed, buffer);
                    generator.writeString(buffer, 0, bufferLength);
                }
                return true;
            }
        }
        if (!context.scalar(nameSize, base64Length + 2)) {
            return false;
        }
        writeName(name);
        JsonValues.writeBase64(generator, bytes, offset, length);
        return true;
    }

    // ---- numbers, booleans and null ----

    public boolean writeNumber(SerializableString name, int nameSize, int value) {
        if (!context.scalar(nameSize, sizing ? JsonOutputSize.getSize(value) : 0)) {
            return false;
        }
        writeName(name);
        generator.writeNumber(value);
        return true;
    }

    public boolean writeNumber(SerializableString name, int nameSize, long value) {
        if (!context.scalar(nameSize, sizing ? JsonOutputSize.getSize(value) : 0)) {
            return false;
        }
        writeName(name);
        generator.writeNumber(value);
        return true;
    }

    public boolean writeFloat(SerializableString name, int nameSize, float value) {
        if (Float.isNaN(value)) {
            return writeUntruncated(name, nameSize, NAN);
        }
        if (Float.isInfinite(value)) {
            return writeUntruncated(name, nameSize, value > 0 ? INFINITY : NEGATIVE_INFINITY);
        }
        if (!context.scalar(nameSize, sizing ? Float.toString(value).length() : 0)) {
            return false;
        }
        writeName(name);
        generator.writeNumber(value);
        return true;
    }

    public boolean writeDouble(SerializableString name, int nameSize, double value) {
        if (Double.isNaN(value)) {
            return writeUntruncated(name, nameSize, NAN);
        }
        if (Double.isInfinite(value)) {
            return writeUntruncated(name, nameSize, value > 0 ? INFINITY : NEGATIVE_INFINITY);
        }
        if (!context.scalar(nameSize, sizing ? Double.toString(value).length() : 0)) {
            return false;
        }
        writeName(name);
        generator.writeNumber(value);
        return true;
    }

    public boolean writeBoolean(SerializableString name, int nameSize, boolean value) {
        if (!context.scalar(nameSize, value ? JsonOutputSize.TRUE : JsonOutputSize.FALSE)) {
            return false;
        }
        writeName(name);
        generator.writeBoolean(value);
        return true;
    }

    public boolean writeNull(SerializableString name, int nameSize) {
        if (!context.scalar(nameSize, JsonOutputSize.NULL)) {
            return false;
        }
        writeName(name);
        generator.writeNull();
        return true;
    }

    public boolean writeEnum(SerializableString name, int nameSize, EnumNames names, int number) {
        if (names.isNullValue()) {
            return writeNull(name, nameSize);
        }
        SerializableString value = names.getName(number);
        if (value != null) {
            return writeUntruncated(name, nameSize, value);
        }
        return writeNumber(name, nameSize, number);
    }

    // ---- values from the wire format, like JsonValues ----

    public boolean writeVarint(SerializableString name, int nameSize, FieldInfo field, long raw) {
        return switch (field.type) {
            case INT32 -> writeNumber(name, nameSize, (int) raw);
            case UINT32 -> writeNumber(name, nameSize, raw & 0xFFFFFFFFL);
            case SINT32 -> writeNumber(name, nameSize, CodedInputStream.decodeZigZag32((int) raw));
            case INT64 -> writeUntruncated(name, nameSize, Long.toString(raw));
            case UINT64 -> writeUntruncated(name, nameSize, Long.toUnsignedString(raw));
            case SINT64 -> writeUntruncated(name, nameSize, Long.toString(CodedInputStream.decodeZigZag64(raw)));
            case BOOL -> writeBoolean(name, nameSize, raw != 0);
            case ENUM -> writeEnum(name, nameSize, field.enumNames, (int) raw);
            default -> throw new IllegalStateException("Unexpected varint field type " + field.type);
        };
    }

    public boolean writeFixed32(SerializableString name, int nameSize, FieldInfo field, int raw) {
        return switch (field.type) {
            case FIXED32 -> writeNumber(name, nameSize, raw & 0xFFFFFFFFL);
            case SFIXED32 -> writeNumber(name, nameSize, raw);
            case FLOAT -> writeFloat(name, nameSize, Float.intBitsToFloat(raw));
            default -> throw new IllegalStateException("Unexpected fixed32 field type " + field.type);
        };
    }

    public boolean writeFixed64(SerializableString name, int nameSize, FieldInfo field, long raw) {
        return switch (field.type) {
            case FIXED64 -> writeUntruncated(name, nameSize, Long.toUnsignedString(raw));
            case SFIXED64 -> writeUntruncated(name, nameSize, Long.toString(raw));
            case DOUBLE -> writeDouble(name, nameSize, Double.longBitsToDouble(raw));
            default -> throw new IllegalStateException("Unexpected fixed64 field type " + field.type);
        };
    }

    private char[] getCharBuffer(int length) {
        char[] buffer = charBuffer;
        if (buffer == null || buffer.length < length) {
            buffer = new char[Math.max(length, 64)];
            charBuffer = buffer;
        }
        return buffer;
    }

    private byte[] getByteBuffer(int length) {
        byte[] buffer = byteBuffer;
        if (buffer == null || buffer.length < length) {
            buffer = new byte[Math.max(length, 64)];
            byteBuffer = buffer;
        }
        return buffer;
    }
}
