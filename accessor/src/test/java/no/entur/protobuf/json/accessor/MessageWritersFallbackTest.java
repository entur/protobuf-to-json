package no.entur.protobuf.json.accessor;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.google.common.truth.Truth.assertThat;

public class MessageWritersFallbackTest {

    /** Not a generated message, so writer generation fails. */
    public static final class NotAGeneratedMessage {
    }

    @Test
    public void logsAGenerationFailureOnce() {
        List<LogRecord> records = new ArrayList<>();
        Logger logger = Logger.getLogger(MessageWriters.class.getName());
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
        handler.setLevel(Level.ALL);
        Level previous = logger.getLevel();
        boolean previousUseParentHandlers = logger.getUseParentHandlers();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            MessageWriters.Generated generated = MessageWriters.get(NotAGeneratedMessage.class, false);
            MessageWriters.get(NotAGeneratedMessage.class, true);
            MessageWriters.get(NotAGeneratedMessage.class, false);

            assertThat(generated.transcoding()).isTrue();
            assertThat(generated.failure()).isNotNull();
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getLevel()).isEqualTo(Level.WARNING);
            assertThat(records.get(0).getMessage()).contains(NotAGeneratedMessage.class.getName());
            assertThat(records.get(0).getThrown()).isSameInstanceAs(generated.failure());
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previous);
            logger.setUseParentHandlers(previousUseParentHandlers);
        }
    }

    @Test
    public void doesNotLogExpectedFallbacks() {
        List<LogRecord> records = new ArrayList<>();
        Logger logger = Logger.getLogger(MessageWriters.class.getName());
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
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        try {
            MessageWriters.logFallback(Timestamp.class, null);
            MessageWriters.logFallback(DynamicMessage.class, new IllegalArgumentException("expected"));
            assertThat(records).isEmpty();
        } finally {
            logger.removeHandler(handler);
        }
    }
}
