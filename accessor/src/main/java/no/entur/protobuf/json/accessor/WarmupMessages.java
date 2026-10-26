package no.entur.protobuf.json.accessor;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumDescriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import no.entur.protobuf.json.plan.WellKnownType;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Messages for warming up a writer for message types: every field set, with values like those of real messages (i.e.
 * strings of some length, numbers of several digits), repeated fields and maps with two values, and the fields of message
 * types filled the same way. A message type which is already being filled (i.e. of a recursive message type) is left out,
 * and so are google.protobuf.Any values, as their types might not be resolvable.
 * <br><br>
 * One message per member of the largest oneof (up to 32), so that every member is set in one of them.
 */

final class WarmupMessages {

    private static final int MAX_ONEOF_VARIANTS = 32;

    /** max number of values in a message, so that wide message types do not make huge messages */
    private static final int MAX_VALUES = 10_000;

    private static final String STRING = "https://example.org/warmup/message?with=some&typical=length";

    private final Set<Descriptor> filling = new HashSet<>();
    private final int variant;
    private int values;

    private WarmupMessages(int variant) {
        this.variant = variant;
    }

    /**
     * @param type generated message class
     * @return messages of the type, with every field set
     * @throws IllegalArgumentException if the class is not a generated message class
     */

    static List<Message> create(Class<? extends Message> type) {
        Message prototype = getDefaultInstance(type);
        if (WellKnownType.of(prototype.getDescriptorForType()) != null) {
            // as for fields of the type; none for google.protobuf.Any
            Message message = new WarmupMessages(0).message(prototype.getDescriptorForType(), prototype::newBuilderForType);
            return message != null ? List.of(message) : List.of();
        }
        int variants = 1;
        for (OneofDescriptor oneof : prototype.getDescriptorForType().getRealOneofs()) {
            variants = Math.max(variants, Math.min(MAX_ONEOF_VARIANTS, oneof.getFieldCount()));
        }
        List<Message> messages = new ArrayList<>(variants);
        for (int variant = 0; variant < variants; variant++) {
            messages.add(new WarmupMessages(variant).fill(prototype.newBuilderForType()));
        }
        return messages;
    }

    private static Message getDefaultInstance(Class<? extends Message> type) {
        try {
            Method method = type.getMethod("getDefaultInstance");
            if (Modifier.isStatic(method.getModifiers()) && type.isAssignableFrom(method.getReturnType())) {
                return (Message) method.invoke(null);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(type.getName() + " is not a generated message class", e);
        }
        throw new IllegalArgumentException(type.getName() + " is not a generated message class");
    }

    private Message fill(Message.Builder builder) {
        Descriptor descriptor = builder.getDescriptorForType();
        filling.add(descriptor);
        for (FieldDescriptor field : descriptor.getFields()) {
            OneofDescriptor oneof = field.getRealContainingOneof();
            if (oneof != null && oneof.getField(variant % oneof.getFieldCount()) != field) {
                continue;
            }
            if (values >= MAX_VALUES) {
                break;
            }
            if (field.isMapField()) {
                FieldDescriptor key = field.getMessageType().findFieldByNumber(1);
                FieldDescriptor value = field.getMessageType().findFieldByNumber(2);
                for (int i = 0; i < 2; i++) {
                    Message.Builder entry = builder.newBuilderForField(field);
                    Object entryValue = value(value, () -> entry.newBuilderForField(value));
                    if (entryValue != null) {
                        builder.addRepeatedField(field, entry.setField(key, key(key, i)).setField(value, entryValue).build());
                    }
                }
            } else if (field.isRepeated()) {
                for (int i = 0; i < 2; i++) {
                    Object value = value(field, () -> builder.newBuilderForField(field));
                    if (value != null) {
                        builder.addRepeatedField(field, value);
                    }
                }
            } else {
                Object value = value(field, () -> builder.newBuilderForField(field));
                if (value != null) {
                    builder.setField(field, value);
                }
            }
        }
        filling.remove(descriptor);
        return builder.buildPartial();
    }

    /**
     * @param builder builder for a message value
     * @return a value for the field, or null if left out
     */

    private Object value(FieldDescriptor field, Supplier<Message.Builder> builder) {
        values++;
        return switch (field.getJavaType()) {
            case INT -> 123_456;
            case LONG -> 1_234_567_890_123L;
            case FLOAT -> 1234.5f;
            case DOUBLE -> 123_456.75d;
            case BOOLEAN -> true;
            case STRING -> STRING;
            case BYTE_STRING -> ByteString.copyFromUtf8(STRING);
            case ENUM -> enumValue(field.getEnumType());
            case MESSAGE -> message(field.getMessageType(), builder);
        };
    }

    private static EnumValueDescriptor enumValue(EnumDescriptor type) {
        // a value other than the default, if there is one
        List<EnumValueDescriptor> values = type.getValues();
        return values.get(values.size() > 1 ? 1 : 0);
    }

    private Message message(Descriptor type, Supplier<Message.Builder> builder) {
        WellKnownType wellKnownType = WellKnownType.of(type);
        if (wellKnownType != null) {
            return switch (wellKnownType) {
                case ANY -> null;
                case TIMESTAMP -> Timestamp.newBuilder().setSeconds(1_700_000_000L).setNanos(123_000_000).build();
                case DURATION -> Duration.newBuilder().setSeconds(90).setNanos(500_000_000).build();
                case FIELD_MASK -> FieldMask.newBuilder().addPaths("foo_bar").addPaths("baz").build();
                case STRUCT -> struct();
                case VALUE -> Value.newBuilder().setStructValue(struct()).build();
                case LIST_VALUE -> listValue();
                // the wrappers
                default -> fill(builder.get());
            };
        }
        if (filling.contains(type)) {
            return null;
        }
        return fill(builder.get());
    }

    private static Struct struct() {
        return Struct.newBuilder()
                .putFields("text", Value.newBuilder().setStringValue(STRING).build())
                .putFields("number", Value.newBuilder().setNumberValue(123.5).build())
                .putFields("flag", Value.newBuilder().setBoolValue(true).build())
                .putFields("list", Value.newBuilder().setListValue(listValue()).build())
                .build();
    }

    private static ListValue listValue() {
        return ListValue.newBuilder()
                .addValues(Value.newBuilder().setStringValue(STRING))
                .addValues(Value.newBuilder().setNumberValue(1))
                .build();
    }

    private static Object key(FieldDescriptor key, int index) {
        return switch (key.getJavaType()) {
            case INT -> index + 1;
            case LONG -> (long) index + 1;
            case BOOLEAN -> index == 1;
            case STRING -> "key" + index;
            default -> throw new IllegalArgumentException("Unexpected map key type " + key.getType());
        };
    }
}
