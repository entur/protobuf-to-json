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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Like {@linkplain ProtobufJsonWriterBenchmark}, but writing a message as a String: {@code writeAsString()} for the writers
 * of this project (which write UTF-8, then decode it), JsonFormat's printer, and {@code writeValueAsString} for HubSpot
 * jackson-datatype-protobuf and curioswitch protobuf-jackson (which write characters).
 */

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class ProtobufJsonStringBenchmark {

    @Param({"small", "medium", "large", "xlarge", "xxlarge"})
    public String payload;

    private Message message;

    @Setup
    public void setup() throws IOException {
        message = BenchmarkPayloads.create(payload);
        ProtobufJsonWriterBenchmark.serializeToBinary(message);
    }

    @Benchmark
    public String jsonFormat(JsonFormatState state) throws IOException {
        return state.writer.writeAsString(message);
    }

    @Benchmark
    public String transcode(TranscodeState state) throws IOException {
        return state.writer.writeAsString(message);
    }

    @Benchmark
    public String stream(StreamState state) throws IOException {
        return state.writer.writeAsString(message);
    }

    @Benchmark
    public String accessor(AccessorState state) throws IOException {
        return state.writer.writeAsString(message);
    }

    @Benchmark
    public String hubspot(HubSpotState state) {
        return state.mapper.writeValueAsString(message);
    }

    @Benchmark
    public String curioswitch(CurioswitchState state) throws IOException {
        return state.marshaller.writeValueAsString(message);
    }
}
