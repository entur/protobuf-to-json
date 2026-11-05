package no.entur.protobuf.json;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Duration;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import com.google.protobuf_test_messages.proto2.TestMessagesProto2.TestAllTypesProto2;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.ForeignMessage;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3.NestedEnum;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3.NestedMessage;
import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.filter.ReferenceFilter;
import no.entur.protobuf.json.path.PathExpressions;
import no.entur.protobuf.json.test.BenchmarkPayloads;
import no.entur.protobuf.json.test.FilterTestMessages;
import no.entur.protobuf.json.test.RandomMessages;
import no.entur.protobuf.json.test.UntruncatedValues;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A filtering writer must write the same as filtering the output of the plain writer with the {@linkplain ReferenceFilter},
 * for random messages and random filters. Both byte-based (UTF-8) and char-based generators are checked.
 */

public abstract class AbstractFilteringProtobufJsonWriterTest {

    private static final int ITERATIONS = 500;
    private static final int FILTERS_PER_MESSAGE = 4;

    private static final int[] MAX_STRING_LENGTHS = {-1, -1, 0, 1, 3, 8, 20, 50};

    /**
     * Filter options, and the other writer options; messages are null for the defaults.
     */

    public record Filter(List<String> anonymizes, List<String> prunes, int maxStringLength, int maxSize, String anonymizeMessage, String pruneMessage, String truncateMessage,
                         UnresolvedAny unresolvedAny) {

        public Filter(List<String> anonymizes, List<String> prunes, int maxStringLength, int maxSize) {
            this(anonymizes, prunes, maxStringLength, maxSize, null, null, null, UnresolvedAny.FAIL);
        }

        public static Filter none() {
            return new Filter(List.of(), List.of(), -1, -1);
        }

        public static Filter anonymize(String... paths) {
            return new Filter(List.of(paths), List.of(), -1, -1);
        }

        public static Filter prune(String... paths) {
            return new Filter(List.of(), List.of(paths), -1, -1);
        }

        public static Filter maxStringLength(int maxStringLength) {
            return new Filter(List.of(), List.of(), maxStringLength, -1);
        }

        public static Filter maxSize(int maxSize) {
            return new Filter(List.of(), List.of(), -1, maxSize);
        }

        public Filter withMaxSize(int maxSize) {
            return new Filter(anonymizes, prunes, maxStringLength, maxSize, anonymizeMessage, pruneMessage, truncateMessage, unresolvedAny);
        }

        public Filter withMaxStringLength(int maxStringLength) {
            return new Filter(anonymizes, prunes, maxStringLength, maxSize, anonymizeMessage, pruneMessage, truncateMessage, unresolvedAny);
        }

        public Filter withMessages(String anonymizeMessage, String pruneMessage, String truncateMessage) {
            return new Filter(anonymizes, prunes, maxStringLength, maxSize, anonymizeMessage, pruneMessage, truncateMessage, unresolvedAny);
        }

        public Filter withUnresolvedAny(UnresolvedAny unresolvedAny) {
            return new Filter(anonymizes, prunes, maxStringLength, maxSize, anonymizeMessage, pruneMessage, truncateMessage, unresolvedAny);
        }

        public boolean isEmpty() {
            return anonymizes.isEmpty() && prunes.isEmpty() && maxStringLength == -1 && maxSize == -1;
        }

        public <B extends AbstractProtobufJsonWriterBuilder<B>> B configure(B builder) {
            builder.withAnonymizePaths(anonymizes)
                    .withPrunePaths(prunes)
                    .withMaxStringLength(maxStringLength)
                    .withMaxSize(maxSize)
                    .withUnresolvedAny(unresolvedAny);
            if (anonymizeMessage != null) {
                builder.withAnonymizeMessage(anonymizeMessage);
            }
            if (pruneMessage != null) {
                builder.withPruneMessage(pruneMessage);
            }
            if (truncateMessage != null) {
                builder.withTruncateMessage(truncateMessage);
            }
            return builder;
        }

        ReferenceFilter toReferenceFilter() {
            return new ReferenceFilter(anonymizes, prunes, maxStringLength, maxSize,
                    anonymizeMessage != null ? ReferenceFilter.quote(anonymizeMessage) : JsonFilter.DEFAULT_ANONYMIZE_RAW_JSON_VALUE,
                    pruneMessage != null ? ReferenceFilter.quote(pruneMessage) : JsonFilter.DEFAULT_PRUNE_RAW_JSON_VALUE,
                    truncateMessage != null ? truncateMessage : JsonFilter.DEFAULT_TRUNCATE_MESSAGE);
        }
    }

    protected abstract ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> unfiltered();

