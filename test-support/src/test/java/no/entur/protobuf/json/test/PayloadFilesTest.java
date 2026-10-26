package no.entur.protobuf.json.test;

import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

public class PayloadFilesTest {

    @Test
    public void payloadsAreTheSameEveryTime() {
        for (String name : BenchmarkPayloads.NAMES) {
            assertThat(BenchmarkPayloads.create(name).toByteArray()).isEqualTo(BenchmarkPayloads.create(name).toByteArray());
        }
    }

    @Test
    public void writesBinaryAndJson(@TempDir Path directory) throws Exception {
        PayloadFiles.write(directory);

        JsonFormat.Printer printer = JsonFormat.printer().usingTypeRegistry(RandomMessages.createTypeRegistry()).omittingInsignificantWhitespace();
        for (String name : BenchmarkPayloads.NAMES) {
            Message message = BenchmarkPayloads.create(name);
            byte[] binary = Files.readAllBytes(directory.resolve(name + ".binpb"));
            assertThat(message.getParserForType().parseFrom(binary)).isEqualTo(message);

            // not parsed, as JSON does not round-trip, i.e. a Value without a kind is written as null
            assertThat(Files.readString(directory.resolve(name + ".json"), StandardCharsets.UTF_8)).isEqualTo(printer.print(message));
        }

        assertThat(Files.readAllLines(directory.resolve(PayloadFiles.RANDOM_MESSAGES_FILE), StandardCharsets.UTF_8)).hasSize(PayloadFiles.RANDOM_MESSAGES);
        assertThat(Files.readAllLines(directory.resolve(PayloadFiles.FILTER_MESSAGES_FILE), StandardCharsets.UTF_8))
                .hasSize(3 + FilterTestMessages.create().size() + 10);

        List<String> lines = Files.readAllLines(directory.resolve(PayloadFiles.PROTOBUF_JACKSON_FILE), StandardCharsets.UTF_8);
        Map<String, Message> messages = ProtobufJacksonTestMessages.create();
        assertThat(lines).hasSize(messages.size());
        int i = 0;
        for (Map.Entry<String, Message> entry : messages.entrySet()) {
            String[] columns = lines.get(i++).split("\t", -1);
            Message message = entry.getValue();
            assertThat(columns).hasLength(5);
            assertThat(List.of(columns).subList(0, 4)).containsExactly(entry.getKey(), message.getDescriptorForType().getFullName(),
                    Base64.getEncoder().encodeToString(message.toByteArray()), printer.print(message)).inOrder();
        }
    }
}
