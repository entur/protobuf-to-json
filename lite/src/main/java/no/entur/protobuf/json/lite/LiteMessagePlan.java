package no.entur.protobuf.json.lite;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per message class information for writing JSON: the fields in field number order, with their JSON names and accessors.
 * <br><br>
 * Lite messages have no descriptors, so the fields are found from the generated code: the public {@code *_FIELD_NUMBER}
 * constants give the field numbers (and the field names in upper case), the public accessors ({@code getX}, {@code hasX},
 * {@code getXList}, {@code getXMap}, {@code getXValue} ...) the values. On Android, these must be kept when shrinking.
 * <br><br>
 * JSON names are derived like protoc does (lower camel case, capitalizing letters after underscores), restoring
 * upper case letters from the accessor names where possible. Upper case letters which cannot be told apart from
 * the capitalization done for accessor names (the first letter, and letters after digits) are assumed to be lower case,
 * as recommended by the protobuf style guide. Custom json_name options are not available in lite messages.
 * <br><br>
 * Based on the reflection-based {@code MessageLiteToString} of protobuf (BSD-3-Clause).
 */

final class LiteMessagePlan {

    /**
     * Cache of plans, and of enum names.
     */

    static final class Plans {

        private final Map<Class<?>, LiteMessagePlan> plans = new ConcurrentHashMap<>();
        private final Map<Class<?>, LiteEnumNames> enumNames = new ConcurrentHashMap<>();

        LiteMessagePlan get(Class<?> type) {
            LiteMessagePlan plan = plans.get(type);
            if (plan == null) {
                plan = new LiteMessagePlan(type, this);
                LiteMessagePlan existing = plans.putIfAbsent(type, plan);
                if (existing != null) {
                    plan = existing;
                }
            }
            return plan;
        }

        LiteEnumNames getEnumNames(Class<?> type) {
            LiteEnumNames names = enumNames.get(type);
            if (names == null) {
                names = new LiteEnumNames(type);
                LiteEnumNames existing = enumNames.putIfAbsent(type, names);
                if (existing != null) {
                    names = existing;
                }
            }
            return names;
        }
    }

    private static final String FIELD_NUMBER_SUFFIX = "_FIELD_NUMBER";

    final Class<?> type;
    final LiteWellKnownType wellKnownType;
    final LiteFieldInfo[] fields;