    /**
     * @param filter options
     * @return a writer with the options; without filter options, the builders return the plain writer
     */

    protected abstract ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> filtered(Filter filter);

    @Test
    public void proto3() throws Exception {
        compareRandomMessages(TestAllTypesProto3::newBuilder);
    }

    @Test
    public void proto2() throws Exception {
        compareRandomMessages(TestAllTypesProto2::newBuilder);
    }

    @Test
    public void filterTestMessages() throws Exception {
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> unfiltered = unfiltered();
        Random random = new Random(1);
        for (Map.Entry<String, Message> entry : FilterTestMessages.create().entrySet()) {
            String json = TestSupport.writeBytes(unfiltered, entry.getValue());
            List<String> paths = paths(json);
            int size = json.getBytes(StandardCharsets.UTF_8).length;
            for (int i = 0; i < 200; i++) {
                Filter filter = randomFilter(random, paths, size);
                if (!filter.isEmpty()) {
                    assertSameAsReferenceFilter(entry.getValue(), json, filter, entry.getKey());
                }
            }
        }
    }

    private void compareRandomMessages(Supplier<Message.Builder> builders) throws Exception {
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> unfiltered = unfiltered();
        Random random = new Random(1);
        for (int seed = 0; seed < ITERATIONS; seed++) {
            Message message = new RandomMessages(seed).fill(builders.get());

            String json = TestSupport.writeBytes(unfiltered, message);
            List<String> paths = paths(json);
            int size = json.getBytes(StandardCharsets.UTF_8).length;

            for (int i = 0; i < FILTERS_PER_MESSAGE; i++) {
                Filter filter = randomFilter(random, paths, size);
                if (filter.isEmpty()) {
                    continue;
                }
                assertSameAsReferenceFilter(message, json, filter, "seed " + seed);
            }
        }
    }

    /**
     * Write a message with a filter, check that the output is the same as filtering the unfiltered output
     * with the reference filter, for both byte-based and char-based generators.
     *
     * @return the output
     */

    protected String write(MessageOrBuilder message, Filter filter) throws Exception {
        String json = TestSupport.writeBytes(unfiltered(), message);
        return assertSameAsReferenceFilter(message, json, filter, "");
    }

    protected String assertSameAsReferenceFilter(MessageOrBuilder message, String json, Filter filter, String description) throws Exception {
        ReferenceFilter referenceFilter = filter.toReferenceFilter();
        // the reference filter only sees JSON, so tell it which strings are not text, and where the unresolved Any values are
        Set<List<Object>> untruncated = UntruncatedValues.find(json, message.getDescriptorForType(), TestSupport.TYPE_REGISTRY);
        Set<List<Object>> unresolved = UntruncatedValues.findUnresolvedAnys(json, message.getDescriptorForType(), TestSupport.TYPE_REGISTRY);
        String expectedChars = referenceFilter.process(json, ReferenceFilter.CHARS, untruncated, unresolved);
        String expectedBytes = referenceFilter.process(json, ReferenceFilter.UTF8, untruncated, unresolved);

        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = filtered(filter);
        String actualChars = TestSupport.writeChars(writer, message);
        String actualBytes = TestSupport.writeBytes(writer, message);

        String text = description + " " + filter + " " + json;
        assertWithMessage("chars " + text).that(actualChars).isEqualTo(expectedChars);
        assertWithMessage("bytes " + text).that(actualBytes).isEqualTo(expectedBytes);
        if (filter.maxSize() != -1 && json.startsWith("{")) {
            assertWithMessage("chars " + text).that(actualChars.length()).isAtMost(filter.maxSize());
            assertWithMessage("bytes " + text).that(actualBytes.getBytes(StandardCharsets.UTF_8).length).isAtMost(filter.maxSize());
        }
        return actualBytes;
    }


    // ---- unresolved google.protobuf.Any ----

    private static final String UNRESOLVED_TYPE_URL = "type.googleapis.com/unknown.Type";

    private static Any unresolved(ByteString value) {
        return Any.newBuilder().setTypeUrl(UNRESOLVED_TYPE_URL).setValue(value).build();
    }

