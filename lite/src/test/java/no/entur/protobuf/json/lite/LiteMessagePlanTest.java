package no.entur.protobuf.json.lite;

import com.google.protobuf_test_messages.proto2.TestMessagesProto2.TestAllTypesProto2;
import com.google.protobuf_test_messages.proto3.TestMessagesProto3.TestAllTypesProto3;
import no.entur.protobuf.json.test.protobufjackson.JsonTestProto;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

public class LiteMessagePlanTest {

    private final LiteMessagePlan.Plans plans = new LiteMessagePlan.Plans();

    private Map<Integer, LiteFieldInfo> fields(Class<?> type) {
        Map<Integer, LiteFieldInfo> fields = new LinkedHashMap<>();
        for (LiteFieldInfo field : plans.get(type).fields) {
            fields.put(field.number, field);
        }
        return fields;
    }

    @Test
    public void fieldNumberOrder() {
        LiteFieldInfo[] fields = plans.get(TestAllTypesProto3.class).fields;
        for (int i = 1; i < fields.length; i++) {
            assertThat(fields[i].number).isGreaterThan(fields[i - 1].number);
        }
        assertThat(fields[0].jsonName).isEqualTo("optionalInt32");
        // all fields of the message, no extensions or nested types
        assertThat(fields.length).isEqualTo(TestAllTypesProto3.class.getDeclaredFields().length > 0 ? countFieldNumberConstants(TestAllTypesProto3.class) : 0);
    }

