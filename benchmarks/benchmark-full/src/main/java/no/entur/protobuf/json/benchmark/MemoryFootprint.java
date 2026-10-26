package no.entur.protobuf.json.benchmark;

import com.google.protobuf.ApiProto;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.StructProto;
import com.google.protobuf.TypeProto;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf_test_messages.proto2.TestMessagesProto2;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.accessor.AccessorProtobufJsonWriter;
import no.entur.protobuf.json.jsonformat.JsonFormatProtobufJsonWriter;
import no.entur.protobuf.json.stream.CodedOutputStreamProtobufJsonWriter;
import no.entur.protobuf.json.test.BenchmarkPayloads;
import no.entur.protobuf.json.test.ProtobufJacksonTestMessages;
import no.entur.protobuf.json.test.RandomMessages;
import no.entur.protobuf.json.test.protobufjackson.GithubApi;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto;
import no.entur.protobuf.json.transcode.TranscodingProtobufJsonWriter;
import org.curioswitch.common.protobuf.json.MessageMarshaller;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Memory used by a writer, besides what it allocates per message (see {@code -prof gc} of the JMH benchmarks): the classes it
 * loads or generates (metaspace), its JIT-compiled code (code cache) and the heap it keeps (i.e. caches per message type),
 * after writing a set of messages for a while. Relative to a baseline which only serializes the messages to binary.
 * <br><br>
 * Each writer runs in its own JVM, with the serial garbage collector for a precise heap size after a full GC:
 * <pre>
 * java -cp benchmarks/benchmark-full/target/benchmarks.jar no.entur.protobuf.json.benchmark.MemoryFootprint
 * </pre>
 * Message sets: {@code payloads}, the benchmark payloads; {@code descriptors}, the descriptors of protobuf's descriptor,
 * type, API and struct files and of the test messages as {@linkplain FileDescriptorProto}s, which have many message types;
 * and {@code all}: those, the protobuf-jackson test messages, and random protobuf conformance test messages.
 */

public class MemoryFootprint {

    private static final List<String> WRITERS = List.of("none", "jsonFormat", "hubspot", "curioswitch", "transcode", "stream", "accessor", "accessorFiltering");
    private static final List<String> MESSAGE_SETS = List.of("payloads", "descriptors", "all");

    private static final long WARMUP_NANOS = 5_000_000_000L;

    private interface Write {
        void write(Message message) throws IOException;
    }

    private record Snapshot(long classes, long metaspace, long codeCache, long heap) {

        static Snapshot take() throws InterruptedException {
            // let the JIT finish compiling
            Thread.sleep(3000);
            for (int i = 0; i < 3; i++) {
                System.gc();
            }
            long metaspace = 0;
            long codeCache = 0;
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                if (pool.getName().equals("Metaspace")) {
                    metaspace += pool.getUsage().getUsed();
                } else if (pool.getName().startsWith("CodeHeap") || pool.getName().equals("CodeCache")) {
                    codeCache += pool.getUsage().getUsed();
                }
            }
            return new Snapshot(ManagementFactory.getClassLoadingMXBean().getLoadedClassCount(), metaspace, codeCache,
                    ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        }

        Snapshot minus(Snapshot other) {
            return new Snapshot(classes - other.classes, metaspace - other.metaspace, codeCache - other.codeCache, heap - other.heap);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            runAll();
            return;
        }
        Snapshot snapshot;
        try {
            snapshot = measure(args[0], args[1]);
        } catch (RuntimeException e) {
            // i.e. curioswitch protobuf-jackson cannot write descriptors
            e.printStackTrace();
            System.out.println("unsupported");
            return;
        }
        System.out.println(snapshot.classes() + " " + snapshot.metaspace() + " " + snapshot.codeCache() + " " + snapshot.heap());
    }

    /**
     * Measure every writer and message set in its own JVM, and print the results as tables, relative to the baseline.
     */

    private static void runAll() throws Exception {
        String java = ProcessHandle.current().info().command().orElse("java");
        for (String messageSet : MESSAGE_SETS) {
            List<Message> messages = messages(messageSet);
            System.out.println();
            Set<Class<?>> types = messageTypes(messages);
            int fields = 0;
            for (Class<?> type : types) {
                fields += ((Message) type.getMethod("getDefaultInstance").invoke(null)).getDescriptorForType().getFields().size();
            }
            System.out.println("Messages: " + messageSet + ", " + types.size() + " message types with " + fields + " fields");
            System.out.println();
            System.out.println("| Writer | Loaded classes | Metaspace (KB) | Code cache (KB) | Heap (KB) |");
            System.out.println("|---|---|---|---|---|");
            Snapshot baseline = null;
            for (String writer : WRITERS) {
                Snapshot snapshot = run(java, writer, messageSet);
                if (baseline == null) {
                    baseline = snapshot;
                    continue;
                }
                if (snapshot == null) {
                    System.out.printf(Locale.ROOT, "| %s | not supported | | | |%n", writer);
                    continue;
                }
                Snapshot delta = snapshot.minus(baseline);
                System.out.printf(Locale.ROOT, "| %s | %d | %.0f | %.0f | %.0f |%n", writer, delta.classes(), delta.metaspace() / 1024.0, delta.codeCache() / 1024.0, delta.heap() / 1024.0);
            }
        }
    }