    @Test
    public void unresolvedAnyWritesTypeAndBase64() throws Exception {
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> writer = filtered(Filter.none().withUnresolvedAny(UnresolvedAny.VALUE));
        Any unknown = unresolved(ByteString.copyFrom(new byte[]{0, 1, 2, (byte) 0xff}));

        String expected = "{\"@type\":\"type.googleapis.com/unknown.Type\",\"@value\":\"AAEC/w==\"}";
        assertThat(TestSupport.writeBytes(writer, unknown)).isEqualTo(expected);
        assertThat(TestSupport.writeChars(writer, unknown)).isEqualTo(expected);
        assertThat(TestSupport.writeBytes(writer, TestAllTypesProto3.newBuilder().setOptionalAny(unknown).build()))
                .isEqualTo("{\"optionalAny\":" + expected + "}");

        assertThat(TestSupport.writeBytes(writer, unresolved(ByteString.EMPTY))).isEqualTo("{\"@type\":\"type.googleapis.com/unknown.Type\",\"@value\":\"\"}");
        assertThat(TestSupport.writeBytes(writer, TestAllTypesProto3.newBuilder().setOptionalAny(Any.getDefaultInstance()).build()))
                .isEqualTo("{\"optionalAny\":{}}");
        // the same as JsonFormat: trailing slashes are ignored
        assertThat(TestSupport.writeBytes(writer, Any.newBuilder().setTypeUrl("a/unknown.Type//").build()))
                .isEqualTo("{\"@type\":\"a/unknown.Type//\",\"@value\":\"\"}");

        Any duration = Any.pack(Duration.newBuilder().setSeconds(1).build());
        assertThat(TestSupport.writeBytes(writer, TestAllTypesProto3.newBuilder().setOptionalAny(duration).build()))
                .isEqualTo("{\"optionalAny\":{\"@type\":\"" + duration.getTypeUrl() + "\",\"value\":\"1s\"}}");

        // an invalid type url always fails
        for (String invalid : List.of("invalid", "type.googleapis.com/", "/")) {
            Any value = Any.newBuilder().setTypeUrl(invalid).setValue(ByteString.copyFrom(new byte[]{1})).build();
            InvalidProtocolBufferException e = assertThrows(InvalidProtocolBufferException.class, () -> TestSupport.writeBytes(writer, value));
            assertThat(e).hasMessageThat().isEqualTo("Invalid type url found: " + invalid);
        }
    }

