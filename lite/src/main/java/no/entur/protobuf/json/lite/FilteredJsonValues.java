package no.entur.protobuf.json.lite;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.google.protobuf.ByteString;
import no.entur.protobuf.json.filter.FilterContext;
import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.filter.JsonOutputSize;
import no.entur.protobuf.json.filter.MaxStringLength;
import no.entur.protobuf.json.path.FilterType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Writes JSON values for a single filtered write, with a Jackson 2 generator. Every token is checked with the
 * {@linkplain FilterContext} before it is written, so a field name is written together with its value, and nothing
 * is written once max size is reached. Long strings are truncated, but not strings which are not text: 64-bit integers,
 * enum names, timestamps, durations, and NaN and infinity.
 * <br><br>
 * The methods take the pending field name, either pre-encoded (a {@linkplain SerializableString}, for fields) or a
 * {@linkplain String} (map and struct keys), or null for array elements and the root value, and its size; they return
 * false if writing stopped because of max size.
 * <br><br>
 * Not thread-safe.
 */

final class FilteredJsonValues {

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

    FilteredJsonValues(JsonFilter filter, JsonGenerator generator) {
        this.generator = generator;
        this.filter = filter;
        this.utf8 = LiteJsonValues.isUtf8(generator);
        this.context = filter.newContext(utf8, utf8 && LiteJsonValues.isEscapeSurrogates(generator));

        this.sizing = context.isSizing();
        this.paths = context.hasPaths();
        this.maxStringLength = filter.getMaxStringLength();
        this.truncateMessageLength = filter.getTruncateMessageLength();

        this.anonymizeRawJsonValue = filter.getAnonymizeRawJsonValue();
        this.pruneRawJsonValue = filter.getPruneRawJsonValue();
        this.anonymizeSize = context.getAnonymizeSize();
        this.pruneSize = context.getPruneSize();
    }

    FilterContext getContext() {
        return context;
    }

    boolean isStopped() {
        return context.isStopped();
    }

    // ---- names and paths ----

    int getNameSize(LiteFieldInfo field) {
        return sizing ? field.getNameSize(utf8) : 0;
    }

    int getNameSize(String name) {
        return sizing ? context.getNameSize(name) : 0;
    }

    FilterType match(LiteFieldInfo field) {
        return paths ? context.matchName(field.jsonName) : null;
    }

    FilterType match(String name) {
        return paths ? context.matchName(name) : null;
    }

    private void writeName(Object name) throws IOException {
        if (name == null) {
            return;
        }
        if (name instanceof SerializableString) {
            generator.writeFieldName((SerializableString) name);
        } else {
            generator.writeFieldName((String) name);
        }
    }

    // ---- objects and arrays ----

    boolean startObject(Object name, int nameSize) throws IOException {
        if (!context.startObject(nameSize)) {
            return false;
        }
        writeName(name);
        generator.writeStartObject();
        return true;
    }

    void endObject() throws IOException {
        context.endObject();
        generator.writeEndObject();
    }

    boolean startArray(Object name, int nameSize) throws IOException {
        if (!context.startArray(nameSize)) {
            return false;
        }
        writeName(name);
        generator.writeStartArray();
        return true;
    }

    void endArray() throws IOException {
        context.endArray();
        generator.writeEndArray();
    }

    // ---- anonymized and pruned values ----

    boolean writeAnonymized(Object name, int nameSize) throws IOException {
        if (!context.scalar(nameSize, anonymizeSize)) {
            return false;
        }
        writeName(name);
        generator.writeRawValue(anonymizeRawJsonValue);
        return true;
    }

    boolean writePruned(Object name, int nameSize) throws IOException {
        if (!context.scalar(nameSize, pruneSize)) {
            return false;
        }
        writeName(name);
        generator.writeRawValue(pruneRawJsonValue);
        return true;
    }

