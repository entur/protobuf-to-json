package no.entur.protobuf.json.lite;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import com.google.protobuf.NullValue;

/**
 * Java type of a field value, as returned by the generated accessors.
 */

enum LiteValueType {
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    BOOL,
    STRING,
    BYTES,
    ENUM,
    NULL_VALUE,
    MESSAGE,
    /** not known up front, i.e. generic types were removed; decided by the runtime type of each value */
    DYNAMIC;

    static LiteValueType of(Class<?> type) {
        if (type == int.class || type == Integer.class) {
            return INT;
        }
        if (type == long.class || type == Long.class) {
            return LONG;
        }
        if (type == float.class || type == Float.class) {
            return FLOAT;
        }
        if (type == double.class || type == Double.class) {
            return DOUBLE;
        }
        if (type == boolean.class || type == Boolean.class) {
            return BOOL;
        }
        if (type == String.class) {
            return STRING;
        }
        if (type == ByteString.class) {
            return BYTES;
        }
        if (type == NullValue.class) {
            return NULL_VALUE;
        }
        if (type.isEnum()) {
            return ENUM;
        }
        if (MessageLite.class.isAssignableFrom(type)) {
            return MESSAGE;
        }
        return DYNAMIC;
    }

    static LiteValueType ofValue(Object value) {
        return of(value instanceof Enum ? ((Enum<?>) value).getDeclaringClass() : value.getClass());
    }
}
