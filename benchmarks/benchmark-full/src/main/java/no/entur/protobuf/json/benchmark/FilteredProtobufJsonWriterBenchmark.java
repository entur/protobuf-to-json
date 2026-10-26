package no.entur.protobuf.json.benchmark;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.accessor.AccessorProtobufJsonWriter;
import no.entur.protobuf.json.stream.CodedOutputStreamProtobufJsonWriter;
import no.entur.protobuf.json.test.BenchmarkPayloads;
import no.entur.protobuf.json.test.RandomMessages;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
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
 * Filtered output of the transcoding, stream and accessor writers, which filter while writing, with the filters of {@linkplain BenchmarkFilters}.
 * Each benchmark writes a message to a (byte-based) JSON generator, like a JSON log encoder does.
 */

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class FilteredProtobufJsonWriterBenchmark {

    @Param({"small", "medium", "large", "xlarge", "xxlarge"})
    public String payload;

    @Param({"none", "maxStringLength", "maxSize", "anyPaths3", "mixedPaths5", "all"})
    public String filter;

    private Message message;

    private final JsonFactory factory = JsonFactory.builder().build();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream(1 << 20);

    private ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> transcodeWriter;
    private ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> streamWriter;
    private ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> accessorWriter;

    @Setup
    public void setup() throws IOException {
        message = BenchmarkPayloads.create(payload);
        ProtobufJsonWriterBenchmark.serializeToBinary(message);

        JsonFormat.TypeRegistry typeRegistry = RandomMessages.createTypeRegistry();
        String unfiltered = TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(typeRegistry).build().writeAsString(message);

        transcodeWriter = BenchmarkFilters.configure(TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(typeRegistry), filter, payload, unfiltered.length()).build();
        streamWriter = BenchmarkFilters.configure(CodedOutputStreamProtobufJsonWriter.newBuilder().withTypeRegistry(typeRegistry), filter, payload, unfiltered.length()).build();
        accessorWriter = BenchmarkFilters.configure(AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(typeRegistry), filter, payload, unfiltered.length()).build();

        // all write the same
        String transcoded = transcodeWriter.writeAsString(message);
        if (!transcoded.equals(streamWriter.writeAsString(message)) || !transcoded.equals(accessorWriter.writeAsString(message))) {
            throw new IllegalStateException("Expected the same output");
        }
    }

    @Benchmark
    public int transcode() throws IOException {
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            transcodeWriter.write(message, generator);
        }
        return output.size();
    }

    @Benchmark
    public int stream() throws IOException {
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            streamWriter.write(message, generator);
        }
        return output.size();
    }

    @Benchmark
    public int accessor() throws IOException {
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            accessorWriter.write(message, generator);
        }
        return output.size();
    }
}
