package no.entur.protobuf.json.accessor;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.plan.FilteredJsonValues;
import no.entur.protobuf.json.plan.MessagePlan.Plans;
import no.entur.protobuf.json.plan.ProtobufJsonWriterSupport;
import no.entur.protobuf.json.transcode.FilteringTranscodingProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;

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
        this.filter = filter;
        this.transcoder = new FilteringTranscodingProtobufJsonWriter(plans, typeRegistry, filter);
    }

    public JsonFilter getFilter() {
        return filter;
    }

    @Override
    public void write(MessageOrBuilder message, JsonGenerator generator) throws IOException {
        Message m;
        if (message instanceof Message msg) {
            m = msg;
        } else {
            m = ((Message.Builder) message).buildPartial();
        }
        MessageWriters.getFilteringWriter(m.getClass()).write(new FilteredJsonValues(filter, generator), null, 0, m, false, this);
    }

    @Override
    public String writeAsString(MessageOrBuilder message) throws IOException {
        return ProtobufJsonWriterSupport.writeAsString(this, message);
    }
}
