package no.entur.protobuf.json;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import no.entur.protobuf.json.filter.JsonFilter;
import no.entur.protobuf.json.path.FilterType;

public class AbstractProtobufJsonWriterBuilderTest {

    private static class Builder extends AbstractProtobufJsonWriterBuilder<Builder> {
        JsonFilter build() {
            return buildFilter();
        }
    }

    @Test
    public void noFilter() {
        assertThat(new Builder().build()).isNull();
    }

    @Test
    public void options() {
        JsonFilter filter = new Builder()
                .withMaxStringLength(10)
                .withMaxSize(100)
                .withAnonymizeKeys("password")
                .withPrunePaths("$.a.b")
                .build();

        assertThat(filter.getMaxStringLength()).isEqualTo(10);
        assertThat(filter.hasMaxStringLength()).isTrue();
        assertThat(filter.getMaxSize()).isEqualTo(100);
        assertThat(filter.hasMaxSize()).isTrue();
        assertThat(filter.hasPaths()).isTrue();
        assertThat(filter.getPathFilter().newMatcher().matchName(3, "password")).isEqualTo(FilterType.ANON);
        assertThat(filter.getAnonymizeRawJsonValue()).isEqualTo("\"*\"");
        assertThat(filter.getPruneRawJsonValue()).isEqualTo("\"[removed]\"");
        assertThat(filter.getTruncateMessage()).isEqualTo("... + ");
    }

    @Test
    public void messages() {
        JsonFilter filter = new Builder()
                .withMaxStringLength(10)
                .withAnonymizeMessage("x\"y")
                .withPruneMessage("gone\n")
                .withTruncateMessage("..\"")
                .build();

        assertThat(filter.getAnonymizeRawJsonValue()).isEqualTo("\"x\\\"y\"");
        assertThat(filter.getPruneRawJsonValue()).isEqualTo("\"gone\\n\"");
        assertThat(filter.getTruncateMessage()).isEqualTo("..\"");
        assertThat(filter.getTruncateMessageLength()).isEqualTo(4);
        assertThat(filter.getAnonymizeSize(false)).isEqualTo(6);
        assertThat(filter.getPruneSize(true)).isEqualTo(8);

        filter = new Builder().withMaxSize(10).withAnonymizeRawJsonValue("null").build();
        assertThat(filter.getAnonymizeRawJsonValue()).isEqualTo("null");
        assertThat(filter.hasMaxStringLength()).isFalse();
        assertThat(filter.hasPaths()).isFalse();
    }

    @Test
    public void invalidOptions() {
        assertThrows(IllegalArgumentException.class, () -> new Builder().withMaxStringLength(-2));
        assertThrows(IllegalArgumentException.class, () -> new Builder().withMaxSize(-2));
        assertThrows(IllegalArgumentException.class, () -> new Builder().withAnonymizePaths("a.b").build());
        assertThrows(IllegalArgumentException.class, () -> new Builder().withPrunePaths("$.a[0]").build());
        assertThrows(IllegalArgumentException.class, () -> new Builder().withUnresolvedAny(null));
    }

    @Test
    public void invalidReplacements() {
        // written as is, so the output would not be JSON
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new Builder().withAnonymizeRawJsonValue("removed"));
        assertThat(e).hasMessageThat().isEqualTo("Expected a JSON value as the anonymize replacement, got 'removed'");
        assertThrows(IllegalArgumentException.class, () -> new Builder().withPruneRawJsonValue("\"a\" \"b\""));
        assertThrows(IllegalArgumentException.class, () -> new Builder().withAnonymizeRawJsonValue(null));
        assertThrows(IllegalArgumentException.class, () -> new Builder().withPruneRawJsonValue(""));

        assertThrows(IllegalArgumentException.class, () -> new Builder().withAnonymizeMessage(null));
        assertThrows(IllegalArgumentException.class, () -> new Builder().withPruneMessage(null));
        assertThrows(IllegalArgumentException.class, () -> new Builder().withTruncateMessage(null));

        assertThrows(IllegalArgumentException.class, () -> new JsonFilter(-1, 10, null, null, "{", "null", "..."));
        assertThrows(IllegalArgumentException.class, () -> new JsonFilter(-1, 10, null, null, "null", "null", null));
    }

    @Test
    public void rawReplacements() {
        JsonFilter filter = new Builder()
                .withMaxSize(10)
                .withAnonymizeRawJsonValue("{\"masked\": true}")
                .withPruneRawJsonValue("[]")
                .build();

        assertThat(filter.getAnonymizeRawJsonValue()).isEqualTo("{\"masked\": true}");
        assertThat(filter.getPruneRawJsonValue()).isEqualTo("[]");
    }
}
