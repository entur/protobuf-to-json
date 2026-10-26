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
import java.util.concurrent.TimeUnit;

/**
 * How fast the lite writer writes to a byte-based JSON generator, depending on whether it has also written to a character-based
 * generator (i.e. with writeAsString) in the same JVM: the JIT compiles the writer for the kinds of generators it has written to.
 */

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class LiteWriterJitBenchmark {

    @Param({"false", "true"})
    public boolean alsoWrittenAsString;

    @Param({"medium", "xlarge"})
    public String payload;

    private MessageLite message;

    private final JsonFactory factory = new JsonFactory();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream(1 << 20);

    private final ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> writer = LitePayloads.newBuilder().build();

    @Setup
    public void setup() throws IOException {
        message = LitePayloads.load(payload);
        if (alsoWrittenAsString) {
            for (int i = 0; i < 100; i++) {
                writer.writeAsString(message);
            }
        }
    }

    @Benchmark
    public int lite() throws IOException {
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            writer.write(message, generator);
        }
        return output.size();
    }
}
