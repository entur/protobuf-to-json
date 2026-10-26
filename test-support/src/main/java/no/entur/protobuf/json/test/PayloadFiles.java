package no.entur.protobuf.json.test;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the test messages as files, for the lite benchmarks and tests, which cannot have the full protobuf runtime on their classpath:
 * <ul>
 *     <li>the {@linkplain BenchmarkPayloads}: {@code <name>.binpb} with the binary form, and {@code <name>.json} with the JsonFormat output</li>
 *     <li>the {@linkplain ProtobufJacksonTestMessages}: {@code protobuf-jackson.tsv}, a line per message with its name, its type,
 *     its binary form in base64, the JsonFormat output, and the locations of the strings which max string length does not apply
 *     to (see {@linkplain UntruncatedValues})</li>
 *     <li>random messages ({@linkplain RandomMessages}): {@code random-messages.tsv}, in the same format</li>
 *     <li>messages for the filter tests: {@code filter-messages.tsv}, in the same format: the small, medium and large benchmark
 *     payloads, the {@linkplain FilterTestMessages} and ten random messages</li>
 * </ul>
 * Run by the builds of the lite modules (with exec-maven-plugin, so that the full protobuf runtime stays off their classpath),
 * so that the files are not part of the sources.
 */

public final class PayloadFiles {

    public static final String PROTOBUF_JACKSON_FILE = "protobuf-jackson.tsv";
    public static final String RANDOM_MESSAGES_FILE = "random-messages.tsv";
    public static final String FILTER_MESSAGES_FILE = "filter-messages.tsv";

    public static final int RANDOM_MESSAGES = 100;

    private PayloadFiles() {
    }

    /**
     * @param args the directory to write to
     * @throws IOException if a file could not be written
     */

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected the directory to write to");
        }
        write(Paths.get(args[0]));
    }

    public static void write(Path directory) throws IOException {
        Files.createDirectories(directory);

        JsonFormat.Printer printer = JsonFormat.printer().usingTypeRegistry(RandomMessages.createTypeRegistry()).omittingInsignificantWhitespace();
        for (String name : BenchmarkPayloads.NAMES) {
            Message message = BenchmarkPayloads.create(name);
            Files.write(directory.resolve(name + ".binpb"), message.toByteArray());
            Files.writeString(directory.resolve(name + ".json"), printer.print(message), StandardCharsets.UTF_8);
        }

        writeLines(directory.resolve(PROTOBUF_JACKSON_FILE), ProtobufJacksonTestMessages.create(), printer);

        Map<String, Message> randomMessages = new LinkedHashMap<>();
        for (int seed = 0; seed < RANDOM_MESSAGES; seed++) {
            randomMessages.put("seed-" + seed, new RandomMessages(seed).fill(TestAllTypesProto3.newBuilder()));
        }
        writeLines(directory.resolve(RANDOM_MESSAGES_FILE), randomMessages, printer);

        Map<String, Message> filterMessages = new LinkedHashMap<>();
        // the larger payloads are like the medium payload, and would make the tests slow
        for (String name : List.of("small", "medium", "large")) {
            filterMessages.put(name, BenchmarkPayloads.create(name));
        }
        filterMessages.putAll(FilterTestMessages.create());
        for (int seed = 0; seed < 10; seed++) {
            filterMessages.put("seed-" + seed, randomMessages.get("seed-" + seed));
        }
        writeLines(directory.resolve(FILTER_MESSAGES_FILE), filterMessages, printer);
    }

    private static void writeLines(Path file, Map<String, Message> messages, JsonFormat.Printer printer) throws IOException {
        StringBuilder lines = new StringBuilder();
        for (Map.Entry<String, Message> entry : messages.entrySet()) {
            Message message = entry.getValue();
            String json = printer.print(message);
            // compact JSON has no line breaks or tabs
            lines.append(entry.getKey()).append('\t')
                    .append(message.getDescriptorForType().getFullName()).append('\t')
                    .append(Base64.getEncoder().encodeToString(message.toByteArray())).append('\t')
                    .append(json).append('\t')
                    .append(UntruncatedValues.toJson(UntruncatedValues.find(json, message.getDescriptorForType(), RandomMessages.createTypeRegistry())))
                    .append('\n');
        }
        Files.writeString(file, lines, StandardCharsets.UTF_8);
    }
}
