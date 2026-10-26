package no.entur.protobuf.json.test;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.util.JsonFormat;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the string values in the JSON of a message which max string length does not apply to: 64-bit integers, enum names,
 * timestamps, durations, and NaN and infinity. The JSON is walked together with the descriptors of the message, so that the
 * reference filter, which only sees JSON, can leave these strings as they are.
 * <br><br>
 * A location is a list of field names and map keys (strings) and array indexes (integers), from the root. The lite writer
 * writes unsigned map keys as signed values, so for such keys the locations with the signed key are included too.
 */

public final class UntruncatedValues {

    private static final JsonMapper MAPPER = new JsonMapper();

    // well-known types written as the value of a "value" field within google.protobuf.Any
    private static final Set<String> ANY_VALUE_TYPES = Set.of(
            "google.protobuf.Any", "google.protobuf.Timestamp", "google.protobuf.Duration", "google.protobuf.FieldMask",
            "google.protobuf.Struct", "google.protobuf.Value", "google.protobuf.ListValue",
            "google.protobuf.DoubleValue", "google.protobuf.FloatValue", "google.protobuf.Int64Value", "google.protobuf.UInt64Value",
            "google.protobuf.Int32Value", "google.protobuf.UInt32Value", "google.protobuf.BoolValue", "google.protobuf.StringValue",
            "google.protobuf.BytesValue");

    private static final BigInteger TWO_TO_32 = BigInteger.ONE.shiftLeft(32);
    private static final BigInteger TWO_TO_64 = BigInteger.ONE.shiftLeft(64);

    private final JsonFormat.TypeRegistry typeRegistry;
    private final Set<List<Object>> locations = new LinkedHashSet<>();

    private UntruncatedValues(JsonFormat.TypeRegistry typeRegistry) {
        this.typeRegistry = typeRegistry;
    }

    /**
     * @param json         JSON written for a message
     * @param type         type of the message
     * @param typeRegistry types of google.protobuf.Any values
     * @return locations of the string values which are not truncated
     */

    public static Set<List<Object>> find(String json, Descriptor type, JsonFormat.TypeRegistry typeRegistry) {
        UntruncatedValues values = new UntruncatedValues(typeRegistry);
        values.message(MAPPER.readTree(json), type, List.of());
        return values.locations;
    }

    /**
     * @param locations locations
     * @return the locations as a JSON array of arrays
     */

    public static String toJson(Set<List<Object>> locations) {
        return MAPPER.writeValueAsString(locations);
    }

    private void message(JsonNode node, Descriptor type, List<Object> location) {
        switch (type.getFullName()) {
            case "google.protobuf.Timestamp", "google.protobuf.Duration", "google.protobuf.Int64Value", "google.protobuf.UInt64Value",
                 "google.protobuf.FloatValue", "google.protobuf.DoubleValue" -> {
                if (node.isString()) {
                    locations.add(location);
                    return;
                }
                // out of range timestamps and durations are written as objects
            }
            case "google.protobuf.Struct" -> {
                struct(node, location);
                return;
            }
            case "google.protobuf.Value" -> {
                value(node, location);
                return;
            }
            case "google.protobuf.ListValue" -> {
                listValue(node, location);
                return;
            }
            case "google.protobuf.Any" -> {
                any(node, location);
                return;
            }
            default -> {
                // fields
            }
        }
        if (!node.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            FieldDescriptor field = findField(type, entry.getKey());
            if (field == null) {
                continue;
            }
            List<Object> fieldLocation = append(location, entry.getKey());
            JsonNode value = entry.getValue();
            if (field.isMapField()) {
                FieldDescriptor keyField = field.getMessageType().findFieldByNumber(1);
                FieldDescriptor valueField = field.getMessageType().findFieldByNumber(2);
                for (Map.Entry<String, JsonNode> mapEntry : value.properties()) {
                    fieldValue(mapEntry.getValue(), valueField, append(fieldLocation, mapEntry.getKey()));
                    String signedKey = signedKey(keyField, mapEntry.getKey());
                    if (signedKey != null) {
                        fieldValue(mapEntry.getValue(), valueField, append(fieldLocation, signedKey));
                    }
                }
            } else if (field.isRepeated()) {
                for (int i = 0; i < value.size(); i++) {
                    fieldValue(value.get(i), field, append(fieldLocation, i));
                }
            } else {
                fieldValue(value, field, fieldLocation);
            }
        }
    }

    private void fieldValue(JsonNode node, FieldDescriptor field, List<Object> location) {
        switch (field.getJavaType()) {
            // 64-bit integers, enum names, and NaN and infinity
            case LONG, ENUM, FLOAT, DOUBLE -> {
                if (node.isString()) {
                    locations.add(location);
                }
            }
            case MESSAGE -> message(node, field.getMessageType(), location);
            default -> {
                // not a string, or text
            }
        }
    }

    private void struct(JsonNode node, List<Object> location) {
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            value(entry.getValue(), append(location, entry.getKey()));
        }
    }

    private void value(JsonNode node, List<Object> location) {
        if (node.isObject()) {
            struct(node, location);
        } else if (node.isArray()) {
            listValue(node, location);
        } else if (node.isString()) {
            // NaN and infinity numbers are written as strings, other strings are text
            String text = node.stringValue();
            if (text.equals("NaN") || text.equals("Infinity") || text.equals("-Infinity")) {
                locations.add(location);
            }
        }
    }

    private void listValue(JsonNode node, List<Object> location) {
        for (int i = 0; i < node.size(); i++) {
            value(node.get(i), append(location, i));
        }
    }

    private void any(JsonNode node, List<Object> location) {
        JsonNode typeUrl = node.get("@type");
        if (typeUrl == null) {
            return;
        }
        String url = typeUrl.stringValue();
        Descriptor type = typeRegistry.find(url.substring(url.lastIndexOf('/') + 1));
        if (type == null) {
            return;
        }
        if (ANY_VALUE_TYPES.contains(type.getFullName())) {
            JsonNode value = node.get("value");
            if (value != null) {
                message(value, type, append(location, "value"));
            }
        } else {
            message(node, type, location);
        }
    }

    private static FieldDescriptor findField(Descriptor type, String jsonName) {
        for (FieldDescriptor field : type.getFields()) {
            if (field.getJsonName().equals(jsonName)) {
                return field;
            }
        }
        return null;
    }

    private static String signedKey(FieldDescriptor keyField, String key) {
        BigInteger value;
        switch (keyField.getType()) {
            case UINT32, FIXED32 -> {
                value = new BigInteger(key);
                return value.bitLength() == 32 ? value.subtract(TWO_TO_32).toString() : null;
            }
            case UINT64, FIXED64 -> {
                value = new BigInteger(key);
                return value.bitLength() == 64 ? value.subtract(TWO_TO_64).toString() : null;
            }
            default -> {
                return null;
            }
        }
    }

    private static List<Object> append(List<Object> location, Object element) {
        List<Object> next = new ArrayList<>(location.size() + 1);
        next.addAll(location);
        next.add(element);
        return next;
    }
}
