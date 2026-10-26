package no.entur.protobuf.json.accessor;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Type;
import com.google.protobuf_test_messages.proto2.TestMessagesProto2.TestAllTypesProto2;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.TestSupport;
import no.entur.protobuf.json.accessor.names.AccessorNamesEditionsProto.AccessorNamesEditions;
import no.entur.protobuf.json.accessor.names.AccessorNamesProto2.AccessorNames2;
import no.entur.protobuf.json.accessor.names.AccessorNamesProto3.AccessorNames3;
import no.entur.protobuf.json.plan.MessagePlan.Plans;
import no.entur.protobuf.json.plan.WellKnownType;
import no.entur.protobuf.json.test.RandomMessages;
import no.entur.protobuf.json.test.ProtobufJacksonTestMessages;
import no.entur.protobuf.json.test.protobufjackson.GithubApi.SearchResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MessageWritersTest {

    /**
     * The message classes of the test messages, and the message classes of their fields.
     */

    static Set<Class<? extends Message>> testMessageClasses() {
        List<Class<? extends Message>> roots = new ArrayList<>(List.of(TestAllTypesProto3.class, TestAllTypesProto2.class, SearchResponse.class, Type.class,
                FileDescriptorProto.class, AccessorNames2.class, AccessorNames3.class, AccessorNamesEditions.class));
        for (Message message : ProtobufJacksonTestMessages.create().values()) {
            roots.add(message.getClass());
        }

        Set<Class<? extends Message>> classes = new LinkedHashSet<>();
        Deque<Class<? extends Message>> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            Class<? extends Message> type = queue.pop();
            if (classes.add(type)) {
                for (MessageAccessors.Field field : MessageAccessors.of(type, new Plans()).fields) {
                    if (field.kind == MessageAccessors.Kind.MESSAGE) {
                        queue.add(field.type.asSubclass(Message.class));
                    }
                }
            }
        }
        return classes;
    }

    @Test
    public void generatesWritersForAllTestMessages() {
        Set<Class<? extends Message>> classes = testMessageClasses();
        assertThat(classes.size()).isGreaterThan(50);
        for (Class<? extends Message> type : classes) {
            for (boolean filtering : new boolean[]{false, true}) {
                MessageWriters.Generated generated = MessageWriters.get(type, filtering);
                assertWithMessage(type.getName()).that(generated.failure()).isNull();
                // only the well-known types are transcoded
                assertWithMessage(type.getName()).that(generated.transcoding()).isEqualTo(WellKnownType.of(MessageAccessors.of(type, new Plans()).plan.descriptor) != null);
            }
        }
    }

    @Test
    public void transcodesWellKnownTypesAndDynamicMessages() {
        assertThat(MessageWriters.get(Timestamp.class, false).transcoding()).isTrue();
        assertThat(MessageWriters.get(Timestamp.class, false).failure()).isNull();
        assertThat(MessageWriters.get(Empty.class, false).transcoding()).isFalse();

        MessageWriters.Generated dynamic = MessageWriters.get(DynamicMessage.class, false);
        assertThat(dynamic.transcoding()).isTrue();
        assertThat(dynamic.failure()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void definesWritersNextToTheLibrary() {
        // the library can see the classes of the messages
        Class<?> writer = MessageWriters.get(TestAllTypesProto3.class, false).writer().getClass();
        assertThat(writer.getPackageName()).isEqualTo(MessageWriters.class.getPackageName());
        assertThat(writer.getName()).startsWith(MessageWriters.class.getPackageName() + ".TestAllTypesProto3$$JsonWriter");
    }

    @Test
    public void registersMessageTypesUpFront() {
        AccessorProtobufJsonWriter.newBuilder()
                .withTypeRegistry(TestSupport.TYPE_REGISTRY)
                .withMessageTypes(TestAllTypesProto3.class, SearchResponse.class)
                .build();
        AccessorProtobufJsonWriter.newBuilder()
                .withMessageTypes(List.of(TestAllTypesProto2.class))
                .withMaxSize(1000)
                .build();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> AccessorProtobufJsonWriter.newBuilder().withMessageTypes(DynamicMessage.class).build());
        assertThat(e).hasMessageThat().contains(DynamicMessage.class.getName());
    }

    @Test
    public void accessorNames() {
        // as generated by protoc, see accessor_names_proto2.proto
        MessageAccessors accessors = MessageAccessors.of(AccessorNames2.class, new Plans());
        List<String> getters = new ArrayList<>();
        for (MessageAccessors.Field field : accessors.fields) {
            getters.add(field.get.getName());
        }
        assertThat(getters).containsAtLeast(
                "getClass_", "getCachedSize_", "getSerializedSize_", "getUnknownFields_", "getDefaultInstanceForType_", "getParserForType_",
                "getAllFields_", "getDescriptorForType_", "getInitializationErrorString_",
                "getFoo10", "getFooCount11", "getBar12", "getBarList13", "getBaz14Map", "getBazCount15",
                "getSameName16", "getSameName17",
                "getLeading", "getTrailing", "getDoubleUnderscore", "getDigit2Letter", "getX1Y",
                "getMultiWord", "getRepeatedMultiWord", "getColor", "getColors", "getColorByNumberMap");

        MessageAccessors editions = MessageAccessors.of(AccessorNamesEditions.class, new Plans());
        List<String> editionsGetters = new ArrayList<>();
        for (MessageAccessors.Field field : editions.fields) {
            editionsGetters.add(field.get.getName());
        }
        assertThat(editionsGetters).containsAtLeast("getGroupLike", "getOtherGroup", "getImplicit", "getClosed");
    }

    @Test
    public void sameAsJsonFormat() {
        // the messages with accessor names which protoc changes, also as DynamicMessage and builder
        var comparison = TestSupport.comparison(AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build());
        var filteringComparison = TestSupport.comparison(AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).withMaxSize(Integer.MAX_VALUE).build());
        for (int seed = 0; seed < 500; seed++) {
            for (Message.Builder builder : List.<Message.Builder>of(AccessorNames2.newBuilder().setRequiredField("x"), AccessorNames3.newBuilder(), AccessorNamesEditions.newBuilder())) {
                Message message = new RandomMessages(seed).fill(builder);
                TestSupport.assertSameAsJsonFormat(comparison, message, "seed " + seed);
                TestSupport.assertSameAsJsonFormat(filteringComparison, message, "seed " + seed);
            }
        }
    }
}