    @Test
    public void unresolvedAnyIsFilteredLikeItsJson() throws Exception {
        Any any = unresolved(ByteString.copyFrom(new byte[]{0, 1, 2, (byte) 0xff}));
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalAny(any).setOptionalInt32(1).build();
        String json = TestSupport.writeBytes(filtered(Filter.none().withUnresolvedAny(UnresolvedAny.VALUE)), message);
        String root = TestSupport.writeBytes(filtered(Filter.none().withUnresolvedAny(UnresolvedAny.VALUE)), any);
        assertThat(json).isEqualTo("{\"optionalInt32\":1,\"optionalAny\":{\"@type\":\"type.googleapis.com/unknown.Type\",\"@value\":\"AAEC/w==\"}}");
        assertThat(root).isEqualTo("{\"@type\":\"type.googleapis.com/unknown.Type\",\"@value\":\"AAEC/w==\"}");

        String anonymized = "{\"optionalInt32\":1,\"optionalAny\":{\"@type\":\"type.googleapis.com/unknown.Type\",\"@value\":\"*\"}}";
        String pruned = "{\"optionalInt32\":1,\"optionalAny\":{\"@type\":\"type.googleapis.com/unknown.Type\",\"@value\":\"[removed]\"}}";

        // paths which match the fields of the unresolved Any
        assertUnresolvedAny(message, json, Filter.anonymize("$.optionalAny.@value"), anonymized);
        assertUnresolvedAny(message, json, Filter.prune("$.optionalAny.@value"), pruned);
        assertUnresolvedAny(message, json, Filter.anonymize("$.optionalAny.*"),
                "{\"optionalInt32\":1,\"optionalAny\":{\"@type\":\"*\",\"@value\":\"*\"}}");
        assertUnresolvedAny(message, json, Filter.anonymize("$.optionalAny"),
                "{\"optionalInt32\":1,\"optionalAny\":{\"@type\":\"*\",\"@value\":\"*\"}}");
        assertUnresolvedAny(message, json, Filter.prune("$.optionalAny"), "{\"optionalInt32\":1,\"optionalAny\":\"[removed]\"}");
        assertUnresolvedAny(message, json, Filter.prune("$.optionalAny.@type"),
                "{\"optionalInt32\":1,\"optionalAny\":{\"@type\":\"[removed]\",\"@value\":\"AAEC/w==\"}}");
        // paths which do not reach into the payload
        assertUnresolvedAny(message, json, Filter.prune("$.optionalInt32", "$.optionalAny.@type.x", "$.optionalAny.@value.x", "$.other.x"),
                "{\"optionalInt32\":\"[removed]\",\"optionalAny\":{\"@type\":\"type.googleapis.com/unknown.Type\",\"@value\":\"AAEC/w==\"}}");

        // paths which could have matched within the payload
        assertUnresolvedAny(message, json, Filter.anonymize("$.optionalAny.password"), anonymized);
        assertUnresolvedAny(message, json, Filter.anonymize("$.optionalAny.value"), anonymized);
        assertUnresolvedAny(message, json, Filter.anonymize("$.optionalAny.*.password"), anonymized);
        assertUnresolvedAny(message, json, Filter.anonymize("$.*.password"), anonymized);
        assertUnresolvedAny(message, json, Filter.anonymize("$..password"), anonymized);
        assertUnresolvedAny(message, json, Filter.anonymize("$..optionalInt32"),
                "{\"optionalInt32\":\"*\",\"optionalAny\":{\"@type\":\"type.googleapis.com/unknown.Type\",\"@value\":\"*\"}}");
        assertUnresolvedAny(message, json, Filter.prune("$.optionalAny.a.b.c"), pruned);
        assertUnresolvedAny(message, json, Filter.prune("$..password"), pruned);
        // prune beats anonymize
        assertUnresolvedAny(message, json, new Filter(List.of("$.optionalAny.@value"), List.of("$..password"), -1, -1), pruned);
        assertUnresolvedAny(message, json, new Filter(List.of("$..password"), List.of("$.optionalAny.x.y"), -1, -1), pruned);
        // a shorter path filters the whole value, so the paths below it do not count
        assertUnresolvedAny(message, json, new Filter(List.of("$.optionalAny.*"), List.of("$.optionalAny.x.y"), -1, -1),
                "{\"optionalInt32\":1,\"optionalAny\":{\"@type\":\"*\",\"@value\":\"*\"}}");
        assertUnresolvedAny(message, json, new Filter(List.of("$.optionalAny.x"), List.of("$.optionalAny.x.y"), -1, -1), anonymized);
        assertUnresolvedAny(message, json, new Filter(List.of("$.optionalAny.x"), List.of("$.optionalAny.*.y"), -1, -1), pruned);

        // max string length and size
        assertUnresolvedAny(message, json, Filter.maxStringLength(4), null);
        assertUnresolvedAny(message, json, Filter.maxSize(40), null);
        assertUnresolvedAny(message, json, Filter.anonymize("$..x").withMaxSize(70), null);

        assertUnresolvedAny(any, root, Filter.anonymize("$.@value"), "{\"@type\":\"type.googleapis.com/unknown.Type\",\"@value\":\"*\"}");
        assertUnresolvedAny(any, root, Filter.anonymize("$.value"), "{\"@type\":\"type.googleapis.com/unknown.Type\",\"@value\":\"*\"}");
        assertUnresolvedAny(any, root, Filter.prune("$.*"), "{\"@type\":\"[removed]\",\"@value\":\"[removed]\"}");
        assertUnresolvedAny(any, root, Filter.prune("$.@type"), "{\"@type\":\"[removed]\",\"@value\":\"AAEC/w==\"}");
    }

    /**
     * Write with a filter, and check against the reference filter, which is told where the unresolved Any values are.
     *
     * @param expected expected output, or null to only compare with the reference filter
     */

    private void assertUnresolvedAny(MessageOrBuilder message, String json, Filter filter, String expected) throws Exception {
        String actual = assertSameAsReferenceFilter(message, json, filter.withUnresolvedAny(UnresolvedAny.VALUE), "");
        if (expected != null) {
            assertWithMessage(filter.toString()).that(actual).isEqualTo(expected);
        }
    }

    @Test
    public void unresolvedAnyRandomFilters() throws Exception {
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> resolving = unfiltered();
        ProtobufJsonWriter<MessageOrBuilder, JsonGenerator> unresolving = filtered(Filter.none().withUnresolvedAny(UnresolvedAny.VALUE));
        Random random = new Random(1);
        for (int seed = 0; seed < 100; seed++) {
            // the same random payloads, as resolved and as unresolved Any values, at several depths
            TestAllTypesProto3 payload = new RandomMessages(seed).fill(TestAllTypesProto3.newBuilder());
            TestAllTypesProto3 message = withAnys(unresolved(payload.toByteString()), new Random(seed));
            TestAllTypesProto3 resolved = withAnys(Any.pack(payload), new Random(seed));

            String json = TestSupport.writeBytes(unresolving, message);
            // the paths of the resolved payloads reach into the unresolved payloads
            Set<String> paths = new LinkedHashSet<>(paths(json));
            paths.addAll(paths(TestSupport.writeBytes(resolving, resolved)));
            List<String> candidates = new ArrayList<>(paths);
            int size = json.getBytes(StandardCharsets.UTF_8).length;
            for (int i = 0; i < FILTERS_PER_MESSAGE; i++) {
                Filter filter = randomFilter(random, candidates, size).withUnresolvedAny(UnresolvedAny.VALUE);
                assertSameAsReferenceFilter(message, json, filter, "seed " + seed);
            }
        }
    }

