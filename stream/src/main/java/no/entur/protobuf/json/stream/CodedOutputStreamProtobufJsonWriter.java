package no.entur.protobuf.json.stream;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import no.entur.protobuf.json.AbstractProtobufJsonWriterBuilder;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.UnresolvedAny;
import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.plan.MessagePlan;
import no.entur.protobuf.json.plan.MessagePlan.Plans;
import no.entur.protobuf.json.plan.ProtobufJsonWriterSupport;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * {@linkplain ProtobufJsonWriter} which lets a message's generated serialization code write JSON directly, via a
 * {@linkplain com.google.protobuf.CodedOutputStream} which produces JSON instead of the binary format.
 * This is the fastest implementation, and allocates the least.
 * <br><br>
 * CodedOutputStream only has a private constructor, so a subclass is generated at runtime with the ClassFile API (Java 24+).
 * This depends on how protobuf's generated code and CodedOutputStream work internally. If the subclass cannot be generated,
 * or if writing probe messages shows that this protobuf version writes fields in a way the writer does not handle, the writer
 * logs a warning (once) and delegates to {@linkplain TranscodingProtobufJsonWriter}, which writes the same JSON more slowly.
 * {@linkplain com.google.protobuf.DynamicMessage}s are written the same way, via their reflection-based serialization.
 * Writing fails for messages whose (custom) serialization writes raw byte arrays or buffers.
 * <br><br>
 * The output is the same as for {@linkplain TranscodingProtobufJsonWriter}, which also writes the well-known types and the
 * contents of google.protobuf.Any values; see it for differences from JsonFormat.
 * <br><br>
 * Use the {@linkplain #newBuilder() builder} for filtering (anonymize and prune paths, max string length and max size);
 * see {@linkplain FilteringCodedOutputStreamProtobufJsonWriter}.
 */

public class CodedOutputStreamProtobufJsonWriter implements ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> {

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder extends AbstractProtobufJsonWriterBuilder<Builder> {

        private JsonFormat.TypeRegistry typeRegistry = JsonFormat.TypeRegistry.getEmptyTypeRegistry();

        /**
         * @param typeRegistry types for resolving google.protobuf.Any
         * @return this builder
         */

        public Builder withTypeRegistry(JsonFormat.TypeRegistry typeRegistry) {
            this.typeRegistry = typeRegistry;
            return this;
        }

        /**
         * @return a filtering writer if any filter option is set, otherwise a plain writer
         */

        public ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> build() {
            JsonFilter filter = buildFilter();
            if (filter != null) {
                return new FilteringCodedOutputStreamProtobufJsonWriter(typeRegistry, filter, unresolvedAny);
            }
            return new CodedOutputStreamProtobufJsonWriter(typeRegistry, unresolvedAny);
        }
    }

    private final Plans plans = new Plans();
    private final TranscodingProtobufJsonWriter transcoder;
    private final boolean streaming;

    protected CodedOutputStreamProtobufJsonWriter(JsonFormat.TypeRegistry typeRegistry) {
        this(typeRegistry, UnresolvedAny.FAIL);
    }

    protected CodedOutputStreamProtobufJsonWriter(JsonFormat.TypeRegistry typeRegistry, UnresolvedAny unresolvedAny) {
        this(typeRegistry, unresolvedAny, JsonCodedOutputStreamFactory.isAvailable(false));
    }

    /**
     * @param typeRegistry  types for resolving google.protobuf.Any
     * @param unresolvedAny how to write google.protobuf.Any values of types which are not in the registry
     * @param streaming     false to delegate to the transcoding writer
     */

    CodedOutputStreamProtobufJsonWriter(JsonFormat.TypeRegistry typeRegistry, UnresolvedAny unresolvedAny, boolean streaming) {
        this.transcoder = new TranscodingProtobufJsonWriter(plans, typeRegistry, unresolvedAny);
        this.streaming = streaming;
    }

    @Override
    public boolean write(MessageOrBuilder message, JsonGenerator generator) {
        if (!streaming) {
            return transcoder.write(message, generator);
        }
        Message m;
        if (message instanceof Message msg) {
            m = msg;
        } else {
            m = ((Message.Builder) message).buildPartial();
        }

        MessagePlan plan = plans.get(m.getDescriptorForType());
        try {
            if (plan.wellKnownType != null) {
                transcoder.writeWellKnown(plan.wellKnownType, m, generator);
            } else {
                new CodedOutputStreamJsonSink(plans, transcoder, generator).write(m);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return true;
    }

    @Override
    public String writeAsString(MessageOrBuilder message) {
        return ProtobufJsonWriterSupport.writeAsString(this, message);
    }
}
