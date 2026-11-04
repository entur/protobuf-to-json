package no.entur.protobuf.json.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RawJsonTest {

    @Test
    public void acceptsOneJsonValue() {
        for (String raw : new String[] {
                "null",
                "true",
                "false",
                "\"*\"",
                "\"[removed]\"",
                "\"x\\\"y\"",
                "\"\\u0041\"",
                "\"héllo\"",
                "0",
                "-1",
                "1.5",
                "1e10",
                "1E-2",
                "0.0",
                "{}",
                "[]",
                "{\"a\":1}",
                "[1, 2]",
                "{\"a\":[true, null]}",
                "  null\n",
                " { \"a\" : \"b\" } "
        }) {
            assertDoesNotThrow(() -> RawJson.requireValue(raw), raw);
        }
    }

    @Test
    public void rejectsTextThatIsNotOneJsonValue() {
        for (String raw : new String[] {
                "",
                "   ",
                "*",
                "foo",
                "'x'",
                "\"unterminated",
                "{\"a\":1}{\"b\":2}",
                "null null",
                "1,2",
                "{",
                "[1,]",
                "01",
                "+1",
                ".5",
                "1.",
                "-",
                "1e",
                "1e+",
                "{\"a\"}",
                "{,}",
                "[,]",
                "undefined",
                "\"\n\"",
                "\"\\x\""
        }) {
            assertThrows(IllegalArgumentException.class, () -> RawJson.requireValue(raw), raw);
        }
        assertThrows(IllegalArgumentException.class, () -> RawJson.requireValue(null));
    }
}
