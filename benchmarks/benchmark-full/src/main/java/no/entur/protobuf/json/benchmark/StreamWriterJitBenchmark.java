package no.entur.protobuf.json.benchmark;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.accessor.AccessorProtobufJsonWriter;
import no.entur.protobuf.json.stream.CodedOutputStreamProtobufJsonWriter;
import no.entur.protobuf.json.test.BenchmarkPayloads;
import no.entur.protobuf.json.test.RandomMessages;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import org.curioswitch.common.protobuf.json.MessageMarshaller;
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
 * How fast the stream writer is, depending on whether the message types are also serialized to binary in the same JVM.
 * The stream writer runs the generated serialization code of the messages with its own CodedOutputStream, and the JIT compiles
 * that code for the CodedOutputStream types it has seen. For comparison: the transcoding writer, which serializes to binary
 * itself, and the accessor writer and curioswitch protobuf-jackson, which read the fields with their accessors.
 * See BinarySerializationBenchmark for the other way around.
 */

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class StreamWriterJitBenchmark {

    @Param({"none", "toByteArrayOnce", "toByteArray", "toByteArrayAndWriteTo"})
    public String binarySerialization;

    @Param({"medium", "large"})
    public String payload;

    private Message message;

    private final JsonFactory factory = JsonFactory.builder().build();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream(1 << 20);

    private final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> streamWriter = CodedOutputStreamProtobufJsonWriter.newBuilder().withTypeRegistry(RandomMessages.createTypeRegistry()).build();
    private final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> transcodeWriter = TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(RandomMessages.createTypeRegistry()).build();
    private final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> accessorWriter = AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(RandomMessages.createTypeRegistry()).build();
    private final MessageMarshaller curioswitchMarshaller = CurioswitchProtobufJackson.createMarshaller();

    @Setup
    public void setup() throws IOException {
        message = BenchmarkPayloads.create(payload);

        ByteArrayOutputStream binary = new ByteArrayOutputStream();
        int iterations = binarySerialization.equals("toByteArrayOnce") ? 1 : 20_000;
        for (int i = 0; i < iterations; i++) {
            if (!binarySerialization.equals("none")) {
                message.toByteArray();
            }
            if (binarySerialization.equals("toByteArrayAndWriteTo")) {
                // like gRPC
                binary.reset();
                message.writeTo(binary);
            }
        }
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
    public int transcode() throws IOException {
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            transcodeWriter.write(message, generator);
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

    @Benchmark
    public int curioswitch() throws IOException {
        // creates a (Jackson 2) generator for the output stream
        output.reset();
        curioswitchMarshaller.writeValue(message, output);
        return output.size();
    }
}
