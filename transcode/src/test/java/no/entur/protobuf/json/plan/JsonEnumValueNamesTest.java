package no.entur.protobuf.json.plan;

import com.google.protobuf.NullValue;
import com.google.protobuf.Syntax;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

public class JsonEnumValueNamesTest {

    @Test
    public void looksUpCustomNamesWhenProtobufJavaUtilHasTheOption() {
        // protobuf-java-util 4.36 and later; the build also runs the tests with an older protobuf version
        boolean option;
        try {
            Class.forName("com.google.protobuf.util.JsonEnumvalueOptionsProto");
            option = true;
        } catch (ClassNotFoundException e) {
            option = false;
        }
        assertThat(JsonEnumValueNames.isAvailable()).isEqualTo(option);
    }

    @Test
    public void writesNamesOfValuesWithoutCustomNames() {
        assertThat(JsonEnumValueNames.getJsonName(NullValue.NULL_VALUE.getValueDescriptor())).isEqualTo("NULL_VALUE");
        assertThat(JsonEnumValueNames.getJsonName(Syntax.SYNTAX_PROTO3.getValueDescriptor())).isEqualTo("SYNTAX_PROTO3");
    }
}
