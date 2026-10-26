package no.entur.protobuf.json.accessor;

import com.google.protobuf.Message;
import no.entur.protobuf.json.plan.FilteredJsonValues;
import no.entur.protobuf.json.plan.MessagePlan;
import no.entur.protobuf.json.plan.MessagePlan.Plans;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;

import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The generated writers, one unfiltered and one filtering writer per message class, generated when first used (or when
 * registered) and shared by all writer instances.
 * <br><br>
 * Messages which are not generated (i.e. {@linkplain com.google.protobuf.DynamicMessage}), the well-known types (as root
 * values), and message classes a writer cannot be generated for, are transcoded instead.
 * <br><br>
 * Internal; public only because the generated writers may be defined in the packages of the messages.
 */

public final class MessageWriters {

    /**
     * A writer: the generated class (or the transcoder), its static write method, and the message classes of its fields.
     *
     * @param writer      {@linkplain MessageWriter} or {@linkplain FilteringMessageWriter}
     * @param handle      the static write method
     * @param nested      the message classes of the fields, which are written by their own writers
     * @param transcoding whether messages are transcoded rather than written by generated code
     * @param failure     why no writer could be generated, or null
     */

    record Generated(Object writer, MethodHandle handle, List<Class<? extends Message>> nested, boolean transcoding, Throwable failure) {
    }

    private static final MethodHandle TRANSCODE;
    private static final MethodHandle TRANSCODE_FILTERED;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            TRANSCODE = lookup.findStatic(MessageWriters.class, "transcode",
                    MethodType.methodType(void.class, JsonGenerator.class, SerializableString.class, Message.class, AccessorProtobufJsonWriter.class));
            TRANSCODE_FILTERED = lookup.findStatic(MessageWriters.class, "transcode",
                    MethodType.methodType(boolean.class, FilteredJsonValues.class, SerializableString.class, int.class, Message.class, boolean.class, FilteringAccessorProtobufJsonWriter.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final MessageWriter TRANSCODING_WRITER = MessageWriters::transcode;
    private static final FilteringMessageWriter FILTERING_TRANSCODING_WRITER = MessageWriters::transcode;

    private static final ClassValue<Generated> WRITERS = new ClassValue<>() {
        @Override
        protected Generated computeValue(Class<?> type) {
            return generate(type, false);
        }
    };

    private static final ClassValue<Generated> FILTERING_WRITERS = new ClassValue<>() {
        @Override
        protected Generated computeValue(Class<?> type) {
            return generate(type, true);
        }
    };

    private MessageWriters() {
    }

    static MessageWriter getWriter(Class<?> type) {
        return (MessageWriter) WRITERS.get(type).writer();
    }

    static FilteringMessageWriter getFilteringWriter(Class<?> type) {
        return (FilteringMessageWriter) FILTERING_WRITERS.get(type).writer();
    }

    static Generated get(Class<?> type, boolean filtering) {
        return (filtering ? FILTERING_WRITERS : WRITERS).get(type);
    }

    /**
     * Bootstrap method for the invokedynamic instructions of the generated writers, which call the writer of the message type
     * of a field.
     *
     * @param caller caller
     * @param name   method name
     * @param type   method type; the message type is the third parameter of an unfiltered writer, the fourth of a filtering writer
     * @return call site of the writer's static write method
     */

    public static CallSite bootstrap(MethodHandles.Lookup caller, String name, MethodType type) {
        boolean filtering = type.returnType() == boolean.class;
        Class<?> messageType = type.parameterType(filtering ? 3 : 2);
        return new ConstantCallSite(get(messageType, filtering).handle().asType(type));
    }

    private static Generated generate(Class<?> type, boolean filtering) {
        try {
            MessageAccessors accessors = MessageAccessors.of(type.asSubclass(Message.class), new Plans());
            if (accessors.plan.wellKnownType != null) {
                // written as specified for the type
                return transcoding(filtering, null);
            }
            return MessageWriterGenerator.generate(accessors, filtering);
        } catch (Exception | LinkageError e) {
            return transcoding(filtering, e);
        }
    }

    private static Generated transcoding(boolean filtering, Throwable failure) {
        if (filtering) {
            return new Generated(FILTERING_TRANSCODING_WRITER, TRANSCODE_FILTERED, List.of(), true, failure);
        }
        return new Generated(TRANSCODING_WRITER, TRANSCODE, List.of(), true, failure);
    }

    /**
     * Generate the writers of message types, and of the message types of their fields.
     *
     * @param types     message classes
     * @param filtering whether to generate the filtering or the unfiltered writers
     * @throws IllegalArgumentException if a writer cannot be generated for a message class
     */

    static void register(Collection<Class<? extends Message>> types, boolean filtering) {
        Set<Class<?>> registered = new HashSet<>();
        Deque<Class<? extends Message>> queue = new ArrayDeque<>(types);
        while (!queue.isEmpty()) {
            Class<? extends Message> type = queue.pop();
            if (registered.add(type)) {
                Generated generated = get(type, filtering);
                if (generated.failure() != null) {
                    throw new IllegalArgumentException("Unable to generate a writer for " + type.getName(), generated.failure());
                }
                queue.addAll(generated.nested());
            }
        }
    }

    // ---- writing with the transcoder ----

    private static void transcode(JsonGenerator generator, SerializableString name, Message message, AccessorProtobufJsonWriter writer) throws IOException {
        if (name != null) {
            generator.writeName(name);
        }
        writer.transcoder.write(message, generator);
    }

    private static boolean transcode(FilteredJsonValues values, SerializableString name, int nameSize, Message message, boolean anonymize, FilteringAccessorProtobufJsonWriter writer) throws IOException {
        MessagePlan plan = writer.plans.get(message.getDescriptorForType());
        if (plan.wellKnownType != null) {
            return writer.transcoder.writeWellKnown(plan.wellKnownType, message, values, name, nameSize, anonymize);
        }
        byte[] bytes = message.toByteArray();
        return writer.transcoder.writeMessage(bytes, 0, bytes.length, plan, values, null, name, nameSize, anonymize);
    }
}
