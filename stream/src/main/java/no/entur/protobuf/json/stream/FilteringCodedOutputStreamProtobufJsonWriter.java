package no.entur.protobuf.json.stream;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.UnresolvedAny;
import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.plan.FilteredJsonValues;
import no.entur.protobuf.json.plan.MessagePlan;
import no.entur.protobuf.json.plan.MessagePlan.Plans;
import no.entur.protobuf.json.plan.ProtobufJsonWriterSupport;
import no.entur.protobuf.json.transcode.FilteringTranscodingProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * {@linkplain CodedOutputStreamProtobufJsonWriter} with filtering (anonymize and prune paths, max string length and max size).
 * Pruned messages are not serialized at all. The output is the same as for {@linkplain FilteringTranscodingProtobufJsonWriter},
 * to which it delegates if the stream writers cannot write messages with this protobuf version (see
 * {@linkplain CodedOutputStreamProtobufJsonWriter}).
 */

public class FilteringCodedOutputStreamProtobufJsonWriter implements ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> {

    private final Plans plans = new Plans();
    private final FilteringTranscodingProtobufJsonWriter transcoder;
    private final JsonFilter filter;
    private final boolean streaming;

    protected FilteringCodedOutputStreamProtobufJsonWriter(JsonFormat.TypeRegistry typeRegistry, JsonFilter filter) {
        this(typeRegistry, filter, UnresolvedAny.FAIL);
    }

    protected FilteringCodedOutputStreamProtobufJsonWriter(JsonFormat.TypeRegistry typeRegistry, JsonFilter filter, UnresolvedAny unresolvedAny) {
        this(typeRegistry, filter, unresolvedAny, JsonCodedOutputStreamFactory.isAvailable());
    }

    /**
     * @param typeRegistry  types for resolving google.protobuf.Any
     * @param filter        filter
     * @param unresolvedAny how to write google.protobuf.Any values of types which are not in the registry
     * @param streaming     false to delegate to the transcoding writer
     */

    FilteringCodedOutputStreamProtobufJsonWriter(JsonFormat.TypeRegistry typeRegistry, JsonFilter filter, UnresolvedAny unresolvedAny, boolean streaming) {
        this.filter = filter;
        this.transcoder = new FilteringTranscodingProtobufJsonWriter(plans, typeRegistry, filter, unresolvedAny);
        this.streaming = streaming;
    }

    public JsonFilter getFilter() {
        return filter;
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

        FilteredJsonValues values = new FilteredJsonValues(filter, generator);
        MessagePlan plan = plans.get(m.getDescriptorForType());
        try {
            if (plan.wellKnownType != null) {
                transcoder.writeWellKnown(plan.wellKnownType, m, values, null, 0, false);
            } else {
                new FilteringCodedOutputStreamJsonSink(plans, transcoder, values).write(m);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return !values.isStopped();
    }

    @Override
    public String writeAsString(MessageOrBuilder message) {
        return ProtobufJsonWriterSupport.writeAsString(this, message);
    }
}
