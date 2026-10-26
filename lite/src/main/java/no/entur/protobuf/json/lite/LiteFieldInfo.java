package no.entur.protobuf.json.lite;

import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import no.entur.protobuf.json.filter.JsonOutputSize;

import java.lang.reflect.Method;

/**
 * A field of a message class, with its JSON name and accessors.
 */

final class LiteFieldInfo {

    static final int SINGULAR = 0;
    static final int REPEATED = 1;
    static final int MAP = 2;

    final int number;
    final String jsonName;
    final SerializableString name;
    final int kind;

    /** getX(), getXList() or getXMap() */
    final Method getter;
    /** hasX(), or null for fields without presence */
    final Method hazzer;
    /** getXValue(), getXValueList() or getXValueMap() for open enums, or null */
    final Method valueGetter;

    /** type of the value, or of the elements or map values */
    final LiteValueType valueType;
    /** enum or message class of the values, or null */
    final Class<?> valueClass;
    final LiteEnumNames enumNames;

    /** map key type, or null */
    final LiteValueType keyType;

    // size of the quoted name and colon, for max size
    private final int nameCharSize;
    private final int nameUtf8Size;

    LiteFieldInfo(int number, String jsonName, int kind, Method getter, Method hazzer, Method valueGetter, LiteValueType valueType, Class<?> valueClass,
            LiteEnumNames enumNames, LiteValueType keyType) {
        this.number = number;
        this.jsonName = jsonName;
        this.name = new SerializedString(jsonName);
        this.kind = kind;
        this.getter = getter;
        this.hazzer = hazzer;
        this.valueGetter = valueGetter;
        this.valueType = valueType;
        this.valueClass = valueClass;
        this.enumNames = enumNames;
        this.keyType = keyType;
        this.nameCharSize = JsonOutputSize.getNameSize(jsonName, false);
        this.nameUtf8Size = JsonOutputSize.getNameSize(jsonName, true);
    }

    /**
     * Size of the field name as written, for max size.
     *
     * @param utf8 true if writing UTF-8 bytes, false if characters
     * @return size of the quoted name and the colon
     */

    int getNameSize(boolean utf8) {
        return utf8 ? nameUtf8Size : nameCharSize;
    }

    @Override
    public String toString() {
        return jsonName + " = " + number;
    }
}
