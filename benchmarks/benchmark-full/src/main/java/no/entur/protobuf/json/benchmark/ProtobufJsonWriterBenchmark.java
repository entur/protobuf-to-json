package no.entur.protobuf.json.benchmark;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.accessor.AccessorProtobufJsonWriter;
import no.entur.protobuf.json.jsonformat.JsonFormatProtobufJsonWriter;
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
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Compare JsonFormat and other libraries with the transcoding, stream and accessor writers, without filtering:
 * {@linkplain HubSpotJacksonDatatypeProtobuf} and {@linkplain CurioswitchProtobufJackson} (Jackson 2). All write the payloads like
 * JsonFormat does. Each benchmark writes a message as UTF-8 JSON to an output stream, via a JSON generator like a JSON log encoder does.
 */

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class ProtobufJsonWriterBenchmark {

    @Param({"small", "medium", "large", "xlarge", "xxlarge"})
    public String payload;

    private Message message;

    private final JsonFactory factory = JsonFactory.builder().build();
    private final ByteArrayOutputStream output = new ByteArrayOutputStream(1 << 20);

    // each writer in its own state, so that only the measured library is loaded and initialized in a fork

    @State(Scope.Benchmark)
    public static class JsonFormatState {
        final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = JsonFormatProtobufJsonWriter.create(RandomMessages.createTypeRegistry());
    }

    @State(Scope.Benchmark)
    public static class TranscodeState {
        final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(RandomMessages.createTypeRegistry()).build();
    }

    @State(Scope.Benchmark)
    public static class StreamState {
        final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = CodedOutputStreamProtobufJsonWriter.newBuilder().withTypeRegistry(RandomMessages.createTypeRegistry()).build();
    }

    @State(Scope.Benchmark)
    public static class AccessorState {
        final ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(RandomMessages.createTypeRegistry()).build();
    }

    @State(Scope.Benchmark)
    public static class HubSpotState {
        final JsonMapper mapper = HubSpotJacksonDatatypeProtobuf.createMapper();
    }

    @State(Scope.Benchmark)
    public static class CurioswitchState {
        final MessageMarshaller marshaller = CurioswitchProtobufJackson.createMarshaller();
    }

    @Setup
    public void setup() throws IOException {
        message = BenchmarkPayloads.create(payload);
        serializeToBinary(message);
    }

    /**
     * Serialize a message to binary, like an application which sends it (i.e. with gRPC) does. This affects how the JIT
     * compiles the generated serialization code, which the stream writer also uses: if the message types are never
     * serialized to binary, the stream writer is up to twice as slow.
     *
     * @param message message
     * @throws IOException if the message could not be written
     */

    static void serializeToBinary(Message message) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        // fewer times for larger messages, which make more calls each time
        int iterations = Math.max(1_000, Math.min(20_000, 200_000_000 / Math.max(1, message.getSerializedSize())));
        for (int i = 0; i < iterations; i++) {
            message.toByteArray();
            output.reset();
            message.writeTo(output);
        }
    }

    @Benchmark
    public int jsonFormat(JsonFormatState state) throws IOException {
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            state.writer.write(message, generator);
        }
        return output.size();
    }

    @Benchmark
    public int transcode(TranscodeState state) throws IOException {
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            state.writer.write(message, generator);
        }
        return output.size();
    }

    @Benchmark
    public int stream(StreamState state) throws IOException {
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            state.writer.write(message, generator);
        }
        return output.size();
    }

    @Benchmark
    public int accessor(AccessorState state) throws IOException {
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            state.writer.write(message, generator);
        }
        return output.size();
    }

    @Benchmark
    public int hubspot(HubSpotState state) {
        output.reset();
        try (JsonGenerator generator = state.mapper.createGenerator(output)) {
            state.mapper.writeValue(generator, message);
        }
        return output.size();
    }

    @Benchmark
    public int curioswitch(CurioswitchState state) throws IOException {
        // creates a (Jackson 2) generator for the output stream
        output.reset();
        state.marshaller.writeValue(message, output);
        return output.size();
    }

    @Benchmark
    public int curioswitchRawValue(CurioswitchState state) throws IOException {
        // within a Jackson 3 generator, like a JSON log encoder: as a raw value, which must be a String
        output.reset();
        try (JsonGenerator generator = factory.createGenerator(output)) {
            generator.writeRawValue(state.marshaller.writeValueAsString(message));
        }
        return output.size();
    }

    @Benchmark
    public int binary() {
        // reference
        return message.toByteArray().length;
    }
}
