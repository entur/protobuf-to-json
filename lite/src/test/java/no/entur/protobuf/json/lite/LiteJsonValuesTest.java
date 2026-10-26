package no.entur.protobuf.json.lite;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.google.common.truth.Truth.assertThat;

public class LiteJsonValuesTest {

    @Test
    public void fieldMasks() {
        // like FieldMaskUtil.toJsonString, which uses Guava's CaseFormat
        assertThat(LiteJsonValues.formatFieldMask(Arrays.asList("foo_bar", "a.b_c.d", "", "FooBar", "_foo", "a__b", "FOO_BAR"))).isEqualTo("fooBar,a.bC.d,foobar,Foo,aB,fooBar");
        assertThat(LiteJsonValues.formatFieldMask(Arrays.asList())).isEqualTo("");
    }
}