    private static TestAllTypesProto3 withAnys(Any any, Random random) {
        TestAllTypesProto3.Builder builder = TestAllTypesProto3.newBuilder().setOptionalAny(any).setOptionalInt32(random.nextInt(10));
        if (random.nextBoolean()) {
            builder.addRepeatedAny(any).addRepeatedAny(any);
        }
        if (random.nextBoolean()) {
            builder.setOptionalNestedMessage(NestedMessage.newBuilder().setCorecursive(TestAllTypesProto3.newBuilder().setOptionalAny(any)));
        }
        if (random.nextBoolean()) {
            builder.putMapStringNestedMessage("k", NestedMessage.newBuilder().setCorecursive(TestAllTypesProto3.newBuilder().addRepeatedAny(any)).build());
        }
        return builder.build();
    }

    // ---- targeted tests; write(..) also checks against the reference filter ----

    @Test
    public void pruneRepeatedFields() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalInt32(5)
                .addAllRepeatedInt32(List.of(1, 2, 3)) // packed
                .addAllUnpackedInt32(List.of(4, 5))
                .addRepeatedNestedMessage(NestedMessage.newBuilder().setA(1))
                .addRepeatedNestedMessage(NestedMessage.newBuilder().setA(2))
                .build();

        assertThat(write(message, Filter.prune("$.repeatedInt32", "$.unpackedInt32", "$.repeatedNestedMessage")))
                .isEqualTo("{\"optionalInt32\":5,\"repeatedInt32\":\"[removed]\",\"repeatedNestedMessage\":\"[removed]\",\"unpackedInt32\":\"[removed]\"}");
        assertThat(write(message, Filter.anonymize("$.repeatedInt32", "$.unpackedInt32", "$.repeatedNestedMessage")))
                .isEqualTo("{\"optionalInt32\":5,\"repeatedInt32\":[\"*\",\"*\",\"*\"],\"repeatedNestedMessage\":[{\"a\":\"*\"},{\"a\":\"*\"}],\"unpackedInt32\":[\"*\",\"*\"]}");
        // arrays are transparent
        assertThat(write(message, Filter.prune("$.repeatedNestedMessage.a")))
                .isEqualTo("{\"optionalInt32\":5,\"repeatedInt32\":[1,2,3],\"repeatedNestedMessage\":[{\"a\":\"[removed]\"},{\"a\":\"[removed]\"}],\"unpackedInt32\":[4,5]}");
    }

    @Test
    public void pruneAndAnonymizeMaps() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .putMapInt32Int32(1, 2)
                .putMapStringString("a", "1")
                .putMapStringString("b", "2")
                .putMapStringNestedMessage("x", NestedMessage.newBuilder().setA(3).build())
                .putMapStringNestedEnum("y", NestedEnum.BAZ)
                .build();

        assertThat(write(message, Filter.prune("$.mapInt32Int32", "$.mapStringString.a").withMaxStringLength(-1)))
                .isEqualTo("{\"mapInt32Int32\":\"[removed]\",\"mapStringString\":{\"a\":\"[removed]\",\"b\":\"2\"},\"mapStringNestedMessage\":{\"x\":{\"a\":3}},\"mapStringNestedEnum\":{\"y\":\"BAZ\"}}");
        assertThat(write(message, Filter.anonymize("$.mapStringNestedMessage", "$.mapStringNestedEnum.y")))
                .isEqualTo("{\"mapInt32Int32\":{\"1\":2},\"mapStringString\":{\"a\":\"1\",\"b\":\"2\"},\"mapStringNestedMessage\":{\"x\":{\"a\":\"*\"}},\"mapStringNestedEnum\":{\"y\":\"*\"}}");
    }

    @Test
    public void anonymizeNestedMessages() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalNestedMessage(NestedMessage.newBuilder()
                        .setA(1)
                        .setCorecursive(TestAllTypesProto3.newBuilder().setOptionalInt32(2).setOptionalString("x").addAllRepeatedInt32(List.of(1, 2))))
                .setOptionalForeignMessage(ForeignMessage.newBuilder().setC(3))
                .build();

        assertThat(write(message, Filter.anonymize("$.optionalNestedMessage")))
                .isEqualTo("{\"optionalNestedMessage\":{\"a\":\"*\",\"corecursive\":{\"optionalInt32\":\"*\",\"optionalString\":\"*\",\"repeatedInt32\":[\"*\",\"*\"]}},\"optionalForeignMessage\":{\"c\":3}}");
        assertThat(write(message, Filter.anonymize("$.optionalNestedMessage.*")))
                .isEqualTo("{\"optionalNestedMessage\":{\"a\":\"*\",\"corecursive\":{\"optionalInt32\":\"*\",\"optionalString\":\"*\",\"repeatedInt32\":[\"*\",\"*\"]}},\"optionalForeignMessage\":{\"c\":3}}");
        assertThat(write(message, Filter.prune("$.optionalNestedMessage.corecursive.optionalString", "$.*.c")))
                .isEqualTo("{\"optionalNestedMessage\":{\"a\":1,\"corecursive\":{\"optionalInt32\":2,\"optionalString\":\"[removed]\",\"repeatedInt32\":[1,2]}},\"optionalForeignMessage\":{\"c\":\"[removed]\"}}");
    }

    @Test
    public void anonymizeWellKnownTypes() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalTimestamp(Timestamp.newBuilder().setSeconds(1700000000))
                .setOptionalStruct(Struct.newBuilder()
                        .putFields("a", Value.newBuilder().setStringValue("b").build())
                        .putFields("c", Value.newBuilder().setListValue(ListValue.newBuilder()
                                .addValues(Value.newBuilder().setNumberValue(1))
                                .addValues(Value.newBuilder().setBoolValue(true))).build()))
                .setOptionalAny(Any.pack(TestAllTypesProto3.newBuilder().setOptionalInt32(7).setOptionalString("s").build()))
                .setOptionalValue(Value.newBuilder().setListValue(ListValue.newBuilder()
                        .addValues(Value.newBuilder().setStringValue("x"))
                        .addValues(Value.newBuilder().setNullValue(NullValue.NULL_VALUE))))
                .build();

        assertThat(write(message, Filter.anonymize("$.optionalTimestamp", "$.optionalStruct", "$.optionalAny", "$.optionalValue")))
                .isEqualTo("{\"optionalTimestamp\":\"*\",\"optionalStruct\":{\"a\":\"*\",\"c\":[\"*\",\"*\"]},\"optionalAny\":{\"@type\":\"*\",\"optionalInt32\":\"*\",\"optionalString\":\"*\"},\"optionalValue\":[\"*\",\"*\"]}");
        assertThat(write(message, Filter.prune("$.optionalStruct.c", "$.optionalAny.@type", "$.optionalTimestamp")))
                .isEqualTo("{\"optionalTimestamp\":\"[removed]\",\"optionalStruct\":{\"a\":\"b\",\"c\":\"[removed]\"},\"optionalAny\":{\"@type\":\"[removed]\",\"optionalInt32\":7,\"optionalString\":\"s\"},\"optionalValue\":[\"x\",null]}");

        TestAllTypesProto3 wellKnownInAny = TestAllTypesProto3.newBuilder()
                .setOptionalAny(Any.pack(Duration.newBuilder().setSeconds(1).build()))
                .build();
        assertThat(write(wellKnownInAny, Filter.anonymize("$.optionalAny.value")))
                .isEqualTo("{\"optionalAny\":{\"@type\":\"type.googleapis.com/google.protobuf.Duration\",\"value\":\"*\"}}");
    }

    @Test
    public void anyPath() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalString("a")
                .setOptionalNestedMessage(NestedMessage.newBuilder().setCorecursive(TestAllTypesProto3.newBuilder().setOptionalString("b")))
                .putMapStringString("optionalString", "c")
                .setOptionalAny(Any.pack(TestAllTypesProto3.newBuilder().setOptionalString("d").build()))
                .build();

        assertThat(write(message, Filter.anonymize("$..optionalString")))
                .isEqualTo("{\"optionalString\":\"*\",\"optionalNestedMessage\":{\"corecursive\":{\"optionalString\":\"*\"}},\"mapStringString\":{\"optionalString\":\"*\"},"
                        + "\"optionalAny\":{\"@type\":\"type.googleapis.com/protobuf_test_messages.proto3.TestAllTypesProto3\",\"optionalString\":\"*\"}}");
        // prune takes precedence
        assertThat(write(message, new Filter(List.of("$..optionalString"), List.of("$.optionalString"), -1, -1)))
                .startsWith("{\"optionalString\":\"[removed]\",\"optionalNestedMessage\":{\"corecursive\":{\"optionalString\":\"*\"}}");
    }

    @Test
    public void maxStringLength() throws Exception {
        byte[] bytes = new byte[100];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalInt64(Long.MIN_VALUE)
                .setOptionalString("a".repeat(100))
                .setOptionalBytes(ByteString.copyFrom(bytes))
                .setOptionalNestedEnum(NestedEnum.BAZ)
                .addRepeatedString("short")
                .addRepeatedString("ab\"\\\n\u0001cdefghijklmnopqrstuvwxyz\u00e6\u00f8\u00e5")
                .addRepeatedString("abcd\ud83d\ude00" + "x".repeat(20))
                .putMapStringString("a very long key, which is never truncated", "v")
                .setOptionalTimestamp(Timestamp.newBuilder().setSeconds(1700000000))
                .build();

        String json = write(message, Filter.maxStringLength(5));
        assertThat(json).contains("\"optionalString\":\"aaaaa... + 95\"");
        assertThat(json).contains("\"optionalBytes\":\"AAECA... + 131\"");
        assertThat(json).contains("\"repeatedString\":[\"short\",\"ab\\\"\\\\\\n... + 28\",\"abcd... + 22\"]");
        assertThat(json).contains("\"a very long key, which is never truncated\":\"v\"");
        // not text
        assertThat(json).contains("\"optionalInt64\":\"-9223372036854775808\"");
        assertThat(json).contains("\"optionalNestedEnum\":\"BAZ\"");
        assertThat(json).contains("\"optionalTimestamp\":\"2023-11-14T22:13:20Z\"");

        for (int maxStringLength = 0; maxStringLength < 40; maxStringLength++) {
            write(message, Filter.maxStringLength(maxStringLength));
        }
    }

    @Test
    public void maxSize() throws Exception {
        // every size for a small message
        Message small = BenchmarkPayloads.create("small");
        String smallJson = TestSupport.writeBytes(unfiltered(), small);
        for (int maxSize = 2; maxSize <= smallJson.length() + 1; maxSize++) {
            assertSameAsReferenceFilter(small, smallJson, Filter.maxSize(maxSize), "small");
        }

        // a sample of sizes for random messages
        Random random = new Random(1);
        for (int seed = 0; seed < 20; seed++) {
            TestAllTypesProto3 message = new RandomMessages(seed).fill(TestAllTypesProto3.newBuilder());
            String json = TestSupport.writeBytes(unfiltered(), message);
            int size = json.getBytes(StandardCharsets.UTF_8).length;
            for (int i = 0; i < 100; i++) {
                assertSameAsReferenceFilter(message, json, Filter.maxSize(2 + random.nextInt(size)), "seed " + seed);
            }
        }
    }

    @Test
    public void maxSizeWithPaths() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalInt32(1)
                .setOptionalString("secretvalue")
                .setOptionalNestedMessage(NestedMessage.newBuilder().setA(1).setCorecursive(TestAllTypesProto3.newBuilder().setOptionalInt32(2)))
                .addAllRepeatedInt32(List.of(1, 2, 3))
                .putMapStringString("a", "b")
                .build();
        Filter filter = new Filter(List.of("$.optionalNestedMessage", "$.repeatedInt32"), List.of("$.optionalString"), 3, -1);
        int size = TestSupport.writeBytes(filtered(filter), message).length();
        for (int maxSize = 2; maxSize <= size + 1; maxSize++) {
            write(message, filter.withMaxSize(maxSize));
        }
        assertThat(write(message, filter.withMaxSize(48))).isEqualTo("{\"optionalInt32\":1,\"optionalString\":\"[removed]\"}");
    }

    @Test
    public void maxStringLengthDoesNotApplyToValuesWhichAreNotText() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalInt64(Long.MIN_VALUE)
                .setOptionalUint64(-1L)
                .setOptionalSfixed64(Long.MAX_VALUE)
                .setOptionalFloat(Float.NEGATIVE_INFINITY)
                .setOptionalDouble(Double.NaN)
                .setOptionalNestedEnum(NestedEnum.NEG)
                .addRepeatedInt64(1234567890123L)
                .putMapStringNestedEnum("key", NestedEnum.BAZ)
                .setOptionalTimestamp(Timestamp.newBuilder().setSeconds(1700000000).setNanos(123456789))
                .setOptionalDuration(Duration.newBuilder().setSeconds(-1234567).setNanos(-890000000))
                .setOptionalInt64Wrapper(Int64Value.of(Long.MIN_VALUE))
                .setOptionalStringWrapper(StringValue.of("a string wrapper which is text"))
                .setOptionalString("a string which is text")
                .build();

        assertThat(write(message, Filter.maxStringLength(0))).isEqualTo("{"
                + "\"optionalInt64\":\"-9223372036854775808\","
                + "\"optionalUint64\":\"18446744073709551615\","
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
    public void wellKnownTypesAsRoot() throws Exception {
        Timestamp timestamp = Timestamp.newBuilder().setSeconds(1700000000).build();
        assertThat(write(timestamp, Filter.maxStringLength(5))).isEqualTo("\"2023-11-14T22:13:20Z\"");
        // a root scalar value is always written
        assertThat(write(timestamp, Filter.maxSize(3))).isEqualTo("\"2023-11-14T22:13:20Z\"");

        Struct struct = Struct.newBuilder()
                .putFields("a", Value.newBuilder().setStringValue("b").build())
                .putFields("c", Value.newBuilder().setNumberValue(1).build())
                .build();
        assertThat(write(struct, Filter.anonymize("$.a"))).isEqualTo("{\"a\":\"*\",\"c\":1.0}");
        assertThat(write(struct, Filter.maxSize(10))).isEqualTo("{\"a\":\"b\"}");

        assertThat(write(Int64Value.of(Long.MAX_VALUE), Filter.maxStringLength(3))).isEqualTo("\"9223372036854775807\"");
        assertThat(write(StringValue.of("a".repeat(20)), Filter.maxStringLength(3))).isEqualTo("\"aaa... + 17\"");
    }

    @Test
    public void unknownClosedEnumValues() throws Exception {
        // unknown values of closed enums are not written, so there is nothing to prune
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CodedOutputStream output = CodedOutputStream.newInstance(bytes);
        output.writeInt32(1, 1); // optional_int32
        int repeated = TestAllTypesProto2.getDescriptor().findFieldByName("repeated_nested_enum").getNumber();
        output.writeEnum(repeated, 77);
        output.writeEnum(repeated, 78);
        output.flush();
        TestAllTypesProto2 message = TestAllTypesProto2.parseFrom(bytes.toByteArray());

        assertThat(write(message, Filter.prune("$.repeatedNestedEnum"))).isEqualTo("{\"optionalInt32\":1}");
        assertThat(write(message, Filter.anonymize("$.repeatedNestedEnum"))).isEqualTo("{\"optionalInt32\":1}");
    }

    @Test
    public void messages() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder()
                .setOptionalInt32(1)
                .setOptionalString("abcdefghijklmnopqrstuvwxyz")
                .setOptionalNestedMessage(NestedMessage.newBuilder().setA(1))
                .build();

        Filter filter = new Filter(List.of("$.optionalInt32"), List.of("$.optionalNestedMessage"), 3, -1).withMessages("x\"", "gone", "~");
        assertThat(write(message, filter)).isEqualTo("{\"optionalInt32\":\"x\\\"\",\"optionalString\":\"abc~23\",\"optionalNestedMessage\":\"gone\"}");
    }

    @Test
    public void builder() throws Exception {
        TestAllTypesProto3.Builder message = TestAllTypesProto3.newBuilder().setOptionalString("secret").setOptionalInt32(1);
        assertThat(write(message, Filter.anonymize("$.optionalString"))).isEqualTo("{\"optionalInt32\":1,\"optionalString\":\"*\"}");
    }

    @Test
    public void writeAsString() throws Exception {
        TestAllTypesProto3 message = TestAllTypesProto3.newBuilder().setOptionalString("secret").setOptionalInt32(1).build();
        assertThat(filtered(Filter.prune("$.optionalString")).writeAsString(message)).isEqualTo("{\"optionalInt32\":1,\"optionalString\":\"[removed]\"}");
    }

    private static Filter randomFilter(Random random, List<String> paths, int size) {
        List<String> shuffled = new ArrayList<>(paths);
        Collections.shuffle(shuffled, random);
        int anonymizes = Math.min(shuffled.size(), random.nextInt(4));
        int prunes = Math.min(shuffled.size() - anonymizes, random.nextInt(3));
        int maxStringLength = MAX_STRING_LENGTHS[random.nextInt(MAX_STRING_LENGTHS.length)];
        int maxSize = random.nextInt(4) == 0 ? -1 : 2 + random.nextInt(size + 5);
        return new Filter(
                new ArrayList<>(shuffled.subList(0, anonymizes)),
                new ArrayList<>(shuffled.subList(anonymizes, anonymizes + prunes)),
                maxStringLength,
                maxSize);
    }

    /**
     * Path expressions for the keys in a JSON document: absolute paths (also with a wildcard) and any-paths.
     */

    protected static List<String> paths(String json) {
        Set<String> paths = new LinkedHashSet<>();
        collect(TestSupport.MAPPER.readTree(json), new ArrayList<>(), paths);
        return new ArrayList<>(paths);
    }

    private static void collect(JsonNode node, List<String> path, Set<String> paths) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
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
