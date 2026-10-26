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
 * Filtered output of the lite writer, which filters while writing, with the filters of {@linkplain BenchmarkFilters}.
 * Each benchmark writes a message to a (byte-based) JSON generator.
 */

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class FilteredLiteProtobufJsonWriterBenchmark {

    @Param({"small", "medium", "large", "xlarge", "xxlarge"})
    public String payload;

    @Param({"none", "maxStringLength", "maxSize", "anyPaths3", "mixedPaths5", "all"})
    public String filter;

    private MessageLite message;

    private final JsonFactory factory = new JsonFactory();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream(1 << 20);

    private ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> liteWriter;

    @Setup
    public void setup() throws IOException {
        message = LitePayloads.load(payload);

        // with a byte-based generator, like in the benchmark: the JIT compiles the writer for the kinds of generators it has
        // written to, see LiteWriterJitBenchmark
        ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> unfiltered = LitePayloads.newBuilder().build();
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            unfiltered.write(message, generator);
        }
        int unfilteredSize = output.size();

        liteWriter = BenchmarkFilters.configure(LitePayloads.newBuilder(), filter, payload, unfilteredSize).build();
        LitePayloads.checkSize(lite());
    }

    @Benchmark
    public int lite() throws IOException {
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            liteWriter.write(message, generator);
        }
        return output.size();
    }
}
