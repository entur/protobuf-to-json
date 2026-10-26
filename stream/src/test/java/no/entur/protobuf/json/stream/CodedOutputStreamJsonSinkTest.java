package no.entur.protobuf.json.stream;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.google.protobuf.WireFormat;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.plan.FilteredJsonValues;
import no.entur.protobuf.json.plan.MessagePlan.Plans;
import no.entur.protobuf.json.transcode.FilteringTranscodingProtobufJsonWriter;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.json.JsonMapper;

import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class CodedOutputStreamJsonSinkTest {

    private static final Descriptor DESCRIPTOR = TestAllTypesProto3.getDescriptor();
    private static final int MAP_STRING_STRING = DESCRIPTOR.findFieldByName("map_string_string").getNumber();

    @Test
    public void skipsDuplicateMapKeys() throws Exception {
        // generated messages never have duplicate map keys, so write the entries directly

        Message message = message(output -> {
            // map entry as tag, length, key and value
            writeTaggedEntry(output, "a", "1");
            writeTaggedEntry(output, "b", "2");
            writeTaggedEntry(output, "a", "3");
            // map entry as a message
            output.writeMessage(MAP_STRING_STRING, entry("b", "4"));
            output.writeMessage(MAP_STRING_STRING, entry("c", "5"));
        });

        assertThat(write(message)).isEqualTo("{\"mapStringString\":{\"a\":\"1\",\"b\":\"2\",\"c\":\"5\"}}");
        assertThat(write(message, filter(List.of(), List.of()))).isEqualTo("{\"mapStringString\":{\"a\":\"1\",\"b\":\"2\",\"c\":\"5\"}}");
    }

    @Test
    public void filtersMapEntries() throws Exception {
        // pruning or anonymizing a map must keep track of entries written as tag, length, key and value

        Message message = message(output -> {
            writeTaggedEntry(output, "a", "1");
            writeTaggedEntry(output, "b", "2");
            output.writeMessage(MAP_STRING_STRING, entry("c", "3"));
            output.writeString(DESCRIPTOR.findFieldByName("optional_string").getNumber() + 1000, "unknown");
        });

        assertThat(write(message, filter(List.of(), List.of("$.mapStringString")))).isEqualTo("{\"mapStringString\":\"[removed]\"}");
        assertThat(write(message, filter(List.of("$.mapStringString"), List.of()))).isEqualTo("{\"mapStringString\":{\"a\":\"*\",\"b\":\"*\",\"c\":\"*\"}}");
        assertThat(write(message, filter(List.of("$.mapStringString.b"), List.of("$.mapStringString.c")))).isEqualTo("{\"mapStringString\":{\"a\":\"1\",\"b\":\"*\",\"c\":\"[removed]\"}}");
    }

    @Test
    public void filtersFieldsAfterMapEntries() throws Exception {

        int mapStringNestedMessage = DESCRIPTOR.findFieldByName("map_string_nested_message").getNumber();
        int optionalNestedMessage = DESCRIPTOR.findFieldByName("optional_nested_message").getNumber();
        Message message = message(output -> {
            output.writeInt32(DESCRIPTOR.findFieldByName("optional_int32").getNumber(), 1);
            output.writeMessage(optionalNestedMessage, TestAllTypesProto3.NestedMessage.newBuilder().setA(2).build());
            writeTaggedEntry(output, "a", "1");
            output.writeMessage(mapStringNestedMessage, nestedMessageEntry("x", 3));
        });

        assertThat(write(message, filter(List.of(), List.of("$.mapStringString", "$.optionalNestedMessage"))))
                .isEqualTo("{\"optionalInt32\":1,\"optionalNestedMessage\":\"[removed]\",\"mapStringString\":\"[removed]\",\"mapStringNestedMessage\":{\"x\":{\"a\":3}}}");
        assertThat(write(message, filter(List.of("$.mapStringNestedMessage"), List.of())))
                .isEqualTo("{\"optionalInt32\":1,\"optionalNestedMessage\":{\"a\":2},\"mapStringString\":{\"a\":\"1\"},\"mapStringNestedMessage\":{\"x\":{\"a\":\"*\"}}}");
    }

    private static DynamicMessage nestedMessageEntry(String key, int a) {
        Descriptor type = DESCRIPTOR.findFieldByName("map_string_nested_message").getMessageType();
        return DynamicMessage.newBuilder(type)
                .setField(type.findFieldByName("key"), key)
                .setField(type.findFieldByName("value"), TestAllTypesProto3.NestedMessage.newBuilder().setA(a).build())
                .build();
    }

    private static JsonFilter filter(List<String> anonymizes, List<String> prunes) {
        return new JsonFilter(-1, Integer.MAX_VALUE, anonymizes, prunes, JsonFilter.DEFAULT_ANONYMIZE_RAW_JSON_VALUE, JsonFilter.DEFAULT_PRUNE_RAW_JSON_VALUE, JsonFilter.DEFAULT_TRUNCATE_MESSAGE);
    }

    private static String write(Message message, JsonFilter filter) throws Exception {
        Plans plans = new Plans();
        FilteringTranscodingProtobufJsonWriter transcoder = new FilteringTranscodingProtobufJsonWriter(plans, JsonFormat.TypeRegistry.getEmptyTypeRegistry(), filter);
        StringWriter output = new StringWriter();
        try (JsonGenerator generator = JsonMapper.builder().build().createGenerator(output)) {
            new FilteringCodedOutputStreamJsonSink(plans, transcoder, new FilteredJsonValues(filter, generator)).write(message);
        }
        return output.toString();
    }

    private interface Writer {
        void writeTo(CodedOutputStream output) throws Exception;
    }

    private static Message message(Writer writer) {
        return (Message) Proxy.newProxyInstance(CodedOutputStreamJsonSinkTest.class.getClassLoader(), new Class<?>[]{Message.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getDescriptorForType":
                    return DESCRIPTOR;
                case "writeTo":
                    writer.writeTo((CodedOutputStream) args[0]);
                    return null;
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        });
    }

    private static void writeTaggedEntry(CodedOutputStream output, String key, String value) throws Exception {
        output.writeTag(MAP_STRING_STRING, WireFormat.WIRETYPE_LENGTH_DELIMITED);
        output.writeUInt32NoTag(CodedOutputStream.computeStringSize(1, key) + CodedOutputStream.computeStringSize(2, value));
        output.writeString(1, key);
        output.writeString(2, value);
    }

    private static DynamicMessage entry(String key, String value) {
        Descriptor type = DESCRIPTOR.findFieldByName("map_string_string").getMessageType();
        return DynamicMessage.newBuilder(type)
                .setField(type.findFieldByName("key"), key)
                .setField(type.findFieldByName("value"), value)
                .build();
    }

    private static String write(Message message) throws Exception {
        Plans plans = new Plans();
        TranscodingProtobufJsonWriter transcoder = new TranscodingProtobufJsonWriter(plans, JsonFormat.TypeRegistry.getEmptyTypeRegistry());
        StringWriter output = new StringWriter();
        try (JsonGenerator generator = JsonMapper.builder().build().createGenerator(output)) {
            new CodedOutputStreamJsonSink(plans, transcoder, generator).write(message);
        }
        return output.toString();
    }
}
