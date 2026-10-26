package no.entur.protobuf.json.benchmark.lite;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import no.entur.protobuf.json.ProtobufJsonWriter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Lite messages as JSON: the reflection-based MessageLiteToJson (the baseline), which looks up all methods of every message
 * while writing it, compared with the lite writer, which finds the fields once per message class.
 * Note that the outputs differ (field order, int64 quoting and more).
 * Each benchmark writes a message as UTF-8 to an output stream, the JSON writers via a JSON generator.
 * <br><br>
 * For reference:
 * <ul>
 *     <li>messageLiteToString: {@code MessageLite.toString()}, the usual alternative for logging lite messages; protobuf's
 *     reflection-based text format, not JSON</li>
 *     <li>binary: the binary form</li>
 * </ul>
 */

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class LiteProtobufJsonWriterBenchmark {

    @Param({"small", "medium", "large", "xlarge", "xxlarge"})
    public String payload;

    private MessageLite message;

    private final JsonFactory factory = new JsonFactory();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream(1 << 20);

    private final ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> liteWriter = LitePayloads.newBuilder().build();

    @Setup
    public void setup() {
        // only the measured writer runs in a fork: running the others too (i.e. to check their output) changes how the JIT
        // compiles the reflection code they share, see the tests for their output
        message = LitePayloads.load(payload);
    }

    @Benchmark
    public int messageLiteToJson() throws IOException {
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            MessageLiteToJson.toString(generator, message);
        }
        return output.size();
    }

    @Benchmark
    public int lite() throws IOException {
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            liteWriter.write(message, generator);
        }
        return output.size();
    }

    @Benchmark
    public int messageLiteToString() {
        output.reset();
        output.writeBytes(message.toString().getBytes(StandardCharsets.UTF_8));
        return output.size();
    }

    @Benchmark
    public int binary() {
        // for reference
        return message.toByteArray().length;
    }
}
