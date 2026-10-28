package no.entur.protobuf.json.jsonformat;

import com.google.protobuf.Any;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf_test_messages.proto2.TestMessagesProto2.TestAllTypesProto2;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.test.RandomMessages;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

import java.io.IOException;
import java.io.StringWriter;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JsonFormatComparisonTest {

    private static final JsonFormat.TypeRegistry TYPE_REGISTRY = RandomMessages.createTypeRegistry();

    private static final JsonFactory FACTORY = new JsonFactory();

    /**
     * Writes the JsonFormat output through a Jackson generator, which escapes fewer characters than JsonFormat.
     */

    private static final class ReserializingWriter implements ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> {

        private final JsonFormat.Printer printer = JsonFormat.printer().usingTypeRegistry(TYPE_REGISTRY).omittingInsignificantWhitespace();

        @Override
        public boolean write(MessageOrBuilder message, JsonGenerator generator) throws IOException {
            try (JsonParser parser = FACTORY.createParser(ObjectReadContext.empty(), printer.print(message))) {
                parser.nextToken();
                generator.copyCurrentStructure(parser);
            }
            return true;
        }

        @Override
        public String writeAsString(MessageOrBuilder message) throws IOException {
            StringWriter output = new StringWriter();
            try (JsonGenerator generator = FACTORY.createGenerator(ObjectWriteContext.empty(), output)) {
                write(message, generator);
            }
            return output.toString();
        }
    }

    /**
     * Writes the same JSON for every message.
     */

    private record FixedWriter(String json) implements ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> {

        @Override
        public boolean write(MessageOrBuilder message, JsonGenerator generator) {
            generator.writeRawValue(json);
            return true;
        }

        @Override
        public String writeAsString(MessageOrBuilder message) {
            return json;
        }
    }

    private static JsonFormatComparison comparison(ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer) {
        return JsonFormatComparison.newBuilder().withTypeRegistry(TYPE_REGISTRY).withWriter(writer).build();
    }

    @Test
    public void ignoresDifferencesInEscaping() {
        JsonFormatComparison comparison = comparison(new ReserializingWriter());
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalString("a=b<c>&'d\n").build();

        JsonFormatComparison.Result result = comparison.compare(message);

        // JsonFormat escapes some characters which do not need escaping
        assertThat(result.getExpected()).contains("\\u003d");
        assertThat(result.getActual()).contains("a=b");
        assertThat(result.isSame()).isTrue();
        assertThat(result.getDifferences()).isEmpty();

        comparison.verify(message);
    }

    @Test
    public void ignoresPropertyOrder() {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalInt32(1).setOptionalString("a").build();

        JsonFormatComparison.Result result = comparison(new FixedWriter("{\"optionalString\":\"a\",\"optionalInt32\":1}")).compare(message);

        assertThat(result.isSame()).isTrue();
    }

    @Test
    public void reportsDifferences() {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalInt32(1).setOptionalString("a").build();
        JsonFormatComparison comparison = comparison(new FixedWriter("{\"optionalInt32\":2,\"optionalBool\":true}"));

        JsonFormatComparison.Result result = comparison.compare(message);

        assertThat(result.isSame()).isFalse();
        assertThat(String.join("\n", result.getDifferences())).contains("optionalInt32: expected 1, got 2");
        assertThat(String.join("\n", result.getDifferences())).contains("optionalBool: unexpected true");
        assertThat(String.join("\n", result.getDifferences())).contains("optionalString: missing");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> comparison.verify(message));
        assertThat(e).hasMessageThat().contains("optionalInt32");
    }

    @Test
    public void reportsDuplicateProperties() {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalInt32(1).build();

        JsonFormatComparison.Result result = comparison(new FixedWriter("{\"optionalInt32\":1,\"optionalInt32\":1}")).compare(message);

        assertThat(result.isSame()).isFalse();
        assertThat(String.join("\n", result.getDifferences())).contains("duplicate property");
    }

    @Test
    public void reportsWhenOnlyJsonFormatFails() {
        JsonFormatComparison comparison = comparison(new FixedWriter("{\"optionalValue\":\"NaN\"}"));
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalValue(Value.newBuilder().setNumberValue(Double.NaN))
                .build();

        JsonFormatComparison.Result result = comparison.compare(message);

        assertThat(result.isSame()).isFalse();
        assertThat(result.getExpectedFailure()).isNotNull();
        assertThat(result.getActual()).isEqualTo("{\"optionalValue\":\"NaN\"}");
        assertThat(result.getDifferences().get(0)).startsWith("JsonFormat failed");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> comparison.verify(message));
        assertThat(e).hasMessageThat().contains("JsonFormat failed");
    }

    @Test
    public void bothFailingIsTheSame() {
        // the type of the Any value is not in the type registry
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalAny(Any.pack(TestAllTypesProto2.getDefaultInstance()))
                .build();

        JsonFormatComparison.Result result = comparison(new ReserializingWriter()).compare(message);

        assertThat(result.getExpectedFailure()).isNotNull();
        assertThat(result.getActualFailure()).isNotNull();
        assertThat(result.isSame()).isTrue();
    }

    @Test
    public void verifyRandomMessages() {
        JsonFormatComparison jsonFormat = comparison(JsonFormatProtobufJsonWriter.create(TYPE_REGISTRY));
        JsonFormatComparison reserializing = comparison(new ReserializingWriter());
        for (int seed = 0; seed < 200; seed++) {
            TestAllTypesProto3 proto3 = new RandomMessages(seed).fill(TestAllTypesProto3.newBuilder());
            TestAllTypesProto2 proto2 = new RandomMessages(seed).fill(TestAllTypesProto2.newBuilder());

            jsonFormat.verify(proto3);
            jsonFormat.verify(proto2);
            reserializing.verify(proto3);
            reserializing.verify(proto2);
        }
    }
}
