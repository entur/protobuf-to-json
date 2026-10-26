package no.entur.protobuf.json;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf_test_messages.proto2.TestMessagesProto2.TestAllTypesProto2;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.jsonformat.JsonFormatComparison;
import no.entur.protobuf.json.test.RandomMessages;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;

import java.util.function.Supplier;

/**
 * Compare the output of a {@linkplain ProtobufJsonWriter} with {@linkplain JsonFormat} for random messages
 * of the protobuf conformance test types.
 */

public abstract class AbstractJsonFormatConformanceTest {

    private static final int ITERATIONS = 2000;

    protected abstract ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer();

    @Test
    public void proto3() {
        compareRandomMessages(TestAllTypesProto3::newBuilder);
    }

    @Test
    public void proto2() {
        compareRandomMessages(TestAllTypesProto2::newBuilder);
    }

    private void compareRandomMessages(Supplier<Message.Builder> builders) {
        JsonFormatComparison comparison = TestSupport.comparison(writer());
        for (int seed = 0; seed < ITERATIONS; seed++) {
            Message message = new RandomMessages(seed).fill(builders.get());

            TestSupport.assertSameAsJsonFormat(comparison, message, "seed " + seed);
        }
    }
}
