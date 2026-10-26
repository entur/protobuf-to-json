package no.entur.protobuf.json.stream;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import no.entur.protobuf.json.AbstractProtobufJsonWriterBuilder;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.plan.MessagePlan;
import no.entur.protobuf.json.plan.MessagePlan.Plans;
import no.entur.protobuf.json.plan.ProtobufJsonWriterSupport;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * {@linkplain ProtobufJsonWriter} which lets a message's generated serialization code write JSON directly, via a
 * {@linkplain com.google.protobuf.CodedOutputStream} which produces JSON instead of the binary format.
 * This is the fastest implementation, and allocates the least.
 * <br><br>
 * CodedOutputStream only has a private constructor, so a subclass is generated at runtime with the ClassFile API (Java 24+).
 * This depends on how protobuf's generated code and CodedOutputStream work internally; creating the writer fails if the
 * subclass cannot be generated. {@linkplain com.google.protobuf.DynamicMessage}s are written the same way, via their
 * reflection-based serialization.
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
                return new FilteringCodedOutputStreamProtobufJsonWriter(typeRegistry, filter);
            }
            return new CodedOutputStreamProtobufJsonWriter(typeRegistry);
        }
    }

    private final Plans plans = new Plans();
    private final TranscodingProtobufJsonWriter transcoder;

    protected CodedOutputStreamProtobufJsonWriter(JsonFormat.TypeRegistry typeRegistry) {
        JsonCodedOutputStreamFactory.verify();
        this.transcoder = new TranscodingProtobufJsonWriter(plans, typeRegistry);
    }

    @Override
    public void write(MessageOrBuilder message, JsonGenerator generator) throws IOException {
        Message m;
        if (message instanceof Message msg) {
            m = msg;
        } else {
            m = ((Message.Builder) message).buildPartial();
        }

        MessagePlan plan = plans.get(m.getDescriptorForType());
        if (plan.wellKnownType != null) {
            transcoder.writeWellKnown(plan.wellKnownType, m, generator);
        } else {
            new CodedOutputStreamJsonSink(plans, transcoder, generator).write(m);
        }
    }

    @Override
    public String writeAsString(MessageOrBuilder message) throws IOException {
        return ProtobufJsonWriterSupport.writeAsString(this, message);
    }
}
