package no.entur.protobuf.json.accessor;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.UnresolvedAny;
import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.plan.FilteredJsonValues;
import no.entur.protobuf.json.plan.MessagePlan.Plans;
import no.entur.protobuf.json.plan.ProtobufJsonWriterSupport;
import no.entur.protobuf.json.transcode.FilteringTranscodingProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * {@linkplain AccessorProtobufJsonWriter} with filtering (anonymize and prune paths, max string length and max size).
 * Pruned fields are not read, and writing stops at max size. The output is the same as for
 * {@linkplain FilteringTranscodingProtobufJsonWriter}.
 */

public class FilteringAccessorProtobufJsonWriter implements ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> {

    final Plans plans = new Plans();
    final FilteringTranscodingProtobufJsonWriter transcoder;
    private final JsonFilter filter;

    protected FilteringAccessorProtobufJsonWriter(JsonFormat.TypeRegistry typeRegistry, JsonFilter filter) {
        this(typeRegistry, filter, UnresolvedAny.FAIL);
    }

    protected FilteringAccessorProtobufJsonWriter(JsonFormat.TypeRegistry typeRegistry, JsonFilter filter, UnresolvedAny unresolvedAny) {
        this.filter = filter;
        this.transcoder = new FilteringTranscodingProtobufJsonWriter(plans, typeRegistry, filter, unresolvedAny);
    }

    public JsonFilter getFilter() {
        return filter;
    }

    @Override
    public boolean write(MessageOrBuilder message, JsonGenerator generator) {
        Message m;
        if (message instanceof Message msg) {
            m = msg;
        } else {
            m = ((Message.Builder) message).buildPartial();
        }
        FilteredJsonValues values = new FilteredJsonValues(filter, generator);
        try {
            MessageWriters.getFilteringWriter(m.getClass()).write(values, null, 0, m, false, this);
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
