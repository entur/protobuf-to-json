package no.entur.protobuf.json.stream;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location;
import com.google.protobuf.DescriptorProtos.UninterpretedOption;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import com.google.protobuf.UnsafeByteOperations;
import com.google.protobuf.Value;
import com.google.protobuf.WireFormat;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CodedOutputStreamConventionsTest {

    private static final Location PACKED = Location.newBuilder().addPath(1).addPath(128).setLeadingComments("x").build();
    private static final int PATH = Location.PATH_FIELD_NUMBER;
    private static final int LEADING_COMMENTS = Location.LEADING_COMMENTS_FIELD_NUMBER;

    private static final UninterpretedOption.NamePart NAME_PART = UninterpretedOption.NamePart.newBuilder().setNamePart("a").setIsExtension(true).build();
    private static final byte[] BYTES = {0, 1, 2, (byte) 0xFF};
    private static final UninterpretedOption OPTION = UninterpretedOption.newBuilder().addName(NAME_PART).setStringValue(ByteString.copyFrom(BYTES)).build();
    private static final int NAME = UninterpretedOption.NAME_FIELD_NUMBER;
    private static final int STRING_VALUE = UninterpretedOption.STRING_VALUE_FIELD_NUMBER;

    private static final Value NUMBER = Value.newBuilder().setNumberValue(1).build();
    private static final Struct MAP = Struct.newBuilder().putFields("k", NUMBER).build();

    @Test
    public void writesTheProbesOfThisProtobufVersion() throws Exception {
        for (Message probe : CodedOutputStreamConventions.probes()) {
            CodedOutputStreamConventions.verify(probe, probe);
            CodedOutputStreamConventions.verify(probe, DynamicMessage.parseFrom(probe.getDescriptorForType(), probe.toByteString()));
        }
    }

    @Test
    public void acceptsPackedValues() throws Exception {
        // as generated code: the tag and length written as untagged varints
        assertAccepted(PACKED, output -> {
            writePacked(output);
            output.writeString(LEADING_COMMENTS, "x");
        });
        // as the reflection-based serialization: writeTag then the length
        assertAccepted(PACKED, output -> {
            output.writeTag(PATH, WireFormat.WIRETYPE_LENGTH_DELIMITED);
            output.writeUInt32NoTag(3);
            output.writeInt32NoTag(1);
            output.writeInt32NoTag(128);
            output.writeTag(LEADING_COMMENTS, WireFormat.WIRETYPE_LENGTH_DELIMITED);
            output.writeStringNoTag("x");
        });
    }

    @Test
    public void acceptsMapEntriesWrittenAsMessages() throws Exception {
        // protobuf-java 4.32 to 4.35
        assertAccepted(MAP, output -> output.writeMessage(Struct.FIELDS_FIELD_NUMBER, entry("k", NUMBER)));
    }

    @Test
    public void acceptsMapEntriesWrittenAsTagLengthKeyAndValue() throws Exception {
        // protobuf-java 4.36
        assertAccepted(MAP, output -> {
            output.writeTag(Struct.FIELDS_FIELD_NUMBER, WireFormat.WIRETYPE_LENGTH_DELIMITED);
            output.writeUInt32NoTag(CodedOutputStream.computeStringSize(1, "k") + CodedOutputStream.computeMessageSize(2, NUMBER));
            output.writeString(1, "k");
            output.writeMessage(2, NUMBER);
        });
    }

    @Test
    public void acceptsStringsWrittenAsUtf8Bytes() throws Exception {
        byte[] utf8 = "x".getBytes(StandardCharsets.UTF_8);
        assertAccepted(PACKED, output -> {
            writePacked(output);
            output.writeBytes(LEADING_COMMENTS, ByteString.copyFrom(utf8));
        });
        assertAccepted(PACKED, output -> {
            writePacked(output);
            output.writeByteArray(LEADING_COMMENTS, utf8);
        });
        assertAccepted(PACKED, output -> {
            writePacked(output);
            output.writeTag(LEADING_COMMENTS, WireFormat.WIRETYPE_LENGTH_DELIMITED);
            output.writeByteArrayNoTag(utf8);
        });
    }

    @Test
    public void acceptsBytesWrittenAsByteBuffer() throws Exception {
        assertAccepted(OPTION, output -> {
            output.writeMessage(NAME, NAME_PART);
            output.writeByteBuffer(STRING_VALUE, ByteBuffer.wrap(BYTES));
        });
    }

    @Test
    public void acceptsNestedMessagesWrittenAsTagThenMessage() throws Exception {
        // the sinks write the fields of nested messages, so they are checked too
        assertAccepted(OPTION, output -> {
            output.writeTag(NAME, WireFormat.WIRETYPE_LENGTH_DELIMITED);
            output.writeMessageNoTag(NAME_PART);
            output.writeBytes(STRING_VALUE, ByteString.copyFrom(BYTES));
        });
    }

    @Test
    public void rejectsFieldsWrittenAsRawBytes() {
        byte[] raw = {1, (byte) 0x80, 1};
        assertRejected(PACKED, output -> {
            output.writeUInt32NoTag(tag(PATH, WireFormat.WIRETYPE_LENGTH_DELIMITED));
            output.writeUInt32NoTag(raw.length);
            output.writeRawBytes(raw);
            output.writeString(LEADING_COMMENTS, "x");
        });
        assertRejected(PACKED, output -> {
            output.writeUInt32NoTag(tag(PATH, WireFormat.WIRETYPE_LENGTH_DELIMITED));
            output.writeUInt32NoTag(raw.length);
            UnsafeByteOperations.unsafeWriteTo(UnsafeByteOperations.unsafeWrap(raw), output); // writeLazy
            output.writeString(LEADING_COMMENTS, "x");
        });
        assertRejected(PACKED, output -> {
            output.writeUInt32NoTag(tag(PATH, WireFormat.WIRETYPE_LENGTH_DELIMITED));
            output.writeUInt32NoTag(raw.length);
            output.writeRawBytes(ByteBuffer.wrap(raw));
            output.writeString(LEADING_COMMENTS, "x");
        });
    }

    @Test
    public void rejectsFieldsWrittenAsMessageSetExtensions() {
        assertRejected(OPTION, output -> {
            output.writeMessageSetExtension(NAME, NAME_PART);
            output.writeBytes(STRING_VALUE, ByteString.copyFrom(BYTES));
        });
    }

    @Test
    public void rejectsPackedValuesWhichDoNotFollowTheLength() {
        assertRejected(PACKED, output -> {
            output.writeUInt32NoTag(tag(PATH, WireFormat.WIRETYPE_LENGTH_DELIMITED));
            output.writeUInt32NoTag(3);
            output.writeInt32NoTag(1);
            output.writeString(LEADING_COMMENTS, "x");
            output.writeInt32NoTag(128);
        });
    }

    @Test
    public void rejectsPackedValuesWithoutTag() {
        // the length is not preceded by a tag; a value equal to the tag follows
        Location probe = Location.newBuilder().addPath(10).addPath(1).build();
        assertRejected(probe, output -> {
            output.writeUInt32NoTag(2);
            output.writeInt32NoTag(10);
            output.writeInt32NoTag(1);
        });
    }

    @Test
    public void rejectsNoTagValuesAfterAnEncodedTag() {
        // the sinks take the field number of a NoTag value from writeTag, not from a tag written as an untagged varint
        int stringTag = tag(LEADING_COMMENTS, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        assertRejected(PACKED, output -> {
            writePacked(output);
            output.writeUInt32NoTag(stringTag);
            output.writeStringNoTag("x");
        });
        assertRejected(PACKED, output -> {
            writePacked(output);
            output.writeUInt32NoTag(stringTag);
            output.writeBytesNoTag(ByteString.copyFromUtf8("x"));
        });
        assertRejected(PACKED, output -> {
            writePacked(output);
            output.writeUInt32NoTag(stringTag);
            output.writeByteArrayNoTag("x".getBytes(StandardCharsets.UTF_8));
        });
        assertRejected(OPTION, output -> {
            output.writeUInt32NoTag(tag(NAME, WireFormat.WIRETYPE_LENGTH_DELIMITED));
            output.writeMessageNoTag(NAME_PART);
            output.writeBytes(STRING_VALUE, ByteString.copyFrom(BYTES));
        });
        assertRejected(OPTION, output -> {
            output.writeMessage(NAME, NAME_PART);
            output.writeUInt32NoTag(tag(STRING_VALUE, WireFormat.WIRETYPE_LENGTH_DELIMITED));
            output.writeBytesNoTag(ByteString.copyFrom(BYTES));
        });
    }

    @Test
    public void rejectsBoolsWrittenAsRawBytes() {
        // write(byte) is writeBoolNoTag
        UninterpretedOption.NamePart probe = UninterpretedOption.NamePart.newBuilder().setNamePart("a").setIsExtension(true).build();
        assertRejected(probe, output -> {
            output.writeString(UninterpretedOption.NamePart.NAME_PART_FIELD_NUMBER, "a");
            output.writeRawByte(tag(UninterpretedOption.NamePart.IS_EXTENSION_FIELD_NUMBER, WireFormat.WIRETYPE_VARINT));
            output.writeRawByte(1);
        });
    }

    private static void assertAccepted(Message probe, Writer writer) throws Exception {
        CodedOutputStreamConventions.verify(probe, message(probe, writer));
    }

    private static void assertRejected(Message probe, Writer writer) {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> CodedOutputStreamConventions.verify(probe, message(probe, writer)));
        assertThat(e).hasMessageThat().contains(probe.getDescriptorForType().getFullName());
    }

    private static int tag(int fieldNumber, int wireType) {
        return fieldNumber << 3 | wireType;
    }

    private static void writePacked(CodedOutputStream output) throws Exception {
        output.writeUInt32NoTag(tag(PATH, WireFormat.WIRETYPE_LENGTH_DELIMITED));
        output.writeUInt32NoTag(3);
        output.writeInt32NoTag(1);
        output.writeInt32NoTag(128);
    }

    private static DynamicMessage entry(String key, Value value) {
        Descriptor type = Struct.getDescriptor().findFieldByName("fields").getMessageType();
        return DynamicMessage.newBuilder(type)
                .setField(type.findFieldByName("key"), key)
                .setField(type.findFieldByName("value"), value)
                .build();
    }

    private interface Writer {
        void writeTo(CodedOutputStream output) throws Exception;
    }

    /**
     * @return a message of the type of the probe, which the sinks write with the given calls
     */

    private static Message message(Message probe, Writer writer) {
        return (Message) Proxy.newProxyInstance(CodedOutputStreamConventionsTest.class.getClassLoader(), new Class<?>[] {Message.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getDescriptorForType":
                    return probe.getDescriptorForType();
                case "writeTo":
                    writer.writeTo((CodedOutputStream) args[0]);
                    return null;
                case "toString":
                    return "message of " + probe.getDescriptorForType().getFullName();
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        });
    }
}