    private static int countFieldNumberConstants(Class<?> type) {
        int count = 0;
        for (java.lang.reflect.Field field : type.getDeclaredFields()) {
            if (field.getName().endsWith("_FIELD_NUMBER")) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void namesWhichClashWithMethods() {
        // protoc appends an underscore to the accessors of a field named class: getClass_(). From the tests of curioswitch protobuf-jackson
        Map<Integer, LiteFieldInfo> fields = fields(JsonTestProto.TestRegression.class);

        assertThat(fields.get(1).jsonName).isEqualTo("feedIds");
        assertThat(fields.get(2).jsonName).isEqualTo("class");
        assertThat(fields.get(2).getter.getName()).isEqualTo("getClass_");
    }

    @Test
    public void jsonNames() {
        Map<Integer, LiteFieldInfo> fields = fields(TestAllTypesProto3.class);

        Map<Integer, String> expected = new LinkedHashMap<>();
        expected.put(1, "optionalInt32");
        expected.put(14, "optionalString");
        expected.put(18, "optionalNestedMessage");
        expected.put(21, "optionalNestedEnum");
        expected.put(31, "repeatedInt32");
        expected.put(56, "mapInt32Int32");
        expected.put(111, "oneofUint32");
        expected.put(201, "optionalBoolWrapper");
        expected.put(302, "optionalTimestamp");
        // field name to JSON name conventions, the same as JsonFormat
        expected.put(401, "fieldname1");
        expected.put(402, "fieldName2");
        expected.put(403, "FieldName3");
        expected.put(404, "fieldName4");
        expected.put(405, "field0name5");
        expected.put(406, "field0Name6");
        expected.put(407, "fieldName7");
        expected.put(409, "fieldName9");
        expected.put(411, "FIELDNAME11");
        expected.put(412, "FIELDName12");
        expected.put(413, "FieldName13");
        expected.put(414, "FieldName14");
        expected.put(415, "fieldName15");
        expected.put(416, "fieldName16");
        expected.put(417, "fieldName17");
        // known differences: a leading upper case letter cannot be told apart from the capitalization of the accessor names
        expected.put(408, "fieldName8"); // JsonFormat: FieldName8
        expected.put(410, "fieldName10"); // JsonFormat: FieldName10
        expected.put(418, "fieldName18"); // JsonFormat: FieldName18

        for (Map.Entry<Integer, String> entry : expected.entrySet()) {
            assertThat(fields.get(entry.getKey()).jsonName).isEqualTo(entry.getValue());
        }
    }

    @Test
    public void kinds() {
        Map<Integer, LiteFieldInfo> fields = fields(TestAllTypesProto3.class);

        LiteFieldInfo optionalInt32 = fields.get(1);
        assertThat(optionalInt32.kind).isEqualTo(LiteFieldInfo.SINGULAR);
        assertThat(optionalInt32.hazzer).isNull();
        assertThat(optionalInt32.valueType).isEqualTo(LiteValueType.INT);

        LiteFieldInfo nestedMessage = fields.get(18);
        assertThat(nestedMessage.hazzer).isNotNull();
        assertThat(nestedMessage.valueType).isEqualTo(LiteValueType.MESSAGE);

        LiteFieldInfo nestedEnum = fields.get(21);
        assertThat(nestedEnum.valueType).isEqualTo(LiteValueType.ENUM);
        assertThat(nestedEnum.valueGetter.getName()).isEqualTo("getOptionalNestedEnumValue");

        LiteFieldInfo repeatedNestedEnum = fields.get(51);
        assertThat(repeatedNestedEnum.kind).isEqualTo(LiteFieldInfo.REPEATED);
        assertThat(repeatedNestedEnum.valueType).isEqualTo(LiteValueType.ENUM);
        assertThat(repeatedNestedEnum.valueGetter.getName()).isEqualTo("getRepeatedNestedEnumValueList");

        LiteFieldInfo mapStringNestedEnum = fields.get(73);
        assertThat(mapStringNestedEnum.kind).isEqualTo(LiteFieldInfo.MAP);
        assertThat(mapStringNestedEnum.keyType).isEqualTo(LiteValueType.STRING);
        assertThat(mapStringNestedEnum.getter.getName()).isEqualTo("getMapStringNestedEnumMap");
        assertThat(mapStringNestedEnum.valueGetter.getName()).isEqualTo("getMapStringNestedEnumValueMap");

        LiteFieldInfo oneofUint32 = fields.get(111);
        assertThat(oneofUint32.hazzer.getName()).isEqualTo("hasOneofUint32");

        LiteFieldInfo nullValue = fields.get(307);
        assertThat(nullValue.valueType).isEqualTo(LiteValueType.NULL_VALUE);
    }

    @Test
    public void proto2() {
        Map<Integer, LiteFieldInfo> fields = fields(TestAllTypesProto2.class);

        // explicit presence
        assertThat(fields.get(1).hazzer.getName()).isEqualTo("hasOptionalInt32");
        // closed enum: no numbers for unknown values
        assertThat(fields.get(21).valueGetter).isNull();
        // groups are named after the type
        assertThat(fields.get(201).jsonName).isEqualTo("data");
        assertThat(fields.get(204).jsonName).isEqualTo("multiWordGroupField"); // JsonFormat: multiwordgroupfield
        // extensions are not fields
        assertThat(fields.containsKey(120)).isFalse();
    }

    @Test
    public void javaCamelCase() {
        assertThat(LiteMessagePlan.toJavaCamelCase("optional_int32")).isEqualTo("OptionalInt32");
        assertThat(LiteMessagePlan.toJavaCamelCase("field0name5")).isEqualTo("Field0Name5");
        assertThat(LiteMessagePlan.toJavaCamelCase("_field_name3")).isEqualTo("FieldName3");
    }

    @Test
    public void jsonName() {
        assertThat(LiteMessagePlan.toJsonName("field_name2")).isEqualTo("fieldName2");
        assertThat(LiteMessagePlan.toJsonName("__field_name13")).isEqualTo("FieldName13");
        assertThat(LiteMessagePlan.toJsonName("field_0_name6")).isEqualTo("field0Name6");
        // not aligned with the accessor name: fall back to lower case
        assertThat(LiteMessagePlan.toJsonName("FOO_BAR", "Something")).isEqualTo("fooBar");
        // accessor names which would clash get an underscore
        assertThat(LiteMessagePlan.toJsonName("CLASS", "Class_")).isEqualTo("class");
    }
}
