package no.entur.protobuf.json.accessor;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import no.entur.protobuf.json.AbstractProtobufJsonWriterBuilder;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.plan.MessagePlan.Plans;
import no.entur.protobuf.json.plan.ProtobufJsonWriterSupport;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * {@linkplain ProtobufJsonWriter} which reads the fields of a message with its accessors (getters), with code generated
 * per message type at runtime with the ClassFile API (Java 24+). As it does not run the serialization code of the
 * messages, it does not affect how the JIT compiles that code for binary serialization, and vice versa.
 * <br><br>
 * The code for a message type is generated when a message of the type is first written, or when the writer is built for
 * types registered with {@linkplain Builder#withMessageTypes(Class[])}, and the writer can be warmed up with typical messages
 * (or for message types) when it is built, see {@linkplain Builder#withWarmup(Message...)} and
 * {@linkplain Builder#withWarmupTypes(Class[])}. The code is shared by all writers. It is defined as a
 * hidden class in this library's package, or, if this library's class loader cannot see the classes of the messages, in the
 * package of the message (which must then be open to this library, if in a named module).
 * <br><br>
 * The output is the same as for {@linkplain TranscodingProtobufJsonWriter}, which also writes the contents of
 * google.protobuf.Any values, and messages which are not generated (i.e. {@linkplain com.google.protobuf.DynamicMessage});
 * see it for differences from JsonFormat.
 * <br><br>
 * Use the {@linkplain #newBuilder() builder} for filtering (anonymize and prune paths, max string length and max size);
 * see {@linkplain FilteringAccessorProtobufJsonWriter}.
 */

public class AccessorProtobufJsonWriter implements ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> {

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder extends AbstractProtobufJsonWriterBuilder<Builder> {

        /** about as many times as the JIT needs to compile the code of a message type with its optimizing compiler */
        public static final int DEFAULT_WARMUP_ITERATIONS = 10_000;

        private JsonFormat.TypeRegistry typeRegistry = JsonFormat.TypeRegistry.getEmptyTypeRegistry();
        private final List<Class<? extends Message>> messageTypes = new ArrayList<>();
        private final List<Message> warmupMessages = new ArrayList<>();
        private final List<Class<? extends Message>> warmupTypes = new ArrayList<>();
        private int warmupIterations = DEFAULT_WARMUP_ITERATIONS;

        /**
         * @param typeRegistry types for resolving google.protobuf.Any
         * @return this builder
         */

        public Builder withTypeRegistry(JsonFormat.TypeRegistry typeRegistry) {
            this.typeRegistry = typeRegistry;
            return this;
        }

        /**
         * Generate the code for these message types, and for the message types of their fields, when the writer is built,
         * rather than when a message of the type is first written.
         *
         * @param types generated message classes
         * @return this builder
         */

        @SafeVarargs
        public final Builder withMessageTypes(Class<? extends Message>... types) {
            return withMessageTypes(Arrays.asList(types));
        }

        /**
         * Generate the code for these message types, and for the message types of their fields, when the writer is built,
         * rather than when a message of the type is first written.
         *
         * @param types generated message classes
         * @return this builder
         */

        public Builder withMessageTypes(Collection<Class<? extends Message>> types) {
            messageTypes.addAll(types);
            return this;
        }

        /**
         * Warm up the writer when it is built, by writing typical messages (to a UTF-8 generator which discards the output),
         * so that the code for their message types (and the message types of their fields) is generated, and run often enough
         * for the JIT to compile it, before the first messages are written. Takes about as long as writing the messages
         * {@linkplain #withWarmupIterations(int) the given number of times}, plus the compilation.
         * <br><br>
         * The JIT optimizes the code for what it has seen, so the messages should be like the messages the writer will write,
         * i.e. with the same fields set.
         *
         * @param messages typical messages
         * @return this builder
         */

        public Builder withWarmup(Message... messages) {
            return withWarmup(Arrays.asList(messages));
        }

        /**
         * Warm up the writer when it is built, see {@linkplain #withWarmup(Message...)}.
         *
         * @param messages typical messages
         * @return this builder
         */

        public Builder withWarmup(Collection<? extends Message> messages) {
            warmupMessages.addAll(messages);
            return this;
        }

        /**
         * Warm up the writer for these message types when it is built, like {@linkplain #withWarmup(Message...)}, with messages
         * of the types with every field set, and the fields of message types filled the same way (to a limited size). Typical
         * messages are better if there are any, as the JIT optimizes the code for what it has seen. The types are also
         * registered, see {@linkplain #withMessageTypes(Class[])}.
         *
         * @param types generated message classes
         * @return this builder
         */

        @SafeVarargs
        public final Builder withWarmupTypes(Class<? extends Message>... types) {
            return withWarmupTypes(Arrays.asList(types));
        }

        /**
         * Warm up the writer for these message types when it is built, see {@linkplain #withWarmupTypes(Class[])}.
         *
         * @param types generated message classes
         * @return this builder
         */

        public Builder withWarmupTypes(Collection<Class<? extends Message>> types) {
            warmupTypes.addAll(types);
            return this;
        }

        /**
         * @param iterations how many times to write each warmup message, by default {@value #DEFAULT_WARMUP_ITERATIONS}
         * @return this builder
         */

        public Builder withWarmupIterations(int iterations) {
            if (iterations < 0) {
                throw new IllegalArgumentException("Expected a non-negative number of warmup iterations, got " + iterations);
            }
            this.warmupIterations = iterations;
            return this;
        }

        /**
         * @return a filtering writer if any filter option is set, otherwise a plain writer
         * @throws IllegalArgumentException if code cannot be generated for a registered message type (or a message type to
         *                                  warm up for), or a warmup message cannot be written
         */

        public ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> build() {
            JsonFilter filter = buildFilter();
            List<Class<? extends Message>> types = new ArrayList<>(messageTypes);
            types.addAll(warmupTypes);
            MessageWriters.register(types, filter != null);
            ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer;
            if (filter != null) {
                writer = new FilteringAccessorProtobufJsonWriter(typeRegistry, filter);
            } else {
                writer = new AccessorProtobufJsonWriter(typeRegistry);
            }
            warmUp(writer);
            return writer;
        }

        private void warmUp(ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer) {
            List<Message> messages = new ArrayList<>(warmupMessages);
            for (Class<? extends Message> type : warmupTypes) {
                messages.addAll(WarmupMessages.create(type));
            }
            if (messages.isEmpty()) {
                return;
            }
            try {
                for (int i = 0; i < warmupIterations; i++) {
                    for (Message message : messages) {
                        try (JsonGenerator generator = ProtobufJsonWriterSupport.JSON_FACTORY.createGenerator(ObjectWriteContext.empty(), OutputStream.nullOutputStream())) {
                            writer.write(message, generator);
                        }
                    }
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Unable to write a warmup message", e);
            }
        }
    }

    final Plans plans = new Plans();
    final TranscodingProtobufJsonWriter transcoder;

    protected AccessorProtobufJsonWriter(JsonFormat.TypeRegistry typeRegistry) {
        this.transcoder = new TranscodingProtobufJsonWriter(plans, typeRegistry);
    }

    @Override
    public boolean write(MessageOrBuilder message, JsonGenerator generator) throws IOException {
        Message m;
        if (message instanceof Message msg) {
            m = msg;
        } else {
            m = ((Message.Builder) message).buildPartial();
        }
        MessageWriters.getWriter(m.getClass()).write(generator, null, m, this);
        return true;
    }

    @Override
    public String writeAsString(MessageOrBuilder message) throws IOException {
        return ProtobufJsonWriterSupport.writeAsString(this, message);
    }
}
