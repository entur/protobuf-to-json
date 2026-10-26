package no.entur.protobuf.json.lite;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.Int64Value;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Type;
import com.google.protobuf_test_messages.proto2.TestMessagesProto2.TestAllTypesProto2;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3.NestedEnum;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3.NestedMessage;
import no.entur.protobuf.json.ProtobufJsonWriter;
import no.entur.protobuf.json.filter.ReferenceFilter;
import no.entur.protobuf.json.path.PathExpressions;
import no.entur.protobuf.json.test.protobufjackson.GithubApi.SearchResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

/**
 * The filtering writer writes the same as filtering the output of the plain writer with the {@linkplain ReferenceFilter},
 * for char-based generators, and for byte-based (UTF-8) generators with surrogate pairs escaped (the Jackson 2 default) or combined.
 */

public class FilteringLiteProtobufJsonWriterTest {

    private static final int[] MAX_STRING_LENGTHS = {-1, -1, 0, 1, 3, 8, 20, 50};

    private final ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> unfiltered = LiteTestSupport.writer();

    record Filter(List<String> anonymizes, List<String> prunes, int maxStringLength, int maxSize) {

        static Filter anonymize(String... paths) {
            return new Filter(Arrays.asList(paths), List.of(), -1, -1);
        }

        static Filter prune(String... paths) {
            return new Filter(List.of(), Arrays.asList(paths), -1, -1);
        }

        Filter withMaxStringLength(int maxStringLength) {
            return new Filter(anonymizes, prunes, maxStringLength, maxSize);
        }

        Filter withMaxSize(int maxSize) {
            return new Filter(anonymizes, prunes, maxStringLength, maxSize);
        }

        boolean isEmpty() {
            return anonymizes.isEmpty() && prunes.isEmpty() && maxStringLength == -1 && maxSize == -1;
        }

        ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> build() {
            return LiteTestSupport.newBuilder()
                    .withAnonymizePaths(anonymizes)
                    .withPrunePaths(prunes)
                    .withMaxStringLength(maxStringLength)
                    .withMaxSize(maxSize)
                    .build();
        }

        ReferenceFilter toReferenceFilter() {
            return new ReferenceFilter(anonymizes, prunes, maxStringLength, maxSize);
        }
    }

    /**
     * A message for the filter tests, with the locations of the strings which max string length does not apply to.
     */

    record TestMessage(String name, MessageLite message, Set<List<Object>> untruncated) {
    }

    /**
     * The benchmark payloads, messages with all kinds of values (including strings with surrogate pairs and escapes) and random
     * messages, written during the build from the full protobuf messages (see FilterTestMessages and PayloadFiles in the
     * test-support module).
     */

    static List<TestMessage> messages() throws Exception {
        Map<String, MessageLite> types = new HashMap<>();
        for (MessageLite type : new MessageLite[] {TestAllTypesProto3.getDefaultInstance(), TestAllTypesProto2.getDefaultInstance(),
                SearchResponse.getDefaultInstance(), Type.getDefaultInstance(), Struct.getDefaultInstance(), Timestamp.getDefaultInstance()}) {
            types.put(type.getClass().getName(), type);
        }
        Map<String, String> javaNames = Map.of(
                "protobuf_test_messages.proto3.TestAllTypesProto3", TestAllTypesProto3.class.getName(),
                "protobuf_test_messages.proto2.TestAllTypesProto2", TestAllTypesProto2.class.getName(),
                "curioswitch.protobufjson.test.SearchResponse", SearchResponse.class.getName(),
                "google.protobuf.Type", Type.class.getName(),
                "google.protobuf.Struct", Struct.class.getName(),
                "google.protobuf.Timestamp", Timestamp.class.getName());

        List<TestMessage> messages = new ArrayList<>();
        for (String[] columns : LiteTestSupport.readMessages("/payloads/filter-messages.tsv")) {
            MessageLite type = types.get(javaNames.get(columns[1]));
            assertWithMessage(columns[1]).that(type).isNotNull();
            MessageLite message = type.getParserForType().parseFrom(Base64.getDecoder().decode(columns[2]));
            List<List<Object>> untruncated = LiteTestSupport.MAPPER.readValue(columns[4], new TypeReference<List<List<Object>>>() {
            });
            messages.add(new TestMessage(columns[0], message, new HashSet<>(untruncated)));
        }
        return messages;
    }

