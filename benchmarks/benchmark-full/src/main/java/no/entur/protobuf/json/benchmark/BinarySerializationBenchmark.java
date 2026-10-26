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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Whether writing JSON slows down regular binary serialization of the same message types in the same JVM. The stream writer
 * runs the generated serialization code with its own CodedOutputStream, so the JIT sees one more CodedOutputStream type in that
 * code (making its call sites bimorphic or megamorphic). The transcoding writer uses protobuf's own toByteArray(). The accessor
 * writer and curioswitch protobuf-jackson generate code per message type which reads the fields with their accessors, without
 * a CodedOutputStream.
 * <br><br>
 * The setup writes the message as JSON (if a JSON writer is used) as often as it serializes it to binary, like an application
 * which logs every message it sends.
 */

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class BinarySerializationBenchmark {

    /**
     * The JSON writer also used for the same message types: none, stream, transcode, accessor or curioswitch. Or unrelated
     * code (regular expressions, formatting and a TreeMap), for reference: binary serialization of the large payload is compiled
     * less well when the JVM does anything else.
     */

    @Param({"none", "unrelated", "stream", "transcode", "accessor", "curioswitch"})
    public String json;

    /**
     * How the message is serialized to binary: toByteArray, or writeTo an output stream (like gRPC).
     */

    @Param({"toByteArray", "writeTo"})
    public String binary;

    @Param({"medium", "large"})
    public String payload;

    private Message message;
    private boolean toByteArray;

    private final ByteArrayOutputStream output = new ByteArrayOutputStream(1 << 20);

    @Setup
    public void setup() throws IOException {
        message = BenchmarkPayloads.create(payload);
        toByteArray = binary.equals("toByteArray");

        JsonFactory factory = JsonFactory.builder().build();
        ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream(1 << 20);
        JsonWrite jsonWrite = switch (json) {
            case "stream", "transcode", "accessor" -> {
                ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = switch (json) {
                    case "stream" -> CodedOutputStreamProtobufJsonWriter.newBuilder().withTypeRegistry(RandomMessages.createTypeRegistry()).build();
                    case "transcode" -> TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(RandomMessages.createTypeRegistry()).build();
                    default -> AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(RandomMessages.createTypeRegistry()).build();
                };
                yield () -> {
                    jsonOutput.reset();
                    try (JsonGenerator generator = factory.createGenerator(jsonOutput)) {
                        writer.write(message, generator);
                    }
                };
            }
            case "curioswitch" -> {
                MessageMarshaller marshaller = CurioswitchProtobufJackson.createMarshaller();
                yield () -> {
                    jsonOutput.reset();
                    marshaller.writeValue(message, jsonOutput);
                };
            }
            case "unrelated" -> {
                Pattern pattern = Pattern.compile("([a-z]+)\\.([A-Z][a-z]*)+");
                Random random = new Random(1);
                yield () -> {
                    Map<String, Integer> map = new TreeMap<>();
                    for (int i = 0; i < 200; i++) {
                        String key = String.format("key-%d.Value%s", random.nextInt(1000), Integer.toHexString(i));
                        map.merge(key, pattern.matcher(key).find() ? 1 : 0, Integer::sum);
                    }
                    jsonOutput.reset();
                    jsonOutput.write(map.toString().getBytes(StandardCharsets.UTF_8));
                };
            }
            default -> null;
        };
        for (int i = 0; i < 20_000; i++) {
            serialize();
            if (jsonWrite != null) {
                jsonWrite.write();
            }
        }
    }

    private interface JsonWrite {

        void write() throws IOException;
    }

    @Benchmark
    public int serialize() throws IOException {
        if (toByteArray) {
            return message.toByteArray().length;
        }
        output.reset();
        message.writeTo(output);
        return output.size();
    }
}
