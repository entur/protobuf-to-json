package no.entur.protobuf.json;

import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.jsonformat.JsonFormatComparison;
import no.entur.protobuf.json.stream.CodedOutputStreamProtobufJsonWriter;
import no.entur.protobuf.json.test.RandomMessages;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;

import static com.google.common.truth.Truth.assertThat;

public class CodedOutputStreamJsonFormatConformanceTest extends AbstractJsonFormatConformanceTest {

    @Override
    protected ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer() {
        return CodedOutputStreamProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build();
    }

    @Test
    public void verify() {
        JsonFormatComparison comparison = TestSupport.comparison(writer());
        comparison.verify(new RandomMessages(1).fill(com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3.newBuilder()));
    }
}