    @Test
    public void sameAsReferenceFilter() throws Exception {
        Random random = new Random(1);
        int compared = 0;
        int changed = 0;
        for (TestMessage message : messages()) {
            String json = LiteTestSupport.writeChars(unfiltered, message.message());
            List<String> paths = paths(json);
            int size = json.getBytes(StandardCharsets.UTF_8).length;
            for (int i = 0; i < 300; i++) {
                Filter filter = randomFilter(random, paths, size);
                if (filter.isEmpty()) {
                    continue;
                }
                if (!assertSameAsReferenceFilter(message.message(), json, filter, message.untruncated()).equals(json)) {
                    changed++;
                }
                compared++;
            }
        }
        assertThat(compared).isGreaterThan(1500);
        // the filters do filter
        assertThat(changed).isGreaterThan(compared / 2);
    }

    /**
     * Check a filter against the reference filter.
     *
     * @return the output (chars)
     */

    private String assertSameAsReferenceFilter(MessageLiteOrBuilder message, String json, Filter filter, Set<List<Object>> untruncated) throws Exception {
        ReferenceFilter referenceFilter = filter.toReferenceFilter();

        ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> writer = filter.build();
        String actualChars = LiteTestSupport.writeChars(writer, message);
        String actualBytes = LiteTestSupport.writeBytes(writer, message, true);
        // surrogate pairs escaped (the Jackson 2 default) take more room
        String escaped = LiteTestSupport.writeBytes(writer, message, false);

        String description = filter + " " + json;
        assertWithMessage("chars " + description).that(actualChars).isEqualTo(referenceFilter.process(json, LiteTestSupport.CHARS, untruncated));
        assertWithMessage("bytes " + description).that(actualBytes).isEqualTo(referenceFilter.process(json, LiteTestSupport.UTF8, untruncated));
        assertWithMessage("escaped " + description).that(escaped).isEqualTo(referenceFilter.process(json, LiteTestSupport.UTF8_ESCAPED_SURROGATES, untruncated));

        if (filter.maxSize() != -1 && (json.startsWith("{") || json.startsWith("["))) {
            assertWithMessage("chars " + description).that(actualChars.length()).isAtMost(filter.maxSize());
            assertWithMessage("bytes " + description).that(actualBytes.getBytes(StandardCharsets.UTF_8).length).isAtMost(filter.maxSize());
            assertWithMessage("escaped " + description).that(escaped.getBytes(StandardCharsets.UTF_8).length).isAtMost(filter.maxSize());
        }
        return actualChars;
    }

    private String write(MessageLiteOrBuilder message, Filter filter) throws Exception {
        return write(message, filter, Set.of());
    }

    /**
     * @param untruncated locations of the strings which max string length does not apply to
     */

    private String write(MessageLiteOrBuilder message, Filter filter, Set<List<Object>> untruncated) throws Exception {
        return assertSameAsReferenceFilter(message, LiteTestSupport.writeChars(unfiltered, message), filter, untruncated);
    }

    @Test
    public void noOpFiltersWriteTheSameAsThePlainWriter() throws Exception {
        ProtobufJsonWriter<MessageLiteOrBuilder, JsonGenerator> writer = LiteTestSupport.newBuilder()
                .withMaxSize(Integer.MAX_VALUE)
                .withAnonymizePaths("$.notAField", "$.*.notAField")
                .withPruneKeys("notAField")
                .withMaxStringLength(1_000_000)
                .build();
        assertThat(writer).isInstanceOf(FilteringLiteProtobufJsonWriter.class);

        for (TestMessage testMessage : messages()) {
            MessageLite message = testMessage.message();
            assertThat(LiteTestSupport.writeChars(writer, message)).isEqualTo(LiteTestSupport.writeChars(unfiltered, message));
            assertThat(LiteTestSupport.writeBytes(writer, message, true)).isEqualTo(LiteTestSupport.writeBytes(unfiltered, message, true));
            assertThat(LiteTestSupport.writeBytes(writer, message, false)).isEqualTo(LiteTestSupport.writeBytes(unfiltered, message, false));
            assertThat(writer.writeAsString(message)).isEqualTo(unfiltered.writeAsString(message));
        }
    }

