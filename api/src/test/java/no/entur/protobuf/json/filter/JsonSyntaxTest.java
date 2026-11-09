package no.entur.protobuf.json.filter;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.jupiter.api.Test;

public class JsonSyntaxTest {

    @Test
    public void values() {
        for (String json : new String[]{
                "\"*\"", "\"[removed]\"", "\"\"", "\"a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\\u00e5\\uD83D\\uDE00\"", "\"æøå 😀\"",
                "null", "true", "false",
                "0", "-0", "1", "-12", "1.5", "-0.25", "1e3", "1E+3", "2.5e-10",
                "{}", "[]", "{ }", "[ ]", "{\"a\":1}", "{\"a\": [1, true, null, {\"b\": \"c\"}], \"d\": {}}", "[[[]]]",
                " null ", "\t\r\n\"x\"\n"
        }) {
            assertWithMessage(json).that(JsonSyntax.isValue(json)).isTrue();
        }
    }

    @Test
    public void notValues() {
        for (String json : new String[]{
                null, "", " ", "removed", "*", "nul", "nulll", "True", "undefined", "NaN", "Infinity",
                "\"unterminated", "\"a\nb\"", "\"\\x\"", "\"\\u00g0\"", "\"\\u00\"", "\"\\", "'single'",
                "01", "-", "+1", "1.", ".5", "1e", "1e+", "0x10", "--1",
                "{", "}", "[", "[1,]", "[,1]", "[1 2]", "{\"a\"}", "{\"a\":}", "{a:1}", "{\"a\":1,}", "{\"a\" 1}",
                "1 2", "\"a\" \"b\"", "null,", "{}{}", "[]]"
        }) {
            assertWithMessage(String.valueOf(json)).that(JsonSyntax.isValue(json)).isFalse();
        }
    }

    @Test
    public void depth() {
        assertThat(JsonSyntax.isValue("[".repeat(1000) + "]".repeat(1000))).isTrue();
        assertThat(JsonSyntax.isValue("[".repeat(1001) + "]".repeat(1001))).isFalse();
        assertThat(JsonSyntax.isValue("{\"a\":".repeat(1001) + "1" + "}".repeat(1001))).isFalse();
    }
}
