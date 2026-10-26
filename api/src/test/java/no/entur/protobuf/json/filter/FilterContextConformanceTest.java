package no.entur.protobuf.json.filter;

import static com.google.common.truth.Truth.assertWithMessage;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

/**
 * Filtering via {@linkplain FilterContext} gives the same output as the {@linkplain ReferenceFilter}, for random documents and filters.
 */

public class FilterContextConformanceTest {

    private static final JsonFactory FACTORY = new JsonFactory();

    private static final String[] KEYS = {"a", "b", "c", "name", "password", "æøå", "x/y", "@type", "k\"ey"};

    private static final String[] PATHS = {"$.a", "$.b", "$.a.b", "$.a.c", "$.b.c", "$.*.a", "$.a.*", "$.*.b.c", "$..password", "$..name", "$..æøå",
            "$..x/y", "$.x/y", "$.@type", "$.c.*.a", "$.a.b.c", "$.*", "$.k\"ey"};

    private static final int[] MAX_STRING_LENGTHS = {-1, -1, 0, 1, 2, 5, 10};

    @Test
    public void sameAsReferenceFilter() {
        Random random = new Random(1);
        int compared = 0;
        for (int i = 0; i < 20000; i++) {
            String json = randomJson(random);

            List<String> anonymizes = randomPaths(random, 3);
            List<String> prunes = randomPaths(random, 2);
            int maxStringLength = MAX_STRING_LENGTHS[random.nextInt(MAX_STRING_LENGTHS.length)];
            int maxSize = random.nextInt(3) == 0 ? -1 : 2 + random.nextInt(json.getBytes(StandardCharsets.UTF_8).length + 4);
            if (anonymizes.isEmpty() && prunes.isEmpty() && maxStringLength == -1 && maxSize == -1) {
                continue;
            }

            ReferenceFilter expectedFilter = new ReferenceFilter(anonymizes, prunes, maxStringLength, maxSize);

            JsonFilter filter = new JsonFilter(maxStringLength, maxSize, anonymizes, prunes, JsonFilter.DEFAULT_ANONYMIZE_RAW_JSON_VALUE,
                    JsonFilter.DEFAULT_PRUNE_RAW_JSON_VALUE, JsonFilter.DEFAULT_TRUNCATE_MESSAGE);

            String description = json + " anonymize " + anonymizes + " prune " + prunes + " max string length " + maxStringLength + " max size " + maxSize;

            String expectedChars = expectedFilter.process(json, ReferenceFilter.CHARS);
            assertWithMessage(description).that(TokenReplay.toChars(json, filter)).isEqualTo(expectedChars);

            String expectedBytes = expectedFilter.process(json, ReferenceFilter.UTF8);
            assertWithMessage(description).that(TokenReplay.toBytes(json, filter)).isEqualTo(expectedBytes);

            if (maxSize != -1 && (json.startsWith("{") || json.startsWith("["))) {
                // a scalar root value is always kept
                assertWithMessage(description).that(expectedChars.length()).isAtMost(maxSize);
                assertWithMessage(description).that(expectedBytes.getBytes(StandardCharsets.UTF_8).length).isAtMost(maxSize);
            }
            compared++;
        }
        assertWithMessage("compared").that(compared).isGreaterThan(15000);
    }

    private static List<String> randomPaths(Random random, int max) {
        List<String> paths = new ArrayList<>(Arrays.asList(PATHS));
        Collections.shuffle(paths, random);
        return new ArrayList<>(paths.subList(0, random.nextInt(max + 1)));
    }

    static String randomJson(Random random) {
        StringWriter output = new StringWriter();
        try (JsonGenerator generator = FACTORY.createGenerator(ObjectWriteContext.empty(), output)) {
            int kind = random.nextInt(10);
            if (kind < 8) {
                writeObject(random, generator, 0);
            } else if (kind == 8) {
                writeArray(random, generator, 0);
            } else {
                writeValue(random, generator, 3);
            }
        }
        return output.toString();
    }

    private static void writeObject(Random random, JsonGenerator generator, int depth) {
        generator.writeStartObject();
        List<String> keys = new ArrayList<>(Arrays.asList(KEYS));
        Collections.shuffle(keys, random);
        int count = random.nextInt(5);
        for (int i = 0; i < count; i++) {
            generator.writeName(keys.get(i));
            writeValue(random, generator, depth + 1);
        }
        generator.writeEndObject();
    }

    private static void writeArray(Random random, JsonGenerator generator, int depth) {
        generator.writeStartArray();
        int count = random.nextInt(4);
        for (int i = 0; i < count; i++) {
            writeValue(random, generator, depth + 1);
        }
        generator.writeEndArray();
    }

    private static void writeValue(Random random, JsonGenerator generator, int depth) {
        int kind = depth >= 3 ? 2 + random.nextInt(6) : random.nextInt(8);
        switch (kind) {
            case 0:
                writeObject(random, generator, depth);
                break;
            case 1:
                writeArray(random, generator, depth);
                break;
            case 2:
            case 3:
                generator.writeString(RandomStrings.randomString(random, random.nextInt(25)));
                break;
            case 4:
                generator.writeNumber(random.nextInt(2000) - 1000);
                break;
            case 5:
                generator.writeNumber(random.nextDouble() * 1000);
                break;
            case 6:
                generator.writeBoolean(random.nextBoolean());
                break;
            default:
                generator.writeNull();
        }
    }
}
