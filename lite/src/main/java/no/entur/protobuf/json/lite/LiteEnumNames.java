package no.entur.protobuf.json.lite;

import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.protobuf.Internal;

import java.util.HashMap;
import java.util.Map;

/**
 * Names of the values of an enum, by number. The first value wins for aliases, like for the descriptor-based JSON writers.
 */

final class LiteEnumNames {

    private final Map<Integer, SerializableString> names = new HashMap<>();

    LiteEnumNames(Class<?> enumType) {
        for (Object constant : enumType.getEnumConstants()) {
            int number;
            try {
                number = ((Internal.EnumLite) constant).getNumber();
            } catch (IllegalArgumentException e) {
                // UNRECOGNIZED
                continue;
            }
            if (!names.containsKey(number)) {
                names.put(number, new SerializedString(((Enum<?>) constant).name()));
            }
        }
    }

    /**
     * @param number enum number
     * @return the name, or null if unknown
     */

    SerializableString getName(int number) {
        return names.get(number);
    }

    /**
     * Number of an enum value.
     *
     * @param value enum value, not UNRECOGNIZED
     * @return number
     */

    static int getNumber(Object value) {
        return ((Internal.EnumLite) value).getNumber();
    }
}
