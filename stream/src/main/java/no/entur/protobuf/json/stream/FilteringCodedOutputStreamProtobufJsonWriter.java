package no.entur.protobuf.json.stream;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.plan.FilteredJsonValues;
import no.entur.protobuf.json.plan.MessagePlan;
import no.entur.protobuf.json.plan.MessagePlan.Plans;
import no.entur.protobuf.json.plan.ProtobufJsonWriterSupport;
import no.entur.protobuf.json.transcode.FilteringTranscodingProtobufJsonWriter;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;

/**
 * {@linkplain CodedOutputStreamProtobufJsonWriter} with filtering (anonymize and prune paths, max string length and max size).
 * Pruned messages are not serialized at all. The output is the same as for {@linkplain FilteringTranscodingProtobufJsonWriter}.
 */

public class FilteringCodedOutputStreamProtobufJsonWriter implements ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> {

    private final Plans plans = new Plans();
    private final FilteringTranscodingProtobufJsonWriter transcoder;
    private final JsonFilter filter;

    protected FilteringCodedOutputStreamProtobufJsonWriter(JsonFormat.TypeRegistry typeRegistry, JsonFilter filter) {
        JsonCodedOutputStreamFactory.verify();
        this.filter = filter;
        this.transcoder = new FilteringTranscodingProtobufJsonWriter(plans, typeRegistry, filter);
    }

    public JsonFilter getFilter() {
        return filter;
    }

    @Override
    public boolean write(MessageOrBuilder message, JsonGenerator generator) throws IOException {
        Message m;
        if (message instanceof Message msg) {
            m = msg;
        } else {
            m = ((Message.Builder) message).buildPartial();
        }

        FilteredJsonValues values = new FilteredJsonValues(filter, generator);
        MessagePlan plan = plans.get(m.getDescriptorForType());
        if (plan.wellKnownType != null) {
            transcoder.writeWellKnown(plan.wellKnownType, m, values, null, 0, false);
        } else {
            new FilteringCodedOutputStreamJsonSink(plans, transcoder, values).write(m);
        }
        return !values.isStopped();
    }

    @Override
    public String writeAsString(MessageOrBuilder message) throws IOException {
        return ProtobufJsonWriterSupport.writeAsString(this, message);
    }
}
