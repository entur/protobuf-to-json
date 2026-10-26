package no.entur.protobuf.json.accessor;

import com.google.protobuf.Any;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Duration;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Empty;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Int32Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Type;
import com.google.protobuf.Value;
import com.google.protobuf_test_messages.proto2.TestMessagesProto2.TestAllTypesProto2;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.TestSupport;
import no.entur.protobuf.json.accessor.names.AccessorNamesEditionsProto.AccessorNamesEditions;
import no.entur.protobuf.json.accessor.names.AccessorNamesProto2.AccessorNames2;
import no.entur.protobuf.json.accessor.names.AccessorNamesProto3.AccessorNames3;
import no.entur.protobuf.json.jsonformat.JsonFormatComparison;
import no.entur.protobuf.json.test.protobufjackson.GithubApi.SearchResponse;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WarmupMessagesTest {

    private static final List<Class<? extends Message>> TYPES = List.of(TestAllTypesProto3.class, TestAllTypesProto2.class, AccessorNames2.class,
            AccessorNames3.class, AccessorNamesEditions.class, SearchResponse.class, Type.class, FileDescriptorProto.class);

    @Test
    public void setsEveryField() {
        for (Class<? extends Message> type : TYPES) {
            List<Message> messages = WarmupMessages.create(type);
            Descriptor descriptor = messages.get(0).getDescriptorForType();
            Set<FieldDescriptor> set = new HashSet<>();
            for (Message message : messages) {
                set.addAll(message.getAllFields().keySet());
            }
            for (FieldDescriptor field : descriptor.getFields()) {
                if (!isLeftOut(field, descriptor)) {
                    assertWithMessage(field.getFullName()).that(set).contains(field);
                }
            }
        }
        // one message per oneof member
        assertThat(WarmupMessages.create(TestAllTypesProto3.class)).hasSize(TestAllTypesProto3.getDescriptor().getRealOneofs().get(0).getFieldCount());
    }

    /**
     * Any values, fields of the message's own type, and enums with a single value, which is the default
     */

    private static boolean isLeftOut(FieldDescriptor field, Descriptor descriptor) {
        FieldDescriptor value = field.isMapField() ? field.getMessageType().findFieldByNumber(2) : field;
        if (value.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
            return value.getMessageType().getFullName().equals("google.protobuf.Any") || value.getMessageType() == descriptor;
        }
        return value.getJavaType() == FieldDescriptor.JavaType.ENUM && value.getEnumType().getValues().size() == 1 && !field.hasPresence();
    }

    @Test
    public void sameAsJsonFormat() {
        JsonFormatComparison comparison = TestSupport.comparison(AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build());
        for (Class<? extends Message> type : TYPES) {
            for (Message message : WarmupMessages.create(type)) {
                TestSupport.assertSameAsJsonFormat(comparison, message, type.getName());
            }
        }
    }

    @Test
    public void wellKnownTypes() throws Exception {
        assertThat(WarmupMessages.create(Any.class)).isEmpty();
        assertThat(WarmupMessages.create(Timestamp.class)).containsExactly(Timestamp.newBuilder().setSeconds(1_700_000_000L).setNanos(123_000_000).build());
        assertThat(((Int32Value) WarmupMessages.create(Int32Value.class).get(0)).getValue()).isNotEqualTo(0);

        // the well-known types themselves, as curioswitch protobuf-jackson registers them
        AccessorProtobufJsonWriter.newBuilder()
                .withWarmupTypes(Any.class, Timestamp.class, Duration.class, Struct.class, Value.class, ListValue.class, FieldMask.class, Int32Value.class, Empty.class)
                .withWarmupIterations(1)
                .build();
    }

    @Test
    public void notAGeneratedMessageClass() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> WarmupMessages.create(DynamicMessage.class));
        assertThat(e).hasMessageThat().contains(DynamicMessage.class.getName());
    }
}
