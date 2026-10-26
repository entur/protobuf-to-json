package no.entur.protobuf.json.benchmark;

import com.google.protobuf.Message;
import no.entur.protobuf.json.benchmark.ProtobufJsonWriterBenchmark.AccessorState;
import no.entur.protobuf.json.benchmark.ProtobufJsonWriterBenchmark.CurioswitchState;
import no.entur.protobuf.json.benchmark.ProtobufJsonWriterBenchmark.HubSpotState;
import no.entur.protobuf.json.benchmark.ProtobufJsonWriterBenchmark.JsonFormatState;
import no.entur.protobuf.json.benchmark.ProtobufJsonWriterBenchmark.StreamState;
import no.entur.protobuf.json.benchmark.ProtobufJsonWriterBenchmark.TranscodeState;
import no.entur.protobuf.json.test.BenchmarkPayloads;
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
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.json.JsonFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Serializing a message to binary with {@code toByteArray()} and then writing it as JSON, in sequence, like an application
 * which sends a message and logs it. The JSON is written like in {@linkplain ProtobufJsonWriterBenchmark}. As both are done in
 * every operation, the results also show how the two affect each other in the JIT, see {@linkplain BinarySerializationBenchmark}.
 */

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class BinaryAndJsonBenchmark {

    @Param({"small", "medium", "large", "xlarge", "xxlarge"})
    public String payload;

    private Message message;

    private final JsonFactory factory = JsonFactory.builder().build();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream(1 << 20);

    @Setup
    public void setup() {
        message = BenchmarkPayloads.create(payload);
    }

    @Benchmark
    public int binary() {
        // reference: binary only
        return message.toByteArray().length;
    }

    @Benchmark
    public int jsonFormat(JsonFormatState state) throws IOException {
        int size = message.toByteArray().length;
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            state.writer.write(message, generator);
        }
        return size + output.size();
    }

    @Benchmark
    public int hubspot(HubSpotState state) {
        int size = message.toByteArray().length;
        output.reset();
        try (JsonGenerator generator = state.mapper.createGenerator(output)) {
            state.mapper.writeValue(generator, message);
        }
        return size + output.size();
    }

    @Benchmark
    public int curioswitch(CurioswitchState state) throws IOException {
        int size = message.toByteArray().length;
        output.reset();
        state.marshaller.writeValue(message, output);
        return size + output.size();
    }

    @Benchmark
    public int transcode(TranscodeState state) throws IOException {
        int size = message.toByteArray().length;
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            state.writer.write(message, generator);
        }
        return size + output.size();
    }

    @Benchmark
    public int stream(StreamState state) throws IOException {
        int size = message.toByteArray().length;
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            state.writer.write(message, generator);
        }
        return size + output.size();
    }

    @Benchmark
    public int accessor(AccessorState state) throws IOException {
        int size = message.toByteArray().length;
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            state.writer.write(message, generator);
        }
        return size + output.size();
    }
}