    @Test
    public void pruneAndAnonymize() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalInt32(5)
                .setOptionalNestedMessage(NestedMessage.newBuilder().setA(1).setCorecursive(TestAllTypesProto3.newBuilder().setOptionalString("x")))
                .addRepeatedNestedEnumValue(1)
                .addRepeatedNestedEnumValue(99)
                .putMapStringString("a", "1")
                .putMapStringString("b", "2")
                .setOptionalAny(Any.newBuilder()
                        .setTypeUrl(LiteTestSupport.TEST_ALL_TYPES_PROTO3_URL)
                        .setValue(TestAllTypesProto3.newBuilder().setOptionalString("y").build().toByteString()))
                .build();

        assertThat(write(message, Filter.prune("$.optionalNestedMessage", "$.mapStringString.a", "$.optionalAny.@type")))
                .isEqualTo("{\"optionalInt32\":5,\"optionalNestedMessage\":\"[removed]\",\"repeatedNestedEnum\":[\"BAR\",99],\"mapStringString\":{\"a\":\"[removed]\",\"b\":\"2\"},"
                        + "\"optionalAny\":{\"@type\":\"[removed]\",\"optionalString\":\"y\"}}");
        assertThat(write(message, Filter.anonymize("$.optionalNestedMessage", "$.repeatedNestedEnum", "$..optionalString")))
                .isEqualTo("{\"optionalInt32\":5,\"optionalNestedMessage\":{\"a\":\"*\",\"corecursive\":{\"optionalString\":\"*\"}},\"repeatedNestedEnum\":[\"*\",\"*\"],"
                        + "\"mapStringString\":{\"a\":\"1\",\"b\":\"2\"},\"optionalAny\":{\"@type\":\"type.googleapis.com/protobuf_test_messages.proto3.TestAllTypesProto3\",\"optionalString\":\"*\"}}");
        // prune takes precedence
        assertThat(write(message, new Filter(List.of("$..a"), List.of("$.mapStringString.a"), -1, -1)))
                .isEqualTo("{\"optionalInt32\":5,\"optionalNestedMessage\":{\"a\":\"*\",\"corecursive\":{\"optionalString\":\"x\"}},\"repeatedNestedEnum\":[\"BAR\",99],"
                        + "\"mapStringString\":{\"a\":\"[removed]\",\"b\":\"2\"},\"optionalAny\":{\"@type\":\"type.googleapis.com/protobuf_test_messages.proto3.TestAllTypesProto3\",\"optionalString\":\"y\"}}");
    }

    @Test
    public void maxStringLength() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalInt64(Long.MIN_VALUE)
                .setOptionalString("abcd😀" + "x".repeat(20))
                .setOptionalBytes(ByteString.copyFrom(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}))
                .setOptionalNestedEnum(NestedEnum.BAZ)
                .putMapStringString("a very long key, which is never truncated", "v")
                .setOptionalTimestamp(Timestamp.newBuilder().setSeconds(1700000000))
                .build();

        Set<List<Object>> untruncated = Set.of(List.of("optionalInt64"), List.of("optionalNestedEnum"), List.of("optionalTimestamp"));
        assertThat(write(message, new Filter(List.of(), List.of(), 5, -1), untruncated))
                .isEqualTo("{\"optionalInt64\":\"-9223372036854775808\",\"optionalString\":\"abcd... + 22\",\"optionalBytes\":\"AAECA... + 11\",\"optionalNestedEnum\":\"BAZ\","
                        + "\"mapStringString\":{\"a very long key, which is never truncated\":\"v\"},\"optionalTimestamp\":\"2023-11-14T22:13:20Z\"}");
        for (int maxStringLength = 0; maxStringLength < 40; maxStringLength++) {
            write(message, new Filter(List.of(), List.of(), maxStringLength, -1), untruncated);
        }
    }

    @Test
    public void maxStringLengthDoesNotApplyToValuesWhichAreNotText() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalInt64(Long.MIN_VALUE)
                .setOptionalSfixed64(Long.MAX_VALUE)
                .setOptionalFloat(Float.NEGATIVE_INFINITY)
                .setOptionalDouble(Double.NaN)
                .setOptionalNestedEnum(NestedEnum.NEG)
                .addRepeatedInt64(1234567890123L)
                .putMapStringNestedEnum("key", NestedEnum.BAZ)
                .setOptionalTimestamp(Timestamp.newBuilder().setSeconds(1700000000).setNanos(123456789))
                .setOptionalDuration(Duration.newBuilder().setSeconds(-1234567).setNanos(-890000000))
                .setOptionalInt64Wrapper(Int64Value.newBuilder().setValue(Long.MIN_VALUE))
                .setOptionalStringWrapper(StringValue.newBuilder().setValue("a string wrapper which is text"))
                .setOptionalString("a string which is text")
                .build();
        Set<List<Object>> untruncated = Set.of(List.of("optionalInt64"), List.of("optionalSfixed64"), List.of("optionalFloat"), List.of("optionalDouble"),
                List.of("optionalNestedEnum"), List.of("repeatedInt64", 0), List.of("mapStringNestedEnum", "key"), List.of("optionalInt64Wrapper"),
                List.of("optionalDuration"), List.of("optionalTimestamp"));

        assertThat(write(message, new Filter(List.of(), List.of(), 0, -1), untruncated)).isEqualTo("{"
                + "\"optionalInt64\":\"-9223372036854775808\","
                + "\"optionalSfixed64\":\"9223372036854775807\","
                + "\"optionalFloat\":\"-Infinity\","
                + "\"optionalDouble\":\"NaN\","
                + "\"optionalString\":\"... + 22\","
                + "\"optionalNestedEnum\":\"NEG\","
                + "\"repeatedInt64\":[\"1234567890123\"],"
                + "\"mapStringNestedEnum\":{\"key\":\"BAZ\"},"
                + "\"optionalInt64Wrapper\":\"-9223372036854775808\","
                + "\"optionalStringWrapper\":\"... + 30\","
                + "\"optionalDuration\":\"-1234567.890s\","
                + "\"optionalTimestamp\":\"2023-11-14T22:13:20.123456789Z\"}");
    }

    @Test
    public void maxSize() throws Exception {
        for (TestMessage message : messages()) {
            String json = LiteTestSupport.writeChars(unfiltered, message.message());
            int size = json.getBytes(StandardCharsets.UTF_8).length;
            int step = Math.max(1, size / 200);
            for (int maxSize = 2; maxSize <= size + 1; maxSize += step) {
                assertSameAsReferenceFilter(message.message(), json, new Filter(List.of(), List.of(), -1, maxSize), message.untruncated());
            }
        }
    }

    @Test
    public void wellKnownTypesAsRoot() throws Exception {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(1700000000).build();
        assertThat(write(timestamp, new Filter(List.of(), List.of(), 5, -1), Set.of(List.of()))).isEqualTo("\"2023-11-14T22:13:20Z\"");
        // a root scalar value is always written
        assertThat(write(timestamp, new Filter(List.of(), List.of(), -1, 3))).isEqualTo("\"2023-11-14T22:13:20Z\"");
    }

    private static Filter randomFilter(Random random, List<String> paths, int size) {
        List<String> shuffled = new ArrayList<>(paths);
        Collections.shuffle(shuffled, random);
        int anonymizes = Math.min(shuffled.size(), random.nextInt(4));
        int prunes = Math.min(shuffled.size() - anonymizes, random.nextInt(3));
        int maxStringLength = MAX_STRING_LENGTHS[random.nextInt(MAX_STRING_LENGTHS.length)];
        int maxSize = random.nextInt(4) == 0 ? -1 : 2 + random.nextInt(size + 5);
        return new Filter(new ArrayList<>(shuffled.subList(0, anonymizes)), new ArrayList<>(shuffled.subList(anonymizes, anonymizes + prunes)), maxStringLength, maxSize);
    }

    /**
     * Path expressions for the keys in a JSON document: absolute paths (also with a wildcard) and any-paths.
     */

    static List<String> paths(String json) throws Exception {
        Set<String> paths = new LinkedHashSet<>();
        collect(LiteTestSupport.readTree(json), new ArrayList<>(), paths);
        return new ArrayList<>(paths);
    }

    private static void collect(JsonNode node, List<String> path, Set<String> paths) {
        if (node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> iterator = node.fields(); iterator.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                List<String> child = new ArrayList<>(path);
                child.add(entry.getKey());
                add(paths, "$." + String.join(".", child));
                add(paths, "$.." + entry.getKey());
                if (child.size() > 1) {
                    List<String> wildcard = new ArrayList<>(child);
                    wildcard.set(child.size() - 2, "*");
                    add(paths, "$." + String.join(".", wildcard));
                }
                collect(entry.getValue(), child, paths);
            }
        } else if (node.isArray()) {
            // arrays are transparent
            for (int i = 0; i < node.size(); i++) {
                collect(node.get(i), path, paths);
            }
        }
    }

    private static void add(Set<String> paths, String expression) {
        if (PathExpressions.isValidExpression(expression)) {
            paths.add(expression);
        }
    }
}
