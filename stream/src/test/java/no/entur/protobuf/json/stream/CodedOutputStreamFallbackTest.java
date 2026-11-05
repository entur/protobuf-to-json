package no.entur.protobuf.json.stream;

import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.TestSupport;
import no.entur.protobuf.json.test.RandomMessages;
import no.entur.protobuf.json.transcode.FilteringTranscodingProtobufJsonWriter;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.google.common.truth.Truth.assertThat;

public class CodedOutputStreamFallbackTest {

    @Test
    public void delegatesToTheTranscodingWriter() throws Exception {
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> expected = TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(TestSupport.TYPE_REGISTRY).build();
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> fallback = new CodedOutputStreamProtobufJsonWriter(TestSupport.TYPE_REGISTRY, false);
        assertSameOutput(expected, fallback);
    }

    @Test
    public void filteringDelegatesToTheFilteringTranscodingWriter() throws Exception {
        FilteringTranscodingProtobufJsonWriter expected = (FilteringTranscodingProtobufJsonWriter) TranscodingProtobufJsonWriter.newBuilder()
                .withTypeRegistry(TestSupport.TYPE_REGISTRY)
                .withAnonymizePaths("$..optionalString")
                .withPruneKeys("optionalNestedMessage")
                .withMaxStringLength(5)
                .withMaxSize(400)
                .build();
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> fallback = new FilteringCodedOutputStreamProtobufJsonWriter(TestSupport.TYPE_REGISTRY, expected.getFilter(), false);
        assertSameOutput(expected, fallback);
    }

    @Test
    public void logsAFailedCheckAsAWarning() {
        List<LogRecord> records = new ArrayList<>();
        Logger logger = Logger.getLogger(JsonCodedOutputStreamFactory.class.getName());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            assertThat(JsonCodedOutputStreamFactory.check(() -> {
            })).isTrue();
            assertThat(records).isEmpty();

            IllegalStateException failure = new IllegalStateException("incompatible");
            assertThat(JsonCodedOutputStreamFactory.check(() -> {
                throw failure;
            })).isFalse();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getLevel()).isEqualTo(Level.WARNING);
            assertThat(records.get(0).getMessage()).contains("transcoding");
            assertThat(records.get(0).getThrown()).isSameInstanceAs(failure);
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }
    }

    private static void assertSameOutput(ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> expected, ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> actual) throws Exception {
        RandomMessages random = new RandomMessages(1);
        for (int i = 0; i < 50; i++) {
            TestAllTypesProto3 message = random.fill(TestAllTypesProto3.newBuilder());
            assertThat(TestSupport.writtenBytes(actual, message)).isEqualTo(TestSupport.writtenBytes(expected, message));
            assertThat(TestSupport.writtenChars(actual, message)).isEqualTo(TestSupport.writtenChars(expected, message));
        }
    }
}