    /**
     * Write an anonymized or pruned value.
     *
     * @param type filter type, not null
     * @return false if writing stopped
     */

    boolean writeFiltered(Object name, int nameSize, FilterType type) throws IOException {
        if (type == FilterType.PRUNE) {
            return writePruned(name, nameSize);
        }
        return writeAnonymized(name, nameSize);
    }

    // ---- strings ----

    boolean writeString(Object name, int nameSize, String value) throws IOException {
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
        generator.writeString(value);
        return true;
    }

    /**
     * Write a string which is not text as is, as max string length does not apply to it: 64-bit integers, enum names,
     * timestamps, durations, and NaN and infinity.
     */

    boolean writeUntruncated(Object name, int nameSize, String value) throws IOException {
        if (!context.scalar(nameSize, sizing ? context.getQuotedSize(value) : 0)) {
            return false;
        }
        writeName(name);
        generator.writeString(value);
        return true;
    }

    /**
     * Write a pre-encoded string which is not text as is, i.e. an enum name.
     */

    boolean writeUntruncated(Object name, int nameSize, SerializableString value) throws IOException {
        if (!context.scalar(nameSize, sizing ? context.getQuotedSize(value.getValue()) : 0)) {
            return false;
        }
        writeName(name);
        generator.writeString(value);
        return true;
    }

    /**
     * Write bytes as base64. A truncated value only encodes the bytes which are kept.
     */

    boolean writeBase64(Object name, int nameSize, ByteString value) throws IOException {
        int length = value.size();
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
                ByteBuffer encoded = LiteJsonValues.encodeBase64(value.substring(0, encodeLength));
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
        LiteJsonValues.writeBase64(generator, value, utf8);
        return true;
    }

    // ---- numbers, booleans and null ----

    boolean writeNumber(Object name, int nameSize, int value) throws IOException {
        if (!context.scalar(nameSize, sizing ? JsonOutputSize.getSize(value) : 0)) {
            return false;
        }
        writeName(name);
        generator.writeNumber(value);
        return true;
    }

    boolean writeNumber(Object name, int nameSize, long value) throws IOException {
        if (!context.scalar(nameSize, sizing ? JsonOutputSize.getSize(value) : 0)) {
            return false;
        }
        writeName(name);
        generator.writeNumber(value);
        return true;
    }

    boolean writeFloat(Object name, int nameSize, float value) throws IOException {
        if (!LiteJsonValues.isFinite(value)) {
            return writeUntruncated(name, nameSize, LiteJsonValues.toString(value));
        }
        if (!context.scalar(nameSize, sizing ? Float.toString(value).length() : 0)) {
            return false;
        }
        writeName(name);
        generator.writeNumber(value);
        return true;
    }

    boolean writeDouble(Object name, int nameSize, double value) throws IOException {
        if (!LiteJsonValues.isFinite(value)) {
            return writeUntruncated(name, nameSize, LiteJsonValues.toString(value));
        }
        if (!context.scalar(nameSize, sizing ? Double.toString(value).length() : 0)) {
            return false;
        }
        writeName(name);
        generator.writeNumber(value);
        return true;
    }

    boolean writeBoolean(Object name, int nameSize, boolean value) throws IOException {
        if (!context.scalar(nameSize, value ? JsonOutputSize.TRUE : JsonOutputSize.FALSE)) {
            return false;
        }
        writeName(name);
        generator.writeBoolean(value);
        return true;
    }

    boolean writeNull(Object name, int nameSize) throws IOException {
        if (!context.scalar(nameSize, JsonOutputSize.NULL)) {
            return false;
        }
        writeName(name);
        generator.writeNull();
        return true;
    }

    boolean writeEnum(Object name, int nameSize, LiteEnumNames names, int number) throws IOException {
        SerializableString value = names.getName(number);
        if (value != null) {
            return writeUntruncated(name, nameSize, value);
        }
        return writeNumber(name, nameSize, number);
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
