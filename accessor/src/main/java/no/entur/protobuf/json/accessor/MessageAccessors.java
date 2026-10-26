package no.entur.protobuf.json.accessor;

import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Duration;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.FieldMask;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.Internal;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import no.entur.protobuf.json.plan.MessagePlan;
import no.entur.protobuf.json.plan.MessagePlan.FieldInfo;
import no.entur.protobuf.json.plan.WellKnownType;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The accessors of a generated message class, per field, in field number order. They are found by the names protoc gives them
 * (following the rules of protoc's Java code generator, BSD-3-Clause), and verified with reflection.
 */

final class MessageAccessors {

    /**
     * How a single value is read and written.
     */

    enum Kind {
        INT32(int.class),
        UINT32(int.class),
        INT64(long.class),
        UINT64(long.class),
        BOOL(boolean.class),
        FLOAT(float.class),
        DOUBLE(double.class),
        STRING(String.class),
        BYTES(ByteString.class),
        /** open enum, read as its number with getXValue() */
        ENUM(int.class),
        /** closed enum, read as the enum constant */
        CLOSED_ENUM(null),
        MESSAGE(null),
        WELL_KNOWN(null);

        /** type of a single value, if the same for all fields of this kind */
        final Class<?> type;

        Kind(Class<?> type) {
            this.type = type;
        }

        static Kind of(FieldInfo field) {
            return switch (field.type) {
                case INT32, SINT32, SFIXED32 -> INT32;
                case UINT32, FIXED32 -> UINT32;
                case INT64, SINT64, SFIXED64 -> INT64;
                case UINT64, FIXED64 -> UINT64;
                case BOOL -> BOOL;
                case FLOAT -> FLOAT;
                case DOUBLE -> DOUBLE;
                case STRING -> STRING;
                case BYTES -> BYTES;
                case ENUM -> field.closedEnum ? CLOSED_ENUM : ENUM;
                case MESSAGE, GROUP -> WellKnownType.of(field.descriptor.getMessageType()) != null ? WELL_KNOWN : MESSAGE;
            };
        }
    }

    /**
     * Classes of the well-known types, which are written by {@linkplain AccessorJsonValues}.
     */

    private static final Map<WellKnownType, Class<? extends Message>> WELL_KNOWN_TYPES = Map.ofEntries(
            Map.entry(WellKnownType.ANY, Any.class),
            Map.entry(WellKnownType.TIMESTAMP, Timestamp.class),
            Map.entry(WellKnownType.DURATION, Duration.class),
            Map.entry(WellKnownType.FIELD_MASK, FieldMask.class),
            Map.entry(WellKnownType.STRUCT, Struct.class),
            Map.entry(WellKnownType.VALUE, Value.class),
            Map.entry(WellKnownType.LIST_VALUE, ListValue.class),
            Map.entry(WellKnownType.BOOL_VALUE, BoolValue.class),
            Map.entry(WellKnownType.INT32_VALUE, Int32Value.class),
            Map.entry(WellKnownType.UINT32_VALUE, UInt32Value.class),
            Map.entry(WellKnownType.INT64_VALUE, Int64Value.class),
            Map.entry(WellKnownType.UINT64_VALUE, UInt64Value.class),
            Map.entry(WellKnownType.STRING_VALUE, StringValue.class),
            Map.entry(WellKnownType.BYTES_VALUE, BytesValue.class),
            Map.entry(WellKnownType.FLOAT_VALUE, FloatValue.class),
            Map.entry(WellKnownType.DOUBLE_VALUE, DoubleValue.class));

    /**
     * Accessor names which would collide with methods of the base classes, so protoc appends an underscore.
     */

    private static final Set<String> FORBIDDEN_NAMES = Set.of(
            "Class", "DefaultInstanceForType", "ParserForType", "SerializedSize", "AllFields", "DescriptorForType",
            "InitializationErrorString", "UnknownFields", "CachedSize");

    static final class Field {

        final FieldInfo info;
        /** of a single value, for map fields of the map value */
        final Kind kind;
        final WellKnownType wellKnownType;
        /** of a single value: primitive, String, ByteString, the enum class or the message class */
        final Class<?> type;

        /** singular fields with presence; null for implicit presence */
        final Method has;
        /** singular: getX() or getXValue(); repeated: getX(int) or getXValue(int); map: getXMap() or getXValueMap() */
        final Method get;
        /** repeated and map fields: getXCount() */
        final Method count;

        /** map fields: kind of the key */
        final Kind keyKind;

        Field(FieldInfo info, Kind kind, Class<?> type, Method has, Method get, Method count, Kind keyKind) {
            this.info = info;
            this.kind = kind;
            this.wellKnownType = kind == Kind.WELL_KNOWN ? WellKnownType.of((info.map ? info.mapValue : info).descriptor.getMessageType()) : null;
            this.type = type;
            this.has = has;
            this.get = get;
            this.count = count;
            this.keyKind = keyKind;
        }

        boolean isRepeated() {
            return info.repeated && !info.map;
        }

        boolean isMap() {
            return info.map;
        }
    }

    final Class<? extends Message> type;
    final MessagePlan plan;
    final List<Field> fields;

    private MessageAccessors(Class<? extends Message> type, MessagePlan plan, List<Field> fields) {
        this.type = type;
        this.plan = plan;
        this.fields = fields;
    }

    /**
     * Find the accessors of a generated message class.
     *
     * @param type  message class
     * @param plans plans
     * @return accessors
     * @throws IllegalArgumentException if the class is not a generated message class, or an accessor is not found
     */

    static MessageAccessors of(Class<? extends Message> type, MessagePlan.Plans plans) {
        if (type == DynamicMessage.class || !Modifier.isPublic(type.getModifiers()) || !Modifier.isFinal(type.getModifiers())) {
            throw new IllegalArgumentException(type.getName() + " is not a generated message class");
        }
        Descriptor descriptor = getDescriptor(type);
        MessagePlan plan = plans.get(descriptor);

        List<FieldDescriptor> descriptors = descriptor.getFields();
        String[] names = getAccessorNames(descriptors);

        List<Field> fields = new ArrayList<>(descriptors.size());
        for (int i = 0; i < descriptors.size(); i++) {
            FieldInfo info = plan.getField(descriptors.get(i).getNumber());
            fields.add(getField(type, info, names[i]));
        }
        fields.sort(Comparator.comparingInt(field -> field.info.number));
        return new MessageAccessors(type, plan, List.copyOf(fields));
    }

    private static Descriptor getDescriptor(Class<? extends Message> type) {
        try {
            Method method = type.getMethod("getDescriptor");
            if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == Descriptor.class) {
                return (Descriptor) method.invoke(null);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(type.getName() + " is not a generated message class", e);
        }
        throw new IllegalArgumentException(type.getName() + " is not a generated message class");
    }

    private static Field getField(Class<? extends Message> type, FieldInfo info, String name) {
        if (info.map) {
            Kind keyKind = Kind.of(info.mapKey);
            Kind valueKind = Kind.of(info.mapValue);
            Method get = getMethod(type, "get" + name + (valueKind == Kind.ENUM ? "ValueMap" : "Map"), Map.class);
            Method count = getMethod(type, "get" + name + "Count", int.class);
            return new Field(info, valueKind, getMapValueType(get, info.mapValue, valueKind), null, get, count, keyKind);
        }
        Kind kind = Kind.of(info);
        if (info.repeated) {
            Method get = getMethod(type, "get" + name + (kind == Kind.ENUM ? "Value" : ""), getValueType(type, info, kind, name), int.class);
            Method count = getMethod(type, "get" + name + "Count", int.class);
            return new Field(info, kind, get.getReturnType(), null, get, count, null);
        }
        Method get = getMethod(type, "get" + name + (kind == Kind.ENUM ? "Value" : ""), getValueType(type, info, kind, name));
        Method has = info.descriptor.hasPresence() ? getMethod(type, "has" + name, boolean.class) : null;
        return new Field(info, kind, get.getReturnType(), has, get, null, null);
    }

    /**
     * @return the type of a single value, or null if it is a message or enum class (checked separately)
     */

    private static Class<?> getValueType(Class<? extends Message> type, FieldInfo info, Kind kind, String name) {
        if (kind.type != null) {
            return kind.type;
        }
        // the class of the message or enum, as returned by the getter
        Class<?> valueType;
        try {
            valueType = info.repeated ? type.getMethod("get" + name, int.class).getReturnType() : type.getMethod("get" + name).getReturnType();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("No accessor for field " + info.descriptor.getFullName() + " in " + type.getName(), e);
        }
        checkValueType(valueType, info, kind);
        return valueType;
    }

    private static Class<?> getMapValueType(Method get, FieldInfo value, Kind kind) {
        if (kind.type != null) {
            return kind.type;
        }
        // i.e. Map<String, NestedMessage>
        Type returnType = get.getGenericReturnType();
        if (returnType instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments()[1] instanceof Class<?> valueType) {
            checkValueType(valueType, value, kind);
            return valueType;
        }
        throw new IllegalArgumentException("Unexpected map type " + returnType + " of " + get);
    }

    private static void checkValueType(Class<?> valueType, FieldInfo info, Kind kind) {
        boolean expected = switch (kind) {
            case CLOSED_ENUM -> valueType.isEnum() && Internal.EnumLite.class.isAssignableFrom(valueType);
            case MESSAGE -> Message.class.isAssignableFrom(valueType) && valueType != DynamicMessage.class && Modifier.isPublic(valueType.getModifiers());
            case WELL_KNOWN -> valueType == WELL_KNOWN_TYPES.get(WellKnownType.of(info.descriptor.getMessageType()));
            default -> false;
        };
        if (!expected) {
            throw new IllegalArgumentException("Unexpected type " + valueType.getName() + " for field " + info.descriptor.getFullName());
        }
    }

    private static Method getMethod(Class<?> type, String name, Class<?> returnType, Class<?>... parameterTypes) {
        Method method;
        try {
            method = type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("No accessor " + name + " in " + type.getName(), e);
        }
        if (method.getReturnType() != returnType || Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Unexpected accessor " + method);
        }
        return method;
    }

    // ---- accessor names, as protoc generates them ----

    /**
     * Names of the accessors, without the get or has prefix, as protoc generates them: the field name in upper camel case
     * (for groups, the name of the group's message type), with an underscore appended if it would collide with a method of
     * the base classes. If two fields would get colliding accessors, both get their field number appended.
     *
     * @param fields the fields of a message type, in declaration order
     * @return names, in the same order
     */

    static String[] getAccessorNames(List<FieldDescriptor> fields) {
        String[] names = new String[fields.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = getCapitalizedName(fields.get(i));
        }
        boolean[] conflicting = new boolean[names.length];
        for (int i = 0; i < names.length; i++) {
            for (int j = i + 1; j < names.length; j++) {
                if (names[i].equals(names[j]) || isConflicting(fields.get(i), names[i], fields.get(j), names[j])) {
                    conflicting[i] = true;
                    conflicting[j] = true;
                }
            }
        }
        String[] result = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            result[i] = conflicting[i] ? names[i] + fields.get(i).getNumber() : names[i];
        }
        return result;
    }

    private static String getCapitalizedName(FieldDescriptor field) {
        String name = isGroupLike(field) ? field.getMessageType().getName() : field.getName();
        String camelCase = underscoresToCamelCase(name);
        return FORBIDDEN_NAMES.contains(camelCase) ? camelCase + "_" : camelCase;
    }

    /**
     * A repeated field and a singular field conflict if the singular field has the name of an accessor of the repeated field.
     */

    private static boolean isConflicting(FieldDescriptor field1, String name1, FieldDescriptor field2, String name2) {
        if (field1.isRepeated() == field2.isRepeated()) {
            return false;
        }
        if (field2.isRepeated()) {
            return isConflicting(field2, name2, field1, name1);
        }
        return name2.equals(name1 + "Count") || name2.equals(name1 + "List");
    }

    private static boolean isGroupLike(FieldDescriptor field) {
        if (field.getType() != FieldDescriptor.Type.GROUP) {
            return false;
        }
        Descriptor type = field.getMessageType();
        if (!field.getName().equals(type.getName().toLowerCase(Locale.ROOT)) || type.getFile() != field.getFile()) {
            return false;
        }
        return field.isExtension() ? type.getContainingType() == field.getExtensionScope() : type.getContainingType() == field.getContainingType();
    }

    /**
     * Like protoc: letters after an underscore or a digit are capitalized, other characters (i.e. underscores) are removed,
     * and the first letter is capitalized.
     */

    static String underscoresToCamelCase(String input) {
        StringBuilder result = new StringBuilder(input.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 'a' && c <= 'z') {
                result.append(capitalizeNext ? (char) (c - 'a' + 'A') : c);
                capitalizeNext = false;
            } else if (c >= 'A' && c <= 'Z') {
                result.append(c);
                capitalizeNext = false;
            } else if (c >= '0' && c <= '9') {
                result.append(c);
                capitalizeNext = true;
            } else {
                capitalizeNext = true;
            }
        }
        return result.toString();
    }
}