    LiteMessagePlan(Class<?> type, Plans plans) {
        this.type = type;
        this.wellKnownType = LiteWellKnownType.of(type);

        Accessors accessors = new Accessors(type);
        List<LiteFieldInfo> list = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers) || field.getType() != int.class || !field.getName().endsWith(FIELD_NUMBER_SUFFIX)) {
                continue;
            }
            int number;
            try {
                number = field.getInt(null);
            } catch (IllegalAccessException e) {
                continue;
            }
            String base = field.getName().substring(0, field.getName().length() - FIELD_NUMBER_SUFFIX.length());
            LiteFieldInfo info = createField(base, number, accessors, plans);
            if (info != null) {
                list.add(info);
            }
        }
        Collections.sort(list, new Comparator<LiteFieldInfo>() {
            @Override
            public int compare(LiteFieldInfo a, LiteFieldInfo b) {
                return Integer.compare(a.number, b.number);
            }
        });
        this.fields = list.toArray(new LiteFieldInfo[0]);
    }

    /**
     * Public no-argument accessors of a message class, by name and by upper case name.
     */

    private static final class Accessors {

        private final Map<String, Method> byName = new HashMap<>();
        private final Map<String, List<Method>> byUpperCaseName = new HashMap<>();

        Accessors(Class<?> type) {
            for (Method method : type.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers) || method.isBridge() || method.isSynthetic() || method.getParameterTypes().length != 0) {
                    continue;
                }
                byName.put(method.getName(), method);
                String upper = method.getName().toUpperCase(Locale.ROOT);
                List<Method> methods = byUpperCaseName.get(upper);
                if (methods == null) {
                    methods = new ArrayList<>(1);
                    byUpperCaseName.put(upper, methods);
                }
                methods.add(method);
            }
        }

        /**
         * Find an accessor, by its exact name, or else by its name ignoring case if that is not ambiguous.
         */

        Method find(String prefix, String camelCase, String upperCase, Class<?> returnType, boolean exactReturnType) {
            Method method = byName.get(prefix + camelCase);
            if (method != null && matches(method, returnType, exactReturnType)) {
                return method;
            }
            List<Method> methods = byUpperCaseName.get(prefix.toUpperCase(Locale.ROOT) + upperCase);
            if (methods == null) {
                return null;
            }
            Method found = null;
            for (Method candidate : methods) {
                if (matches(candidate, returnType, exactReturnType)) {
                    if (found != null) {
                        // ambiguous
                        return null;
                    }
                    found = candidate;
                }
            }
            return found;
        }

        Method get(String name, Class<?> returnType) {
            Method method = byName.get(name);
            if (method != null && method.getReturnType() == returnType) {
                return method;
            }
            return null;
        }

        private static boolean matches(Method method, Class<?> returnType, boolean exactReturnType) {
            Class<?> actual = method.getReturnType();
            if (exactReturnType) {
                return actual == returnType;
            }
            // singular fields: anything but collections
            return actual != List.class && actual != Map.class && actual != void.class;
        }
    }

    private static LiteFieldInfo createField(String base, int number, Accessors accessors, Plans plans) {
        String camelCase = toJavaCamelCase(base.toLowerCase(Locale.ROOT));
        String upperCase = base.replace("_", "");

        LiteFieldInfo info = createField(base, number, camelCase, upperCase, accessors, plans);
        if (info == null) {
            // protoc appends an underscore to names which would clash with other methods, i.e. getClass_() for a field named class
            info = createField(base, number, camelCase + "_", upperCase + "_", accessors, plans);
        }
        return info;
    }

    private static LiteFieldInfo createField(String base, int number, String camelCase, String upperCase, Accessors accessors, Plans plans) {
        Method getter;
        int kind;
        String suffix;
        if ((getter = accessors.find("get", camelCase + "Map", upperCase + "MAP", Map.class, true)) != null) {
            kind = LiteFieldInfo.MAP;
            suffix = getter.getName().substring(3, getter.getName().length() - 3);
        } else if ((getter = accessors.find("get", camelCase + "List", upperCase + "LIST", List.class, true)) != null) {
            kind = LiteFieldInfo.REPEATED;
            suffix = getter.getName().substring(3, getter.getName().length() - 4);
        } else if ((getter = accessors.find("get", camelCase, upperCase, null, false)) != null) {
            kind = LiteFieldInfo.SINGULAR;
            suffix = getter.getName().substring(3);
        } else {
            // i.e. an extension
            return null;
        }

        Method hazzer = kind == LiteFieldInfo.SINGULAR ? accessors.get("has" + suffix, boolean.class) : null;

        LiteValueType keyType = null;
        Class<?> valueClass;
        Method valueGetter = null;
        if (kind == LiteFieldInfo.SINGULAR) {
            valueClass = getter.getReturnType();
            if (valueClass.isEnum()) {
                valueGetter = accessors.get("get" + suffix + "Value", int.class);
            }
        } else if (kind == LiteFieldInfo.REPEATED) {
            valueClass = getTypeArgument(getter, 0);
            if (valueClass != null && valueClass.isEnum()) {
                valueGetter = accessors.get("get" + suffix + "ValueList", List.class);
            }
        } else {
            Class<?> keyClass = getTypeArgument(getter, 0);
            keyType = keyClass != null ? LiteValueType.of(keyClass) : LiteValueType.DYNAMIC;
            valueClass = getTypeArgument(getter, 1);
            if (valueClass != null && valueClass.isEnum()) {
                valueGetter = accessors.get("get" + suffix + "ValueMap", Map.class);
            }
        }
        LiteValueType valueType = valueClass != null ? LiteValueType.of(valueClass) : LiteValueType.DYNAMIC;
        LiteEnumNames enumNames = valueType == LiteValueType.ENUM ? plans.getEnumNames(valueClass) : null;

        return new LiteFieldInfo(number, toJsonName(base, suffix), kind, getter, hazzer, valueGetter, valueType, valueClass, enumNames, keyType);
    }

    private static Class<?> getTypeArgument(Method method, int index) {
        // generic signatures might have been removed when shrinking
        Type type = method.getGenericReturnType();
        if (type instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
            if (index < arguments.length && arguments[index] instanceof Class) {
                return (Class<?>) arguments[index];
            }
        }
        return null;
    }

    /**
     * Camel case like protoc's names for Java accessors: letters after underscores and digits, and the first letter,
     * are capitalized; underscores are removed.
     *
     * @param name field name
     * @return camel case name
     */

    static String toJavaCamelCase(String name) {
        StringBuilder result = new StringBuilder(name.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c >= 'a' && c <= 'z') {
                result.append(capitalizeNext ? (char) (c - ('a' - 'A')) : c);
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

    /**
     * JSON name of a field.
     *
     * @param base     upper case field name, from the field number constant
     * @param accessor the accessor name without prefix and suffix, i.e. FieldName for getFieldNameList
     * @return JSON name
     */

    static String toJsonName(String base, String accessor) {
        int letters = 0;
        for (int i = 0; i < base.length(); i++) {
            if (isLetterOrDigit(base.charAt(i))) {
                letters++;
            }
        }
        // protoc appends an underscore to accessor names which would clash, i.e. getClass_
        boolean aligned = accessor.length() == letters || (accessor.length() == letters + 1 && accessor.endsWith("_"));

        StringBuilder original = new StringBuilder(base.length());
        boolean capitalized = true; // whether the accessor capitalizes the next letter
        int index = 0;
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                boolean upper = false;
                if (aligned) {
                    if (!capitalized) {
                        upper = isUpperCase(accessor.charAt(index));
                    } else if (i + 1 < base.length() && isUpperCase(base.charAt(i + 1))) {
                        // ambiguous, but the next letter is certainly upper case, as in FIELD_NAME
                        upper = isUpperCase(accessor.charAt(index + 1));
                    }
                }
                original.append(upper ? c : (char) (c + ('a' - 'A')));
                capitalized = false;
                index++;
            } else if (c >= '0' && c <= '9') {
                original.append(c);
                capitalized = true;
                index++;
            } else {
                original.append(c);
                capitalized = true;
            }
        }
        return toJsonName(original);
    }

    private static boolean isLetterOrDigit(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    private static boolean isUpperCase(char c) {
        return c >= 'A' && c <= 'Z';
    }


    /**
     * Like protoc: remove underscores, and capitalize the letter after each.
     *
     * @param name field name
     * @return JSON name
     */

    static String toJsonName(CharSequence name) {
        StringBuilder result = new StringBuilder(name.length());
        boolean capitalizeNext = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(c >= 'a' && c <= 'z' ? (char) (c - ('a' - 'A')) : c);
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