    private static Snapshot run(String java, String writer, String messageSet) throws Exception {
        List<String> command = List.of(java, "-XX:+UseSerialGC", "-Xmx1g", "-cp", System.getProperty("java.class.path"), MemoryFootprint.class.getName(), writer, messageSet);
        Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            line = reader.readLine();
        }
        if (process.waitFor() != 0 || line == null) {
            throw new IllegalStateException("Failed: " + command);
        }
        if (line.equals("unsupported")) {
            return null;
        }
        String[] values = line.split(" ");
        return new Snapshot(Long.parseLong(values[0]), Long.parseLong(values[1]), Long.parseLong(values[2]), Long.parseLong(values[3]));
    }

    private static Snapshot measure(String writer, String messageSet) throws Exception {
        List<Message> messages = messages(messageSet);
        JsonFormat.TypeRegistry typeRegistry = RandomMessages.createTypeRegistry();

        // like an application which sends the messages
        ByteArrayOutputStream binary = new ByteArrayOutputStream();
        for (int i = 0; i < 1000; i++) {
            for (Message message : messages) {
                message.toByteArray();
                binary.reset();
                message.writeTo(binary);
            }
        }

        // Jackson 3, which all but curioswitch use
        JsonFactory factory = JsonFactory.builder().build();
        ByteArrayOutputStream output = new ByteArrayOutputStream(1 << 20);
        try (JsonGenerator generator = factory.createGenerator(output)) {
            generator.writeStartObject();
            generator.writeName("a");
            generator.writeString("b");
            generator.writeEndObject();
        }

        Snapshot before = Snapshot.take();
        Write write = switch (writer) {
            case "none" -> message -> {
            };
            case "jsonFormat" -> generator(JsonFormatProtobufJsonWriter.create(typeRegistry), factory, output);
            case "transcode" -> generator(TranscodingProtobufJsonWriter.newBuilder().withTypeRegistry(typeRegistry).build(), factory, output);
            case "stream" -> generator(CodedOutputStreamProtobufJsonWriter.newBuilder().withTypeRegistry(typeRegistry).build(), factory, output);
            case "accessor" -> generator(AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(typeRegistry).build(), factory, output);
            case "accessorFiltering" -> generator(AccessorProtobufJsonWriter.newBuilder().withTypeRegistry(typeRegistry).withMaxSize(Integer.MAX_VALUE).build(), factory, output);
            case "hubspot" -> {
                JsonMapper mapper = HubSpotJacksonDatatypeProtobuf.createMapper();
                yield message -> {
                    output.reset();
                    mapper.writeValue(output, message);
                };
            }
            case "curioswitch" -> {
                MessageMarshaller.Builder builder = CurioswitchProtobufJackson.builder();
                if (!messageSet.equals("payloads")) {
                    builder.register(FileDescriptorProto.getDefaultInstance());
                }
                MessageMarshaller marshaller = builder.build();
                yield message -> {
                    output.reset();
                    marshaller.writeValue(message, output);
                };
            }
            default -> throw new IllegalArgumentException(writer);
        };
        long end = System.nanoTime() + WARMUP_NANOS;
        while (System.nanoTime() < end) {
            for (Message message : messages) {
                write.write(message);
            }
        }
        Snapshot after = Snapshot.take();
        // keep the writer
        write.write(messages.get(0));
        return after.minus(before);
    }

    private static Write generator(ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer, JsonFactory factory, ByteArrayOutputStream output) {
        return message -> {
            output.reset();
            try (JsonGenerator generator = factory.createGenerator(output)) {
                writer.write(message, generator);
            }
        };
    }

    static List<Message> messages(String messageSet) {
        List<Message> messages = new ArrayList<>();
        switch (messageSet) {
            case "payloads" -> {
                for (String name : BenchmarkPayloads.NAMES) {
                    messages.add(BenchmarkPayloads.create(name));
                }
            }
            case "descriptors" -> {
                for (var file : List.of(DescriptorProtos.getDescriptor(), TypeProto.getDescriptor(), ApiProto.getDescriptor(), StructProto.getDescriptor(),
                        TestMessagesProto2.getDescriptor(), TestMessagesProto3.getDescriptor(), GithubApi.getDescriptor(), JsonTestProto.getDescriptor())) {
                    messages.add(file.toProto());
                }
            }
            case "all" -> {
                messages.addAll(messages("payloads"));
                messages.addAll(messages("descriptors"));
                messages.addAll(ProtobufJacksonTestMessages.create().values());
                for (int seed = 0; seed < 20; seed++) {
                    messages.add(new RandomMessages(seed).fill(TestMessagesProto2.TestAllTypesProto2.newBuilder()));
                    messages.add(new RandomMessages(seed).fill(TestMessagesProto3.TestAllTypesProto3.newBuilder()));
                }
            }
            default -> throw new IllegalArgumentException(messageSet);
        }
        return messages;
    }

    /**
     * @return the message types of the messages and of the messages in their fields
     */

    static Set<Class<?>> messageTypes(Collection<Message> messages) {
        Set<Class<?>> types = new LinkedHashSet<>();
        for (Message message : messages) {
            collect(message, types);
        }
        return types;
    }

    private static void collect(Message message, Set<Class<?>> types) {
        types.add(message.getClass());
        for (Map.Entry<FieldDescriptor, Object> field : message.getAllFields().entrySet()) {
            FieldDescriptor descriptor = field.getKey();
            if (descriptor.getJavaType() != FieldDescriptor.JavaType.MESSAGE) {
                continue;
            }
            if (descriptor.isMapField()) {
                // the values of the map entries
                FieldDescriptor value = descriptor.getMessageType().findFieldByNumber(2);
                if (value.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
                    for (Object entry : (List<?>) field.getValue()) {
                        collect((Message) ((Message) entry).getField(value), types);
                    }
                }
            } else if (descriptor.isRepeated()) {
                for (Object value : (List<?>) field.getValue()) {
                    collect((Message) value, types);
                }
            } else {
                collect((Message) field.getValue(), types);
            }
        }
    }
}
